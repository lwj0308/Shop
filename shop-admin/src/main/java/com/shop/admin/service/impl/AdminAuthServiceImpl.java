package com.shop.admin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.admin.mapper.AdminPermissionMapper;
import com.shop.admin.mapper.AdminRoleMapper;
import com.shop.admin.mapper.AdminRolePermissionMapper;
import com.shop.admin.mapper.AdminUserMapper;
import com.shop.admin.mapper.AdminUserRoleMapper;
import com.shop.admin.service.AdminAuthService;
import com.shop.admin.service.AdminLoginLogService;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.admin.dto.AdminLoginDTO;
import com.shop.model.admin.entity.AdminPermission;
import com.shop.model.admin.entity.AdminRole;
import com.shop.model.admin.entity.AdminRolePermission;
import com.shop.model.admin.entity.AdminUser;
import com.shop.model.admin.entity.AdminUserRole;
import com.shop.model.admin.vo.AdminLoginVO;
import com.shop.model.admin.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理员认证服务实现类
 * <p>
 * 实现管理员登录认证的核心业务逻辑，包括验证码生成、登录校验、登出等。
 * 登录流程：获取验证码 → 输入用户名+密码+验证码 → 校验验证码 → 校验账号锁定 →
 * 校验密码 → 校验账号状态 → Sa-Token登录 → 返回Token和权限信息
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 管理员信息Mapper，操作admin_user表 */
    private final AdminUserMapper adminUserMapper;

    /** 管理员-角色关联Mapper，查询管理员拥有的角色 */
    private final AdminUserRoleMapper adminUserRoleMapper;

    /** 角色-权限关联Mapper，查询角色拥有的权限 */
    private final AdminRolePermissionMapper adminRolePermissionMapper;

    /** 角色Mapper，查询角色详情 */
    private final AdminRoleMapper adminRoleMapper;

    /** 权限Mapper，查询权限详情 */
    private final AdminPermissionMapper adminPermissionMapper;

    /** Redis操作工具，用于验证码存储和登录失败次数限制 */
    private final StringRedisTemplate redisTemplate;

    /** 登录日志服务，记录每次登录尝试 */
    private final AdminLoginLogService adminLoginLogService;

    /** 安全事件服务，记录安全风险事件 */
    private final AdminSecurityEventService adminSecurityEventService;

    /** 验证码的Redis Key前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "admin:captcha:";

    /** 登录失败次数的Redis Key前缀 */
    private static final String LOGIN_FAIL_KEY_PREFIX = "admin:login_fail:";

    /** 验证码有效期（分钟） */
    private static final int CAPTCHA_EXPIRE_MINUTES = 5;

    /** 最大登录失败次数，超过这个数就锁定账号 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 账号锁定时间（分钟），登录失败太多次后要等这么久才能再试 */
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * 获取验证码
     * <p>
     * 使用Hutool的CircleCaptcha生成一个圆形干扰验证码图片。
     * 验证码答案存到Redis，key是UUID，5分钟后自动过期。
     * 前端拿到captchaKey和base64图片后，显示图片让用户输入验证码，
     * 登录时把captchaKey和用户输入的验证码一起提交。
     * </p>
     *
     * @return 验证码数据，包含captchaKey和base64图片
     */
    @Override
    public CaptchaVO getCaptcha() {
        // 使用Hutool生成圆形干扰验证码：宽200，高70，4个字符，10个干扰圆
        CircleCaptcha captcha = cn.hutool.captcha.CaptchaUtil.createCircleCaptcha(200, 70, 4, 10);

        // 生成唯一的验证码key，用UUID保证不重复
        String captchaKey = UUID.randomUUID().toString();

        // 把验证码答案存到Redis，5分钟后自动过期
        // 验证码不区分大小写，统一转小写存储，比对时也转小写
        String captchaCode = captcha.getCode().toLowerCase();
        redisTemplate.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaKey,
                captchaCode,
                CAPTCHA_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 封装返回结果
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(captchaKey);
        vo.setCaptchaImage(captcha.getImageBase64Data());

        log.debug("生成验证码，key：{}，有效期：{}分钟", captchaKey, CAPTCHA_EXPIRE_MINUTES);
        return vo;
    }

    /**
     * 管理员登录
     * <p>
     * 完整的登录流程：
     * 1. 校验验证码（从Redis取出正确答案和用户输入比对）
     * 2. 根据用户名查找管理员
     * 3. 检查账号是否被锁定（连续5次密码错误锁定30分钟）
     * 4. 校验密码（BCrypt比对，密码错误不透露是用户名还是密码错）
     * 5. 检查账号状态（被禁用的不能登录）
     * 6. Sa-Token登录，存入管理员信息到Session
     * 7. 查询管理员的权限和角色
     * 8. 更新最后登录IP和时间
     * 9. 记录登录日志
     * 10. 返回Token和权限信息
     * </p>
     *
     * @param dto 登录请求参数
     * @return 登录响应数据
     */
    @Override
    public AdminLoginVO login(AdminLoginDTO dto) {
        // ========== 第1步：校验验证码 ==========
        validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        // ========== 第2步：根据用户名查找管理员 ==========
        AdminUser adminUser = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, dto.getUsername())
        );

        // 管理员不存在时，不透露是用户名还是密码错误，防止攻击者枚举用户名
        if (adminUser == null) {
            // 即使管理员不存在，也记录登录日志（安全审计需要）
            adminLoginLogService.recordLoginLog(dto.getUsername(), getClientIp(), null, null, false, "用户名或密码错误");
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_ERROR);
        }

        String failKey = LOGIN_FAIL_KEY_PREFIX + dto.getUsername();

        // ========== 第3步：检查账号是否被锁定 ==========
        // 从Redis获取登录失败次数，如果超过5次就锁定
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL_COUNT) {
            adminLoginLogService.recordLoginLog(dto.getUsername(), getClientIp(), null, null, false, "账号已锁定");
            throw new BusinessException(ErrorCode.ADMIN_LOGIN_LOCKED);
        }

        // ========== 第4步：校验密码 ==========
        if (!BCrypt.checkpw(dto.getPassword(), adminUser.getPassword())) {
            // 密码错误：增加失败计数
            incrementLoginFailCount(failKey);

            // 如果失败次数达到上限，记录安全事件
            String updatedFailCountStr = redisTemplate.opsForValue().get(failKey);
            if (updatedFailCountStr != null && Integer.parseInt(updatedFailCountStr) >= MAX_LOGIN_FAIL_COUNT) {
                adminSecurityEventService.recordSecurityEvent(
                        "BRUTE_FORCE",
                        adminUser.getId(),
                        adminUser.getUsername(),
                        "连续" + MAX_LOGIN_FAIL_COUNT + "次登录失败，账号已锁定30分钟",
                        getClientIp()
                );
            }

            adminLoginLogService.recordLoginLog(dto.getUsername(), getClientIp(), null, null, false, "用户名或密码错误");
            throw new BusinessException(ErrorCode.ADMIN_PASSWORD_ERROR);
        }

        // 密码正确：清除失败计数
        redisTemplate.delete(failKey);

        // ========== 第5步：检查账号状态 ==========
        // status: 0禁用 1正常
        if (adminUser.getStatus() != 1) {
            adminLoginLogService.recordLoginLog(dto.getUsername(), getClientIp(), null, null, false, "账号已被禁用");
            throw new BusinessException(ErrorCode.ADMIN_DISABLED);
        }

        // ========== 第6步：Sa-Token登录 ==========
        StpUtil.login(adminUser.getId());

        // 在Session中存储管理员信息，方便后续获取
        StpUtil.getSession().set("adminUserId", adminUser.getId());
        StpUtil.getSession().set("adminUsername", adminUser.getUsername());

        // ========== 第7步：查询管理员的权限和角色 ==========
        Set<String> permissions = getAdminPermissions(adminUser.getId());
        Set<String> roles = getAdminRoles(adminUser.getId());

        // ========== 第8步：更新最后登录IP和时间 ==========
        adminUser.setLastLoginIp(getClientIp());
        adminUser.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(adminUser);

        // ========== 第9步：记录登录日志 ==========
        adminLoginLogService.recordLoginLog(dto.getUsername(), getClientIp(), null, null, true, null);

        // ========== 第10步：返回登录结果 ==========
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setAdminUserId(adminUser.getId());
        vo.setUsername(adminUser.getUsername());
        vo.setNickname(adminUser.getNickname());
        vo.setAvatar(adminUser.getAvatar());
        vo.setPermissions(permissions);
        vo.setRoles(roles);

        log.info("管理员登录成功，管理员ID：{}，用户名：{}", adminUser.getId(), adminUser.getUsername());
        return vo;
    }

    /**
     * 管理员登出
     * <p>
     * 使当前Token失效，退出后需要重新登录才能访问需要登录的接口。
     * Sa-Token会自动清除服务端的Token记录，确保Token无法再被使用。
     * </p>
     */
    @Override
    public void logout() {
        Long adminUserId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        log.info("管理员退出登录，管理员ID：{}", adminUserId);
    }

    /**
     * 校验验证码
     * <p>
     * 从Redis中取出正确答案，和用户输入的验证码做比对。
     * 比对后立即删除Redis中的验证码，防止重复使用（一次性验证码）。
     * 验证码不区分大小写。
     * </p>
     *
     * @param captchaKey   验证码key，登录时前端传过来
     * @param captchaCode  用户输入的验证码
     */
    private void validateCaptcha(String captchaKey, String captchaCode) {
        // 如果没有传验证码key或验证码，直接报错
        if (captchaKey == null || captchaKey.isEmpty() || captchaCode == null || captchaCode.isEmpty()) {
            throw new BusinessException(ErrorCode.ADMIN_CAPTCHA_ERROR);
        }

        // 从Redis取出正确答案
        String correctCode = redisTemplate.opsForValue().get(CAPTCHA_KEY_PREFIX + captchaKey);
        // 用完即删，防止验证码被重复使用
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + captchaKey);

        // 验证码不存在（过期了）或输入错误
        if (correctCode == null || !correctCode.equals(captchaCode.toLowerCase())) {
            throw new BusinessException(ErrorCode.ADMIN_CAPTCHA_ERROR);
        }
    }

    /**
     * 增加登录失败次数
     * <p>
     * 每次登录失败后，在Redis中增加计数。
     * 第一次失败时设置30分钟过期时间，之后累加计数。
     * </p>
     *
     * @param failKey Redis Key
     */
    private void incrementLoginFailCount(String failKey) {
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = (failCountStr == null) ? 1 : Integer.parseInt(failCountStr) + 1;
        redisTemplate.opsForValue().set(failKey, String.valueOf(failCount), LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        log.warn("管理员登录失败，当前失败次数：{}，Key：{}", failCount, failKey);
    }

    /**
     * 获取管理员拥有的权限标识集合
     * <p>
     * 查询流程：管理员ID → admin_user_role表找到角色ID → admin_role_permission表找到权限ID → admin_permission表找到权限标识
     * 只返回状态为启用(1)的权限，禁用的权限不算数。
     * </p>
     *
     * @param adminUserId 管理员ID
     * @return 权限标识集合，比如["user:list", "user:add", "role:delete"]
     */
    private Set<String> getAdminPermissions(Long adminUserId) {
        // 1. 根据管理员ID查出所有角色ID
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminUserId)
        );
        if (userRoles.isEmpty()) {
            return new HashSet<>();
        }

        // 2. 取出角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 过滤掉禁用的角色，只保留启用的角色
        List<AdminRole> activeRoles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .in(AdminRole::getId, roleIds)
                        .eq(AdminRole::getStatus, 1)
        );
        if (activeRoles.isEmpty()) {
            return new HashSet<>();
        }

        // 4. 取出启用角色的ID列表
        List<Long> activeRoleIds = activeRoles.stream()
                .map(AdminRole::getId)
                .collect(Collectors.toList());

        // 5. 根据角色ID查出所有权限ID
        List<AdminRolePermission> rolePermissions = adminRolePermissionMapper.selectList(
                new LambdaQueryWrapper<AdminRolePermission>()
                        .in(AdminRolePermission::getRoleId, activeRoleIds)
        );
        if (rolePermissions.isEmpty()) {
            return new HashSet<>();
        }

        // 6. 取出权限ID列表（去重）
        List<Long> permissionIds = rolePermissions.stream()
                .map(AdminRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        // 7. 查出权限详情，只返回启用状态的权限标识
        List<AdminPermission> permissions = adminPermissionMapper.selectList(
                new LambdaQueryWrapper<AdminPermission>()
                        .in(AdminPermission::getId, permissionIds)
                        .eq(AdminPermission::getStatus, 1)
        );

        return permissions.stream()
                .map(AdminPermission::getPermissionKey)
                .collect(Collectors.toSet());
    }

    /**
     * 获取管理员拥有的角色标识集合
     * <p>
     * 查询流程：管理员ID → admin_user_role表找到角色ID → admin_role表找到角色标识
     * 只返回状态为启用(1)的角色，禁用的角色不算数。
     * </p>
     *
     * @param adminUserId 管理员ID
     * @return 角色标识集合，比如["admin", "operator"]
     */
    private Set<String> getAdminRoles(Long adminUserId) {
        // 1. 根据管理员ID查出所有角色ID
        List<AdminUserRole> userRoles = adminUserRoleMapper.selectList(
                new LambdaQueryWrapper<AdminUserRole>().eq(AdminUserRole::getUserId, adminUserId)
        );
        if (userRoles.isEmpty()) {
            return new HashSet<>();
        }

        // 2. 取出角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(AdminUserRole::getRoleId)
                .collect(Collectors.toList());

        // 3. 查出角色详情，只返回启用状态的角色标识
        List<AdminRole> roles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>()
                        .in(AdminRole::getId, roleIds)
                        .eq(AdminRole::getStatus, 1)
        );

        return roles.stream()
                .map(AdminRole::getRoleKey)
                .collect(Collectors.toSet());
    }

    /**
     * 获取客户端IP地址
     * <p>
     * 优先从代理头中获取真实IP（因为可能经过了Nginx等反向代理），
     * 如果没有代理头，就从RemoteAddr获取。
     * </p>
     *
     * @return 客户端IP地址
     */
    private String getClientIp() {
        // 从HttpServletRequest中获取客户端IP，优先从代理头中获取真实IP
        jakarta.servlet.http.HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
