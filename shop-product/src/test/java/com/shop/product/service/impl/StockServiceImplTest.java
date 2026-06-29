package com.shop.product.service.impl;

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
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StockServiceImpl 库存服务实现类的单元测试
 * <p>
 * 这个测试类专门验证库存的扣减、回滚、初始化逻辑是否正确。
 * 简单理解：库存服务用 Redis + Lua 脚本来保证不超卖、不重复扣减，
 * 我们这里 mock 掉 Redis（不真的连 Redis），只验证业务逻辑对不对。
 * </p>
 * <p>
 * 核心思路：
 * 1. 用 @Mock 造一个假的 StringRedisTemplate，所有 Redis 调用都是假的
 * 2. 用 @InjectMocks 把假的 Redis 注入到 StockServiceImpl 里
 * 3. 调用方法后，用 verify 检查 Redis 是否按预期被调用，用 assertThat 检查返回值
 * </p>
 */
@DisplayName("StockServiceImpl 库存服务单元测试")
@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    /** mock 的 Redis 模板，所有 Redis 操作都走这个假对象 */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /** mock 的 ValueOperations，给 initStock 调用 opsForValue() 时返回的 */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 被测对象，里面的 stringRedisTemplate 会被上面 mock 的注入 */
    @InjectMocks
    private StockServiceImpl stockService;

    // ==================== deductStock 扣减库存测试 ====================

    @Nested
    @DisplayName("deductStock 扣减库存")
    class DeductStockTest {

        @Test
        @DisplayName("扣减成功：Lua脚本返回1L，方法应返回true")
        void deductStockSuccess() {
            // 准备测试数据：SKU=1001，扣5件，订单号 ORDER_001
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_001";

            // mock Redis 执行 Lua 脚本返回 1L（1 表示扣减成功）
            // 注意：execute 是 varargs，实际传了 2 个参数（quantity, orderNo），
            // 所以 stub 时要写 2 个 any() 来匹配
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            // 执行被测方法
            boolean result = stockService.deductStock(skuId, quantity, orderNo);

            // 验证返回 true（扣减成功）
            assertThat(result).isTrue();
            // 验证 execute 被调用了恰好 1 次
            verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        }

        @Test
        @DisplayName("扣减失败：库存不足，Lua脚本返回0L，方法应返回false")
        void deductStockFailByInsufficientStock() {
            Long skuId = 1001L;
            Integer quantity = 100;
            String orderNo = "ORDER_002";

            // mock execute 返回 0L（0 表示库存不足或已扣减过）
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(0L);

            boolean result = stockService.deductStock(skuId, quantity, orderNo);

            // 库存不足应该返回 false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("扣减失败：execute返回null，方法应返回false（边界场景，避免NPE）")
        void deductStockFailByNullResult() {
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_003";

            // mock execute 返回 null（极端情况：Redis 连接异常等）
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(null);

            boolean result = stockService.deductStock(skuId, quantity, orderNo);

            // 源码用 result != null && result == 1 判断，null 时应该返回 false（不抛 NPE）
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("验证Lua脚本参数：keys应包含stock:sku:{skuId}和stock:deduct:{skuId}，args应包含quantity和orderNo")
        void deductStockScriptArgsAreCorrect() {
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_004";

            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            stockService.deductStock(skuId, quantity, orderNo);

            // 用 ArgumentCaptor 捕获传入的 keys 和 args，看业务代码是不是按规则拼的
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
            verify(stringRedisTemplate).execute(
                    any(RedisScript.class),
                    keysCaptor.capture(),
                    argsCaptor.capture(),
                    argsCaptor.capture()
            );

            // 验证 keys：应该有 2 个，分别是库存 key 和扣减记录 key
            List<String> keys = keysCaptor.getValue();
            assertThat(keys).containsExactly("stock:sku:1001", "stock:deduct:1001");

            // 验证 args：第 1 个是扣减数量（字符串），第 2 个是订单号
            // 注意源码传的是 String.valueOf(quantity)，所以是字符串 "5"
            List<Object> args = argsCaptor.getAllValues();
            assertThat(args).containsExactly("5", "ORDER_004");
        }
    }

    // ==================== addStock 回滚库存测试 ====================

    @Nested
    @DisplayName("addStock 回滚库存")
    class AddStockTest {

        @Test
        @DisplayName("正常回滚：execute被调用一次，方法正常返回（void方法不抛异常即可）")
        void addStockSuccess() {
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_001";

            // mock execute 返回 1L 表示回滚成功
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            // void 方法，只要不抛异常就行
            stockService.addStock(skuId, quantity, orderNo);

            // 验证 execute 被调用了 1 次
            verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        }

        @Test
        @DisplayName("回滚失败（已回滚过或无扣减记录）：execute返回0L，方法应正常返回void不抛异常")
        void addStockFail() {
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_002";

            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(0L);

            // 即使回滚失败，方法也只是打 warn 日志，不抛异常
            stockService.addStock(skuId, quantity, orderNo);

            verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        }

        @Test
        @DisplayName("验证Lua脚本参数：keys应包含stock:sku、stock:deduct、stock:rollback三个key")
        void addStockScriptArgsAreCorrect() {
            Long skuId = 1001L;
            Integer quantity = 5;
            String orderNo = "ORDER_003";

            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                    .thenReturn(1L);

            stockService.addStock(skuId, quantity, orderNo);

            // 捕获 keys 和 args
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
            verify(stringRedisTemplate).execute(
                    any(RedisScript.class),
                    keysCaptor.capture(),
                    argsCaptor.capture(),
                    argsCaptor.capture()
            );

            // 验证 keys：回滚需要 3 个 key（库存、扣减记录、回滚记录）
            List<String> keys = keysCaptor.getValue();
            assertThat(keys).containsExactly(
                    "stock:sku:1001",
                    "stock:deduct:1001",
                    "stock:rollback:1001"
            );

            // 验证 args：回滚数量 + 订单号
            List<Object> args = argsCaptor.getAllValues();
            assertThat(args).containsExactly("5", "ORDER_003");
        }
    }

    // ==================== initStock 初始化库存测试 ====================

    @Nested
    @DisplayName("initStock 初始化库存")
    class InitStockTest {

        @Test
        @DisplayName("正常初始化：setIfAbsent返回true，方法应调用setIfAbsent(\"stock:sku:{skuId}\", stock.toString())")
        void initStockSuccess() {
            Long skuId = 1001L;
            Integer stock = 100;

            // mock opsForValue() 返回我们的 mock valueOperations
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // mock setIfAbsent 返回 true 表示 key 之前不存在，设置成功
            when(valueOperations.setIfAbsent("stock:sku:1001", "100")).thenReturn(true);

            stockService.initStock(skuId, stock);

            // 验证调用了 opsForValue() 拿到 ValueOperations
            verify(stringRedisTemplate).opsForValue();
            // 验证 setIfAbsent 被调用，key 是 stock:sku:1001，value 是 "100"
            verify(valueOperations).setIfAbsent("stock:sku:1001", "100");
        }

        @Test
        @DisplayName("key已存在：setIfAbsent返回false，方法应正常返回void（不覆盖已有库存）")
        void initStockKeyAlreadyExists() {
            Long skuId = 1001L;
            Integer stock = 100;

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // mock setIfAbsent 返回 false 表示 key 已存在，不会覆盖
            when(valueOperations.setIfAbsent("stock:sku:1001", "100")).thenReturn(false);

            // 不抛异常即可（源码只是打 debug 日志）
            stockService.initStock(skuId, stock);

            verify(valueOperations).setIfAbsent("stock:sku:1001", "100");
        }

        @Test
        @DisplayName("setIfAbsent返回null边界：源码用Boolean.TRUE.equals判断，null时不会NPE")
        void initStockSetIfAbsentReturnNull() {
            Long skuId = 1001L;
            Integer stock = 100;

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // setIfAbsent 在某些异常情况下可能返回 null
            when(valueOperations.setIfAbsent("stock:sku:1001", "100")).thenReturn(null);

            // 源码用 Boolean.TRUE.equals(success) 判断，null 时走 else 分支不抛异常
            stockService.initStock(skuId, stock);

            verify(valueOperations).setIfAbsent("stock:sku:1001", "100");
        }

        @Test
        @DisplayName("stock为0：String.valueOf(0)返回\"0\"，方法应正常返回void")
        void initStockWithZeroStock() {
            Long skuId = 1001L;
            Integer stock = 0;

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent("stock:sku:1001", "0")).thenReturn(true);

            // stock=0 是合法值，源码没特殊处理，会正常调用 setIfAbsent
            stockService.initStock(skuId, stock);

            verify(valueOperations).setIfAbsent("stock:sku:1001", "0");
        }

        @Test
        @DisplayName("stock为null：String.valueOf(null)返回字符串\"null\"，方法应正常返回void不抛NPE")
        void initStockWithNullStock() {
            Long skuId = 1001L;
            Integer stock = null;

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // 注意：String.valueOf((Object) null) 返回字符串 "null"，不会抛 NPE
            // （如果直接调 String.valueOf(null) 会调重载的 char[] 版本抛 NPE，但 Integer 是 Object）
            when(valueOperations.setIfAbsent("stock:sku:1001", "null")).thenReturn(true);

            stockService.initStock(skuId, stock);

            verify(valueOperations).setIfAbsent("stock:sku:1001", "null");
        }
    }
}
