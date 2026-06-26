/**
 * 用户相关类型定义
 * 包含用户信息、登录请求/响应等类型
 * 对齐后端 User 相关实体和 VO
 */

/** 用户性别枚举 */
export type UserGender = 0 | 1 | 2

/** 用户基本信息 */
export interface UserInfo {
  /** 用户ID */
  id: number
  /** 手机号 */
  phone: string
  /** 昵称 */
  nickname: string
  /** 头像URL */
  avatar: string
  /** 用户性别：0-未知 1-男 2-女 */
  gender: UserGender
  /** 注册时间 */
  createTime: string
}

/** 登录请求参数 */
export interface LoginParams {
  /** 手机号 */
  phone: string
  /** 密码 */
  password: string
}

/** 登录响应数据 */
export interface LoginResult {
  /** 访问令牌，用于接口鉴权 */
  accessToken: string
  /** 刷新令牌，用于获取新的accessToken */
  refreshToken: string
  /** accessToken过期时间（秒） */
  expiresIn: number
}

/** 注册请求参数 */
export interface RegisterParams {
  /** 手机号 */
  phone: string
  /** 密码 */
  password: string
  /** 确认密码 */
  confirmPassword: string
  /** 短信验证码 */
  verifyCode: string
}

/** 修改密码请求参数 */
export interface ChangePasswordParams {
  /** 原密码 */
  oldPassword: string
  /** 新密码 */
  newPassword: string
  /** 确认新密码 */
  confirmPassword: string
}

/** 收货地址信息（对齐后端 AddressVO） */
export interface AddressInfo {
  /** 地址ID */
  id: number
  /** 收货人姓名 */
  name: string
  /** 收货人手机号（脱敏后的，比如138****1234） */
  phone: string
  /** 省 */
  province: string
  /** 市 */
  city: string
  /** 区 */
  district: string
  /** 详细地址 */
  detail: string
  /** 是否默认：0否 1是 */
  isDefault: number
  /** 创建时间 */
  createTime: string
}

/** 添加/修改收货地址参数（对齐后端 AddressDTO） */
export interface AddressParams {
  /** 收货人姓名 */
  name: string
  /** 收货人手机号 */
  phone: string
  /** 省 */
  province: string
  /** 市 */
  city: string
  /** 区 */
  district: string
  /** 详细地址 */
  detail: string
  /** 是否设为默认地址 */
  isDefault?: boolean
}
