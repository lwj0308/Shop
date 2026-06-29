package com.shop.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.marketing.mapper.CouponMapper;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.dto.CouponQueryDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.enums.CouponStatusEnum;
import com.shop.model.coupon.enums.CouponTypeEnum;
import com.shop.model.coupon.vo.CouponVO;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券服务实现类（CouponServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证优惠券模板的创建、修改、下架、查询、领用计数等操作能不能正常工作。
 * 简单理解：我们把真正访问数据库的 Mapper "假装"一下（Mock），
 * 这样测试就不需要真的连数据库，跑得又快又稳定。
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 * <p>
 * 测试覆盖的10个方法：createCoupon、updateCoupon、offlineCoupon、getCouponList、
 * getCouponDetail、getReceivableCouponList、getCouponById、incrReceivedCount、incrUsedCount、decrUsedCount
 * </p>
 */
@DisplayName("优惠券服务 CouponServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    /** 假装数据库操作的 Mapper，不真的连数据库 */
    @Mock
    private CouponMapper couponMapper;

    /** 被测试的优惠券服务，Mockito 会自动把上面的 Mock 注入进来 */
    @InjectMocks
    private CouponServiceImpl couponService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long MERCHANT_ID = 2001L;          // 当前商家ID
    private static final Long OTHER_MERCHANT_ID = 2002L;    // 另一个商家ID（测试越权用）
    private static final Long COUPON_ID = 5001L;            // 优惠券ID
    private static final Long NEW_COUPON_ID = 6001L;        // 新创建优惠券的ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：CouponServiceImpl 的查询方法用到了 .eq(Coupon::getMerchantId, ...) 这种写法，
     * 这行代码会让 MyBatis-Plus 去查"merchantId 字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：Coupon 这个实体有哪些字段、对应哪些列。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Coupon.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个合法的"满100减20"满减券DTO（领取时间在未来，状态会是待生效）
     * 小白理解：商家创建优惠券时填的表单数据，所有时间规则都合法
     *
     * @return 构造好的 CouponCreateDTO
     */
    private CouponCreateDTO buildValidFullReductionDto() {
        CouponCreateDTO dto = new CouponCreateDTO();
        dto.setName("满100减20");
        dto.setType(CouponTypeEnum.FULL_REDUCTION.getCode());
        dto.setAmount(new BigDecimal("20.00"));
        dto.setThreshold(new BigDecimal("100.00"));
        dto.setTotalCount(100);
        dto.setPerLimit(1);
        LocalDateTime now = LocalDateTime.now();
        // 领取时间在未来1~2天，使用时间在未来3~10天（领取结束要早于等于有效期开始）
        dto.setReceiveStartTime(now.plusDays(1));
        dto.setReceiveEndTime(now.plusDays(2));
        dto.setValidStartTime(now.plusDays(3));
        dto.setValidEndTime(now.plusDays(10));
        dto.setDescription("测试优惠券");
        return dto;
    }

    /**
     * 构造一个优惠券实体
     * 小白理解：数据库 coupon 表里的一条记录
     *
     * @param id         优惠券ID
     * @param merchantId 商家ID
     * @param status     状态码（对应 CouponStatusEnum）
     * @return 构造好的 Coupon
     */
    private Coupon buildCoupon(Long id, Long merchantId, Integer status) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setMerchantId(merchantId);
        coupon.setName("满100减20");
        coupon.setType(CouponTypeEnum.FULL_REDUCTION.getCode());
        coupon.setAmount(new BigDecimal("20.00"));
        coupon.setThreshold(new BigDecimal("100.00"));
        coupon.setTotalCount(100);
        coupon.setReceivedCount(0);
        coupon.setUsedCount(0);
        coupon.setPerLimit(1);
        coupon.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        coupon.setReceiveStartTime(now.plusDays(1));
        coupon.setReceiveEndTime(now.plusDays(2));
        coupon.setValidStartTime(now.plusDays(3));
        coupon.setValidEndTime(now.plusDays(10));
        coupon.setDescription("测试");
        return coupon;
    }

    // ==================== 1. createCoupon 创建优惠券 ====================

    @Nested
    @DisplayName("createCoupon 创建优惠券")
    class CreateCouponTest {

        @Test
        @DisplayName("领取开始时间晚于结束时间 → 抛出参数错误异常")
        void createCoupon_receiveStartAfterEnd_throwsException() {
            // 场景：领取开始时间填在了结束时间之后，时间窗口不合法
            CouponCreateDTO dto = buildValidFullReductionDto();
            dto.setReceiveStartTime(dto.getReceiveEndTime().plusDays(1));

            // 验证：抛出参数错误异常
            assertThatThrownBy(() -> couponService.createCoupon(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：校验失败，没有调用insert
            verify(couponMapper, never()).insert(any(Coupon.class));
        }

        @Test
        @DisplayName("折扣券折扣率大于等于1 → 抛出参数错误异常")
        void createCoupon_discountRateOutOfRange_throwsException() {
            // 场景：创建折扣券，但折扣率填了2.00（应该0~1之间，如0.85表示85折）
            CouponCreateDTO dto = buildValidFullReductionDto();
            dto.setType(CouponTypeEnum.DISCOUNT.getCode());
            dto.setAmount(new BigDecimal("2.00")); // 大于1，不合法

            // 验证：抛出参数错误异常
            assertThatThrownBy(() -> couponService.createCoupon(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(couponMapper, never()).insert(any(Coupon.class));
        }

        @Test
        @DisplayName("正常创建满减券（领取时间在未来）→ 状态初始化为待生效，返回优惠券ID")
        void createCoupon_normalFutureTime_statusPending() {
            // 场景：领取时间在未来，创建后状态应该是"待生效(0)"
            CouponCreateDTO dto = buildValidFullReductionDto();

            // 模拟 insert 时把雪花ID回填到实体上（真实环境由MyBatis-Plus自动设置）
            when(couponMapper.insert(any(Coupon.class))).thenAnswer(invocation -> {
                Coupon c = invocation.getArgument(0);
                c.setId(NEW_COUPON_ID);
                return 1;
            });

            // 执行创建
            Long couponId = couponService.createCoupon(MERCHANT_ID, dto);

            // 验证：返回的ID就是insert时回填的ID
            assertThat(couponId).isEqualTo(NEW_COUPON_ID);

            // 捕获传给insert的实体，校验关键字段被正确初始化
            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            verify(couponMapper).insert(captor.capture());
            Coupon saved = captor.getValue();
            assertThat(saved.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(saved.getStatus()).isEqualTo(CouponStatusEnum.PENDING.getCode());
            assertThat(saved.getReceivedCount()).isEqualTo(0);
            assertThat(saved.getUsedCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("创建时当前时间在领取窗口内 → 状态初始化为进行中")
        void createCoupon_nowInReceiveWindow_statusActive() {
            // 场景：领取开始时间已过、结束时间未到，当前正在领取期内 → 状态"进行中(1)"
            CouponCreateDTO dto = buildValidFullReductionDto();
            LocalDateTime now = LocalDateTime.now();
            dto.setReceiveStartTime(now.minusDays(1));  // 领取已开始
            dto.setReceiveEndTime(now.plusDays(1));      // 领取未结束
            dto.setValidStartTime(now.plusDays(2));      // 有效期开始（要晚于等于领取结束）
            dto.setValidEndTime(now.plusDays(10));

            when(couponMapper.insert(any(Coupon.class))).thenAnswer(invocation -> {
                invocation.<Coupon>getArgument(0).setId(NEW_COUPON_ID);
                return 1;
            });

            couponService.createCoupon(MERCHANT_ID, dto);

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            verify(couponMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CouponStatusEnum.ACTIVE.getCode());
        }

        @Test
        @DisplayName("创建时已过领取结束时间 → 状态初始化为已结束")
        void createCoupon_nowAfterReceiveEnd_statusEnded() {
            // 场景：领取结束时间已过，创建后直接是"已结束(2)"状态
            CouponCreateDTO dto = buildValidFullReductionDto();
            LocalDateTime now = LocalDateTime.now();
            dto.setReceiveStartTime(now.minusDays(5));
            dto.setReceiveEndTime(now.minusDays(1));    // 领取已结束
            dto.setValidStartTime(now.minusDays(1));    // 有效期开始等于领取结束（合法）
            dto.setValidEndTime(now.plusDays(1));

            when(couponMapper.insert(any(Coupon.class))).thenAnswer(invocation -> {
                invocation.<Coupon>getArgument(0).setId(NEW_COUPON_ID);
                return 1;
            });

            couponService.createCoupon(MERCHANT_ID, dto);

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            verify(couponMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CouponStatusEnum.ENDED.getCode());
        }
    }

    // ==================== 2. updateCoupon 修改优惠券 ====================

    @Nested
    @DisplayName("updateCoupon 修改优惠券")
    class UpdateCouponTest {

        @Test
        @DisplayName("优惠券不存在 → 抛出数据不存在异常")
        void updateCoupon_notFound_throwsException() {
            // 场景：修改一个不存在的优惠券
            when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.updateCoupon(MERCHANT_ID, COUPON_ID, buildValidFullReductionDto()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            verify(couponMapper, never()).updateById(any(Coupon.class));
        }

        @Test
        @DisplayName("非本人优惠券 → 抛出无权限异常")
        void updateCoupon_notOwner_throwsForbidden() {
            // 场景：优惠券存在，但属于另一个商家，当前商家无权修改
            Coupon coupon = buildCoupon(COUPON_ID, OTHER_MERCHANT_ID, CouponStatusEnum.PENDING.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            assertThatThrownBy(() -> couponService.updateCoupon(MERCHANT_ID, COUPON_ID, buildValidFullReductionDto()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            verify(couponMapper, never()).updateById(any(Coupon.class));
        }

        @Test
        @DisplayName("非待生效状态（进行中）→ 抛出操作失败异常")
        void updateCoupon_notPending_throwsException() {
            // 场景：优惠券是"进行中"状态，不能修改（只有待生效才能改）
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            assertThatThrownBy(() -> couponService.updateCoupon(MERCHANT_ID, COUPON_ID, buildValidFullReductionDto()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            verify(couponMapper, never()).updateById(any(Coupon.class));
        }

        @Test
        @DisplayName("待生效状态正常修改 → 调用updateById")
        void updateCoupon_pendingNormal_updateById() {
            // 场景：待生效状态的优惠券，提交合法的新参数，应成功修改
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.PENDING.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            couponService.updateCoupon(MERCHANT_ID, COUPON_ID, buildValidFullReductionDto());

            // 验证：调用了updateById
            verify(couponMapper).updateById(any(Coupon.class));
        }
    }

    // ==================== 3. offlineCoupon 下架优惠券 ====================

    @Nested
    @DisplayName("offlineCoupon 下架优惠券")
    class OfflineCouponTest {

        @Test
        @DisplayName("优惠券不存在 → 抛出数据不存在异常")
        void offlineCoupon_notFound_throwsException() {
            when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.offlineCoupon(MERCHANT_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            verify(couponMapper, never()).updateById(any(Coupon.class));
        }

        @Test
        @DisplayName("优惠券已是下架状态 → 抛出操作失败异常")
        void offlineCoupon_alreadyOffline_throwsException() {
            // 场景：优惠券已经是下架状态，重复下架应报错
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.OFFLINE.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            assertThatThrownBy(() -> couponService.offlineCoupon(MERCHANT_ID, COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            verify(couponMapper, never()).updateById(any(Coupon.class));
        }

        @Test
        @DisplayName("正常下架 → 状态变为已下架，调用updateById")
        void offlineCoupon_normal_statusChangedToOffline() {
            // 场景：进行中的优惠券下架，状态应变为"已下架(3)"
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            couponService.offlineCoupon(MERCHANT_ID, COUPON_ID);

            verify(couponMapper).updateById(any(Coupon.class));
            assertThat(coupon.getStatus()).isEqualTo(CouponStatusEnum.OFFLINE.getCode());
        }
    }

    // ==================== 4. getCouponList 查询优惠券列表 ====================

    @Nested
    @DisplayName("getCouponList 查询优惠券列表")
    class GetCouponListTest {

        @Test
        @DisplayName("分页查询 → 返回VO列表，正确填充描述和剩余数量")
        void getCouponList_pagedQuery_returnsVoList() {
            // 场景：商家查询自己的优惠券列表，返回2条记录
            Coupon c1 = buildCoupon(1L, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            c1.setTotalCount(100);
            c1.setReceivedCount(30);  // 剩余 = 100 - 30 = 70
            Coupon c2 = buildCoupon(2L, MERCHANT_ID, CouponStatusEnum.PENDING.getCode());
            c2.setTotalCount(0);      // 不限量，剩余应为 -1

            Page<Coupon> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(c1, c2));
            page.setTotal(2);
            when(couponMapper.selectPage(any(), any())).thenReturn(page);

            CouponQueryDTO query = new CouponQueryDTO();
            query.setPageNum(1);
            query.setPageSize(10);

            PageResult<CouponVO> result = couponService.getCouponList(MERCHANT_ID, query);

            // 验证：分页信息和记录数正确
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getRecords()).hasSize(2);
            // 验证：VO 填充了类型描述、状态描述、剩余数量
            CouponVO vo1 = result.getRecords().get(0);
            assertThat(vo1.getTypeDesc()).isEqualTo("满减");
            assertThat(vo1.getStatusDesc()).isEqualTo("进行中");
            assertThat(vo1.getRemainCount()).isEqualTo(70);
            // 不限量券剩余数量为 -1
            assertThat(result.getRecords().get(1).getRemainCount()).isEqualTo(-1);
        }
    }

    // ==================== 5. getCouponDetail 查询优惠券详情 ====================

    @Nested
    @DisplayName("getCouponDetail 查询优惠券详情")
    class GetCouponDetailTest {

        @Test
        @DisplayName("优惠券不存在 → 抛出数据不存在异常")
        void getCouponDetail_notFound_throwsException() {
            when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

            assertThatThrownBy(() -> couponService.getCouponDetail(COUPON_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常返回 → 填充类型和状态描述")
        void getCouponDetail_normal_returnsVo() {
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.PENDING.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            CouponVO vo = couponService.getCouponDetail(COUPON_ID);

            assertThat(vo.getId()).isEqualTo(COUPON_ID);
            assertThat(vo.getTypeDesc()).isEqualTo("满减");
            assertThat(vo.getStatusDesc()).isEqualTo("待生效");
        }
    }

    // ==================== 6. getReceivableCouponList 可领取优惠券列表 ====================

    @Nested
    @DisplayName("getReceivableCouponList 可领取优惠券列表")
    class GetReceivableCouponListTest {

        @Test
        @DisplayName("查询可领取列表 → 返回进行中的优惠券VO列表")
        void getReceivableCouponList_returnsActiveCoupons() {
            // 场景：领券中心查询，返回2张可领取的优惠券
            Coupon c1 = buildCoupon(1L, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            Coupon c2 = buildCoupon(2L, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            when(couponMapper.selectList(any())).thenReturn(Arrays.asList(c1, c2));

            List<CouponVO> list = couponService.getReceivableCouponList(MERCHANT_ID);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getId()).isEqualTo(1L);
            assertThat(list.get(1).getId()).isEqualTo(2L);
        }
    }

    // ==================== 7. getCouponById 根据ID获取优惠券（内部接口） ====================

    @Nested
    @DisplayName("getCouponById 根据ID获取优惠券")
    class GetCouponByIdTest {

        @Test
        @DisplayName("存在 → 返回优惠券实体")
        void getCouponById_exists_returnsEntity() {
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            Coupon result = couponService.getCouponById(COUPON_ID);

            assertThat(result).isSameAs(coupon);
        }

        @Test
        @DisplayName("不存在 → 返回null")
        void getCouponById_notExists_returnsNull() {
            when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

            Coupon result = couponService.getCouponById(COUPON_ID);

            assertThat(result).isNull();
        }
    }

    // ==================== 8. incrReceivedCount 增加已领取数量 ====================

    @Nested
    @DisplayName("incrReceivedCount 增加已领取数量")
    class IncrReceivedCountTest {

        @Test
        @DisplayName("优惠券不存在 → 返回false，不调用update")
        void incrReceivedCount_notFound_returnsFalse() {
            when(couponMapper.selectById(COUPON_ID)).thenReturn(null);

            boolean result = couponService.incrReceivedCount(COUPON_ID);

            assertThat(result).isFalse();
            verify(couponMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("不限量券（totalCount=0）→ 调用update，返回true")
        void incrReceivedCount_unlimited_returnsTrue() {
            // 场景：totalCount=0表示不限量，直接+1，不校验库存
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            coupon.setTotalCount(0);
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            boolean result = couponService.incrReceivedCount(COUPON_ID);

            assertThat(result).isTrue();
            verify(couponMapper).update(any(), any());
        }

        @Test
        @DisplayName("限量券已领完（receivedCount>=totalCount）→ 返回false，不调用update")
        void incrReceivedCount_soldOut_returnsFalse() {
            // 场景：100张券已领100张，没有余量了
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            coupon.setTotalCount(100);
            coupon.setReceivedCount(100);
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);

            boolean result = couponService.incrReceivedCount(COUPON_ID);

            assertThat(result).isFalse();
            verify(couponMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("限量券有余量 → update成功，返回true")
        void incrReceivedCount_hasStock_returnsTrue() {
            // 场景：100张券只领了50张，还有余量，乐观锁update返回1（更新了1行）
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            coupon.setTotalCount(100);
            coupon.setReceivedCount(50);
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);
            when(couponMapper.update(any(), any())).thenReturn(1);

            boolean result = couponService.incrReceivedCount(COUPON_ID);

            assertThat(result).isTrue();
            verify(couponMapper).update(any(), any());
        }

        @Test
        @DisplayName("限量券有余量但乐观锁竞争失败（update返回0）→ 返回false")
        void incrReceivedCount_optimisticLockFail_returnsFalse() {
            // 场景：还有余量，但并发领取时被别人抢先一步（where received_count<total_count不成立），update返回0
            Coupon coupon = buildCoupon(COUPON_ID, MERCHANT_ID, CouponStatusEnum.ACTIVE.getCode());
            coupon.setTotalCount(100);
            coupon.setReceivedCount(50);
            when(couponMapper.selectById(COUPON_ID)).thenReturn(coupon);
            when(couponMapper.update(any(), any())).thenReturn(0);

            boolean result = couponService.incrReceivedCount(COUPON_ID);

            assertThat(result).isFalse();
        }
    }

    // ==================== 9. incrUsedCount 增加已使用数量 ====================

    @Nested
    @DisplayName("incrUsedCount 增加已使用数量")
    class IncrUsedCountTest {

        @Test
        @DisplayName("调用update → 已使用数量+1")
        void incrUsedCount_callsUpdate() {
            // 场景：用户核销优惠券，已使用数量+1
            couponService.incrUsedCount(COUPON_ID);

            // 验证：调用了update方法，SQL为 used_count = used_count + 1
            verify(couponMapper).update(any(), any());
        }
    }

    // ==================== 10. decrUsedCount 减少已使用数量 ====================

    @Nested
    @DisplayName("decrUsedCount 减少已使用数量")
    class DecrUsedCountTest {

        @Test
        @DisplayName("调用update → 已使用数量-1")
        void decrUsedCount_callsUpdate() {
            // 场景：订单取消回退，已使用数量-1
            couponService.decrUsedCount(COUPON_ID);

            verify(couponMapper).update(any(), any());
        }
    }
}
