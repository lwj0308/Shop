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

/** 创建axios实例，配置基础信息 */
const service: AxiosInstance = axios.create({
  // baseURL从环境变量读取，不同环境（开发/生产）会自动切换
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000, // 请求超时时间10秒
})

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
    if (res.code !== SUCCESS_CODE) {
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

    // 成功则直接返回整个响应（调用方通过response.data获取数据）
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
 * 跳转到登录页
 * 根据当前路径判断是用户端还是商家端，跳到对应的登录页
 */
function redirectToLogin(): void {
  const currentPath = window.location.pathname
  if (currentPath.includes('/merchant')) {
    window.location.href = '/merchant/login'
  } else {
    window.location.href = '/login'
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
