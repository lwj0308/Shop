package com.shop.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.feign.NotificationFeignClient;
import com.shop.merchant.mapper.MerchantMapper;
import com.shop.merchant.mapper.MerchantSettlementMapper;
import com.shop.merchant.mapper.SettlementRecordMapper;
import com.shop.merchant.mapper.WithdrawOrderMapper;
import com.shop.model.merchant.dto.MerchantSettlementDTO;
import com.shop.model.merchant.dto.WithdrawApplyDTO;
import com.shop.model.merchant.dto.WithdrawAuditDTO;
import com.shop.model.merchant.entity.Merchant;
import com.shop.model.merchant.entity.MerchantSettlement;
import com.shop.model.merchant.entity.SettlementRecord;
import com.shop.model.merchant.entity.WithdrawOrder;
import com.shop.model.merchant.vo.MerchantSettlementVO;
import com.shop.model.merchant.vo.SettlementRecordVO;
import com.shop.model.merchant.vo.WithdrawOrderVO;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家结算服务实现类（MerchantSettlementServiceImpl）的单元测试
 * <p>
 * 这个测试类用来验证商家的结算账户管理、提现申请、订单结算、提现审核等功能。
 * 结算服务涉及真金白银，所以测试要特别仔细，重点关注：
 * 1. 资金安全：提现金额不能超过余额，乐观锁失败必须抛异常（防止钱算错）
 * 2. 幂等设计：同一订单不能重复结算，有待审核提现时不能再申请（防止重复扣钱）
 * 3. 佣金计算：平台抽成 5% 要算对（比如100元订单，平台抽5元，商家得95元）
 * 4. 状态机：提现审核状态转换要正确（待审核→通过/拒绝）
 * 5. 容错：通知发送失败不影响审核主流程（审核已经完成，通知只是告知）
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架，提供 @Test、@DisplayName 等注解
 * - Mockito：用来"假装"依赖的对象（Mock），让它们返回我们指定的值
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isEqualTo(1)
 * </p>
 * <p>
 * 测试覆盖的9个方法：addSettlement、updateSettlement、getSettlement、getSettlementRecords、
 * applyWithdraw、getWithdrawList、settleOrder、adminGetWithdrawList、auditWithdraw
 * </p>
 */
@DisplayName("商家结算服务 MerchantSettlementServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class MerchantSettlementServiceImplTest {

    /** 假装结算账户 Mapper，不真的连数据库 */
    @Mock
    private MerchantSettlementMapper settlementMapper;

    /** 假装结算流水 Mapper，不真的连数据库 */
    @Mock
    private SettlementRecordMapper settlementRecordMapper;

    /** 假装提现申请 Mapper，不真的连数据库 */
    @Mock
    private WithdrawOrderMapper withdrawOrderMapper;

    /** 假装商家信息 Mapper，用于管理端查提现列表时关联商家名称 */
    @Mock
    private MerchantMapper merchantMapper;

    /** 假装通知服务 Feign 客户端，不真的调用户服务 */
    @Mock
    private NotificationFeignClient notificationFeignClient;

    /** 被测试的结算服务，Mockito 会自动把上面几个 Mock 注入进来 */
    @InjectMocks
    private MerchantSettlementServiceImpl settlementService;

    // 常用的测试数据，用常量定义方便复用
    private static final Long MERCHANT_ID = 1001L;          // 商家ID
    private static final Long SETTLEMENT_ID = 1L;            // 结算账户ID
    private static final Long WITHDRAW_ID = 10L;             // 提现申请ID
    private static final String BANK_NAME = "中国工商银行";   // 银行名称
    private static final String BANK_ACCOUNT = "6222021234567890"; // 银行账号（16位）
    private static final String ACCOUNT_NAME = "张三";        // 账户名
    private static final String ORDER_NO = "ORD202607160001"; // 订单号

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：MerchantSettlementServiceImpl 里用到了 new LambdaQueryWrapper<MerchantSettlement>().eq(MerchantSettlement::getMerchantId, ...)，
     * 这行代码会让 MyBatis-Plus 去查"merchantId 字段对应数据库哪一列"。
     * 正常启动 Spring 时框架会自动做这件事，但单元测试没有 Spring 环境，
     * 所以需要我们手动告诉 MyBatis-Plus：这些实体类有哪些字段。
     * 不初始化的话会报 "can not find lambda cache for this entity" 错误。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MerchantSettlement.class
        );
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SettlementRecord.class
        );
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                WithdrawOrder.class
        );
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Merchant.class
        );
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个结算账户实体
     * 小白理解：数据库 merchant_settlement 表里的一条记录
     *
     * @param id            结算账户ID
     * @param merchantId    商家ID
     * @param balance       可用余额（元）
     * @param frozenAmount  冻结金额（元）
     * @return 构造好的MerchantSettlement
     */
    private MerchantSettlement buildSettlement(Long id, Long merchantId, BigDecimal balance, BigDecimal frozenAmount) {
        MerchantSettlement settlement = new MerchantSettlement();
        settlement.setId(id);
        settlement.setMerchantId(merchantId);
        settlement.setBankName(BANK_NAME);
        settlement.setBankAccount(BANK_ACCOUNT);
        settlement.setAccountName(ACCOUNT_NAME);
        settlement.setBalance(balance);
        settlement.setFrozenAmount(frozenAmount);
        return settlement;
    }

    /**
     * 构造一个结算账户DTO
     * 小白理解：商家配置银行账户时前端传过来的参数
     *
     * @return 构造好的MerchantSettlementDTO
     */
    private MerchantSettlementDTO buildSettlementDTO() {
        MerchantSettlementDTO dto = new MerchantSettlementDTO();
        dto.setBankName(BANK_NAME);
        dto.setBankAccount(BANK_ACCOUNT);
        dto.setAccountName(ACCOUNT_NAME);
        return dto;
    }

    /**
     * 构造一个提现申请DTO
     * 小白理解：商家点"申请提现"时前端传过来的参数，只需要填金额
     *
     * @param amount 提现金额（元）
     * @return 构造好的WithdrawApplyDTO
     */
    private WithdrawApplyDTO buildWithdrawApplyDTO(BigDecimal amount) {
        WithdrawApplyDTO dto = new WithdrawApplyDTO();
        dto.setAmount(amount);
        return dto;
    }

    /**
     * 构造一个提现审核DTO
     * 小白理解：管理员审核提现时前端传过来的参数
     *
     * @param id          提现申请ID
     * @param status      审核结果：1通过 2拒绝
     * @param auditRemark 审核备注
     * @return 构造好的WithdrawAuditDTO
     */
    private WithdrawAuditDTO buildWithdrawAuditDTO(Long id, Integer status, String auditRemark) {
        WithdrawAuditDTO dto = new WithdrawAuditDTO();
        dto.setId(id);
        dto.setStatus(status);
        dto.setAuditRemark(auditRemark);
        return dto;
    }

    /**
     * 构造一个提现申请实体
     * 小白理解：数据库 withdraw_order 表里的一条记录
     *
     * @param id         提现申请ID
     * @param merchantId 商家ID
     * @param amount     提现金额（元）
     * @param status     状态：0待审核 1已通过 2已拒绝 3已打款
     * @return 构造好的WithdrawOrder
     */
    private WithdrawOrder buildWithdrawOrder(Long id, Long merchantId, BigDecimal amount, Integer status) {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(id);
        order.setMerchantId(merchantId);
        order.setAmount(amount);
        order.setStatus(status);
        order.setBankName(BANK_NAME);
        order.setBankAccount(BANK_ACCOUNT);
        order.setAccountName(ACCOUNT_NAME);
        return order;
    }

    /**
     * 构造一个结算流水实体
     * 小白理解：数据库 settlement_record 表里的一条记录，记录一笔订单的结算信息
     *
     * @param id               结算流水ID
     * @param merchantId       商家ID
     * @param orderNo          订单号
     * @param orderAmount      订单金额（元）
     * @param settlementAmount 商家应得金额（元）
     * @param status           状态：0待结算 1已结算 2已退款
     * @return 构造好的SettlementRecord
     */
    private SettlementRecord buildSettlementRecord(Long id, Long merchantId, String orderNo,
                                                    BigDecimal orderAmount, BigDecimal settlementAmount, Integer status) {
        SettlementRecord record = new SettlementRecord();
        record.setId(id);
        record.setMerchantId(merchantId);
        record.setOrderNo(orderNo);
        record.setOrderAmount(orderAmount);
        record.setSettlementAmount(settlementAmount);
        record.setStatus(status);
        return record;
    }

    // ==================== 1. addSettlement 添加结算账户 ====================

    @Nested
    @DisplayName("addSettlement 添加结算账户")
    class AddSettlementTest {

        @Test
        @DisplayName("商家已有结算账户 → 抛出MERCHANT_SETTLEMENT_EXISTS异常")
        void addSettlement_alreadyExists_throwsException() {
            // 场景：商家已经配置过结算账户了，又来添加，应该被拒绝
            // 小白理解：一个商家只能有一个结算账户，不能重复添加
            MerchantSettlementDTO dto = buildSettlementDTO();

            // 模拟：根据merchantId查到了1条记录（说明已经配置过）
            when(settlementMapper.selectCount(any())).thenReturn(1L);

            // 验证：抛出"结算账户已存在"异常，错误码20014
            assertThatThrownBy(() -> settlementService.addSettlement(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.MERCHANT_SETTLEMENT_EXISTS.getCode());

            // 验证：校验失败，没有调用insert
            verify(settlementMapper, never()).insert(any(MerchantSettlement.class));
        }

        @Test
        @DisplayName("正常添加 → 调用insert，新账户余额为0")
        void addSettlement_normal_success() {
            // 场景：商家还没配置过结算账户，正常添加
            // 小白理解：新账户刚创建时，可用余额和冻结金额都应该是0
            MerchantSettlementDTO dto = buildSettlementDTO();

            // 模拟：根据merchantId查到0条记录（说明没配置过）
            when(settlementMapper.selectCount(any())).thenReturn(0L);

            // 执行添加
            settlementService.addSettlement(MERCHANT_ID, dto);

            // 捕获传给insert的settlement对象，验证字段正确
            ArgumentCaptor<MerchantSettlement> captor = ArgumentCaptor.forClass(MerchantSettlement.class);
            verify(settlementMapper).insert(captor.capture());
            MerchantSettlement captured = captor.getValue();

            // 验证：银行信息正确
            assertThat(captured.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(captured.getBankName()).isEqualTo(BANK_NAME);
            assertThat(captured.getBankAccount()).isEqualTo(BANK_ACCOUNT);
            assertThat(captured.getAccountName()).isEqualTo(ACCOUNT_NAME);
            // 验证：新账户余额为0（涉及资金安全，必须验证）
            assertThat(captured.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(captured.getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== 2. updateSettlement 更新结算账户 ====================

    @Nested
    @DisplayName("updateSettlement 更新结算账户")
    class UpdateSettlementTest {

        @Test
        @DisplayName("结算账户不存在 → 自动创建新账户，余额为0")
        void updateSettlement_notExists_autoCreate() {
            // 场景：商家还没配置结算账户，调用更新接口时会自动创建
            // 小白理解：更新接口很贴心，没有账户就帮你创建一个，余额从0开始
            MerchantSettlementDTO dto = buildSettlementDTO();

            // 模拟：根据merchantId查不到结算账户
            when(settlementMapper.selectOne(any())).thenReturn(null);

            // 执行更新（实际会创建）
            settlementService.updateSettlement(MERCHANT_ID, dto);

            // 验证：调用了insert（自动创建），没有调用updateById
            ArgumentCaptor<MerchantSettlement> captor = ArgumentCaptor.forClass(MerchantSettlement.class);
            verify(settlementMapper).insert(captor.capture());
            verify(settlementMapper, never()).updateById(any(MerchantSettlement.class));

            // 验证：新账户余额为0
            assertThat(captor.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(captor.getValue().getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("结算账户已存在 → 调用updateById更新银行卡信息，不改余额")
        void updateSettlement_exists_update() {
            // 场景：商家已有结算账户，只更新银行卡信息，余额不能被改
            // 小白理解：更新接口只改银行卡信息，不能趁机改余额（资金安全）
            MerchantSettlementDTO dto = buildSettlementDTO();
            // 已有账户，余额100元，冻结20元
            MerchantSettlement existSettlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), new BigDecimal("20.00"));

            when(settlementMapper.selectOne(any())).thenReturn(existSettlement);

            // 执行更新
            settlementService.updateSettlement(MERCHANT_ID, dto);

            // 验证：调用了updateById，没有调用insert
            ArgumentCaptor<MerchantSettlement> captor = ArgumentCaptor.forClass(MerchantSettlement.class);
            verify(settlementMapper).updateById(captor.capture());
            verify(settlementMapper, never()).insert(any(MerchantSettlement.class));

            // 验证：银行卡信息已更新
            MerchantSettlement captured = captor.getValue();
            assertThat(captured.getBankName()).isEqualTo(BANK_NAME);
            assertThat(captured.getBankAccount()).isEqualTo(BANK_ACCOUNT);
            // 验证：余额没被改动（资金安全：不能通过更新接口改余额）
            assertThat(captured.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(captured.getFrozenAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        }
    }

    // ==================== 3. getSettlement 查询结算账户 ====================

    @Nested
    @DisplayName("getSettlement 查询结算账户")
    class GetSettlementTest {

        @Test
        @DisplayName("商家未配置结算账户 → 返回null")
        void getSettlement_notExists_returnsNull() {
            // 场景：商家还没配置结算账户，查询返回null
            when(settlementMapper.selectOne(any())).thenReturn(null);

            MerchantSettlementVO vo = settlementService.getSettlement(MERCHANT_ID);

            // 验证：返回null
            assertThat(vo).isNull();
        }

        @Test
        @DisplayName("正常查询 → 返回VO，银行账号已脱敏")
        void getSettlement_normal_returnsVO_withDesensitize() {
            // 场景：商家已配置结算账户，查询返回信息，银行账号要脱敏
            // 小白理解：银行账号是敏感信息，不能完整返回给前端，只显示后4位
            // 比如 6222021234567890 → ************7890
            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("500.00"), new BigDecimal("50.00"));

            when(settlementMapper.selectOne(any())).thenReturn(settlement);

            MerchantSettlementVO vo = settlementService.getSettlement(MERCHANT_ID);

            // 验证：返回了正确的信息
            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(SETTLEMENT_ID);
            assertThat(vo.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(vo.getBankName()).isEqualTo(BANK_NAME);
            // 验证：银行账号已脱敏（只显示后4位）
            assertThat(vo.getBankAccount()).isEqualTo("************7890");
            // 验证：余额和冻结金额正确
            assertThat(vo.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(vo.getFrozenAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        }
    }

    // ==================== 4. getSettlementRecords 查询结算流水 ====================

    @Nested
    @DisplayName("getSettlementRecords 查询结算流水列表")
    class GetSettlementRecordsTest {

        @Test
        @DisplayName("正常分页查询 → 返回流水列表，状态描述正确")
        void getSettlementRecords_normal_paging() {
            // 场景：查询商家的结算流水，分页返回
            // 小白理解：商家在结算流水页面可以看到每笔订单结算了多少钱
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            // 构造2条结算流水
            SettlementRecord record1 = buildSettlementRecord(1L, MERCHANT_ID, ORDER_NO,
                    new BigDecimal("100.00"), new BigDecimal("95.00"), 1);
            SettlementRecord record2 = buildSettlementRecord(2L, MERCHANT_ID, "ORD202607160002",
                    new BigDecimal("200.00"), new BigDecimal("190.00"), 1);

            // 构造分页结果
            Page<SettlementRecord> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(record1, record2));
            page.setTotal(2);

            when(settlementRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            // 执行查询
            PageResult<SettlementRecordVO> result = settlementService.getSettlementRecords(MERCHANT_ID, null, pageRequest);

            // 验证：返回了2条记录
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            // 验证：第一条记录的金额和状态描述正确
            SettlementRecordVO firstVO = result.getRecords().get(0);
            assertThat(firstVO.getOrderAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(firstVO.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("95.00"));
            // 验证：状态描述为"已结算"
            assertThat(firstVO.getStatusDesc()).isEqualTo("已结算");
        }

        @Test
        @DisplayName("带状态筛选 → 调用selectPage时传入状态条件")
        void getSettlementRecords_withStatusFilter() {
            // 场景：只查询"已结算"(status=1)的流水
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            // 构造空分页结果（没有符合条件的数据）
            Page<SettlementRecord> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(settlementRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            // 执行查询（status=1表示已结算）
            PageResult<SettlementRecordVO> result = settlementService.getSettlementRecords(MERCHANT_ID, 1, pageRequest);

            // 验证：返回空列表
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
            // 验证：调用了selectPage
            verify(settlementRecordMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ==================== 5. applyWithdraw 申请提现 ====================

    @Nested
    @DisplayName("applyWithdraw 申请提现")
    class ApplyWithdrawTest {

        @Test
        @DisplayName("有待审核的提现申请 → 抛出异常（幂等校验）")
        void applyWithdraw_hasPending_throwsException() {
            // 场景：商家已经有一笔待审核的提现申请，不能再次申请
            // 小白理解：防止商家快速连点"申请提现"按钮，导致余额被多次冻结
            // 这是资金安全的重要设计：同一时间只能有一笔待审核的提现
            WithdrawApplyDTO dto = buildWithdrawApplyDTO(new BigDecimal("50.00"));

            // 模拟：查到1条待审核的提现申请
            when(withdrawOrderMapper.selectCount(any())).thenReturn(1L);

            // 验证：抛出异常
            assertThatThrownBy(() -> settlementService.applyWithdraw(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            // 验证：没有查询结算账户，没有更新余额
            verify(settlementMapper, never()).selectOne(any());
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("结算账户不存在 → 抛出PARAM_ERROR异常")
        void applyWithdraw_settlementNotExists_throwsException() {
            // 场景：商家还没配置结算账户就想提现
            WithdrawApplyDTO dto = buildWithdrawApplyDTO(new BigDecimal("50.00"));

            // 模拟：没有待审核申请，但也没有结算账户
            when(withdrawOrderMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出"参数错误"异常
            assertThatThrownBy(() -> settlementService.applyWithdraw(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：没有更新余额
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("提现金额超过余额 → 抛出PARAM_ERROR异常（资金安全）")
        void applyWithdraw_balanceNotEnough_throwsException() {
            // 场景：商家余额只有100元，却想提现200元
            // 小白理解：这是资金安全的关键校验，不能让商家提现超过余额的钱
            WithdrawApplyDTO dto = buildWithdrawApplyDTO(new BigDecimal("200.00"));

            // 结算账户余额只有100元
            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), BigDecimal.ZERO);

            when(withdrawOrderMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);

            // 验证：抛出"参数错误"异常（余额不足）
            assertThatThrownBy(() -> settlementService.applyWithdraw(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：没有更新余额，没有创建提现记录
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
            verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        }

        @Test
        @DisplayName("乐观锁更新失败（update返回0） → 抛出OPERATION_FAIL异常（资金安全）")
        void applyWithdraw_optimisticLockFail_throwsException() {
            // 场景：提现时余额被并发修改了，乐观锁更新失败
            // 小白理解：乐观锁就是"我查的时候余额是100，更新时如果余额还是100才更新成功"
            // 如果别人刚好改了余额，update返回0，必须抛异常，防止钱算错
            WithdrawApplyDTO dto = buildWithdrawApplyDTO(new BigDecimal("50.00"));

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), BigDecimal.ZERO);

            when(withdrawOrderMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新失败（返回0）
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

            // 验证：抛出"操作失败"异常
            assertThatThrownBy(() -> settlementService.applyWithdraw(MERCHANT_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            // 验证：没有创建提现记录（更新失败，不能创建）
            verify(withdrawOrderMapper, never()).insert(any(WithdrawOrder.class));
        }

        @Test
        @DisplayName("正常提现 → 余额减少，冻结增加，创建提现记录")
        void applyWithdraw_normal_success() {
            // 场景：商家余额100元，提现30元
            // 小白理解：提现时钱从"可用余额"转到"冻结金额"
            // 提现前：balance=100, frozen=0
            // 提现后：balance=70, frozen=30（等审核通过才真正打款）
            WithdrawApplyDTO dto = buildWithdrawApplyDTO(new BigDecimal("30.00"));

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), BigDecimal.ZERO);

            when(withdrawOrderMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新成功（返回1）
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            // 执行提现
            settlementService.applyWithdraw(MERCHANT_ID, dto);

            // 验证：更新了余额（balance减少，frozen增加）
            ArgumentCaptor<LambdaUpdateWrapper<MerchantSettlement>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
            verify(settlementMapper).update(any(), wrapperCaptor.capture());

            // 验证：创建了提现申请记录
            ArgumentCaptor<WithdrawOrder> orderCaptor = ArgumentCaptor.forClass(WithdrawOrder.class);
            verify(withdrawOrderMapper).insert(orderCaptor.capture());
            WithdrawOrder capturedOrder = orderCaptor.getValue();

            // 验证：提现记录的金额和状态正确
            assertThat(capturedOrder.getMerchantId()).isEqualTo(MERCHANT_ID);
            assertThat(capturedOrder.getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            // 验证：状态为待审核(0)
            assertThat(capturedOrder.getStatus()).isEqualTo(0);
            // 验证：银行卡信息从结算账户快照过来
            assertThat(capturedOrder.getBankName()).isEqualTo(BANK_NAME);
            assertThat(capturedOrder.getBankAccount()).isEqualTo(BANK_ACCOUNT);
        }
    }

    // ==================== 6. getWithdrawList 查询提现列表 ====================

    @Nested
    @DisplayName("getWithdrawList 查询提现申请列表")
    class GetWithdrawListTest {

        @Test
        @DisplayName("正常分页查询 → 返回提现列表，银行账号已脱敏")
        void getWithdrawList_normal_paging() {
            // 场景：商家查询自己的提现申请列表
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            // 构造2条提现申请
            WithdrawOrder order1 = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("50.00"), 0);  // 待审核
            WithdrawOrder order2 = buildWithdrawOrder(11L, MERCHANT_ID,
                    new BigDecimal("30.00"), 1);  // 已通过

            Page<WithdrawOrder> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(order1, order2));
            page.setTotal(2);

            when(withdrawOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            // 执行查询
            PageResult<WithdrawOrderVO> result = settlementService.getWithdrawList(MERCHANT_ID, null, pageRequest);

            // 验证：返回了2条记录
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            // 验证：第一条记录金额正确，银行账号已脱敏
            WithdrawOrderVO firstVO = result.getRecords().get(0);
            assertThat(firstVO.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(firstVO.getBankAccount()).isEqualTo("************7890");
            // 验证：待审核状态描述正确
            assertThat(firstVO.getStatusDesc()).isEqualTo("待审核");
            // 验证：第二条记录已通过状态描述正确
            assertThat(result.getRecords().get(1).getStatusDesc()).isEqualTo("已通过");
        }
    }

    // ==================== 7. settleOrder 订单结算 ====================

    @Nested
    @DisplayName("settleOrder 订单结算")
    class SettleOrderTest {

        @Test
        @DisplayName("订单已结算过 → 直接返回，不重复结算（幂等设计）")
        void settleOrder_alreadySettled_idempotent() {
            // 场景：同一订单号已经结算过了，再次调用应该直接返回
            // 小白理解：防止用户重复确认收货导致商家余额被多次增加
            // 这是资金安全的重要设计：同一订单只能结算一次

            // 模拟：订单号已存在1条结算记录
            when(settlementRecordMapper.selectCount(any())).thenReturn(1L);

            // 执行结算
            settlementService.settleOrder(MERCHANT_ID, ORDER_NO, new BigDecimal("100.00"));

            // 验证：没有插入新的结算流水
            verify(settlementRecordMapper, never()).insert(any(SettlementRecord.class));
            // 验证：没有更新商家余额
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
            verify(settlementMapper, never()).insert(any(MerchantSettlement.class));
        }

        @Test
        @DisplayName("商家无结算账户 → 自动创建账户，余额为结算金额")
        void settleOrder_noSettlement_autoCreate() {
            // 场景：订单结算时商家还没配置结算账户，系统自动创建一个
            // 小白理解：商家没配银行卡也能结算，钱先记着，等配了银行卡再提现
            // 自动创建的账户银行卡信息是"未配置"

            // 模拟：订单没结算过
            when(settlementRecordMapper.selectCount(any())).thenReturn(0L);
            // 模拟：商家没有结算账户
            when(settlementMapper.selectOne(any())).thenReturn(null);

            // 执行结算（订单100元，佣金5%，商家应得95元）
            settlementService.settleOrder(MERCHANT_ID, ORDER_NO, new BigDecimal("100.00"));

            // 验证：插入了结算流水
            verify(settlementRecordMapper).insert(any(SettlementRecord.class));
            // 验证：自动创建了结算账户
            ArgumentCaptor<MerchantSettlement> captor = ArgumentCaptor.forClass(MerchantSettlement.class);
            verify(settlementMapper).insert(captor.capture());
            MerchantSettlement captured = captor.getValue();

            // 验证：余额为95元（100 - 5%佣金 = 95）
            assertThat(captured.getBalance()).isEqualByComparingTo(new BigDecimal("95.00"));
            assertThat(captured.getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            // 验证：银行卡信息为"未配置"
            assertThat(captured.getBankName()).isEqualTo("未配置");
            // 没有调用update（因为是新建，不是更新）
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("正常结算 → 佣金5%计算正确，商家余额增加")
        void settleOrder_normal_commissionCalc() {
            // 场景：订单100元，平台抽成5%（5元），商家应得95元
            // 小白理解：佣金计算是核心逻辑，必须算对
            // commission_amount = order_amount * 0.05 = 100 * 0.05 = 5.00
            // settlement_amount = order_amount - commission_amount = 100 - 5 = 95.00
            BigDecimal orderAmount = new BigDecimal("100.00");

            // 商家已有结算账户，余额100元
            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), BigDecimal.ZERO);

            when(settlementRecordMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新成功
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            // 执行结算
            settlementService.settleOrder(MERCHANT_ID, ORDER_NO, orderAmount);

            // 验证：插入了结算流水，验证佣金计算正确
            ArgumentCaptor<SettlementRecord> recordCaptor = ArgumentCaptor.forClass(SettlementRecord.class);
            verify(settlementRecordMapper).insert(recordCaptor.capture());
            SettlementRecord capturedRecord = recordCaptor.getValue();

            // 验证：订单金额正确
            assertThat(capturedRecord.getOrderAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            // 验证：佣金比例是5%
            assertThat(capturedRecord.getCommissionRate()).isEqualByComparingTo(new BigDecimal("0.05"));
            // 验证：佣金金额是5元（100 * 5% = 5）
            assertThat(capturedRecord.getCommissionAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
            // 验证：商家应得95元（100 - 5 = 95）
            assertThat(capturedRecord.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("95.00"));
            // 验证：状态为已结算(1)
            assertThat(capturedRecord.getStatus()).isEqualTo(1);

            // 验证：更新了商家余额（100 + 95 = 195）
            verify(settlementMapper).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("乐观锁更新失败 → 抛出OPERATION_FAIL异常（资金安全）")
        void settleOrder_optimisticLockFail_throwsException() {
            // 场景：结算时余额被并发修改了，乐观锁更新失败
            // 小白理解：乐观锁失败说明余额可能已经变了，不能继续，必须抛异常
            BigDecimal orderAmount = new BigDecimal("100.00");

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), BigDecimal.ZERO);

            when(settlementRecordMapper.selectCount(any())).thenReturn(0L);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新失败
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

            // 验证：抛出"操作失败"异常
            assertThatThrownBy(() -> settlementService.settleOrder(MERCHANT_ID, ORDER_NO, orderAmount))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());
        }
    }

    // ==================== 8. adminGetWithdrawList 管理端查询提现列表 ====================

    @Nested
    @DisplayName("adminGetWithdrawList 管理端查询提现列表")
    class AdminGetWithdrawListTest {

        @Test
        @DisplayName("正常查询 → 返回提现列表，批量关联商家名称")
        void adminGetWithdrawList_normal_withMerchantNames() {
            // 场景：管理端查询全平台提现申请，需要关联查出商家名称用于展示
            // 小白理解：管理员看到的提现列表要有商家名称，不然不知道是谁提的现
            // 这里用批量查询避免N+1问题（一次查所有商家，而不是每条记录查一次）
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            // 构造2条不同商家的提现申请
            WithdrawOrder order1 = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("50.00"), 0);
            WithdrawOrder order2 = buildWithdrawOrder(11L, 1002L,
                    new BigDecimal("30.00"), 0);

            Page<WithdrawOrder> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(order1, order2));
            page.setTotal(2);

            // 构造2个商家信息
            Merchant merchant1 = new Merchant();
            merchant1.setId(MERCHANT_ID);
            merchant1.setName("张三的数码店");
            Merchant merchant2 = new Merchant();
            merchant2.setId(1002L);
            merchant2.setName("李四的服装店");

            when(withdrawOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);
            when(merchantMapper.selectBatchIds(any())).thenReturn(Arrays.asList(merchant1, merchant2));

            // 执行查询
            PageResult<WithdrawOrderVO> result = settlementService.adminGetWithdrawList(null, pageRequest);

            // 验证：返回了2条记录
            assertThat(result.getRecords()).hasSize(2);
            // 验证：第一条记录关联了正确的商家名称
            WithdrawOrderVO firstVO = result.getRecords().get(0);
            assertThat(firstVO.getMerchantName()).isEqualTo("张三的数码店");
            // 验证：第二条记录关联了正确的商家名称
            assertThat(result.getRecords().get(1).getMerchantName()).isEqualTo("李四的服装店");
            // 验证：银行账号已脱敏
            assertThat(firstVO.getBankAccount()).isEqualTo("************7890");
        }

        @Test
        @DisplayName("提现列表为空 → 返回空列表，不查询商家名称")
        void adminGetWithdrawList_emptyList() {
            // 场景：没有任何提现申请，返回空列表
            // 小白理解：没有数据时不需要查商家名称，避免无意义的数据库查询
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            Page<WithdrawOrder> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            when(withdrawOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            // 执行查询
            PageResult<WithdrawOrderVO> result = settlementService.adminGetWithdrawList(null, pageRequest);

            // 验证：返回空列表
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
            // 验证：没有查商家名称（空列表不需要查）
            verify(merchantMapper, never()).selectBatchIds(any());
        }
    }

    // ==================== 9. auditWithdraw 审核提现 ====================

    @Nested
    @DisplayName("auditWithdraw 审核提现申请")
    class AuditWithdrawTest {

        @Test
        @DisplayName("审核状态值非法（不是1或2） → 抛出PARAM_ERROR异常")
        void auditWithdraw_invalidStatus_throwsException() {
            // 场景：传了一个非法的审核状态值99（只能是1通过或2拒绝）
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 99, "通过");

            // 验证：抛出"参数错误"异常
            assertThatThrownBy(() -> settlementService.auditWithdraw(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：没有查提现申请，没有更新任何数据
            verify(withdrawOrderMapper, never()).selectById(any());
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("提现申请不存在 → 抛出PARAM_ERROR异常")
        void auditWithdraw_orderNotFound_throwsException() {
            // 场景：审核一个不存在的提现申请
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "通过");

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(null);

            // 验证：抛出"参数错误"异常
            assertThatThrownBy(() -> settlementService.auditWithdraw(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.PARAM_ERROR.getCode());

            // 验证：没有更新余额
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("提现申请已审核过 → 抛出OPERATION_FAIL异常（防重复审核）")
        void auditWithdraw_alreadyAudited_throwsException() {
            // 场景：提现申请已经审核过了（状态不是待审核），不能重复审核
            // 小白理解：防止管理员重复审核，导致金额被多次扣减或退回
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "通过");

            // 提现申请状态是1（已通过），不是0（待审核）
            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("50.00"), 1);

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);

            // 验证：抛出"操作失败"异常
            assertThatThrownBy(() -> settlementService.auditWithdraw(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            // 验证：没有更新余额
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("商家结算账户不存在 → 抛出OPERATION_FAIL异常")
        void auditWithdraw_settlementNotFound_throwsException() {
            // 场景：提现申请存在且待审核，但商家的结算账户被删了
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "通过");

            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("50.00"), 0); // 待审核

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);
            when(settlementMapper.selectOne(any())).thenReturn(null);

            // 验证：抛出"操作失败"异常
            assertThatThrownBy(() -> settlementService.auditWithdraw(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            // 验证：没有更新余额
            verify(settlementMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("审核通过 → 冻结金额扣减，更新提现状态为已通过，发送通知")
        void auditWithdraw_approve_normal() {
            // 场景：管理员审核通过提现申请
            // 小白理解：审核通过意味着钱已经打款给商家了，冻结金额要扣减
            // 提现前：balance=100, frozen=50（提现30时从balance转了30到frozen）
            // 审核通过后：frozen=50-30=20（钱已打款，冻结金额扣除）
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "审核通过");

            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("30.00"), 0); // 待审核，提现30元

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), new BigDecimal("50.00")); // 冻结50元

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新成功
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            // 执行审核
            settlementService.auditWithdraw(dto);

            // 验证：更新了冻结金额
            verify(settlementMapper).update(any(), any(LambdaUpdateWrapper.class));
            // 验证：更新了提现申请状态
            ArgumentCaptor<WithdrawOrder> orderCaptor = ArgumentCaptor.forClass(WithdrawOrder.class);
            verify(withdrawOrderMapper).updateById(orderCaptor.capture());
            WithdrawOrder capturedOrder = orderCaptor.getValue();
            // 验证：状态改为已通过(1)
            assertThat(capturedOrder.getStatus()).isEqualTo(1);
            // 验证：审核备注和审核时间已设置
            assertThat(capturedOrder.getAuditRemark()).isEqualTo("审核通过");
            assertThat(capturedOrder.getAuditTime()).isNotNull();
            // 验证：发送了通知
            verify(notificationFeignClient).sendNotification(any());
        }

        @Test
        @DisplayName("审核通过但乐观锁失败 → 抛出OPERATION_FAIL异常，不更新提现状态")
        void auditWithdraw_approve_optimisticLockFail_throwsException() {
            // 场景：审核通过时冻结金额被并发修改了，乐观锁更新失败
            // 小白理解：乐观锁失败说明金额可能变了，不能继续，必须抛异常
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "审核通过");

            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("30.00"), 0);

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), new BigDecimal("50.00"));

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新失败
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

            // 验证：抛出"操作失败"异常
            assertThatThrownBy(() -> settlementService.auditWithdraw(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.OPERATION_FAIL.getCode());

            // 验证：没有更新提现申请状态（审核失败）
            verify(withdrawOrderMapper, never()).updateById(any(WithdrawOrder.class));
            // 验证：没有发送通知
            verify(notificationFeignClient, never()).sendNotification(any());
        }

        @Test
        @DisplayName("审核拒绝 → 冻结金额转回可用余额，更新提现状态为已拒绝")
        void auditWithdraw_reject_normal() {
            // 场景：管理员审核拒绝提现申请
            // 小白理解：审核拒绝意味着钱要退回给商家的可用余额
            // 提现前：balance=100, frozen=50（提现30时从balance转了30到frozen）
            // 审核拒绝后：balance=100+30=130, frozen=50-30=20（钱退回可用余额）
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 2, "资质不符");

            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("30.00"), 0); // 待审核，提现30元

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), new BigDecimal("50.00"));

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            // 模拟：乐观锁更新成功
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            // 执行审核
            settlementService.auditWithdraw(dto);

            // 验证：更新了冻结金额和可用余额
            verify(settlementMapper).update(any(), any(LambdaUpdateWrapper.class));
            // 验证：更新了提现申请状态
            ArgumentCaptor<WithdrawOrder> orderCaptor = ArgumentCaptor.forClass(WithdrawOrder.class);
            verify(withdrawOrderMapper).updateById(orderCaptor.capture());
            WithdrawOrder capturedOrder = orderCaptor.getValue();
            // 验证：状态改为已拒绝(2)
            assertThat(capturedOrder.getStatus()).isEqualTo(2);
            // 验证：审核备注已设置
            assertThat(capturedOrder.getAuditRemark()).isEqualTo("资质不符");
            // 验证：发送了通知
            verify(notificationFeignClient).sendNotification(any());
        }

        @Test
        @DisplayName("通知发送失败 → 不影响审核主流程（容错设计）")
        void auditWithdraw_notificationFail_doesNotAffectMain() {
            // 场景：审核通过后发送通知失败，但审核结果已经保存，不应该回滚
            // 小白理解：通知只是告知商家审核结果，发不发成功都不影响审核本身
            // 这个设计很重要：通知服务挂了不能影响商家的提现审核
            WithdrawAuditDTO dto = buildWithdrawAuditDTO(WITHDRAW_ID, 1, "审核通过");

            WithdrawOrder order = buildWithdrawOrder(WITHDRAW_ID, MERCHANT_ID,
                    new BigDecimal("30.00"), 0);

            MerchantSettlement settlement = buildSettlement(SETTLEMENT_ID, MERCHANT_ID,
                    new BigDecimal("100.00"), new BigDecimal("50.00"));

            when(withdrawOrderMapper.selectById(WITHDRAW_ID)).thenReturn(order);
            when(settlementMapper.selectOne(any())).thenReturn(settlement);
            when(settlementMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
            // 模拟：通知服务调用抛异常
            doThrow(new RuntimeException("通知服务挂了"))
                    .when(notificationFeignClient).sendNotification(any());

            // 执行审核（不应该抛异常，通知失败被catch了）
            settlementService.auditWithdraw(dto);

            // 验证：审核主流程都执行了（更新了冻结金额和提现状态）
            verify(settlementMapper).update(any(), any(LambdaUpdateWrapper.class));
            verify(withdrawOrderMapper).updateById(any(WithdrawOrder.class));
            // 验证：尝试发送了通知（虽然失败了）
            verify(notificationFeignClient).sendNotification(any());
        }
    }
}
