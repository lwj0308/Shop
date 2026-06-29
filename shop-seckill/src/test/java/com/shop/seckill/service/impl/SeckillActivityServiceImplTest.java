package com.shop.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.seckill.dto.SeckillCreateDTO;
import com.shop.model.seckill.dto.SeckillQueryDTO;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.model.seckill.enums.SeckillStatusEnum;
import com.shop.model.seckill.vo.SeckillVO;
import com.shop.seckill.mapper.SeckillActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeckillActivityServiceImpl 秒杀活动服务实现类的单元测试
 * <p>
 * 这个测试类专门验证秒杀活动的"创建/下架/查询"逻辑是否正确。
 * 用 Mockito 把数据库 Mapper 和 Redis 都替换成假的（mock），
 * 这样测试跑起来不需要真数据库和真 Redis，速度快又稳定。
 * </p>
 * <p>
 * 小白理解要点：
 * - @Mock：造一个假的依赖（比如假 Mapper），它的行为由我们用 when().thenReturn() 控制
 * - @InjectMocks：把假的依赖自动塞进被测对象 SeckillActivityServiceImpl
 * - verify()：检查某个方法"有没有被调用过、参数对不对"
 * - assertThat()：断言结果是不是我们期望的，不对就报错让测试失败
 * </p>
 */
@DisplayName("SeckillActivityServiceImpl 秒杀活动服务测试")
@ExtendWith(MockitoExtension.class)
class SeckillActivityServiceImplTest {

    /** 秒杀库存 Redis key 前缀（和被测类里的常量保持一致） */
    private static final String SECKILL_STOCK_KEY_PREFIX = "seckill:stock:";

    /** 假的秒杀活动 Mapper，模拟数据库操作 */
    @Mock
    private SeckillActivityMapper seckillActivityMapper;

    /** 假的 Redis 模板，模拟库存预热和清理 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** 假的 Redis 字符串操作对象，opsForValue() 的返回值 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 被测对象，依赖会自动注入上面两个 mock */
    @InjectMocks
    private SeckillActivityServiceImpl seckillActivityService;

    /**
     * 每个测试方法执行前的准备工作
     * <p>
     * 用 lenient() 设置 opsForValue() 的桩，是因为不是每个测试都会用到 Redis。
     * 如果用严格模式又没用到，Mockito 会报"多余的桩"错误。
     * lenient() 表示"宽松模式"，没用到也不报错。
     * </p>
     */
    @BeforeEach
    void setUp() {
        // 让 stringRedisTemplate.opsForValue() 返回我们的假 valueOperations
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 创建秒杀活动测试 ====================

    @Nested
    @DisplayName("createSeckillActivity 创建秒杀活动")
    class CreateSeckillActivityTest {

        /**
         * 构造一个合法的创建参数，后续测试在此基础上改个别字段来制造异常场景
         * 这样避免每个测试都重复写一大堆字段，减少重复代码
         */
        private SeckillCreateDTO buildValidDto() {
            SeckillCreateDTO dto = new SeckillCreateDTO();
            dto.setProductId(1001L);
            dto.setSkuId(2001L);
            dto.setSeckillPrice(new BigDecimal("9.90"));
            dto.setOriginalPrice(new BigDecimal("19.90"));
            dto.setTotalCount(100);
            dto.setLimitCount(1);
            // 开始时间设为1小时后，结束时间设为2小时后，保证活动处于"待生效"状态
            dto.setStartTime(LocalDateTime.now().plusHours(1));
            dto.setEndTime(LocalDateTime.now().plusHours(2));
            dto.setDescription("限时秒杀测试");
            return dto;
        }

        @Test
        @DisplayName("秒杀价为null时，应抛出参数错误异常")
        void shouldThrowWhenSeckillPriceIsNull() {
            // 准备：秒杀价设为 null
            SeckillCreateDTO dto = buildValidDto();
            dto.setSeckillPrice(null);

            // 执行并验证：应该抛 BusinessException，错误码是 PARAM_ERROR
            assertThatThrownBy(() -> seckillActivityService.createSeckillActivity(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀价格必须大于0")
                    .extracting("code").isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("秒杀价小于等于0时，应抛出参数错误异常")
        void shouldThrowWhenSeckillPriceNotPositive() {
            // 准备：秒杀价设为 0（小于等于0都不合法）
            SeckillCreateDTO dto = buildValidDto();
            dto.setSeckillPrice(BigDecimal.ZERO);

            // 执行并验证
            assertThatThrownBy(() -> seckillActivityService.createSeckillActivity(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀价格必须大于0")
                    .extracting("code").isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("库存总数小于等于0时，应抛出参数错误异常")
        void shouldThrowWhenTotalCountNotPositive() {
            // 准备：库存设为 0
            SeckillCreateDTO dto = buildValidDto();
            dto.setTotalCount(0);

            // 执行并验证
            assertThatThrownBy(() -> seckillActivityService.createSeckillActivity(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀库存总数必须大于0")
                    .extracting("code").isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("限购数量小于等于0时，应抛出参数错误异常")
        void shouldThrowWhenLimitCountNotPositive() {
            // 准备：限购数量设为 0
            SeckillCreateDTO dto = buildValidDto();
            dto.setLimitCount(0);

            // 执行并验证
            assertThatThrownBy(() -> seckillActivityService.createSeckillActivity(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("每人限购数量必须大于0")
                    .extracting("code").isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("开始时间晚于结束时间时，应抛出参数错误异常")
        void shouldThrowWhenStartTimeAfterEndTime() {
            // 准备：开始时间在结束时间之后（时间窗口不合法）
            SeckillCreateDTO dto = buildValidDto();
            dto.setStartTime(LocalDateTime.now().plusHours(2));
            dto.setEndTime(LocalDateTime.now().plusHours(1));

            // 执行并验证
            assertThatThrownBy(() -> seckillActivityService.createSeckillActivity(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("活动开始时间必须早于结束时间")
                    .extracting("code").isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        }

        @Test
        @DisplayName("参数校验失败时，不应调用 Mapper 和 Redis")
        void shouldNotCallMapperOrRedisWhenParamInvalid() {
            // 准备：一个不合法的参数（库存为0）
            SeckillCreateDTO dto = buildValidDto();
            dto.setTotalCount(0);

            // 执行：会抛异常
            try {
                seckillActivityService.createSeckillActivity(1L, dto);
            } catch (BusinessException ignored) {
                // 预期抛异常，忽略
            }

            // 验证：校验失败时既不应该写数据库，也不应该写 Redis
            // 注意：用 any(SeckillActivity.class) 而不是 any()，因为 BaseMapper.insert 有重载方法，
            // 用 any() 编译器无法确定匹配哪个重载，会报"引用不明确"错误
            verify(seckillActivityMapper, org.mockito.Mockito.never()).insert(any(SeckillActivity.class));
            verify(stringRedisTemplate, org.mockito.Mockito.never()).opsForValue();
        }

        @Test
        @DisplayName("正常创建：活动未开始，状态应为待生效(0)，并预热Redis库存，返回活动ID")
        void shouldCreateWithPendingStatusWhenStartTimeInFuture() {
            // 准备：开始时间在未来1小时，结束时间在未来2小时
            SeckillCreateDTO dto = buildValidDto();
            Long merchantId = 1L;

            // 模拟 MyBatis-Plus insert 时自动回填主键ID（真实环境由数据库生成）
            when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenAnswer(invocation -> {
                SeckillActivity saved = invocation.getArgument(0);
                saved.setId(5001L);
                return 1;
            });

            // 执行
            Long activityId = seckillActivityService.createSeckillActivity(merchantId, dto);

            // 验证1：返回的活动ID应该是 insert 时回填的ID
            assertThat(activityId).isEqualTo(5001L);

            // 验证2：捕获传给 insert 的实体，检查各字段是否正确设置
            ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
            verify(seckillActivityMapper).insert(captor.capture());
            SeckillActivity saved = captor.getValue();
            assertThat(saved.getMerchantId()).isEqualTo(merchantId);
            // 可用库存应等于总库存
            assertThat(saved.getAvailableCount()).isEqualTo(dto.getTotalCount());
            // 当前时间早于开始时间，状态应为待生效(0)
            assertThat(saved.getStatus()).isEqualTo(SeckillStatusEnum.PENDING.getCode());

            // 验证3：库存应预热到 Redis，key=seckill:stock:5001，value=总库存数
            verify(stringRedisTemplate.opsForValue()).set(SECKILL_STOCK_KEY_PREFIX + 5001L, "100");
        }

        @Test
        @DisplayName("正常创建：活动已过期，状态应为已结束(2)")
        void shouldCreateWithEndedStatusWhenEndTimeInPast() {
            // 准备：开始和结束时间都在过去，模拟一个"已过期"的活动
            SeckillCreateDTO dto = buildValidDto();
            dto.setStartTime(LocalDateTime.now().minusHours(2));
            dto.setEndTime(LocalDateTime.now().minusHours(1));

            // 模拟 insert 回填ID
            when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenAnswer(invocation -> {
                SeckillActivity saved = invocation.getArgument(0);
                saved.setId(5002L);
                return 1;
            });

            // 执行
            seckillActivityService.createSeckillActivity(1L, dto);

            // 验证：当前时间晚于结束时间，状态应为已结束(2)
            ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
            verify(seckillActivityMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SeckillStatusEnum.ENDED.getCode());

            // 验证 Redis 预热
            verify(stringRedisTemplate.opsForValue()).set(SECKILL_STOCK_KEY_PREFIX + 5002L, "100");
        }

        @Test
        @DisplayName("正常创建：活动进行中，状态应为进行中(1)")
        void shouldCreateWithActiveStatusWhenNowWithinTimeWindow() {
            // 准备：开始时间在过去1分钟，结束时间在未来1小时，模拟一个"正在进行"的活动
            SeckillCreateDTO dto = buildValidDto();
            dto.setStartTime(LocalDateTime.now().minusMinutes(1));
            dto.setEndTime(LocalDateTime.now().plusHours(1));

            // 模拟 insert 回填ID
            when(seckillActivityMapper.insert(any(SeckillActivity.class))).thenAnswer(invocation -> {
                SeckillActivity saved = invocation.getArgument(0);
                saved.setId(5003L);
                return 1;
            });

            // 执行
            seckillActivityService.createSeckillActivity(1L, dto);

            // 验证：当前时间在时间窗口内，状态应为进行中(1)
            ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
            verify(seckillActivityMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SeckillStatusEnum.ACTIVE.getCode());
        }
    }

    // ==================== 下架秒杀活动测试 ====================

    @Nested
    @DisplayName("offlineSeckillActivity 下架秒杀活动")
    class OfflineSeckillActivityTest {

        /** 构造一个进行中的秒杀活动，供下架测试使用 */
        private SeckillActivity buildActiveActivity(Long merchantId) {
            SeckillActivity activity = new SeckillActivity();
            activity.setId(6001L);
            activity.setMerchantId(merchantId);
            activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
            activity.setTotalCount(100);
            activity.setAvailableCount(80);
            return activity;
        }

        @Test
        @DisplayName("活动不存在时，应抛出数据不存在异常，不执行下架")
        void shouldThrowWhenActivityNotExist() {
            // 准备：selectById 返回 null，模拟活动不存在
            when(seckillActivityMapper.selectById(6001L)).thenReturn(null);

            // 执行并验证：应抛 BusinessException，错误码是 DATA_NOT_FOUND
            assertThatThrownBy(() -> seckillActivityService.offlineSeckillActivity(1L, 6001L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动不存在")
                    .extracting("code").isEqualTo(ErrorCode.DATA_NOT_FOUND.getCode());

            // 验证：活动不存在时不应执行更新和清理 Redis
            verify(seckillActivityMapper, org.mockito.Mockito.never()).updateById(any(SeckillActivity.class));
            verify(stringRedisTemplate, org.mockito.Mockito.never()).delete(any(String.class));
        }

        @Test
        @DisplayName("商家下架别人的活动时，应抛出无权限异常")
        void shouldThrowWhenMerchantOfflineOthersActivity() {
            // 准备：活动属于商家2，但商家1想下架它
            when(seckillActivityMapper.selectById(6001L)).thenReturn(buildActiveActivity(2L));

            // 执行并验证：应抛 FORBIDDEN 异常
            assertThatThrownBy(() -> seckillActivityService.offlineSeckillActivity(1L, 6001L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权操作此秒杀活动")
                    .extracting("code").isEqualTo(ErrorCode.FORBIDDEN.getCode());

            // 验证：无权限时不应执行更新和清理 Redis
            verify(seckillActivityMapper, org.mockito.Mockito.never()).updateById(any(SeckillActivity.class));
            verify(stringRedisTemplate, org.mockito.Mockito.never()).delete(any(String.class));
        }

        @Test
        @DisplayName("活动已是下架状态时，应抛出操作失败异常")
        void shouldThrowWhenActivityAlreadyOffline() {
            // 准备：活动状态已经是"已下架"
            SeckillActivity activity = buildActiveActivity(1L);
            activity.setStatus(SeckillStatusEnum.OFFLINE.getCode());
            when(seckillActivityMapper.selectById(6001L)).thenReturn(activity);

            // 执行并验证：重复下架应抛 OPERATION_FAIL 异常
            assertThatThrownBy(() -> seckillActivityService.offlineSeckillActivity(1L, 6001L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动已是下架状态")
                    .extracting("code").isEqualTo(ErrorCode.OPERATION_FAIL.getCode());

            // 验证：已是下架状态时不应执行更新
            verify(seckillActivityMapper, org.mockito.Mockito.never()).updateById(any(SeckillActivity.class));
        }

        @Test
        @DisplayName("平台管理员(merchantId=0)下架任意活动：状态变为已下架(3)，清理Redis库存")
        void shouldOfflineByPlatformAdmin() {
            // 准备：merchantId=0 表示平台管理员，可以下架任意商家的活动
            when(seckillActivityMapper.selectById(6001L)).thenReturn(buildActiveActivity(2L));

            // 执行：平台管理员下架商家2的活动
            seckillActivityService.offlineSeckillActivity(0L, 6001L);

            // 验证1：updateById 被调用，且实体状态变为已下架(3)
            ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
            verify(seckillActivityMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SeckillStatusEnum.OFFLINE.getCode());

            // 验证2：Redis 库存缓存应被删除，key=seckill:stock:6001
            verify(stringRedisTemplate).delete(SECKILL_STOCK_KEY_PREFIX + 6001L);
        }

        @Test
        @DisplayName("商家下架自己的活动：状态变为已下架(3)，清理Redis库存")
        void shouldOfflineByOwnerMerchant() {
            // 准备：商家1下架自己的活动
            when(seckillActivityMapper.selectById(6001L)).thenReturn(buildActiveActivity(1L));

            // 执行
            seckillActivityService.offlineSeckillActivity(1L, 6001L);

            // 验证1：状态变为已下架(3)
            ArgumentCaptor<SeckillActivity> captor = ArgumentCaptor.forClass(SeckillActivity.class);
            verify(seckillActivityMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(SeckillStatusEnum.OFFLINE.getCode());

            // 验证2：Redis 库存缓存被删除
            verify(stringRedisTemplate).delete(SECKILL_STOCK_KEY_PREFIX + 6001L);
        }
    }

    // ==================== 查询秒杀活动列表测试 ====================

    @Nested
    @DisplayName("getSeckillList 查询秒杀活动列表")
    class GetSeckillListTest {

        /** 构造一个秒杀活动实体用于列表返回 */
        private SeckillActivity buildActivity(Long id, Long merchantId, Integer status, int total, int available) {
            SeckillActivity activity = new SeckillActivity();
            activity.setId(id);
            activity.setMerchantId(merchantId);
            activity.setProductId(1001L);
            activity.setSkuId(2001L);
            activity.setSeckillPrice(new BigDecimal("9.90"));
            activity.setOriginalPrice(new BigDecimal("19.90"));
            activity.setTotalCount(total);
            activity.setAvailableCount(available);
            activity.setLimitCount(1);
            activity.setStatus(status);
            activity.setStartTime(LocalDateTime.now().plusHours(1));
            activity.setEndTime(LocalDateTime.now().plusHours(2));
            activity.setDescription("测试活动" + id);
            activity.setCreateTime(LocalDateTime.now());
            return activity;
        }

        @Test
        @DisplayName("商家查询自己的活动列表：应返回该商家的活动，并正确填充分页信息")
        void shouldReturnMerchantActivitiesWithPagination() {
            // 准备：商家1查询自己的活动列表
            Long merchantId = 1L;
            SeckillQueryDTO query = new SeckillQueryDTO();
            query.setPageNum(1);
            query.setPageSize(10);

            // 构造两条该商家的活动数据
            SeckillActivity a1 = buildActivity(7001L, merchantId, SeckillStatusEnum.ACTIVE.getCode(), 100, 75);
            SeckillActivity a2 = buildActivity(7002L, merchantId, SeckillStatusEnum.PENDING.getCode(), 50, 50);

            // 模拟 selectPage 返回一个填充好数据的 Page 对象
            Page<SeckillActivity> pageResult = new Page<>(1, 10);
            pageResult.setRecords(Arrays.asList(a1, a2));
            pageResult.setTotal(2);
            pageResult.setPages(1);
            when(seckillActivityMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

            // 执行
            PageResult<SeckillVO> result = seckillActivityService.getSeckillList(merchantId, query);

            // 验证1：分页信息正确
            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(result.getPageNum()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(10);
            assertThat(result.getPages()).isEqualTo(1);

            // 验证2：返回2条记录
            assertThat(result.getRecords()).hasSize(2);

            // 验证3：第一条是进行中状态，应正确填充 statusDesc 和 progress
            SeckillVO first = result.getRecords().get(0);
            assertThat(first.getId()).isEqualTo(7001L);
            assertThat(first.getStatus()).isEqualTo(SeckillStatusEnum.ACTIVE.getCode());
            assertThat(first.getStatusDesc()).isEqualTo("进行中");
            // 进度 = (100-75)*100/100 = 25
            assertThat(first.getProgress()).isEqualTo(25);
        }

        @Test
        @DisplayName("管理端查询所有活动(merchantId=null)：应返回所有活动，不做商家过滤")
        void shouldReturnAllActivitiesWhenMerchantIdIsNull() {
            // 准备：merchantId=null 表示管理端查询所有活动
            SeckillQueryDTO query = new SeckillQueryDTO();
            query.setPageNum(1);
            query.setPageSize(10);

            // 构造两个不同商家的活动
            SeckillActivity a1 = buildActivity(7001L, 1L, SeckillStatusEnum.ACTIVE.getCode(), 100, 100);
            SeckillActivity a2 = buildActivity(7002L, 2L, SeckillStatusEnum.ENDED.getCode(), 200, 50);

            Page<SeckillActivity> pageResult = new Page<>(1, 10);
            pageResult.setRecords(Arrays.asList(a1, a2));
            pageResult.setTotal(2);
            pageResult.setPages(1);
            when(seckillActivityMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(pageResult);

            // 执行
            PageResult<SeckillVO> result = seckillActivityService.getSeckillList(null, query);

            // 验证：管理端应返回所有活动
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);

            // 验证：第二条是已结束状态，progress = (200-50)*100/200 = 75
            SeckillVO second = result.getRecords().get(1);
            assertThat(second.getStatusDesc()).isEqualTo("已结束");
            assertThat(second.getProgress()).isEqualTo(75);
        }

        @Test
        @DisplayName("查询结果为空时：应返回空列表，total为0")
        void shouldReturnEmptyWhenNoData() {
            // 准备：模拟查询结果为空
            SeckillQueryDTO query = new SeckillQueryDTO();
            query.setPageNum(1);
            query.setPageSize(10);

            Page<SeckillActivity> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            emptyPage.setPages(0);
            when(seckillActivityMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

            // 执行
            PageResult<SeckillVO> result = seckillActivityService.getSeckillList(1L, query);

            // 验证：返回空列表，total=0
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0);
        }
    }

    // ==================== 查询秒杀活动详情测试 ====================

    @Nested
    @DisplayName("getSeckillDetail 查询秒杀活动详情")
    class GetSeckillDetailTest {

        @Test
        @DisplayName("活动不存在时，应抛出数据不存在异常")
        void shouldThrowWhenDetailNotExist() {
            // 准备：selectById 返回 null
            when(seckillActivityMapper.selectById(8001L)).thenReturn(null);

            // 执行并验证：应抛 DATA_NOT_FOUND 异常
            assertThatThrownBy(() -> seckillActivityService.getSeckillDetail(8001L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动不存在")
                    .extracting("code").isEqualTo(ErrorCode.DATA_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常返回详情：应正确填充状态描述和秒杀进度")
        void shouldReturnDetailWithStatusDescAndProgress() {
            // 准备：构造一个进行中的活动，总库存100，已售75（剩余25）
            SeckillActivity activity = new SeckillActivity();
            activity.setId(8001L);
            activity.setMerchantId(1L);
            activity.setProductId(1001L);
            activity.setSkuId(2001L);
            activity.setSeckillPrice(new BigDecimal("9.90"));
            activity.setOriginalPrice(new BigDecimal("19.90"));
            activity.setTotalCount(100);
            activity.setAvailableCount(25);
            activity.setLimitCount(1);
            activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
            activity.setStartTime(LocalDateTime.now().minusMinutes(1));
            activity.setEndTime(LocalDateTime.now().plusHours(1));
            activity.setDescription("限时秒杀");
            when(seckillActivityMapper.selectById(8001L)).thenReturn(activity);

            // 执行
            SeckillVO vo = seckillActivityService.getSeckillDetail(8001L);

            // 验证1：基本字段正确拷贝
            assertThat(vo.getId()).isEqualTo(8001L);
            assertThat(vo.getSeckillPrice()).isEqualByComparingTo("9.90");
            assertThat(vo.getTotalCount()).isEqualTo(100);
            assertThat(vo.getAvailableCount()).isEqualTo(25);

            // 验证2：状态描述应填充为"进行中"
            assertThat(vo.getStatus()).isEqualTo(SeckillStatusEnum.ACTIVE.getCode());
            assertThat(vo.getStatusDesc()).isEqualTo("进行中");

            // 验证3：进度 = (100-25)*100/100 = 75
            assertThat(vo.getProgress()).isEqualTo(75);
        }
    }

    // ==================== 内部接口：根据ID查询实体测试 ====================

    @Nested
    @DisplayName("getSeckillById 内部接口-根据ID查询秒杀活动实体")
    class GetSeckillByIdTest {

        @Test
        @DisplayName("活动不存在时，应抛出数据不存在异常")
        void shouldThrowWhenGetSeckillByIdNotExist() {
            // 准备：selectById 返回 null
            when(seckillActivityMapper.selectById(9001L)).thenReturn(null);

            // 执行并验证：内部接口查不到也抛 DATA_NOT_FOUND 异常
            // 说明：实际实现是抛异常（不是返回null），供 Feign 调用方处理
            assertThatThrownBy(() -> seckillActivityService.getSeckillById(9001L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("秒杀活动不存在")
                    .extracting("code").isEqualTo(ErrorCode.DATA_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("正常返回：应返回原始实体（含所有字段，供其他微服务使用）")
        void shouldReturnEntityWhenExists() {
            // 准备：构造一个秒杀活动实体
            SeckillActivity activity = new SeckillActivity();
            activity.setId(9001L);
            activity.setMerchantId(1L);
            activity.setProductId(1001L);
            activity.setSkuId(2001L);
            activity.setSeckillPrice(new BigDecimal("9.90"));
            activity.setOriginalPrice(new BigDecimal("19.90"));
            activity.setTotalCount(100);
            activity.setAvailableCount(80);
            activity.setLimitCount(2);
            activity.setStatus(SeckillStatusEnum.ACTIVE.getCode());
            activity.setStartTime(LocalDateTime.now().minusMinutes(1));
            activity.setEndTime(LocalDateTime.now().plusHours(1));
            when(seckillActivityMapper.selectById(9001L)).thenReturn(activity);

            // 执行
            SeckillActivity result = seckillActivityService.getSeckillById(9001L);

            // 验证：返回的应该是原始实体，包含所有字段
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(9001L);
            assertThat(result.getMerchantId()).isEqualTo(1L);
            assertThat(result.getSeckillPrice()).isEqualByComparingTo("9.90");
            assertThat(result.getTotalCount()).isEqualTo(100);
            assertThat(result.getAvailableCount()).isEqualTo(80);
            assertThat(result.getLimitCount()).isEqualTo(2);
            assertThat(result.getStatus()).isEqualTo(SeckillStatusEnum.ACTIVE.getCode());
        }
    }
}
