package com.shop.merchant.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.mapper.MerchantMapper;
import com.shop.merchant.mapper.MerchantQualificationMapper;
import com.shop.merchant.service.ShopService;
import com.shop.model.merchant.dto.MerchantApplyDTO;
import com.shop.model.merchant.dto.MerchantAuditDTO;
import com.shop.model.merchant.dto.MerchantChangePasswordDTO;
import com.shop.model.merchant.dto.MerchantLoginDTO;
import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.entity.Merchant;
import com.shop.model.merchant.entity.MerchantQualification;
import com.shop.model.merchant.enums.MerchantStatusEnum;
import com.shop.model.merchant.vo.MerchantAuditVO;
import com.shop.model.merchant.vo.MerchantVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家服务实现类（MerchantServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证商家的各种操作能不能正常工作，比如入驻申请、登录、审核、修改信息等。
 * 简单理解：我们把真正访问数据库的 Mapper、访问 Redis 的工具、调用店铺服务的 ShopService 都"假装"一下（Mock），
 * 这样测试就不需要真的连数据库、Redis 和其他服务，跑得又快又稳定。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * - mockStatic：用来假装静态方法（比如 Sa-Token 的 StpUtil），因为静态方法不能直接 Mock
 * </p>
 * <p>
 * 测试覆盖的9个方法：apply、login、changePassword、audit、getMerchantInfo、getMerchantByUserId、
 * updateMerchant、getAuditStatus、checkMerchantActive
 * </p>
 */
@DisplayName("商家服务 MerchantServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class MerchantServiceImplTest {

    /** 假装商家信息 Mapper，不真的连数据库 */
    @Mock
    private MerchantMapper merchantMapper;

    /** 假装商家资质 Mapper，不真的连数据库 */
    @Mock
    private MerchantQualificationMapper qualificationMapper;

    /** 假装店铺服务，不真的调店铺模块 */
    @Mock
    private ShopService shopService;

    /** 假装 Redis 操作工具，不真的连 Redis */
    @Mock
    private StringRedisTemplate redisTemplate;

    /** 假装 Redis 的 Value 操作（用来 Mock get/set 方法） */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 被测试的商家服务，Mockito 会自动把上面几个 Mock 注入进来 */
    @InjectMocks
    private MerchantServiceImpl merchantService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long MERCHANT_ID = 1L;        // 商家ID
    private static final Long USER_ID = 1001L;          // 用户ID
    private static final String PHONE = "13812345678";  // 联系电话
    private static final String PASSWORD = "password123"; // 明文密码

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：MerchantServiceImpl 里用到了 new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, ...)，
     * 这行代码会让 MyBatis-Plus 去查"userId 字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：Merchant 和 MerchantQualification 这两个实体有哪些字段。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Merchant.class
        );
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MerchantQualification.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个商家实体
     * 小白理解：数据库 merchant 表里的一条记录
     *
     * @param id     商家ID
     * @param userId 关联的用户ID
     * @param name   商家名称
     * @param phone  联系电话
     * @param status 商家状态：0待审核 1已通过 2已拒绝 3已禁用
     * @return 构造好的Merchant
     */
    private Merchant buildMerchant(Long id, Long userId, String name, String phone, Integer status) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setUserId(userId);
        merchant.setName(name);
        merchant.setContactPhone(phone);
        merchant.setStatus(status);
        return merchant;
    }

    /**
     * 构造一个入驻申请DTO
     * 小白理解：用户点"申请入驻"时前端传过来的参数
     *
     * @param name     商家名称
     * @param phone    联系电话
     * @param password 密码
     * @return 构造好的MerchantApplyDTO
     */
    private MerchantApplyDTO buildApplyDTO(String name, String phone, String password) {
        MerchantApplyDTO dto = new MerchantApplyDTO();
        dto.setName(name);
        dto.setContactPhone(phone);
        dto.setLicenseNo("91110108MA01XXXXX");
        dto.setLicenseImg("https://example.com/license.jpg");
        dto.setLegalPerson("张三");
        dto.setPassword(password);
        return dto;
    }

    /**
     * 构造一个登录DTO
     *
     * @param phone    联系电话
     * @param password 密码
     * @return 构造好的MerchantLoginDTO
     */
    private MerchantLoginDTO buildLoginDTO(String phone, String password) {
        MerchantLoginDTO dto = new MerchantLoginDTO();
        dto.setContactPhone(phone);
        dto.setPassword(password);
        return dto;
    }

    /**
     * 构造一个审核DTO
     *
     * @param merchantId 商家ID
     * @param status     审核状态：1通过 2拒绝
     * @param auditNote  审核备注
     * @return 构造好的MerchantAuditDTO
     */
    private MerchantAuditDTO buildAuditDTO(Long merchantId, Integer status, String auditNote) {
        MerchantAuditDTO dto = new MerchantAuditDTO();
        dto.setMerchantId(merchantId);
        dto.setStatus(status);
        dto.setAuditNote(auditNote);
        return dto;
    }

    // ==================== 1. apply 商家入驻申请 ====================

    @Nested
    @DisplayName("apply 商家入驻申请")
    class ApplyTest {

        @Test
        @DisplayName("用户已入驻过商家（重复申请） → 抛出MERCHANT_ALREADY_EXISTS异常")
        void apply_userAlreadyExists_throwsException() {
            // 场景：用户1001已经入驻过商家了，又来申请，应该被拒绝
            MerchantApplyDTO dto = buildApplyDTO("新商家", PHONE, PASSWORD);

            // 模拟：根据userId查到了1条记录（说明已经入驻过）
            when(merchantMapper.selectCount(any())).thenReturn(1L);

            // 验证：抛出"已入驻"异常，错误码20005
            assertThatThrownBy(() -> merchantService.apply(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_ALREADY_EXISTS.getCode());

            // 验证：校验失败，没有调用insert
            verify(merchantMapper, never()).insert(any(Merchant.class));
        }

        @Test
        @DisplayName("商家名称已存在 → 抛出MERCHANT_NAME_EXISTS异常")
        void apply_nameExists_throwsException() {
            // 场景：用户没入驻过，但商家名称"已存在商家"已经被别人用了
            MerchantApplyDTO dto = buildApplyDTO("已存在商家", PHONE, PASSWORD);

            // 模拟：userId查到0条（没入驻过），name查到1条（名称重复）
            when(merchantMapper.selectCount(any())).thenReturn(0L, 1L);

            // 验证：抛出"名称已存在"异常，错误码20011
            assertThatThrownBy(() -> merchantService.apply(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NAME_EXISTS.getCode());

            verify(merchantMapper, never()).insert(any(Merchant.class));
        }

        @Test
        @DisplayName("手机号已被其他商家使用 → 抛出MERCHANT_PHONE_EXISTS异常")
        void apply_phoneExists_throwsException() {
            // 场景：用户没入驻过，名称也没重复，但手机号被别人用了
            MerchantApplyDTO dto = buildApplyDTO("新商家", PHONE, PASSWORD);

            // 模拟：userId=0条，name=0条，phone=1条（手机号重复）
            when(merchantMapper.selectCount(any())).thenReturn(0L, 0L, 1L);

            // 验证：抛出"手机号已存在"异常，错误码20012
            assertThatThrownBy(() -> merchantService.apply(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_PHONE_EXISTS.getCode());

            verify(merchantMapper, never()).insert(any(Merchant.class));
        }

        @Test
        @DisplayName("正常申请 → 调用insert创建商家和资质，状态为待审核")
        void apply_normal_success() {
            // 场景：所有校验都通过，正常提交入驻申请
            MerchantApplyDTO dto = buildApplyDTO("新商家", PHONE, PASSWORD);

            // 模拟：userId=0条，name=0条，phone=0条（都不重复）
            when(merchantMapper.selectCount(any())).thenReturn(0L);

            // 执行申请
            MerchantVO result = merchantService.apply(USER_ID, dto);

            // 验证：调用了商家insert和资质insert
            verify(merchantMapper).insert(any(Merchant.class));
            verify(qualificationMapper).insert(any(MerchantQualification.class));

            // 验证：返回的VO状态是待审核(0)，名称正确
            assertThat(result.getStatus()).isEqualTo(MerchantStatusEnum.PENDING.getCode());
            assertThat(result.getName()).isEqualTo("新商家");
        }
    }

    // ==================== 2. login 商家登录 ====================

    @Nested
    @DisplayName("login 商家登录")
    class LoginTest {

        @Test
        @DisplayName("登录失败次数达上限 → 抛出USER_LOGIN_LOCKED异常")
        void login_locked_throwsException() {
            // 场景：连续密码错误5次，账号被锁定30分钟
            MerchantLoginDTO dto = buildLoginDTO(PHONE, PASSWORD);

            // 模拟：Redis里记录了5次失败
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("5");

            // 验证：抛出"登录锁定"异常，错误码11011
            assertThatThrownBy(() -> merchantService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_LOGIN_LOCKED.getCode());

            // 验证：锁定后直接返回，不查数据库
            verify(merchantMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void login_merchantNotFound_throwsException() {
            // 场景：手机号没注册过商家
            MerchantLoginDTO dto = buildLoginDTO(PHONE, PASSWORD);

            // 模拟：没被锁定，但数据库查不到商家
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(merchantMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出"商家不存在"异常，错误码20001
            assertThatThrownBy(() -> merchantService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("商家被禁用 → 抛出MERCHANT_DISABLED异常")
        void login_merchantDisabled_throwsException() {
            // 场景：商家被管理员封禁了，不能登录
            MerchantLoginDTO dto = buildLoginDTO(PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.DISABLED.getCode());
            merchant.setPassword(BCrypt.hashpw(PASSWORD));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(merchantMapper.selectOne(any())).thenReturn(merchant);

            // 验证：抛出"商家已禁用"异常，错误码20004
            assertThatThrownBy(() -> merchantService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_DISABLED.getCode());
        }

        @Test
        @DisplayName("密码错误 → 抛出MERCHANT_PASSWORD_ERROR异常，并增加失败计数")
        void login_passwordError_throwsException() {
            // 场景：商家存在且状态正常，但密码输错了
            MerchantLoginDTO dto = buildLoginDTO(PHONE, "wrongpassword");
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());
            merchant.setPassword(BCrypt.hashpw(PASSWORD)); // 正确密码是password123

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(merchantMapper.selectOne(any())).thenReturn(merchant);

            // 验证：抛出"密码错误"异常，错误码20006
            assertThatThrownBy(() -> merchantService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_PASSWORD_ERROR.getCode());

            // 验证：密码错误时增加了失败计数（调用了Redis的set方法）
            verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("正常登录 → 返回token和商家ID，清除失败计数")
        void login_normal_returnsToken() {
            // 场景：手机号和密码都正确，商家状态正常，登录成功
            MerchantLoginDTO dto = buildLoginDTO(PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());
            merchant.setPassword(BCrypt.hashpw(PASSWORD));

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(merchantMapper.selectOne(any())).thenReturn(merchant);
            // 模拟：商家还没有店铺
            when(shopService.getShopByMerchantId(MERCHANT_ID)).thenReturn(null);

            // 小白理解：Sa-Token的StpUtil是静态方法，需要用mockStatic来假装它
            // try-with-resources会在测试结束后自动关闭Mock，不影响其他测试
            try (MockedStatic<StpUtil> stpMock = Mockito.mockStatic(StpUtil.class)) {
                SaSession session = Mockito.mock(SaSession.class);
                // StpUtil.login是void方法，默认就不做事，不用stub
                // StpUtil.getSession要返回一个假的session对象
                stpMock.when(StpUtil::getSession).thenReturn(session);
                // StpUtil.getTokenValue要返回一个假的token字符串
                stpMock.when(StpUtil::getTokenValue).thenReturn("test-token-abc");

                // 执行登录
                Map<String, Object> result = merchantService.login(dto);

                // 验证：返回了正确的token和商家ID
                assertThat(result).containsEntry("token", "test-token-abc");
                assertThat(result).containsEntry("merchantId", MERCHANT_ID);

                // 验证：Session里存了角色和商家信息
                verify(session).set("role", "merchant");
                verify(session).set("userId", USER_ID);
                verify(session).set("merchantId", MERCHANT_ID);
            }

            // 验证：登录成功后清除了失败计数
            verify(redisTemplate).delete(anyString());
        }
    }

    // ==================== 3. changePassword 修改密码 ====================

    @Nested
    @DisplayName("changePassword 修改密码")
    class ChangePasswordTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void changePassword_merchantNotFound_throwsException() {
            // 场景：修改一个不存在的商家的密码
            MerchantChangePasswordDTO dto = new MerchantChangePasswordDTO();
            dto.setOldPassword("oldpass");
            dto.setNewPassword("newpass123");

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.changePassword(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("旧密码错误 → 抛出USER_OLD_PASSWORD_ERROR异常")
        void changePassword_oldPasswordError_throwsException() {
            // 场景：输入的旧密码不对，不能修改密码
            MerchantChangePasswordDTO dto = new MerchantChangePasswordDTO();
            dto.setOldPassword("wrongoldpass");
            dto.setNewPassword("newpass123");

            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());
            merchant.setPassword(BCrypt.hashpw("correctoldpass")); // 正确的旧密码

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.changePassword(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.USER_OLD_PASSWORD_ERROR.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("正常修改密码 → 调用updateById，新密码已加密")
        void changePassword_normal_success() {
            // 场景：旧密码正确，正常修改为新密码
            MerchantChangePasswordDTO dto = new MerchantChangePasswordDTO();
            dto.setOldPassword(PASSWORD);
            dto.setNewPassword("newpassword123");

            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());
            merchant.setPassword(BCrypt.hashpw(PASSWORD)); // 旧密码的加密值

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            // 执行修改密码
            merchantService.changePassword(MERCHANT_ID, dto);

            // 捕获传给updateById的merchant对象，验证密码已更新
            ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
            verify(merchantMapper).updateById(captor.capture());

            // 验证：新密码能用BCrypt验证通过（说明确实加密存储了新密码）
            assertThat(BCrypt.checkpw("newpassword123", captor.getValue().getPassword())).isTrue();
        }
    }

    // ==================== 4. audit 审核商家 ====================

    @Nested
    @DisplayName("audit 审核商家")
    class AuditTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void audit_merchantNotFound_throwsException() {
            // 场景：审核一个不存在的商家
            MerchantAuditDTO dto = buildAuditDTO(MERCHANT_ID, MerchantStatusEnum.APPROVED.getCode(), "通过");

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.audit(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("商家状态非待审核 → 抛出MERCHANT_STATUS_ERROR异常")
        void audit_statusNotPending_throwsException() {
            // 场景：商家已经是"已通过"状态了，不能重复审核
            MerchantAuditDTO dto = buildAuditDTO(MERCHANT_ID, MerchantStatusEnum.APPROVED.getCode(), "通过");
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode()); // 已经是已通过状态

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.audit(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_STATUS_ERROR.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("审核状态值非法（不是1或2） → 抛出PARAM_ERROR异常")
        void audit_invalidStatus_throwsException() {
            // 场景：传了一个非法的审核状态值99
            MerchantAuditDTO dto = buildAuditDTO(MERCHANT_ID, 99, "通过");
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.PENDING.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.audit(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("审核通过 → 更新商家状态，自动创建店铺")
        void audit_approve_normal() {
            // 场景：管理员审核通过，商家状态变为已通过，系统自动创建默认店铺
            MerchantAuditDTO dto = buildAuditDTO(MERCHANT_ID, MerchantStatusEnum.APPROVED.getCode(), "资质齐全，审核通过");
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.PENDING.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            when(qualificationMapper.selectOne(any())).thenReturn(null);

            // 执行审核
            merchantService.audit(dto);

            // 验证：更新了商家状态
            verify(merchantMapper).updateById(any(Merchant.class));
            // 验证：审核通过时自动创建了店铺
            verify(shopService).createShop(eq(MERCHANT_ID), any(ShopDTO.class));
        }

        @Test
        @DisplayName("审核拒绝 → 更新商家状态，不创建店铺")
        void audit_reject_normal() {
            // 场景：管理员审核拒绝，商家状态变为已拒绝，不创建店铺
            MerchantAuditDTO dto = buildAuditDTO(MERCHANT_ID, MerchantStatusEnum.REJECTED.getCode(), "营业执照模糊，请重新上传");
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.PENDING.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            when(qualificationMapper.selectOne(any())).thenReturn(null);

            // 执行审核
            merchantService.audit(dto);

            // 验证：更新了商家状态
            verify(merchantMapper).updateById(any(Merchant.class));
            // 验证：审核拒绝时不创建店铺
            verify(shopService, never()).createShop(anyLong(), any(ShopDTO.class));
        }
    }

    // ==================== 5. getMerchantInfo 获取商家信息 ====================

    @Nested
    @DisplayName("getMerchantInfo 获取商家信息")
    class GetMerchantInfoTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void getMerchantInfo_notFound_throwsException() {
            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.getMerchantInfo(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询 → 返回商家信息，联系电话已脱敏")
        void getMerchantInfo_normal_returnsVO() {
            // 场景：查询存在的商家信息
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            // 执行查询
            MerchantVO vo = merchantService.getMerchantInfo(MERCHANT_ID);

            // 验证：返回了正确的商家信息
            assertThat(vo.getId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getName()).isEqualTo("测试商家");
            assertThat(vo.getStatus()).isEqualTo(MerchantStatusEnum.APPROVED.getCode());
            // 验证：联系电话已脱敏（13812345678 → 138****5678）
            assertThat(vo.getContactPhone()).isEqualTo("138****5678");
        }
    }

    // ==================== 6. getMerchantByUserId 根据用户ID获取商家 ====================

    @Nested
    @DisplayName("getMerchantByUserId 根据用户ID获取商家")
    class GetMerchantByUserIdTest {

        @Test
        @DisplayName("用户未入驻商家 → 返回null")
        void getMerchantByUserId_notFound_returnsNull() {
            // 场景：该用户还没有入驻任何商家
            when(merchantMapper.selectOne(any())).thenReturn(null);

            MerchantVO vo = merchantService.getMerchantByUserId(USER_ID);

            // 验证：返回null
            assertThat(vo).isNull();
        }

        @Test
        @DisplayName("正常查询 → 返回商家信息")
        void getMerchantByUserId_normal_returnsVO() {
            // 场景：该用户已入驻商家
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());

            when(merchantMapper.selectOne(any())).thenReturn(merchant);

            MerchantVO vo = merchantService.getMerchantByUserId(USER_ID);

            // 验证：返回了正确的商家信息
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getName()).isEqualTo("测试商家");
        }
    }

    // ==================== 7. updateMerchant 更新商家信息 ====================

    @Nested
    @DisplayName("updateMerchant 更新商家信息")
    class UpdateMerchantTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void updateMerchant_notFound_throwsException() {
            MerchantApplyDTO dto = buildApplyDTO("新名称", PHONE, PASSWORD);

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.updateMerchant(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("商家被禁用 → 抛出MERCHANT_DISABLED异常")
        void updateMerchant_disabled_throwsException() {
            // 场景：被禁用的商家不能修改信息
            MerchantApplyDTO dto = buildApplyDTO("新名称", PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.DISABLED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.updateMerchant(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_DISABLED.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("商家审核中 → 抛出MERCHANT_PENDING异常")
        void updateMerchant_pending_throwsException() {
            // 场景：待审核状态的商家不能修改信息（要等审核完才能改）
            MerchantApplyDTO dto = buildApplyDTO("新名称", PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.PENDING.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.updateMerchant(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_PENDING.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("商家名称重复 → 抛出MERCHANT_NAME_EXISTS异常")
        void updateMerchant_nameExists_throwsException() {
            // 场景：修改名称时，新名称已被其他商家使用
            MerchantApplyDTO dto = buildApplyDTO("已存在名称", PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "旧名称", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            // 模拟：名称查到1条（重复）
            when(merchantMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> merchantService.updateMerchant(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NAME_EXISTS.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("手机号已被其他商家使用 → 抛出MERCHANT_PHONE_EXISTS异常")
        void updateMerchant_phoneExists_throwsException() {
            // 场景：名称没改（不查名称），但手机号被别人用了
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", "13900000000",
                    MerchantStatusEnum.APPROVED.getCode());
            // DTO的名称和商家一样（不触发名称检查），但手机号不同
            MerchantApplyDTO dto = buildApplyDTO("测试商家", PHONE, PASSWORD);

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            // 模拟：手机号查到1条（重复）
            when(merchantMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> merchantService.updateMerchant(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_PHONE_EXISTS.getCode());

            verify(merchantMapper, never()).updateById(any(Merchant.class));
        }

        @Test
        @DisplayName("正常更新 → 调用updateById，状态重置为待审核")
        void updateMerchant_normal_success() {
            // 场景：名称和手机号都没变，正常更新
            MerchantApplyDTO dto = buildApplyDTO("测试商家", PHONE, PASSWORD);
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            when(qualificationMapper.selectOne(any())).thenReturn(null);

            // 执行更新
            merchantService.updateMerchant(MERCHANT_ID, dto);

            // 捕获传给updateById的merchant，验证状态被重置为待审核
            ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
            verify(merchantMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(MerchantStatusEnum.PENDING.getCode());
        }
    }

    // ==================== 8. getAuditStatus 获取审核状态 ====================

    @Nested
    @DisplayName("getAuditStatus 获取审核状态")
    class GetAuditStatusTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void getAuditStatus_notFound_throwsException() {
            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.getAuditStatus(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常查询 → 返回审核状态和审核备注")
        void getAuditStatus_normal_returnsVO() {
            // 场景：查询已通过审核的商家状态
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());
            MerchantQualification qualification = new MerchantQualification();
            qualification.setAuditNote("资质齐全，审核通过");

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);
            when(qualificationMapper.selectOne(any())).thenReturn(qualification);

            // 执行查询
            MerchantAuditVO vo = merchantService.getAuditStatus(MERCHANT_ID);

            // 验证：返回了正确的审核状态信息
            assertThat(vo.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getStatus()).isEqualTo(MerchantStatusEnum.APPROVED.getCode());
            assertThat(vo.getStatusDesc()).isEqualTo("已通过");
            assertThat(vo.getAuditNote()).isEqualTo("资质齐全，审核通过");
        }
    }

    // ==================== 9. checkMerchantActive 校验商家状态 ====================

    @Nested
    @DisplayName("checkMerchantActive 校验商家是否可操作")
    class CheckMerchantActiveTest {

        @Test
        @DisplayName("商家不存在 → 抛出MERCHANT_NOT_FOUND异常")
        void checkMerchantActive_notFound_throwsException() {
            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

            assertThatThrownBy(() -> merchantService.checkMerchantActive(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("商家被禁用 → 抛出MERCHANT_DISABLED异常")
        void checkMerchantActive_disabled_throwsException() {
            // 场景：被禁用的商家不能进行任何操作
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.DISABLED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            assertThatThrownBy(() -> merchantService.checkMerchantActive(MERCHANT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_DISABLED.getCode());
        }

        @Test
        @DisplayName("商家状态正常（已通过） → 校验通过，不抛异常")
        void checkMerchantActive_normal_passes() {
            // 场景：商家状态正常，校验通过
            Merchant merchant = buildMerchant(MERCHANT_ID, USER_ID, "测试商家", PHONE,
                    MerchantStatusEnum.APPROVED.getCode());

            when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant);

            // 执行校验（不应该抛异常）
            merchantService.checkMerchantActive(MERCHANT_ID);

            // 验证：只查了一次数据库，没有其他操作
            verify(merchantMapper).selectById(MERCHANT_ID);
        }
    }
}
