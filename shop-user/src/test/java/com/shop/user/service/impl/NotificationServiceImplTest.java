package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.model.notification.dto.NotificationQueryDTO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.entity.Notification;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.notification.vo.NotificationVO;
import com.shop.user.mapper.NotificationMapper;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息通知服务 NotificationServiceImpl 单元测试
 * <p>
 * 小白理解：通知服务就是给用户发消息（比如"您的订单已发货"），以及让用户标记已读。
 * 这个测试验证发送通知、查列表、标记已读等逻辑对不对。
 * </p>
 */
@DisplayName("消息通知服务 NotificationServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 100L;
    private static final int RECEIVER_TYPE_USER = ReceiverTypeEnum.USER.getCode();
    private static final int TYPE_ORDER = NotificationTypeEnum.ORDER.getCode();

    /**
     * 初始化 MyBatis-Plus Lambda 缓存
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Notification.class);
    }

    // ==================== 发送通知测试 ====================

    @Nested
    @DisplayName("sendNotification 发送通知")
    class SendNotificationTest {

        @Test
        @DisplayName("接收人类型为空：抛出参数异常")
        void sendNotification_nullReceiverType_throwsException() {
            NotificationSendDTO dto = buildSendDTO();
            dto.setReceiverType(null);

            assertThatThrownBy(() -> notificationService.sendNotification(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("通知参数不完整");

            verify(notificationMapper, never()).insert(any(Notification.class));
        }

        @Test
        @DisplayName("接收人ID为空：抛出参数异常")
        void sendNotification_nullReceiverId_throwsException() {
            NotificationSendDTO dto = buildSendDTO();
            dto.setReceiverId(null);

            assertThatThrownBy(() -> notificationService.sendNotification(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("通知参数不完整");
        }

        @Test
        @DisplayName("无效接收人类型：抛出参数异常")
        void sendNotification_invalidReceiverType_throwsException() {
            NotificationSendDTO dto = buildSendDTO();
            dto.setReceiverType(999); // 不存在的类型码

            assertThatThrownBy(() -> notificationService.sendNotification(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无效的接收人类型");
        }

        @Test
        @DisplayName("无效通知类型：抛出参数异常")
        void sendNotification_invalidType_throwsException() {
            NotificationSendDTO dto = buildSendDTO();
            dto.setType(999); // 不存在的类型码

            assertThatThrownBy(() -> notificationService.sendNotification(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无效的通知类型");
        }

        @Test
        @DisplayName("正常发送：插入通知记录，isRead默认为0")
        void sendNotification_valid_insertsRecord() {
            NotificationSendDTO dto = buildSendDTO();

            notificationService.sendNotification(dto);

            // 验证：插入了一条通知，字段正确，isRead=0
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationMapper).insert(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getReceiverType()).isEqualTo(RECEIVER_TYPE_USER);
            assertThat(saved.getReceiverId()).isEqualTo(USER_ID);
            assertThat(saved.getType()).isEqualTo(TYPE_ORDER);
            assertThat(saved.getTitle()).isEqualTo("订单已发货");
            assertThat(saved.getContent()).isEqualTo("您的订单已发货");
            assertThat(saved.getIsRead()).isEqualTo(0);
        }

        @Test
        @DisplayName("type为null时也能正常发送（通知类型可选）")
        void sendNotification_nullType_insertsRecord() {
            NotificationSendDTO dto = buildSendDTO();
            dto.setType(null);

            notificationService.sendNotification(dto);

            verify(notificationMapper).insert(any(Notification.class));
        }
    }

    // ==================== 通知列表测试 ====================

    @Nested
    @DisplayName("getNotificationList 通知列表分页")
    class GetNotificationListTest {

        @Test
        @DisplayName("正常分页查询：返回VO列表和分页信息")
        void getNotificationList_returnsPagedVOs() {
            NotificationQueryDTO queryDTO = buildQueryDTO();

            List<Notification> records = List.of(
                    buildNotification(1L, TYPE_ORDER, "订单已发货", 0),
                    buildNotification(2L, NotificationTypeEnum.PAY.getCode(), "支付成功", 1)
            );
            Page<Notification> page = new Page<>(1, 10);
            page.setRecords(records);
            page.setTotal(2);

            when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(page);

            PageResult<NotificationVO> result = notificationService.getNotificationList(queryDTO);

            // 验证：返回2条VO，分页信息正确
            assertThat(result.getRecords()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2);
            // 验证VO转换：typeDesc被正确填充
            NotificationVO firstVO = result.getRecords().get(0);
            assertThat(firstVO.getTypeDesc()).isEqualTo("订单");
            assertThat(firstVO.getTitle()).isEqualTo("订单已发货");
        }
    }

    // ==================== 未读数量测试 ====================

    @Nested
    @DisplayName("getUnreadCount 未读通知数量")
    class GetUnreadCountTest {

        @Test
        @DisplayName("返回未读通知数量")
        void getUnreadCount_returnsCount() {
            when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            long count = notificationService.getUnreadCount(RECEIVER_TYPE_USER, USER_ID);

            assertThat(count).isEqualTo(5L);
        }
    }

    // ==================== 全部标记已读测试 ====================

    @Nested
    @DisplayName("markAllAsRead 全部标记已读")
    class MarkAllAsReadTest {

        @Test
        @DisplayName("批量更新为已读：调用update方法")
        void markAllAsRead_callsUpdate() {
            when(notificationMapper.update(any(Notification.class), any(LambdaUpdateWrapper.class)))
                    .thenReturn(3);

            notificationService.markAllAsRead(RECEIVER_TYPE_USER, USER_ID);

            // 验证：调用了 update，且传入的实体 isRead=1
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationMapper).update(captor.capture(), any(LambdaUpdateWrapper.class));
            assertThat(captor.getValue().getIsRead()).isEqualTo(1);
        }
    }

    // ==================== 单条标记已读测试 ====================

    @Nested
    @DisplayName("markAsRead 单条标记已读")
    class MarkAsReadTest {

        @Test
        @DisplayName("通知不存在：抛出NOT_FOUND异常")
        void markAsRead_notFound_throwsException() {
            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(null);

            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("通知不存在");

            verify(notificationMapper, never()).updateById(any(Notification.class));
        }

        @Test
        @DisplayName("非本人通知：抛出FORBIDDEN异常")
        void markAsRead_notYours_throwsException() {
            // 场景：通知属于用户2，但用户1想标记已读
            Notification notification = buildNotification(NOTIFICATION_ID, TYPE_ORDER, "测试", 0);
            notification.setReceiverId(2L); // 不是当前用户
            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(notification);

            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权操作此通知");

            verify(notificationMapper, never()).updateById(any(Notification.class));
        }

        @Test
        @DisplayName("已是已读状态：幂等返回，不调用updateById")
        void markAsRead_alreadyRead_noUpdate() {
            Notification notification = buildNotification(NOTIFICATION_ID, TYPE_ORDER, "测试", 1);
            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(notification);

            notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

            // 验证：没有调用 updateById（因为已经是已读）
            verify(notificationMapper, never()).updateById(any(Notification.class));
        }

        @Test
        @DisplayName("未读通知：调用updateById标记为已读")
        void markAsRead_unread_updatesToRead() {
            Notification notification = buildNotification(NOTIFICATION_ID, TYPE_ORDER, "测试", 0);
            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(notification);

            notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

            // 验证：调用了 updateById，且 isRead=1
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationMapper).updateById(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(NOTIFICATION_ID);
            assertThat(captor.getValue().getIsRead()).isEqualTo(1);
        }
    }

    // ==================== 辅助方法 ====================

    private NotificationSendDTO buildSendDTO() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverType(RECEIVER_TYPE_USER);
        dto.setReceiverId(USER_ID);
        dto.setType(TYPE_ORDER);
        dto.setTitle("订单已发货");
        dto.setContent("您的订单已发货");
        return dto;
    }

    private NotificationQueryDTO buildQueryDTO() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setReceiverType(RECEIVER_TYPE_USER);
        dto.setReceiverId(USER_ID);
        dto.setPage(1);
        dto.setSize(10);
        return dto;
    }

    private Notification buildNotification(Long id, int type, String title, int isRead) {
        Notification n = new Notification();
        n.setId(id);
        n.setReceiverType(RECEIVER_TYPE_USER);
        n.setReceiverId(USER_ID);
        n.setType(type);
        n.setTitle(title);
        n.setContent("内容");
        n.setIsRead(isRead);
        return n;
    }
}
