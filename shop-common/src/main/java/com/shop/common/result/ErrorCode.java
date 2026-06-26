package com.shop.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码定义
 * <p>
 * 错误码规范：5位数字，前2位是模块标识，后3位是具体错误编号。
 * 这样一看错误码就知道是哪个模块出了什么问题，方便快速定位。
 * </p>
 * <p>
 * 模块划分：
 * - 通用错误：10xxx（全局通用，不归属具体模块）
 * - 用户模块：10xxx
 * - 商家模块：20xxx
 * - 商品模块：30xxx
 * - 订单模块：40xxx
 * - 支付模块：50xxx
 * - 购物车模块：60xxx
 * - 管理后台模块：70xxx
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 通用错误 10xxx ====================
    /** 操作成功 */
    SUCCESS(200, "操作成功"),
    /** 参数错误：前端传的参数不对 */
    PARAM_ERROR(400, "参数错误"),
    /** 参数校验失败：@Valid 校验不通过 */
    PARAM_VALID_FAIL(10001, "参数校验失败"),
    /** 未登录：没有登录就访问需要登录的接口 */
    UNAUTHORIZED(401, "未登录"),
    /** 无权限：登录了但没有权限操作 */
    FORBIDDEN(403, "无权限"),
    /** 资源不存在：请求的接口或数据不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 请求方法不支持：比如接口要求POST，前端发了GET */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    /** 请求体不可读：JSON格式错误或缺少请求体 */
    BODY_NOT_READABLE(10002, "请求数据格式错误"),
    /** 缺少必要请求参数 */
    MISSING_PARAM(10003, "缺少必要请求参数"),
    /** 请求太频繁：触发了限流 */
    TOO_MANY_REQUESTS(429, "请求太频繁"),
    /** 数据已存在：新增时发现数据重复 */
    DATA_ALREADY_EXISTS(10004, "数据已存在"),
    /** 数据不存在：查询时找不到对应数据 */
    DATA_NOT_FOUND(10005, "数据不存在"),
    /** 操作失败：通用操作失败提示 */
    OPERATION_FAIL(10006, "操作失败"),
    /** 服务器内部错误：代码出bug了 */
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ==================== 用户模块 11xxx ====================
    /** 用户不存在：根据ID或手机号查不到用户 */
    USER_NOT_FOUND(11001, "用户不存在"),
    /** 密码错误：登录时密码不对 */
    USER_PASSWORD_ERROR(11002, "密码错误"),
    /** 手机号已注册：注册时手机号已经被别人用了 */
    USER_PHONE_EXISTS(11003, "手机号已注册"),
    /** 用户已被禁用：账号被管理员封禁了 */
    USER_DISABLED(11004, "用户已被禁用"),
    /** Token已过期：AccessToken过期了，需要用RefreshToken刷新 */
    USER_TOKEN_EXPIRED(11005, "登录已过期，请重新登录"),
    /** Token无效：Token格式错误或被篡改 */
    USER_TOKEN_INVALID(11006, "无效的登录凭证"),
    /** RefreshToken已过期：需要重新登录 */
    USER_REFRESH_TOKEN_EXPIRED(11007, "登录已过期，请重新登录"),
    /** 旧密码错误：修改密码时输入的旧密码不对 */
    USER_OLD_PASSWORD_ERROR(11008, "旧密码错误"),
    /** 用户未实名认证 */
    USER_NOT_VERIFIED(11009, "用户未实名认证"),
    /** 用户昵称已存在 */
    USER_NICKNAME_EXISTS(11010, "昵称已被使用"),
    /** 登录失败次数过多：密码错误超过5次，账号被锁定30分钟 */
    USER_LOGIN_LOCKED(11011, "登录失败次数过多，请30分钟后再试"),

    // ==================== 商家模块 20xxx ====================
    /** 商家不存在：根据ID查不到商家 */
    MERCHANT_NOT_FOUND(20001, "商家不存在"),
    /** 商家审核中：商家提交了入驻申请，还在等管理员审核 */
    MERCHANT_PENDING(20002, "商家审核中"),
    /** 商家审核未通过：入驻申请被拒绝了 */
    MERCHANT_REJECTED(20003, "商家审核未通过"),
    /** 商家已被禁用：被管理员封禁了 */
    MERCHANT_DISABLED(20004, "商家已被禁用"),
    /** 商家已入驻：一个用户只能入驻一个商家 */
    MERCHANT_ALREADY_EXISTS(20005, "您已入驻商家，不可重复申请"),
    /** 商家密码错误：商家登录时密码不对 */
    MERCHANT_PASSWORD_ERROR(20006, "商家密码错误"),
    /** 商家资质信息不完整：缺少必要的资质材料 */
    MERCHANT_QUALIFICATION_INCOMPLETE(20007, "商家资质信息不完整"),
    /** 结算账户未配置：商家还没配置银行账户 */
    MERCHANT_SETTLEMENT_NOT_CONFIG(20008, "请先配置结算账户"),
    /** 店铺不存在 */
    SHOP_NOT_FOUND(20009, "店铺不存在"),
    /** 店铺已关闭 */
    SHOP_CLOSED(20010, "店铺已关闭"),
    /** 商家名称已存在：入驻或修改时名称重复 */
    MERCHANT_NAME_EXISTS(20011, "商家名称已存在"),
    /** 手机号已被其他商家使用 */
    MERCHANT_PHONE_EXISTS(20012, "该手机号已被其他商家使用"),
    /** 商家状态不允许此操作 */
    MERCHANT_STATUS_ERROR(20013, "当前商家状态不允许此操作"),
    /** 结算账户已存在 */
    MERCHANT_SETTLEMENT_EXISTS(20014, "结算账户已存在，请使用更新接口"),
    /** 店铺名称已存在 */
    SHOP_NAME_EXISTS(20015, "店铺名称已存在"),

    // ==================== 商品模块 30xxx ====================
    /** 商品不存在：根据ID查不到商品 */
    PRODUCT_NOT_FOUND(30001, "商品不存在"),
    /** 商品已下架：商品被商家下架了，不能购买 */
    PRODUCT_OFF_SHELF(30002, "商品已下架"),
    /** 库存不足：商品库存不够了，不能下单 */
    PRODUCT_STOCK_NOT_ENOUGH(30003, "库存不足"),
    /** 分类不存在 */
    CATEGORY_NOT_FOUND(30004, "分类不存在"),
    /** 分类已禁用 */
    CATEGORY_DISABLED(30005, "分类已禁用"),
    /** 分类名称已存在 */
    CATEGORY_NAME_EXISTS(30006, "分类名称已存在"),
    /** 品牌不存在 */
    BRAND_NOT_FOUND(30007, "品牌不存在"),
    /** SKU不存在 */
    PRODUCT_SKU_NOT_FOUND(30008, "商品规格不存在"),
    /** 商品名称已存在 */
    PRODUCT_NAME_EXISTS(30009, "商品名称已存在"),
    /** 商品评价已存在：同一订单商品不能重复评价 */
    COMMENT_ALREADY_EXISTS(30010, "该商品已评价，不可重复评价"),
    /** 规格值组合已存在 */
    SKU_SPEC_EXISTS(30011, "该规格组合已存在"),

    // ==================== 订单模块 40xxx ====================
    /** 订单不存在：根据订单号查不到订单 */
    ORDER_NOT_FOUND(40001, "订单不存在"),
    /** 订单状态异常：订单当前状态不允许这个操作（比如已取消的订单不能发货） */
    ORDER_STATUS_ERROR(40002, "订单状态异常"),
    /** 创建订单失败：下单时出了问题 */
    ORDER_CREATE_FAIL(40003, "创建订单失败"),
    /** 订单已取消：不能对已取消的订单操作 */
    ORDER_ALREADY_CANCELLED(40004, "订单已取消"),
    /** 订单已支付：不能重复支付 */
    ORDER_ALREADY_PAID(40005, "订单已支付"),
    /** 订单未支付：还没付钱不能发货 */
    ORDER_NOT_PAID(40006, "订单未支付"),
    /** 订单超时未支付：超过支付时限，订单自动取消 */
    ORDER_PAY_TIMEOUT(40007, "订单超时未支付"),
    /** 非法操作：不是自己的订单不能操作 */
    ORDER_NOT_YOURS(40008, "无权操作此订单"),
    /** 退款单不存在 */
    REFUND_NOT_FOUND(40009, "退款单不存在"),
    /** 退款状态异常：退款单当前状态不允许此操作 */
    REFUND_STATUS_ERROR(40010, "退款状态异常"),
    /** 退款金额超限：退款金额不能大于实付金额 */
    REFUND_AMOUNT_EXCEED(40011, "退款金额超出限制"),

    // ==================== 支付模块 50xxx ====================
    /** 支付失败：调用支付接口返回失败 */
    PAYMENT_FAIL(50001, "支付失败"),
    /** 支付超时：用户太久没付款，支付链接过期了 */
    PAYMENT_TIMEOUT(50002, "支付超时"),
    /** 支付回调处理失败：支付平台通知我们支付结果时处理出错 */
    PAYMENT_CALLBACK_ERROR(50003, "支付回调处理失败"),
    /** 支付单不存在 */
    PAYMENT_NOT_FOUND(50004, "支付单不存在"),
    /** 支付金额不匹配：前端传的金额和订单金额不一致 */
    PAYMENT_AMOUNT_MISMATCH(50005, "支付金额不匹配"),
    /** 支付单已关闭：不能对已关闭的支付单发起支付 */
    PAYMENT_ALREADY_CLOSED(50006, "支付单已关闭"),
    /** 支付单已支付：不能重复支付 */
    PAYMENT_ALREADY_PAID(50007, "支付单已支付"),
    /** 不支持的支付方式 */
    PAYMENT_TYPE_NOT_SUPPORT(50008, "不支持的支付方式"),
    /** 余额不足：用户账户余额不够支付 */
    PAYMENT_BALANCE_NOT_ENOUGH(50009, "余额不足"),

    // ==================== 购物车模块 60xxx ====================
    /** 购物车项不存在 */
    CART_ITEM_NOT_FOUND(60001, "购物车商品不存在"),
    /** 购物车商品已存在：同一SKU不要重复添加，应该增加数量 */
    CART_ITEM_EXISTS(60002, "商品已在购物车中"),
    /** 购物车商品数量超限：单个用户购物车商品种类不能超过限制 */
    CART_ITEM_LIMIT_EXCEED(60003, "购物车商品数量超限"),
    /** 购物车为空：结算时购物车里没有商品 */
    CART_EMPTY(60004, "购物车为空"),

    // ==================== 管理后台模块 70xxx ====================
    /** 管理员不存在：根据ID或用户名查不到管理员 */
    ADMIN_NOT_FOUND(70001, "管理员不存在"),
    /** 管理员用户名已存在：新增时用户名重复 */
    ADMIN_USERNAME_EXISTS(70002, "用户名已存在"),
    /** 管理员密码错误：登录时密码不对 */
    ADMIN_PASSWORD_ERROR(70003, "用户名或密码错误"),
    /** 管理员已被禁用：账号被超级管理员禁用了 */
    ADMIN_DISABLED(70004, "账号已被禁用"),
    /** 管理员登录失败次数过多：密码错误超过5次，锁定30分钟 */
    ADMIN_LOGIN_LOCKED(70005, "登录失败次数过多，请30分钟后再试"),
    /** 验证码错误：输入的验证码不对或已过期 */
    ADMIN_CAPTCHA_ERROR(70006, "验证码错误或已过期"),
    /** 角色不存在：根据ID查不到角色 */
    ADMIN_ROLE_NOT_FOUND(70007, "角色不存在"),
    /** 角色标识已存在：新增角色时roleKey重复 */
    ADMIN_ROLE_KEY_EXISTS(70008, "角色标识已存在"),
    /** 超级管理员角色不可操作：不能删除或禁用超级管理员角色 */
    ADMIN_ROLE_IMMUTABLE(70009, "超级管理员角色不可修改或删除"),
    /** 权限不存在：根据ID查不到权限 */
    ADMIN_PERMISSION_NOT_FOUND(70010, "权限不存在"),
    /** 部门不存在：根据ID查不到部门 */
    ADMIN_DEPT_NOT_FOUND(70011, "部门不存在"),
    /** 部门名称已存在：同一层级下部门名称重复 */
    ADMIN_DEPT_NAME_EXISTS(70012, "部门名称已存在"),
    /** 部门下存在子部门：删除部门时还有子部门 */
    ADMIN_DEPT_HAS_CHILDREN(70013, "部门下存在子部门，无法删除"),
    /** 部门下存在管理员：删除部门时还有管理员属于该部门 */
    ADMIN_DEPT_HAS_USERS(70014, "部门下存在管理员，无法删除"),
    /** 旧密码错误：修改密码时输入的旧密码不对 */
    ADMIN_OLD_PASSWORD_ERROR(70015, "旧密码错误"),
    /** 安全事件不存在 */
    ADMIN_SECURITY_EVENT_NOT_FOUND(70016, "安全事件不存在"),
    /** Banner不存在 */
    ADMIN_BANNER_NOT_FOUND(70017, "Banner不存在"),
    /** 公告不存在 */
    ADMIN_NOTICE_NOT_FOUND(70018, "公告不存在");

    /** 错误码数字 */
    private final int code;

    /** 错误提示信息 */
    private final String message;
}
