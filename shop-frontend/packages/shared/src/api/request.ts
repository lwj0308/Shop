/**
 * Axios统一封装
 * 这是整个项目的HTTP请求基础层，所有接口请求都通过这里发出
 *
 * 主要功能：
 * 1. 自动携带Token - 每次请求自动在Header里加上登录令牌
 * 2. 401自动刷新Token - Token过期时自动用RefreshToken换新的（竞态安全）
 * 3. 统一错误处理 - 401跳登录、403提示无权限、500提示服务器错误
 * 4. 请求重试 - GET请求网络错误时自动重试1次
 * 5. 请求取消 - 页面切换时取消未完成的请求
 * 6. XSS防护 - POST请求体中的HTML标签自动转义
 * 7. 错误码映射 - 根据errorCode映射中文错误提示
 */

import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { getToken, getRefreshToken, setToken, clearToken } from '../utils/auth'
import { SUCCESS_CODE, getErrorMessage } from '../constants/errorCode'
import type { ApiResponse } from '../types'

/** 接受状态码（排队等异步场景，RL-13 引入）：202 表示请求已接收但未处理完成 */
const ACCEPTED_CODE = 202

/** 创建axios实例，配置基础信息 */
const service: AxiosInstance = axios.create({
  // baseURL从环境变量读取，不同环境（开发/生产）会自动切换
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000, // 请求超时时间10秒
})

/**
 * 生成幂等键（RL-14 引入）
 * <p>
 * 幂等键的作用：防止用户因为网络卡顿、连续点击"提交"按钮而导致重复下单。
 * 前端每次发起写请求（POST/PUT）时生成一个唯一键，后端收到后存入 Redis，
 * 如果同一个键第二次到达，后端直接返回"请勿重复提交"。
 * </p>
 * <p>
 * 组成规则：业务类型_用户ID_时间戳_随机串
 * - 业务类型：order/payment/comment 等，方便后端分类
 * - 用户ID：从 Token 里解析，登录后才有
 * - 时间戳：保证不同请求键不同
 * - 随机串：防止同一毫秒内的并发请求撞键
 * </p>
 *
 * @param businessType 业务类型，比如 'order'、'payment'
 * @returns 幂等键字符串
 */
export function generateIdempotentKey(businessType: string = 'default'): string {
  // 从 localStorage 读取用户信息（和 getToken 同源，避免循环依赖）
  let userId = 'anon'
  try {
    const token = getToken()
    if (token) {
      // 简单取 token 前 8 位作为用户标识，够用且不暴露完整 token
      userId = token.substring(0, 8)
    }
  } catch {
    // getToken 可能依赖 localStorage，SSR 环境下会抛错，忽略即可
  }
  const timestamp = Date.now()
  const random = Math.random().toString(36).slice(2, 10)
  return `${businessType}_${userId}_${timestamp}_${random}`
}

/**
 * 需要 frontend 注入 X-Idempotent-Key 的接口白名单（Header 策略）
 * <p>
 * 后端 @Idempotent 注解有两种 key 策略：
 * 1. Header 策略：注解无 key 属性，从 X-Idempotent-Key Header 获取 → 前端必须传
 * 2. SpEL 策略：注解有 key="#id" 属性，从业务参数提取 → 前端不能传（否则覆盖 SpEL）
 * </p>
 * <p>
 * Header 优先级最高：如果前端对 SpEL 策略接口传了 Header，会覆盖 SpEL 键，
 * 导致每次请求 key 不同（随机值），失去资源级幂等效果。
 * 因此仅对 Header 策略接口注入，其余接口由后端 SpEL 或状态机保障。
 * </p>
 */
const IDEMPOTENT_HEADER_URLS: Array<{ method: string; pattern: RegExp; businessType: string }> = [
  { method: 'post', pattern: /^\/order$/, businessType: 'order' },              // 创建订单
  { method: 'post', pattern: /^\/payment\/create/, businessType: 'payment' },   // 创建支付
  { method: 'post', pattern: /^\/product\/comment$/, businessType: 'comment' }, // 创建评价
]

/**
 * 判断请求是否需要注入 X-Idempotent-Key（Header 策略白名单匹配）
 * @param method HTTP 方法
 * @param url 请求 URL
 * @returns 匹配则返回业务类型（用于生成幂等键），不匹配返回 null
 */
function matchIdempotentHeader(method: string, url: string): string | null {
  const lowerMethod = (method || '').toLowerCase()
  // 去掉 query string，只匹配 path 部分
  const path = (url || '').split('?')[0]
  for (const rule of IDEMPOTENT_HEADER_URLS) {
    if (rule.method === lowerMethod && rule.pattern.test(path)) {
      return rule.businessType
    }
  }
  return null
}

/** 是否正在刷新Token的标记，防止多个请求同时刷新 */
let isRefreshing = false

/** Token刷新期间，暂存等待中的请求 */
let pendingRequests: Array<(token: string) => void> = []

/**
 * 存储所有正在进行的请求的AbortController
 * 页面切换时可以批量取消这些请求，避免内存泄漏和不必要的网络请求
 */
const pendingAbortControllers = new Map<string, AbortController>()

/**
 * 生成请求的唯一标识
 * 用URL+方法+参数组合作为key，用于管理请求的取消
 * @param config - 请求配置
 * @returns 请求唯一标识字符串
 */
function generateRequestKey(config: AxiosRequestConfig): string {
  const { url, method, params } = config
  return `${method}_${url}_${JSON.stringify(params)}`
}

/**
 * 给请求添加AbortController，用于取消请求
 * 每个请求开始前创建一个AbortController，存到Map里
 * @param config - 请求配置
 */
function addPendingRequest(config: InternalAxiosRequestConfig): void {
  const key = generateRequestKey(config)
  if (pendingAbortControllers.has(key)) {
    // 如果已有相同请求，先取消之前的
    pendingAbortControllers.get(key)?.abort()
  }
  const controller = new AbortController()
  config.signal = controller.signal
  pendingAbortControllers.set(key, controller)
}

/**
 * 请求完成后，从Map中移除对应的AbortController
 * @param config - 请求配置
 */
function removePendingRequest(config: AxiosRequestConfig): void {
  const key = generateRequestKey(config)
  pendingAbortControllers.delete(key)
}

/**
 * 取消所有正在进行的请求
 * 页面切换时调用，避免上一个页面的请求影响当前页面
 */
export function cancelAllRequests(): void {
  pendingAbortControllers.forEach((controller) => controller.abort())
  pendingAbortControllers.clear()
}

/**
 * 转义HTML特殊字符，防止XSS攻击
 * 把 < > & " ' 这些字符替换成HTML实体，让恶意脚本无法执行
 * @param str - 原始字符串
 * @returns 转义后的安全字符串
 */
function escapeHtml(str: string): string {
  const htmlEscapeMap: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',
  }
  return str.replace(/[&<>"']/g, (char) => htmlEscapeMap[char] || char)
}

/**
 * 递归转义对象中所有字符串值的HTML标签
 * 遍历对象的每个属性，如果是字符串就转义，如果是对象就递归处理
 * @param data - 需要转义的数据对象
 * @returns 转义后的数据对象
 */
function sanitizeData<T>(data: T): T {
  if (typeof data === 'string') {
    return escapeHtml(data) as T
  }
  if (Array.isArray(data)) {
    return data.map((item) => sanitizeData(item)) as T
  }
  if (data !== null && typeof data === 'object') {
    const sanitized = {} as T
    for (const key in data) {
      if (Object.prototype.hasOwnProperty.call(data, key)) {
        sanitized[key] = sanitizeData(data[key])
      }
    }
    return sanitized
  }
  return data
}

/**
 * 请求拦截器
 * 在每个请求发出之前，自动把Token塞到请求头里
 * 同时添加AbortController用于请求取消
 * RL-14：仅对 Header 策略接口（白名单）注入 X-Idempotent-Key 幂等键
 */
service.interceptors.request.use(
  (config) => {
    // 添加请求取消控制器
    addPendingRequest(config as InternalAxiosRequestConfig)

    const token = getToken()
    if (token && config.headers) {
      // Bearer是OAuth2标准格式，表示"持有者"令牌
      config.headers.Authorization = `Bearer ${token}`
    }

    // 对POST/PUT请求体做XSS防护，转义HTML标签
    if (config.data && ['post', 'put', 'patch'].includes(config.method || '')) {
      config.data = sanitizeData(config.data)
    }

    // RL-14：仅对 Header 策略接口注入 X-Idempotent-Key 幂等键
    // 后端 @Idempotent 注解有两种策略：
    //   Header 策略（无 key 属性）→ 前端传 X-Idempotent-Key，后端从 Header 取
    //   SpEL 策略（有 key="#id"）→ 前端不传 Header，后端从业务参数取
    // 如果对 SpEL 策略接口传了 Header，会覆盖 SpEL 键，导致每次请求 key 不同，失去资源级幂等
    // 因此仅对白名单（Header 策略）接口注入，其余接口由后端 SpEL 或状态机保障
    const method = config.method || ''
    const url = config.url || ''
    const businessType = matchIdempotentHeader(method, url)
    if (businessType && config.headers) {
      const existingKey = (config.headers as Record<string, string>)['X-Idempotent-Key']
      if (!existingKey) {
        ;(config.headers as Record<string, string>)['X-Idempotent-Key'] = generateIdempotentKey(businessType)
      }
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

/**
 * 响应拦截器
 * 在收到服务器响应后，统一处理错误情况
 * 比如：Token过期了自动刷新、没权限弹提示、服务器报错弹提示
 */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    // 请求完成，移除pending记录
    removePendingRequest(response.config)

    const res = response.data

    // code不为200表示业务逻辑出错（比如参数校验失败、余额不足等）
    // 注意：成功码是200，和后端Result.success的code保持一致
    // 例外：202表示"已接受，排队中"（RL-13 引入），不是错误，需要放行给调用方处理
    if (res.code !== SUCCESS_CODE && res.code !== ACCEPTED_CODE) {
      // 401表示Token过期或无效，需要刷新Token
      if (res.code === 401) {
        return handleTokenRefresh(response.config) as unknown as AxiosResponse<ApiResponse>
      }

      // 403表示没有权限访问该接口
      if (res.code === 403) {
        console.error('没有权限访问该资源')
      }

      // 根据错误码映射中文提示，找不到则用后端返回的message
      const errorMessage = getErrorMessage(res.code, res.message)
      // 其他错误，把错误信息抛出去，让调用方处理
      return Promise.reject(new Error(errorMessage))
    }

    // 成功（200）或排队中（202）都直接返回整个响应，调用方通过response.data获取数据
    return response
  },
  (error) => {
    // 请求完成（即使失败），移除pending记录
    if (error.config) {
      removePendingRequest(error.config)
    }

    // 请求被主动取消，不报错
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    // HTTP状态码级别的错误处理
    const status = error.response?.status
    switch (status) {
      case 401:
        return handleTokenRefresh(error.config)
      case 403:
        console.error('没有权限访问该资源')
        break
      case 500:
        console.error('服务器内部错误，请稍后重试')
        break
      default:
        console.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

/**
 * 处理Token刷新
 * 当Token过期（401）时，用RefreshToken去换新的AccessToken
 * 换成功后，自动重试之前失败的请求
 *
 * 竞态安全：多个请求同时401时，只有第一个会真正发起刷新请求，
 * 其他请求会排队等待，刷新成功后一起重试
 *
 * @param config - 原始请求的配置，刷新Token后要用这个配置重试
 */
function handleTokenRefresh(config: AxiosRequestConfig): Promise<unknown> {
  // 如果已经在刷新Token了，就把当前请求暂存起来，等刷新完再发
  if (isRefreshing) {
    return new Promise((resolve) => {
      pendingRequests.push((token: string) => {
        if (config.headers) {
          config.headers.Authorization = `Bearer ${token}`
        }
        resolve(service(config))
      })
    })
  }

  // 标记正在刷新，防止重复刷新
  isRefreshing = true

  return new Promise((resolve, reject) => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      // 没有RefreshToken，说明用户根本没登录过，直接跳登录页
      clearToken()
      redirectToLogin()
      reject(new Error('请重新登录'))
      return
    }

    // 用RefreshToken调后端接口换新的AccessToken
    axios
      .post(`${import.meta.env.VITE_API_BASE_URL || ''}/auth/refresh`, {
        refreshToken,
      })
      .then((res) => {
        const { accessToken, refreshToken: newRefreshToken } = res.data.data
        // 存储新的Token
        setToken(accessToken, newRefreshToken)

        // 重试所有暂存的请求
        pendingRequests.forEach((cb) => cb(accessToken))
        pendingRequests = []

        // 重试原始请求
        if (config.headers) {
          config.headers.Authorization = `Bearer ${accessToken}`
        }
        resolve(service(config))
      })
      .catch(() => {
        // 刷新Token也失败了，说明RefreshToken也过期了，只能重新登录
        clearToken()
        pendingRequests = []
        redirectToLogin()
        reject(new Error('登录已过期，请重新登录'))
      })
      .finally(() => {
        isRefreshing = false
      })
  })
}

/**
 * Token失效后的跳转处理
 *
 * 用户端：不再用 window.location.href 跳转到 /login（会与 /login→/ 重定向形成循环），
 * 而是触发全局 token-expired 事件，由布局组件监听后弹出 AuthModal 登录弹窗。
 *
 * 商家端：仍用整页跳转（商家端有独立的登录页）。
 */
function redirectToLogin(): void {
  clearToken()
  const currentPath = window.location.pathname
  if (currentPath.includes('/merchant')) {
    // 商家端：整页跳转到商家登录页
    window.location.href = '/merchant/login'
  } else {
    // 用户端：触发全局事件，由 DefaultLayout 监听并弹出 AuthModal
    window.dispatchEvent(new CustomEvent('token-expired'))
  }
}

/**
 * 封装GET请求
 * 支持网络错误时自动重试1次（仅GET请求，因为GET是幂等的）
 * @param url - 请求地址
 * @param params - 查询参数，会拼到URL后面
 * @param config - 额外的axios配置
 */
export function get<T = unknown>(
  url: string,
  params?: object,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  const requestConfig: AxiosRequestConfig = { params, ...config }

  return service.get(url, requestConfig).catch((error) => {
    // 仅对网络错误（无response）且是GET请求做重试
    if (!error.response && !axios.isCancel(error)) {
      // 重试1次
      return service.get(url, requestConfig)
    }
    return Promise.reject(error)
  }).then((res) => (res as AxiosResponse<ApiResponse<T>>).data)
}

/**
 * 封装POST请求
 * 请求体中的HTML标签会自动转义，防止XSS攻击
 * @param url - 请求地址
 * @param data - 请求体数据
 * @param config - 额外的axios配置
 */
export function post<T = unknown>(
  url: string,
  data?: object,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  return service.post(url, data, config).then((res) => (res as AxiosResponse<ApiResponse<T>>).data)
}

/**
 * 封装PUT请求
 * @param url - 请求地址
 * @param data - 请求体数据
 * @param config - 额外的axios配置
 */
export function put<T = unknown>(
  url: string,
  data?: object,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  return service.put(url, data, config).then((res) => (res as AxiosResponse<ApiResponse<T>>).data)
}

/**
 * 封装DELETE请求
 * @param url - 请求地址
 * @param config - 额外的axios配置
 */
export function del<T = unknown>(
  url: string,
  config?: AxiosRequestConfig,
): Promise<ApiResponse<T>> {
  return service.delete(url, config).then((res) => (res as AxiosResponse<ApiResponse<T>>).data)
}

export default service
