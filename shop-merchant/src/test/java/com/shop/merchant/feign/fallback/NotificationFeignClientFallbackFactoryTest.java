package com.shop.merchant.feign.fallback;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.merchant.feign.NotificationFeignClient;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.vo.NotificationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知服务Feign降级工厂（NotificationFeignClientFallbackFactory）的单元测试
 * <p>
 * 这个测试类用来验证：当通知服务挂掉时，FallbackFactory 生成的"降级版"客户端会怎么响应。
 * 简单理解：商家服务通过Feign调用通知服务，发送审核结果、提现结果等通知给商家，
 * 如果通知服务挂了，不能影响商家主流程（比如不能因为发不出通知就拒绝审核）。
 * </p>
 * <p>
 * 通知服务的降级策略（按方法区分）：
 * - sendNotification 发送通知：返回失败提示（不抛异常，避免影响调用方主流程）
 * - getNotificationList 查询通知列表：返回空分页（前端显示"暂无通知"）
 * - getUnreadCount 查询未读数量：返回0（前端不显示未读小红点）
 * - markAllAsRead 全部标记已读：返回失败提示
 * </p>
 * <p>
 * 测试工具说明（小白快速理解）：
 * - JUnit 5：Java 最流行的测试框架
 * - AssertJ：提供更易读的断言写法，比如 assertThat(x).isTrue()
 * </p>
 * <p>
 * 测试覆盖的4个方法：sendNotification、getNotificationList、getUnreadCount、markAllAsRead
 * </p>
 */
@DisplayName("通知服务降级工厂 NotificationFeignClientFallbackFactory 单元测试")
class NotificationFeignClientFallbackFactoryTest {

    /** 被测试的降级工厂，直接new出来即可（它没有依赖需要Mock） */
    private final NotificationFeignClientFallbackFactory fallbackFactory = new NotificationFeignClientFallbackFactory();

    /**
     * 创建降级实例
     * <p>
     * 小白理解：模拟通知服务挂掉的场景，调用 FallbackFactory.create(失败原因) 拿到一个降级版的客户端。
     * 这个降级实例的每个方法都已经被重写过，会按预定的策略返回降级响应。
     * </p>
     *
     * @return 降级版NotificationFeignClient实例
     */
    private NotificationFeignClient createFallback() {
        // cause参数是失败原因，Feign框架会自动传入真实的异常，这里用RuntimeException模拟
        return fallbackFactory.create(new RuntimeException("通知服务不可用"));
    }

    // ==================== 通知接口降级：列表返回空、未读数返回0、其他返回fail ====================

    @Nested
    @DisplayName("通知接口降级：列表返回空、未读数返回0、其他返回fail")
    class NotificationFallbackTest {

        @Test
        @DisplayName("sendNotification 发送通知降级 → 返回失败提示（不抛异常）")
        void sendNotification_returnsFail() {
            // 场景：通知服务挂了，商家服务想发送"审核通过"通知
            NotificationFeignClient fallback = createFallback();
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setReceiverType(2);       // 2表示商家
            dto.setReceiverId(1L);
            dto.setTitle("审核通过");

            // 调用降级方法
            Result<Void> result = fallback.sendNotification(dto);

            // 验证：返回失败提示，不抛异常（避免影响审核主流程）
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("通知服务暂时不可用");
        }

        @Test
        @DisplayName("getNotificationList 查询通知列表降级 → 返回空分页")
        void getNotificationList_returnsEmptyPage() {
            // 场景：通知服务挂了，商家查看通知列表
            NotificationFeignClient fallback = createFallback();

            // 调用降级方法
            Result<PageResult<NotificationVO>> result = fallback.getNotificationList(2, 1L, null, null, 1, 10);

            // 验证：返回成功，但数据是空分页（前端显示"暂无通知"）
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().getRecords()).isEmpty();
            assertThat(result.getData().getTotal()).isEqualTo(0L);
        }

        @Test
        @DisplayName("getUnreadCount 查询未读数量降级 → 返回0")
        void getUnreadCount_returnsZero() {
            // 场景：通知服务挂了，商家查询未读通知数量
            NotificationFeignClient fallback = createFallback();

            // 调用降级方法
            Result<Long> result = fallback.getUnreadCount(2, 1L);

            // 验证：返回成功，未读数为0（前端不显示未读小红点）
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(0L);
        }

        @Test
        @DisplayName("markAllAsRead 全部标记已读降级 → 返回失败提示")
        void markAllAsRead_returnsFail() {
            // 场景：通知服务挂了，商家点"全部已读"
            NotificationFeignClient fallback = createFallback();

            // 调用降级方法
            Result<Void> result = fallback.markAllAsRead(2, 1L);

            // 验证：返回失败提示
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("通知服务暂时不可用");
        }
    }
}
