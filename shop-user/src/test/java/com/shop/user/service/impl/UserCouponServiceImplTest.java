package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.entity.UserCoupon;
import com.shop.model.coupon.enums.CouponStatusEnum;
import com.shop.model.coupon.enums.CouponTypeEnum;
import com.shop.model.coupon.enums.UserCouponStatusEnum;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.model.coupon.vo.UserCouponVO;
import com.shop.user.feign.MerchantCouponFeignClient;
import com.shop.user.mapper.UserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户优惠券服务 UserCouponServiceImpl 单元测试
 * <p>
 * 小白理解：用户优惠券服务管着"领券、查我的券、下单时算优惠、用券、退券"这些事。
 * 我们把真正访问数据库的 Mapper 和跨服务调用的 Feign 客户端都"假装"一下（Mock），
 * 这样测试不需要真连数据库和其他微服务，跑得又快又稳。
 * </p>
 * <p>
 * 测试覆盖的 6 个核心方法：
 * 1. receiveCoupon    领取优惠券（Feign 查模板 → 校验 → 写表 → Feign 加领取数）
 * 2. getMyCoupons     查询我的优惠券列表（分页）
 * 3. getReceivableCouponList 查询可领取优惠券（Feign 拉取）
 * 4. getUsableCoupons 查询可用优惠券（含 3 种券类型优惠金额计算）
 * 5. useCoupon        核销优惠券（校验 → 算优惠 → 标记已用 → Feign 加使用数）
 * 6. rollbackCoupon   回退优惠券（按订单号恢复未使用 → Feign 减使用数）
 * </p>
 * <p>
 * 关键点说明：
 * - 涉及 3 种优惠券类型计算：满减券(type=1)、折扣券(type=2)、立减券(type=3)
 * - Feign 调用返回 Result<T> 包装类，mock 时要注意 isSuccess() 和 getData()
 * - Feign 加/减使用数是"弱依赖"：失败不影响主流程（只打日志），这是为了保障核心交易
 * - 用 @BeforeAll 初始化 MyBatis-Plus Lambda 缓存，否则 .eq(UserCoupon::getXxx) 会报错
 * </p>
 */
@DisplayName("用户优惠券服务 UserCouponServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class UserCouponServiceImplTest {

    /** 假装操作 user_coupon 表的 Mapper */
    @Mock
    private UserCouponMapper userCouponMapper;

    /** 假装调用 shop-marketing 的 Feign 客户端 */
    @Mock
    private MerchantCouponFeignClient merchantCouponFeignClient;

    /** 被测试的用户优惠券服务，Mock 会自动注入上面两个依赖 */
    @InjectMocks
    private UserCouponServiceImpl userCouponService;

    /** 测试用用户ID */
    private static final Long USER_ID = 1L;
    /** 测试用优惠券模板ID */
    private static final Long COUPON_ID = 100L;
    /** 测试用用户券ID（user_coupon 表主键） */
    private static final Long USER_COUPON_ID = 1001L;
    /** 测试用订单号 */
    private static final String ORDER_NO = "ORD202607160001";

    /**
     * 初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：Service 里用了 .eq(UserCoupon::getUserId, ...) 这种写法，
     * MyBatis-Plus 需要知道 UserCoupon::getUserId 对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserCoupon.class);
    }

    // ==================== 1. receiveCoupon 领取优惠券 ====================

    @Nested
    @DisplayName("receiveCoupon 领取优惠券")
    class ReceiveCouponTest {

        @Test
        @DisplayName("Feign获取模板失败 → 抛 DATA_NOT_FOUND 异常，不插入记录")
        void receiveCoupon_feignFail_throwsException() {
            // 场景：调用 shop-marketing 查优惠券模板，返回失败
            when(merchantCouponFeignClient.getCouponById(COUPON_ID))
                    .thenReturn(Result.fail(ErrorCode.DATA_NOT_FOUND));

            // 验证：抛"数据不存在"异常
            assertThatThrownBy(() -> userCouponService.receiveCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            // 验证：没有写入用户券
            verify(userCouponMapper, never()).insert(any(UserCoupon.class));
        }

        @Test
        @DisplayName("优惠券非进行中状态(待生效) → 抛 OPERATION_FAIL 异常")
        void receiveCoupon_notActive_throwsException() {
            // 场景：优惠券状态是"待生效"，还没开始
            Coupon coupon = buildCoupon(CouponStatusEnum.PENDING.getCode());
            when(merchantCouponFeignClient.getCouponById(COUPON_ID)).thenReturn(Result.success(coupon));

            assertThatThrownBy(() -> userCouponService.receiveCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不在进行中状态");

            verify(userCouponMapper, never()).insert(any(UserCoupon.class));
        }

        @Test
        @DisplayName("不在领取时间范围(还没开始) → 抛 OPERATION_FAIL 异常")
        void receiveCoupon_outOfReceiveTime_throwsException() {
            // 场景：领取开始时间是明天，现在还不能领
            Coupon coupon = buildCoupon(CouponStatusEnum.ACTIVE.getCode());
            coupon.setReceiveStartTime(LocalDateTime.now().plusDays(1));
            coupon.setReceiveEndTime(LocalDateTime.now().plusDays(10));
            when(merchantCouponFeignClient.getCouponById(COUPON_ID)).thenReturn(Result.success(coupon));

            assertThatThrownBy(() -> userCouponService.receiveCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不在领取时间范围");

            verify(userCouponMapper, never()).insert(any(UserCoupon.class));
        }

        @Test
        @DisplayName("已达每人限领数 → 抛 OPERATION_FAIL 异常")
        void receiveCoupon_perLimitReached_throwsException() {
            // 场景：每人限领1张，用户已经领过1张
            Coupon coupon = buildCoupon(CouponStatusEnum.ACTIVE.getCode());
            coupon.setPerLimit(1);
            when(merchantCouponFeignClient.getCouponById(COUPON_ID)).thenReturn(Result.success(coupon));
            when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> userCouponService.receiveCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("限领");

            verify(userCouponMapper, never()).insert(any(UserCoupon.class));
        }

        @Test
        @DisplayName("incr返回false(已被领完) → 抛 OPERATION_FAIL 异常")
        void receiveCoupon_couponExhausted_throwsException() {
            // 场景：本地校验都通过，插入成功，但 Feign 增加领取数时返回 false（券被抢光了）
            Coupon coupon = buildCoupon(CouponStatusEnum.ACTIVE.getCode());
            when(merchantCouponFeignClient.getCouponById(COUPON_ID)).thenReturn(Result.success(coupon));
            when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);
            when(merchantCouponFeignClient.incrReceivedCount(COUPON_ID))
                    .thenReturn(Result.success(false));

            assertThatThrownBy(() -> userCouponService.receiveCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已被领完");
        }

        @Test
        @DisplayName("正常领取 → 写入用户券(含冗余信息)并增加领取数")
        void receiveCoupon_normal_success() {
            // 场景：所有校验通过，Feign 增加领取数返回 true
            Coupon coupon = buildCoupon(CouponStatusEnum.ACTIVE.getCode());
            when(merchantCouponFeignClient.getCouponById(COUPON_ID)).thenReturn(Result.success(coupon));
            when(userCouponMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);
            when(merchantCouponFeignClient.incrReceivedCount(COUPON_ID))
                    .thenReturn(Result.success(true));

            userCouponService.receiveCoupon(USER_ID, COUPON_ID);

            // 验证：插入了用户券，且冗余信息（名称/类型/面额/门槛）从模板拷贝过来
            ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
            verify(userCouponMapper).insert(captor.capture());
            UserCoupon saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getCouponId()).isEqualTo(COUPON_ID);
            assertThat(saved.getCouponName()).isEqualTo(coupon.getName());
            assertThat(saved.getCouponType()).isEqualTo(coupon.getType());
            assertThat(saved.getAmount()).isEqualByComparingTo(coupon.getAmount());
            assertThat(saved.getThreshold()).isEqualByComparingTo(coupon.getThreshold());
            assertThat(saved.getStatus()).isEqualTo(UserCouponStatusEnum.UNUSED.getCode());
            // 验证：调用了 Feign 增加领取数
            verify(merchantCouponFeignClient).incrReceivedCount(COUPON_ID);
        }
    }

    // ==================== 2. getMyCoupons 查询我的优惠券列表 ====================

    @Nested
    @DisplayName("getMyCoupons 查询我的优惠券列表")
    class GetMyCouponsTest {

        @Test
        @DisplayName("正常分页查询：返回用户券列表，VO填充类型/状态描述")
        void getMyCoupons_returnsPagedResult() {
            // 场景：查第1页每页10条，数据库有2条记录（1未使用 + 1已使用）
            List<UserCoupon> coupons = List.of(
                    buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode()),
                    buildUserCoupon(UserCouponStatusEnum.USED.getCode())
            );
            Page<UserCoupon> page = new Page<>(1, 10);
            page.setRecords(coupons);
            page.setTotal(2);
            when(userCouponMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            PageResult<UserCouponVO> result = userCouponService.getMyCoupons(USER_ID, null, 1, 10);

            // 验证：分页信息正确
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            // 验证：VO 填充了类型描述（如"满减"）和状态描述（如"未使用"）
            UserCouponVO vo = result.getRecords().get(0);
            assertThat(vo.getCouponTypeDesc()).isEqualTo(CouponTypeEnum.FULL_REDUCTION.getDesc());
            assertThat(vo.getStatusDesc()).isEqualTo(UserCouponStatusEnum.UNUSED.getDesc());
        }

        @Test
        @DisplayName("空结果：返回空列表和total=0")
        void getMyCoupons_emptyResult_returnsEmpty() {
            Page<UserCoupon> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            when(userCouponMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(emptyPage);

            PageResult<UserCouponVO> result = userCouponService.getMyCoupons(USER_ID, 0, 1, 10);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }
    }

    // ==================== 3. getReceivableCouponList 可领取优惠券列表 ====================

    @Nested
    @DisplayName("getReceivableCouponList 可领取优惠券列表")
    class GetReceivableCouponListTest {

        @Test
        @DisplayName("Feign返回失败 → 返回空列表（降级）")
        void getReceivableCouponList_feignFail_returnsEmpty() {
            when(merchantCouponFeignClient.getReceivableCouponList())
                    .thenReturn(Result.fail("服务异常"));

            List<CouponVO> result = userCouponService.getReceivableCouponList();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Feign返回空列表 → 返回空列表")
        void getReceivableCouponList_emptyData_returnsEmpty() {
            when(merchantCouponFeignClient.getReceivableCouponList())
                    .thenReturn(Result.success(List.of()));

            List<CouponVO> result = userCouponService.getReceivableCouponList();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常返回优惠券列表")
        void getReceivableCouponList_normal_returnsList() {
            CouponVO couponVO = new CouponVO();
            couponVO.setId(COUPON_ID);
            couponVO.setName("满100减20");
            when(merchantCouponFeignClient.getReceivableCouponList())
                    .thenReturn(Result.success(List.of(couponVO)));

            List<CouponVO> result = userCouponService.getReceivableCouponList();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(COUPON_ID);
        }
    }

    // ==================== 4. getUsableCoupons 可用优惠券（含3种券类型优惠计算） ====================

    @Nested
    @DisplayName("getUsableCoupons 可用优惠券及优惠金额计算")
    class GetUsableCouponsTest {

        @Test
        @DisplayName("满减券未达门槛(99<100) → discountAmount=0")
        void getUsableCoupons_fullReductionBelowThreshold_zeroDiscount() {
            // 场景：满100减20，订单金额99元，未达门槛，优惠0
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            // buildUserCoupon 默认就是满减券：threshold=100, amount=20
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));

            List<UserCouponVO> result = userCouponService.getUsableCoupons(USER_ID, new BigDecimal("99"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("满减券达门槛(150>=100) → discountAmount=20")
        void getUsableCoupons_fullReductionMetThreshold_returnsAmount() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));

            List<UserCouponVO> result = userCouponService.getUsableCoupons(USER_ID, new BigDecimal("150"));

            assertThat(result.get(0).getDiscountAmount()).isEqualByComparingTo(new BigDecimal("20"));
        }

        @Test
        @DisplayName("折扣券(85折) → discountAmount=orderAmount*0.15=30.00")
        void getUsableCoupons_discount_returnsCalculatedAmount() {
            // 场景：85折券，amount=0.85，订单200元，优惠 = 200 * (1-0.85) = 30
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            uc.setCouponType(CouponTypeEnum.DISCOUNT.getCode());
            uc.setAmount(new BigDecimal("0.85"));
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));

            List<UserCouponVO> result = userCouponService.getUsableCoupons(USER_ID, new BigDecimal("200"));

            assertThat(result.get(0).getDiscountAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("立减券 → discountAmount=amount=15")
        void getUsableCoupons_directDiscount_returnsAmount() {
            // 场景：立减15元券，无门槛，订单50元，优惠15元
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            uc.setCouponType(CouponTypeEnum.DIRECT_DISCOUNT.getCode());
            uc.setAmount(new BigDecimal("15"));
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));

            List<UserCouponVO> result = userCouponService.getUsableCoupons(USER_ID, new BigDecimal("50"));

            assertThat(result.get(0).getDiscountAmount()).isEqualByComparingTo(new BigDecimal("15"));
        }

        @Test
        @DisplayName("无可用券 → 返回空列表")
        void getUsableCoupons_noCoupons_returnsEmpty() {
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            List<UserCouponVO> result = userCouponService.getUsableCoupons(USER_ID, new BigDecimal("100"));

            assertThat(result).isEmpty();
        }
    }

    // ==================== 5. useCoupon 核销优惠券 ====================

    @Nested
    @DisplayName("useCoupon 核销优惠券")
    class UseCouponTest {

        @Test
        @DisplayName("用户券不存在 → 抛 DATA_NOT_FOUND 异常")
        void useCoupon_notFound_throwsException() {
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> userCouponService.useCoupon(buildUseDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("非本人券 → 抛 FORBIDDEN 异常")
        void useCoupon_notOwner_throwsException() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            uc.setUserId(999L); // 别人的券
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);

            assertThatThrownBy(() -> userCouponService.useCoupon(buildUseDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("券已使用 → 抛 OPERATION_FAIL 异常")
        void useCoupon_alreadyUsed_throwsException() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.USED.getCode());
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);

            assertThatThrownBy(() -> userCouponService.useCoupon(buildUseDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已使用");

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("券不在有效期内(已过期) → 抛 OPERATION_FAIL 异常")
        void useCoupon_expired_throwsException() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            // 有效期已经过了（昨天结束）
            uc.setValidStartTime(LocalDateTime.now().minusDays(10));
            uc.setValidEndTime(LocalDateTime.now().minusDays(1));
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);

            assertThatThrownBy(() -> userCouponService.useCoupon(buildUseDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不在有效期内");

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("满减券未达门槛(99<100) → 抛 OPERATION_FAIL 异常")
        void useCoupon_fullReductionBelowThreshold_throwsException() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);

            CouponUseDTO dto = buildUseDTO();
            dto.setOrderAmount(new BigDecimal("99")); // 未满100
            assertThatThrownBy(() -> userCouponService.useCoupon(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未满");

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("正常核销满减券 → 返回优惠20，更新状态为已使用")
        void useCoupon_normal_returnsDiscount() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);
            when(userCouponMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            BigDecimal discount = userCouponService.useCoupon(buildUseDTO());

            // 验证：返回优惠金额20（满100减20，订单150）
            assertThat(discount).isEqualByComparingTo(new BigDecimal("20"));
            // 验证：更新了状态为已使用
            verify(userCouponMapper).update(any(), any(LambdaUpdateWrapper.class));
            // 验证：调用了 Feign 增加使用数
            verify(merchantCouponFeignClient).incrUsedCount(COUPON_ID);
        }

        @Test
        @DisplayName("Feign incrUsedCount 异常 → 不影响核销（弱依赖，只打日志）")
        void useCoupon_feignException_stillSucceeds() {
            // 场景：核销主流程成功，但 Feign 增加使用数抛异常，不应影响核销结果
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.UNUSED.getCode());
            uc.setCouponType(CouponTypeEnum.DIRECT_DISCOUNT.getCode());
            uc.setAmount(new BigDecimal("15"));
            when(userCouponMapper.selectById(USER_COUPON_ID)).thenReturn(uc);
            when(userCouponMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
            // Feign 抛异常
            when(merchantCouponFeignClient.incrUsedCount(COUPON_ID))
                    .thenThrow(new RuntimeException("network error"));

            CouponUseDTO dto = buildUseDTO();
            dto.setOrderAmount(new BigDecimal("50"));
            BigDecimal discount = userCouponService.useCoupon(dto);

            // 验证：核销仍然成功，返回立减15元
            assertThat(discount).isEqualByComparingTo(new BigDecimal("15"));
        }
    }

    // ==================== 6. rollbackCoupon 回退优惠券 ====================

    @Nested
    @DisplayName("rollbackCoupon 回退优惠券")
    class RollbackCouponTest {

        @Test
        @DisplayName("订单无已使用券 → 不调用update和decrUsedCount")
        void rollbackCoupon_noCoupons_noUpdate() {
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            userCouponService.rollbackCoupon(ORDER_NO);

            verify(userCouponMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
            verify(merchantCouponFeignClient, never()).decrUsedCount(any());
        }

        @Test
        @DisplayName("正常回退 → 恢复未使用状态并减少使用数")
        void rollbackCoupon_normal_restoresUnused() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.USED.getCode());
            uc.setOrderNo(ORDER_NO);
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));
            when(userCouponMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            userCouponService.rollbackCoupon(ORDER_NO);

            // 验证：调用了 update 恢复状态
            verify(userCouponMapper).update(any(), any(LambdaUpdateWrapper.class));
            // 验证：调用了 Feign 减少使用数
            verify(merchantCouponFeignClient).decrUsedCount(COUPON_ID);
        }

        @Test
        @DisplayName("Feign decrUsedCount 异常 → 不影响回退（弱依赖）")
        void rollbackCoupon_feignException_stillSucceeds() {
            UserCoupon uc = buildUserCoupon(UserCouponStatusEnum.USED.getCode());
            uc.setOrderNo(ORDER_NO);
            when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(uc));
            when(userCouponMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
            // Feign 抛异常
            when(merchantCouponFeignClient.decrUsedCount(COUPON_ID))
                    .thenThrow(new RuntimeException("network error"));

            userCouponService.rollbackCoupon(ORDER_NO);

            // 验证：即使 Feign 异常，update 仍然被调用（回退主流程不受影响）
            verify(userCouponMapper).update(any(), any(LambdaUpdateWrapper.class));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用的优惠券模板
     * <p>
     * 默认创建一张"满100减20"的满减券，状态、领取时间、有效期都设为可领取的合理值。
     * 测试中可通过 setter 修改需要的字段。
     * </p>
     *
     * @param status 优惠券模板状态（CouponStatusEnum 的 code）
     * @return 构造好的 Coupon
     */
    private Coupon buildCoupon(Integer status) {
        Coupon coupon = new Coupon();
        coupon.setId(COUPON_ID);
        coupon.setMerchantId(0L);
        coupon.setName("满100减20");
        coupon.setType(CouponTypeEnum.FULL_REDUCTION.getCode());
        coupon.setAmount(new BigDecimal("20"));
        coupon.setThreshold(new BigDecimal("100"));
        coupon.setTotalCount(100);
        coupon.setReceivedCount(10);
        coupon.setUsedCount(0);
        coupon.setPerLimit(5);
        // 领取时间窗口：昨天到10天后（保证当前时间在窗口内）
        coupon.setReceiveStartTime(LocalDateTime.now().minusDays(1));
        coupon.setReceiveEndTime(LocalDateTime.now().plusDays(10));
        // 使用有效期：昨天到30天后（保证当前时间在有效期内）
        coupon.setValidStartTime(LocalDateTime.now().minusDays(1));
        coupon.setValidEndTime(LocalDateTime.now().plusDays(30));
        coupon.setStatus(status);
        return coupon;
    }

    /**
     * 构造测试用的用户券
     * <p>
     * 默认创建一张"满100减20"的满减用户券，状态、有效期都设为可用。
     * 测试中可通过 setter 修改 couponType/amount/threshold/status 等字段。
     * </p>
     *
     * @param status 用户券状态（UserCouponStatusEnum 的 code）
     * @return 构造好的 UserCoupon
     */
    private UserCoupon buildUserCoupon(Integer status) {
        UserCoupon uc = new UserCoupon();
        uc.setId(USER_COUPON_ID);
        uc.setUserId(USER_ID);
        uc.setCouponId(COUPON_ID);
        uc.setMerchantId(0L);
        uc.setCouponName("满100减20");
        uc.setCouponType(CouponTypeEnum.FULL_REDUCTION.getCode());
        uc.setAmount(new BigDecimal("20"));
        uc.setThreshold(new BigDecimal("100"));
        // 有效期：昨天到30天后（保证当前时间在有效期内）
        uc.setValidStartTime(LocalDateTime.now().minusDays(1));
        uc.setValidEndTime(LocalDateTime.now().plusDays(30));
        uc.setStatus(status);
        return uc;
    }

    /**
     * 构造核销请求DTO
     * <p>默认满减券场景：用户券ID=1001，订单号=ORD202607160001，订单金额=150元（满足满100门槛）</p>
     *
     * @return 构造好的 CouponUseDTO
     */
    private CouponUseDTO buildUseDTO() {
        CouponUseDTO dto = new CouponUseDTO();
        dto.setUserId(USER_ID);
        dto.setUserCouponId(USER_COUPON_ID);
        dto.setOrderNo(ORDER_NO);
        dto.setOrderAmount(new BigDecimal("150"));
        return dto;
    }
}
