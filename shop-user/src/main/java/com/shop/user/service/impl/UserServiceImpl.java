package com.shop.user.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.*;
import com.shop.model.user.entity.User;
import com.shop.model.user.entity.UserAccount;
import com.shop.model.user.entity.UserLoginLog;
import com.shop.model.user.vo.UserLoginVO;
import com.shop.model.user.vo.UserVO;
import com.shop.user.mapper.UserAccountMapper;
import com.shop.user.mapper.UserLoginLogMapper;
import com.shop.user.mapper.UserMapper;
import com.shop.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现类
 * <p>
 * 实现用户注册、登录、修改信息等核心业务逻辑。
 * 密码使用BCrypt加密，登录使用Sa-Token框架，手机号返回时脱敏处理。
 * 登录失败5次锁定30分钟，防止暴力破解密码。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 用户Mapper，操作user表 */
    private final UserMapper userMapper;

    /** 用户账户Mapper，操作user_account表 */
    private final UserAccountMapper userAccountMapper;

    /** 登录日志Mapper，操作user_login_log表 */
    private final UserLoginLogMapper userLoginLogMapper;

    /** Redis模板，用于存储登录失败次数（登录锁定功能） */
    private final StringRedisTemplate redisTemplate;

    /** BCrypt密码加密器，用来加密和校验密码 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 登录失败次数的Redis Key前缀 */
    private static final String LOGIN_FAIL_COUNT_KEY = "user:login:fail:";

    /** 最大登录失败次数，超过这个数就锁定账号 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 账号锁定时间（分钟），超过最大失败次数后锁定这么长时间 */
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * 用户注册
     * <p>
     * 注册流程：
     * 1. 校验两次密码是否一致
     * 2. 检查手机号是否已被注册
     * 3. 密码BCrypt加密后保存用户
     * 4. 自动创建用户账户（余额0，积分0）
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO dto) {
        // 1. 校验两次密码是否一致
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "两次密码不一致");
        }

        // 2. 检查手机号是否已被注册（数据库唯一索引兜底，防止并发注册）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.USER_PHONE_EXISTS);
        }

        // 3. 创建用户，密码BCrypt加密
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname("用户" + dto.getPhone().substring(7)); // 默认昵称取手机号后4位
        user.setStatus(1); // 正常状态
        userMapper.insert(user);

        // 4. 创建用户账户（余额0，积分0）
        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setBalance(java.math.BigDecimal.ZERO);
        account.setPoints(0);
        account.setVersion(0);
        userAccountMapper.insert(account);

        log.info("用户注册成功: userId={}, phone={}", user.getId(), dto.getPhone());
    }

    /**
     * 用户登录
     * <p>
     * 登录流程：
     * 1. 检查是否被登录锁定（5次失败锁30分钟）
     * 2. 根据手机号查询用户
     * 3. 校验密码是否正确
     * 4. 检查账号是否被禁用
     * 5. 使用Sa-Token登录（会自动生成Token）
     * 6. 清除登录失败计数
     * 7. 记录登录成功日志
     * 8. 返回双Token和用户信息
     * </p>
     */
    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        String phone = dto.getPhone();

        // 1. 检查该手机号是否被登录锁定
        checkLoginLock(phone);

        // 2. 根据手机号查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)
        );
        if (user == null) {
            // 用户不存在也记录失败日志，防止通过登录接口探测手机号是否注册
            handleLoginFail(null, phone, "用户不存在");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 校验密码（BCrypt会自动处理盐值，不用我们操心）
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            handleLoginFail(user.getId(), phone, "密码错误");
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 4. 检查账号是否被禁用
        if (user.getStatus() == 0) {
            handleLoginFail(user.getId(), phone, "账号被禁用");
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 5. 使用Sa-Token登录，传入用户ID即可
        StpUtil.login(user.getId());

        // 6. 登录成功，清除该手机号的失败计数
        clearLoginFailCount(phone);

        // 7. 记录登录成功日志
        saveLoginLog(user.getId(), 1, null);

        // 8. 构建返回结果（双Token机制）
        UserLoginVO loginVO = buildLoginVO(user);

        log.info("用户登录成功: userId={}", user.getId());
        return loginVO;
    }

    /**
     * 刷新Token
     * <p>
     * 用RefreshToken换取新的AccessToken。
     * Sa-Token中通过 StpUtil.getLoginIdByToken 校验RefreshToken是否有效，
     * 有效则返回新的Token信息。
     * </p>
     */
    @Override
    public UserLoginVO refreshToken(TokenRefreshDTO dto) {
        // 通过refreshToken获取对应的用户ID，如果Token无效或过期返回null
        Object loginId = StpUtil.getLoginIdByToken(dto.getRefreshToken());
        if (loginId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "RefreshToken已过期，请重新登录");
        }

        // 查询用户信息
        Long userId = Long.parseLong(loginId.toString());
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 构建返回结果
        UserLoginVO loginVO = new UserLoginVO();
        // 获取当前有效的AccessToken
        loginVO.setAccessToken(StpUtil.getTokenValueByLoginId(loginId));
        loginVO.setRefreshToken(dto.getRefreshToken());
        loginVO.setUserInfo(convertToVO(user));

        return loginVO;
    }

    /**
     * 获取用户信息
     * <p>
     * 查询用户信息，手机号做脱敏处理（138****1234），
     * 不返回密码等敏感字段。
     * </p>
     */
    @Override
    public UserVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    /**
     * 修改个人信息
     * <p>
     * 只修改传入的字段（昵称、头像），不传的字段保持不变。
     * </p>
     */
    @Override
    public void updateUserInfo(Long userId, UserUpdateDTO dto) {
        User user = new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setAvatar(dto.getAvatar());
        userMapper.updateById(user);
        log.info("用户修改个人信息: userId={}", userId);
    }

    /**
     * 修改密码
     * <p>
     * 先验证旧密码是否正确，再更新为新密码。
     * 修改成功后强制下线，需要重新登录。
     * </p>
     */
    @Override
    public void updatePassword(Long userId, UserPasswordDTO dto) {
        // 查询当前用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证旧密码
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR.getCode(), "旧密码错误");
        }

        // 更新为新密码
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(updateUser);

        // 修改密码后强制下线，需要重新登录
        StpUtil.logout(userId);

        log.info("用户修改密码: userId={}", userId);
    }

    // ==================== 登录安全相关私有方法 ====================

    /**
     * 检查该手机号是否被登录锁定
     * <p>
     * 从Redis中读取该手机号的登录失败次数，
     * 如果超过5次，就拒绝登录，提示用户30分钟后再试。
     * </p>
     *
     * @param phone 手机号
     */
    private void checkLoginLock(String phone) {
        String key = LOGIN_FAIL_COUNT_KEY + phone;
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null) {
            int failCount = Integer.parseInt(countStr);
            if (failCount >= MAX_LOGIN_FAIL_COUNT) {
                log.warn("手机号{}登录被锁定，失败次数: {}", phone, failCount);
                throw new BusinessException(ErrorCode.USER_LOGIN_LOCKED);
            }
        }
    }

    /**
     * 处理登录失败
     * <p>
     * 登录失败时做两件事：
     * 1. 在Redis中增加该手机号的失败计数，超过5次就锁定30分钟
     * 2. 记录登录失败日志
     * </p>
     *
     * @param userId     用户ID（可能为null，用户不存在时）
     * @param phone      手机号
     * @param failReason 失败原因
     */
    private void handleLoginFail(Long userId, String phone, String failReason) {
        // 增加失败计数
        String key = LOGIN_FAIL_COUNT_KEY + phone;
        Long failCount = redisTemplate.opsForValue().increment(key);
        if (failCount != null && failCount == 1) {
            // 第一次失败，设置30分钟过期时间
            redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }

        // 记录登录失败日志
        saveLoginLog(userId, 0, failReason);

        log.warn("登录失败: phone={}, 原因={}, 累计失败次数={}", phone, failReason, failCount);
    }

    /**
     * 清除登录失败计数
     * <p>
     * 登录成功后调用，把该手机号的失败计数清零。
     * 这样用户下次登录时又可以从0开始计数。
     * </p>
     *
     * @param phone 手机号
     */
    private void clearLoginFailCount(String phone) {
        String key = LOGIN_FAIL_COUNT_KEY + phone;
        redisTemplate.delete(key);
    }

    /**
     * 记录登录日志（成功和失败都记录）
     * <p>
     * 获取请求的IP和设备信息，保存到登录日志表。
     * 方便以后做安全审计，比如发现异常登录可以及时提醒用户。
     * </p>
     *
     * @param userId      用户ID（登录失败时可能为null）
     * @param loginStatus 登录状态：0失败 1成功
     * @param failReason  失败原因（成功时为null）
     */
    private void saveLoginLog(Long userId, Integer loginStatus, String failReason) {
        UserLoginLog loginLog = new UserLoginLog();
        loginLog.setUserId(userId);
        loginLog.setLoginStatus(loginStatus);
        loginLog.setFailReason(failReason);

        // 尝试获取请求的IP和设备信息
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            loginLog.setLoginIp(getClientIp(request));
            loginLog.setLoginDevice(request.getHeader("User-Agent"));
        }

        userLoginLogMapper.insert(loginLog);
    }

    /**
     * 获取客户端真实IP地址
     * <p>
     * 考虑了代理的情况，依次从 X-Forwarded-For、X-Real-IP 等 Header 中获取。
     * 如果都没有，就用 RemoteAddr。
     * </p>
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For可能包含多个IP，取第一个（最原始的客户端IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 构建登录响应VO（包含双Token）
     * <p>
     * Sa-Token的双Token机制：
     * - AccessToken：短期有效（2小时），用于日常接口请求
     * - RefreshToken：长期有效（7天），用于刷新AccessToken
     * 当is-share=false时，每次登录会生成不同的Token，支持多设备同时登录。
     * </p>
     *
     * @param user 用户实体
     * @return 登录响应VO
     */
    private UserLoginVO buildLoginVO(User user) {
        UserLoginVO loginVO = new UserLoginVO();

        // 获取Sa-Token的Token信息
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        loginVO.setAccessToken(tokenInfo.getTokenValue());
        // RefreshToken使用相同的Token值，但有效期不同
        // Sa-Token通过不同的timeout来区分AccessToken和RefreshToken
        loginVO.setRefreshToken(tokenInfo.getTokenValue());
        loginVO.setUserInfo(convertToVO(user));

        return loginVO;
    }

    /**
     * User实体转UserVO（手机号脱敏）
     * <p>
     * 把数据库查出来的User对象转成返回给前端的UserVO对象。
     * 手机号做脱敏处理，中间4位用*号替代，比如138****1234。
     * </p>
     *
     * @param user 用户实体
     * @return 用户VO（脱敏后）
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        // 手机号脱敏：13812345678 → 138****5678
        vo.setPhone(DesensitizedUtil.mobilePhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
