package com.shop.marketing.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.marketing.mapper.PromotionMapper;
import com.shop.marketing.mapper.PromotionProductMapper;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.model.promotion.dto.PromotionCreateDTO;
import com.shop.model.promotion.dto.PromotionQueryDTO;
import com.shop.model.promotion.entity.Promotion;
import com.shop.model.promotion.entity.PromotionProduct;
import com.shop.model.promotion.enums.PromotionScopeTypeEnum;
import com.shop.model.promotion.enums.PromotionStatusEnum;
import com.shop.model.promotion.vo.PromotionVO;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 满减活动服务实现类（PromotionServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证满减活动的创建、修改、下架、查询、优惠计算等操作能不能正常工作。
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
 * 测试覆盖的6个方法：createPromotion、updatePromotion、offlinePromotion、
 * getPromotionList、getPromotionDetail、calculatePromotion
 * </p>
 */
@DisplayName("满减活动服务 PromotionServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class PromotionServiceImplTest {

    /** 假装满减活动表操作的 Mapper */
    @Mock
    private PromotionMapper promotionMapper;

    /** 假装满减活动商品关联表操作的 Mapper */
    @Mock
    private PromotionProductMapper promotionProductMapper;

    /** 被测试的满减活动服务，Mockito 会自动把上面的两个 Mock 注入进来 */
    @InjectMocks
    private PromotionServiceImpl promotionService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long MERCHANT_ID = 2001L;            // 当前商家ID
    private static final Long OTHER_MERCHANT_ID = 2002L;      // 另一个商家ID（测试越权用）
    private static final Long PROMOTION_ID = 7001L;           // 满减活动ID
    private static final Long NEW_PROMOTION_ID = 7002L;       // 新创建活动的ID

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：PromotionServiceImpl 的查询方法用到了 .eq(Promotion::getMerchantId, ...)、
     * .eq(PromotionProduct::getPromotionId, ...) 这种写法，
     * 这会让 MyBatis-Plus 去查"字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：Promotion 和 PromotionProduct 这两个实体有哪些字段、对应哪些列。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Promotion.class);
        TableInfoHelper.initTableInfo(assistant, PromotionProduct.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个合法的"满200减20"全店满减活动DTO（开始时间在未来，状态会是待生效）
     * 小白理解：商家创建满减活动时填的表单数据，所有规则都合法
     *
     * @return 构造好的 PromotionCreateDTO
     */
    private PromotionCreateDTO buildValidAllScopeDto() {
        PromotionCreateDTO dto = new PromotionCreateDTO();
        dto.setName("夏季满200减20");
        dto.setThreshold(new BigDecimal("200.00"));
        dto.setDiscountAmount(new BigDecimal("20.00"));
        dto.setScopeType(PromotionScopeTypeEnum.ALL.getCode());
        LocalDateTime now = LocalDateTime.now();
        dto.setStartTime(now.plusDays(1));   // 未来1天开始
        dto.setEndTime(now.plusDays(10));    // 未来10天结束
        dto.setDescription("测试满减活动");
        return dto;
    }

    /**
     * 构造一个满减活动实体
     * 小白理解：数据库 promotion 表里的一条记录
     *
     * @param id         满减活动ID
     * @param merchantId 商家ID
     * @param status     状态码（对应 PromotionStatusEnum）
     * @return 构造好的 Promotion
     */
    private Promotion buildPromotion(Long id, Long merchantId, Integer status) {
        Promotion p = new Promotion();
        p.setId(id);
        p.setMerchantId(merchantId);
        p.setName("夏季满200减20");
        p.setThreshold(new BigDecimal("200.00"));
        p.setDiscountAmount(new BigDecimal("20.00"));
        p.setScopeType(PromotionScopeTypeEnum.ALL.getCode());
        p.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        p.setStartTime(now.plusDays(1));
        p.setEndTime(now.plusDays(10));
        p.setDescription("测试");
        return p;
    }

    /**
     * 构造一个满减优惠计算请求DTO
     * 小白理解：下单时传给营销服务的参数（商家ID + 订单金额）
     *
     * @param orderAmount 订单总金额
     * @return 构造好的 PromotionCalculateDTO
     */
    private PromotionCalculateDTO buildCalcDto(BigDecimal orderAmount) {
        PromotionCalculateDTO dto = new PromotionCalculateDTO();
        dto.setMerchantId(MERCHANT_ID);
        dto.setOrderAmount(orderAmount);
        dto.setSkuIds(Arrays.asList(1L, 2L));
        return dto;
    }

    // ==================== 1. createPromotion 创建满减活动 ====================

    @Nested
    @DisplayName("createPromotion 创建满减活动")
    class CreatePromotionTest {

        @Test
        @DisplayName("满减门槛金额为0 → 抛出参数错误异常")
        void createPromotion_thresholdZero_throwsException() {
            // 场景：门槛金额填了0，必须大于0
            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setThreshold(BigDecimal.ZERO);

            assertThatThrownBy(() -> promotionService.createPromotion(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(promotionMapper, never()).insert(any(Promotion.class));
        }

        @Test
        @DisplayName("优惠金额大于等于门槛 → 抛出参数错误异常")
        void createPromotion_discountGteThreshold_throwsException() {
            // 场景：满200减250，优惠比门槛还大，明显亏本，不允许
            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setThreshold(new BigDecimal("200.00"));
            dto.setDiscountAmount(new BigDecimal("250.00"));

            assertThatThrownBy(() -> promotionService.createPromotion(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(promotionMapper, never()).insert(any(Promotion.class));
        }

        @Test
        @DisplayName("开始时间晚于结束时间 → 抛出参数错误异常")
        void createPromotion_startAfterEnd_throwsException() {
            // 场景：开始时间填在了结束时间之后
            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setStartTime(dto.getEndTime().plusDays(1));

            assertThatThrownBy(() -> promotionService.createPromotion(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(promotionMapper, never()).insert(any(Promotion.class));
        }

        @Test
        @DisplayName("指定商品满减但商品列表为空 → 抛出参数错误异常")
        void createPromotion_specifiedScopeEmptyProducts_throwsException() {
            // 场景：选了"指定商品"范围，但没选任何商品
            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setScopeType(PromotionScopeTypeEnum.SPECIFIED.getCode());
            dto.setProductIds(Collections.emptyList());

            assertThatThrownBy(() -> promotionService.createPromotion(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            verify(promotionMapper, never()).insert(any(Promotion.class));
        }

        @Test
        @DisplayName("正常创建全店满减（开始时间在未来）→ 状态待生效，返回活动ID")
        void createPromotion_normalFutureTime_statusPending() {
            // 场景：开始时间在未来，创建后状态应为"待生效(0)"
            PromotionCreateDTO dto = buildValidAllScopeDto();

            // 模拟 insert 时回填雪花ID
            when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
                invocation.<Promotion>getArgument(0).setId(NEW_PROMOTION_ID);
                return 1;
            });

            Long id = promotionService.createPromotion(MERCHANT_ID, dto);

            // 验证：返回ID正确
            assertThat(id).isEqualTo(NEW_PROMOTION_ID);

            // 捕获传给insert的实体，校验关键字段
            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            verify(promotionMapper).insert(captor.capture());
            Promotion saved = captor.getValue();
            assertThat(saved.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(saved.getStatus()).isEqualTo(PromotionStatusEnum.PENDING.getCode());

            // 全店满减不需要写商品关联表
            verify(promotionProductMapper, never()).insert(any(PromotionProduct.class));
        }

        @Test
        @DisplayName("创建时当前时间在活动窗口内 → 状态进行中")
        void createPromotion_nowInWindow_statusActive() {
            // 场景：开始时间已过、结束时间未到 → 状态"进行中(1)"
            PromotionCreateDTO dto = buildValidAllScopeDto();
            LocalDateTime now = LocalDateTime.now();
            dto.setStartTime(now.minusDays(1));
            dto.setEndTime(now.plusDays(1));

            when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
                invocation.<Promotion>getArgument(0).setId(NEW_PROMOTION_ID);
                return 1;
            });

            promotionService.createPromotion(MERCHANT_ID, dto);

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            verify(promotionMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PromotionStatusEnum.ACTIVE.getCode());
        }

        @Test
        @DisplayName("创建时已过结束时间 → 状态已结束")
        void createPromotion_nowAfterEnd_statusEnded() {
            // 场景：结束时间已过 → 状态"已结束(2)"
            PromotionCreateDTO dto = buildValidAllScopeDto();
            LocalDateTime now = LocalDateTime.now();
            dto.setStartTime(now.minusDays(5));
            dto.setEndTime(now.minusDays(1));

            when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
                invocation.<Promotion>getArgument(0).setId(NEW_PROMOTION_ID);
                return 1;
            });

            promotionService.createPromotion(MERCHANT_ID, dto);

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            verify(promotionMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PromotionStatusEnum.ENDED.getCode());
        }

        @Test
        @DisplayName("指定商品满减 → 写入活动表 + 批量写入商品关联表")
        void createPromotion_specifiedScope_insertProducts() {
            // 场景：指定商品满减，选了2个商品，需要先insert活动，再insert 2条商品关联
            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setScopeType(PromotionScopeTypeEnum.SPECIFIED.getCode());
            dto.setProductIds(Arrays.asList(100L, 200L));

            when(promotionMapper.insert(any(Promotion.class))).thenAnswer(invocation -> {
                invocation.<Promotion>getArgument(0).setId(NEW_PROMOTION_ID);
                return 1;
            });

            Long id = promotionService.createPromotion(MERCHANT_ID, dto);

            assertThat(id).isEqualTo(NEW_PROMOTION_ID);
            // 验证：活动insert 1次，商品关联insert 2次（每个商品一条）
            verify(promotionMapper).insert(any(Promotion.class));
            verify(promotionProductMapper, times(2)).insert(any(PromotionProduct.class));
        }
    }

    // ==================== 2. updatePromotion 修改满减活动 ====================

    @Nested
    @DisplayName("updatePromotion 修改满减活动")
    class UpdatePromotionTest {

        @Test
        @DisplayName("满减活动不存在 → 抛出数据不存在异常")
        void updatePromotion_notFound_throwsException() {
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(null);

            assertThatThrownBy(() -> promotionService.updatePromotion(MERCHANT_ID, PROMOTION_ID, buildValidAllScopeDto()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            verify(promotionProductMapper, never()).delete(any());
            verify(promotionMapper, never()).updateById(any(Promotion.class));
        }

        @Test
        @DisplayName("非待生效状态（进行中）→ 抛出操作失败异常")
        void updatePromotion_notPending_throwsException() {
            // 场景：活动是"进行中"状态，不能修改（只有待生效才能改）
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            assertThatThrownBy(() -> promotionService.updatePromotion(MERCHANT_ID, PROMOTION_ID, buildValidAllScopeDto()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            verify(promotionProductMapper, never()).delete(any());
            verify(promotionMapper, never()).updateById(any(Promotion.class));
        }

        @Test
        @DisplayName("待生效状态正常修改（全店满减）→ 先删旧关联再updateById")
        void updatePromotion_pendingAllScope_deleteAndUpdate() {
            // 场景：待生效状态的全店满减，提交合法新参数，应删除旧关联并updateById
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.PENDING.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            promotionService.updatePromotion(MERCHANT_ID, PROMOTION_ID, buildValidAllScopeDto());

            // 验证：删除了旧关联，全店满减不写新关联，调用了updateById
            verify(promotionProductMapper).delete(any());
            verify(promotionProductMapper, never()).insert(any(PromotionProduct.class));
            verify(promotionMapper).updateById(any(Promotion.class));
        }

        @Test
        @DisplayName("修改为指定商品满减 → 删旧关联 + 写入新关联 + updateById")
        void updatePromotion_changeToSpecifiedScope_reinsertProducts() {
            // 场景：待生效状态，改成指定商品满减（2个商品），应删旧关联、insert 2条新关联、updateById
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.PENDING.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            PromotionCreateDTO dto = buildValidAllScopeDto();
            dto.setScopeType(PromotionScopeTypeEnum.SPECIFIED.getCode());
            dto.setProductIds(Arrays.asList(100L, 200L));

            promotionService.updatePromotion(MERCHANT_ID, PROMOTION_ID, dto);

            verify(promotionProductMapper).delete(any());
            verify(promotionProductMapper, times(2)).insert(any(PromotionProduct.class));
            verify(promotionMapper).updateById(any(Promotion.class));
        }
    }

    // ==================== 3. offlinePromotion 下架满减活动 ====================

    @Nested
    @DisplayName("offlinePromotion 下架满减活动")
    class OfflinePromotionTest {

        @Test
        @DisplayName("满减活动不存在 → 抛出数据不存在异常")
        void offlinePromotion_notFound_throwsException() {
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(null);

            assertThatThrownBy(() -> promotionService.offlinePromotion(MERCHANT_ID, PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());

            verify(promotionMapper, never()).updateById(any(Promotion.class));
        }

        @Test
        @DisplayName("满减活动已是下架状态 → 抛出操作失败异常")
        void offlinePromotion_alreadyOffline_throwsException() {
            // 场景：活动已是下架状态，重复下架应报错
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.OFFLINE.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            assertThatThrownBy(() -> promotionService.offlinePromotion(MERCHANT_ID, PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            verify(promotionMapper, never()).updateById(any(Promotion.class));
        }

        @Test
        @DisplayName("正常下架 → 状态变为已下架，调用updateById")
        void offlinePromotion_normal_statusChangedToOffline() {
            // 场景：进行中的活动下架，状态应变为"已下架(3)"
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            promotionService.offlinePromotion(MERCHANT_ID, PROMOTION_ID);

            verify(promotionMapper).updateById(any(Promotion.class));
            assertThat(promotion.getStatus()).isEqualTo(PromotionStatusEnum.OFFLINE.getCode());
        }
    }

    // ==================== 4. getPromotionList 查询满减活动列表 ====================

    @Nested
    @DisplayName("getPromotionList 查询满减活动列表")
    class GetPromotionListTest {

        @Test
        @DisplayName("分页查询 → 返回VO列表，正确填充状态和范围描述")
        void getPromotionList_pagedQuery_returnsVoList() {
            // 场景：商家查询自己的满减活动列表，返回2条记录
            Promotion p1 = buildPromotion(1L, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            Promotion p2 = buildPromotion(2L, MERCHANT_ID, PromotionStatusEnum.PENDING.getCode());

            Page<Promotion> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(p1, p2));
            page.setTotal(2);
            when(promotionMapper.selectPage(any(), any())).thenReturn(page);

            PromotionQueryDTO query = new PromotionQueryDTO();
            query.setPageNum(1);
            query.setPageSize(10);

            PageResult<PromotionVO> result = promotionService.getPromotionList(MERCHANT_ID, query);

            // 验证：分页信息和记录数正确
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getRecords()).hasSize(2);
            // 验证：VO 填充了状态描述和范围描述
            PromotionVO vo1 = result.getRecords().get(0);
            assertThat(vo1.getStatusDesc()).isEqualTo("进行中");
            assertThat(vo1.getScopeTypeDesc()).isEqualTo("全店");
            // 全店满减不查询关联商品，productIds 应为 null
            assertThat(vo1.getProductIds()).isNull();
        }
    }

    // ==================== 5. getPromotionDetail 查询满减活动详情 ====================

    @Nested
    @DisplayName("getPromotionDetail 查询满减活动详情")
    class GetPromotionDetailTest {

        @Test
        @DisplayName("满减活动不存在 → 抛出数据不存在异常")
        void getPromotionDetail_notFound_throwsException() {
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(null);

            assertThatThrownBy(() -> promotionService.getPromotionDetail(PROMOTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.DATA_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("全店满减详情 → 正常返回，不查询关联商品")
        void getPromotionDetail_allScope_returnsVo() {
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            PromotionVO vo = promotionService.getPromotionDetail(PROMOTION_ID);

            assertThat(vo.getId()).isEqualTo(PROMOTION_ID);
            assertThat(vo.getStatusDesc()).isEqualTo("进行中");
            assertThat(vo.getScopeTypeDesc()).isEqualTo("全店");
            assertThat(vo.getProductIds()).isNull();
            // 全店满减不查关联商品
            verify(promotionProductMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("指定商品满减详情 → 返回VO带关联商品ID列表")
        void getPromotionDetail_specifiedScope_returnsVoWithProductIds() {
            // 场景：指定商品满减，详情应返回关联的商品ID列表（编辑回显用）
            Promotion promotion = buildPromotion(PROMOTION_ID, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            promotion.setScopeType(PromotionScopeTypeEnum.SPECIFIED.getCode());
            when(promotionMapper.selectById(PROMOTION_ID)).thenReturn(promotion);

            // 模拟关联商品：2个商品
            PromotionProduct pp1 = new PromotionProduct();
            pp1.setProductId(100L);
            PromotionProduct pp2 = new PromotionProduct();
            pp2.setProductId(200L);
            when(promotionProductMapper.selectList(any())).thenReturn(Arrays.asList(pp1, pp2));

            PromotionVO vo = promotionService.getPromotionDetail(PROMOTION_ID);

            assertThat(vo.getId()).isEqualTo(PROMOTION_ID);
            assertThat(vo.getScopeTypeDesc()).isEqualTo("指定商品");
            assertThat(vo.getProductIds()).containsExactly(100L, 200L);
        }
    }

    // ==================== 6. calculatePromotion 计算满减优惠金额 ====================

    @Nested
    @DisplayName("calculatePromotion 计算满减优惠金额")
    class CalculatePromotionTest {

        @Test
        @DisplayName("订单金额为null → 返回0，不查询活动")
        void calculatePromotion_nullAmount_returnsZero() {
            // 场景：订单金额没传，直接返回0，不查数据库
            PromotionCalculateDTO dto = buildCalcDto(null);

            BigDecimal discount = promotionService.calculatePromotion(dto);

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
            verify(promotionMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("订单金额为0 → 返回0，不查询活动")
        void calculatePromotion_zeroAmount_returnsZero() {
            // 场景：订单金额为0（比如全是赠品），没有可优惠的金额
            PromotionCalculateDTO dto = buildCalcDto(BigDecimal.ZERO);

            BigDecimal discount = promotionService.calculatePromotion(dto);

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
            verify(promotionMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("无进行中的满减活动 → 返回0")
        void calculatePromotion_noActivePromotion_returnsZero() {
            // 场景：订单金额有效，但商家没有进行中的满减活动
            when(promotionMapper.selectList(any())).thenReturn(Collections.emptyList());

            BigDecimal discount = promotionService.calculatePromotion(buildCalcDto(new BigDecimal("250.00")));

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("订单金额未达满减门槛 → 返回0")
        void calculatePromotion_belowThreshold_returnsZero() {
            // 场景：满200减20，但订单只有100元，达不到门槛，不享受优惠
            Promotion promotion = buildPromotion(1L, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            promotion.setThreshold(new BigDecimal("200.00"));
            promotion.setDiscountAmount(new BigDecimal("20.00"));
            when(promotionMapper.selectList(any())).thenReturn(Collections.singletonList(promotion));

            BigDecimal discount = promotionService.calculatePromotion(buildCalcDto(new BigDecimal("100.00")));

            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("满足满减门槛 → 返回优惠金额（满200减20，订单250 → 减20）")
        void calculatePromotion_meetThreshold_returnsDiscount() {
            // 场景：满200减20，订单250元达到门槛，优惠20元
            Promotion promotion = buildPromotion(1L, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            promotion.setThreshold(new BigDecimal("200.00"));
            promotion.setDiscountAmount(new BigDecimal("20.00"));
            when(promotionMapper.selectList(any())).thenReturn(Collections.singletonList(promotion));

            BigDecimal discount = promotionService.calculatePromotion(buildCalcDto(new BigDecimal("250.00")));

            assertThat(discount).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("多个活动都满足门槛 → 返回最大优惠金额")
        void calculatePromotion_multipleMeetThreshold_returnsMaxDiscount() {
            // 场景：2个活动都满足门槛
            // 活动1：满200减20（订单250满足）→ 优惠20
            // 活动2：满100减10（订单250满足）→ 优惠10
            // 取最大值，应返回20
            Promotion p1 = buildPromotion(1L, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            p1.setThreshold(new BigDecimal("200.00"));
            p1.setDiscountAmount(new BigDecimal("20.00"));
            Promotion p2 = buildPromotion(2L, MERCHANT_ID, PromotionStatusEnum.ACTIVE.getCode());
            p2.setThreshold(new BigDecimal("100.00"));
            p2.setDiscountAmount(new BigDecimal("10.00"));
            when(promotionMapper.selectList(any())).thenReturn(Arrays.asList(p1, p2));

            BigDecimal discount = promotionService.calculatePromotion(buildCalcDto(new BigDecimal("250.00")));

            assertThat(discount).isEqualByComparingTo(new BigDecimal("20.00"));
        }
    }
}
