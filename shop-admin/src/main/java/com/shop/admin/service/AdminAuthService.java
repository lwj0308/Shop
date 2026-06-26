package com.shop.admin.service;

import com.shop.model.admin.dto.AdminLoginDTO;
import com.shop.model.admin.vo.AdminLoginVO;
import com.shop.model.admin.vo.CaptchaVO;

/**
 * 管理员认证服务接口
 * <p>
 * 定义管理员登录认证相关的业务方法，包括获取验证码、登录、登出。
 * 管理员登录流程：获取验证码 → 输入用户名+密码+验证码 → 登录成功返回Token
 * </p>
 */
public interface AdminAuthService {

    /**
     * 获取验证码
     * <p>
     * 生成一个图形验证码，验证码答案存到Redis（5分钟有效），图片返回给前端显示。
     * 登录时需要把验证码key和用户输入的验证码一起提交，后端根据key从Redis取正确答案做比对。
     * </p>
     *
     * @return 验证码数据，包含captchaKey和base64图片
     */
    CaptchaVO getCaptcha();

    /**
     * 管理员登录
     * <p>
     * 登录流程：
     * 1. 校验验证码是否正确
     * 2. 根据用户名查找管理员
     * 3. 检查账号是否被锁定（连续5次密码错误锁定30分钟）
     * 4. 校验密码（BCrypt比对）
     * 5. 检查账号状态（被禁用的不能登录）
     * 6. 使用Sa-Token生成Token
     * 7. 返回Token和管理员信息
     * </p>
     *
     * @param dto 登录请求参数，包含用户名、密码、验证码key和验证码
     * @return 登录响应数据，包含Token、管理员信息、权限和角色
     */
    AdminLoginVO login(AdminLoginDTO dto);

    /**
     * 管理员登出
     * <p>
     * 使当前Token失效，退出后需要重新登录才能访问需要登录的接口。
     * Sa-Token会自动清除服务端的Token记录，确保Token无法再被使用。
     * </p>
     */
    void logout();
}
