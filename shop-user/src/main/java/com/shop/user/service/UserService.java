package com.shop.user.service;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.model.user.dto.*;
import com.shop.model.user.vo.UserLoginVO;
import com.shop.model.user.vo.UserVO;

/**
 * 用户服务接口
 * <p>
 * 定义用户相关的业务方法，包括注册、登录、修改信息等。
 * 实现类在 UserServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface UserService {

    /**
     * 用户注册
     * <p>
     * 注册流程：校验两次密码一致 → 检查手机号是否已注册 → 密码BCrypt加密 → 保存用户 → 创建用户账户
     * </p>
     *
     * @param dto 注册请求参数（手机号、密码、确认密码）
     */
    void register(UserRegisterDTO dto);

    /**
     * 用户登录
     * <p>
     * 登录流程：根据手机号查用户 → 校验密码 → 检查账号状态 → Sa-Token登录 → 返回双Token → 记录登录日志
     * </p>
     *
     * @param dto 登录请求参数（手机号、密码）
     * @return 登录响应（AccessToken + RefreshToken + 用户信息）
     */
    UserLoginVO login(UserLoginDTO dto);

    /**
     * 刷新Token
     * <p>
     * 当AccessToken过期后，用RefreshToken换取新的AccessToken。
     * 如果RefreshToken也过期了，就需要重新登录。
     * </p>
     *
     * @param dto 刷新Token请求参数（refreshToken）
     * @return 新的登录响应（新的AccessToken + RefreshToken + 用户信息）
     */
    UserLoginVO refreshToken(TokenRefreshDTO dto);

    /**
     * 获取用户信息
     * <p>
     * 根据用户ID查询用户信息，手机号会做脱敏处理（138****1234）。
     * </p>
     *
     * @param userId 用户ID
     * @return 用户信息（不含密码，手机号脱敏）
     */
    UserVO getUserInfo(Long userId);

    /**
     * 修改个人信息
     * <p>
     * 目前只支持修改昵称和头像，手机号和密码需要走专门的接口。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    修改信息请求参数（昵称、头像）
     */
    void updateUserInfo(Long userId, UserUpdateDTO dto);

    /**
     * 修改密码
     * <p>
     * 需要验证旧密码，防止别人偷偷改你的密码。
     * 修改密码后需要重新登录。
     * </p>
     *
     * @param userId 用户ID
     * @param dto    修改密码请求参数（旧密码、新密码）
     */
    void updatePassword(Long userId, UserPasswordDTO dto);
}
