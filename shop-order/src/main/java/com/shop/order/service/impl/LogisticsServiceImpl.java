package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.order.dto.DeliveryDTO;
import com.shop.model.order.entity.OrderInfo;
import com.shop.model.order.entity.OrderLog;
import com.shop.model.order.entity.OrderLogistics;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.order.feign.NotificationFeignClient;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderLogMapper;
import com.shop.order.mapper.OrderLogisticsMapper;
import com.shop.order.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流服务实现类
 * <p>
 * 实现商家发货和查看物流信息功能。
 * 核心优化点：
 * 1. 状态机校验：发货前校验订单状态转换是否合法
 * 2. 乐观锁更新：防止并发发货导致状态异常
 * 3. 物流状态更新接口：供物流回调使用
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    /** 物流信息Mapper */
    private final OrderLogisticsMapper orderLogisticsMapper;

    /** 订单主表Mapper */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单状态日志Mapper */
    private final OrderLogMapper orderLogMapper;

    /** 通知服务Feign客户端，用于发货后通知用户 */
    private final NotificationFeignClient notificationFeignClient;

    /** 操作人类型：2商家 */
    private static final int OPERATOR_TYPE_MERCHANT = 2;

    /**
     * 商家发货
     * <p>
     * 只有"待发货"状态的订单才能发货。
     * 使用状态机校验和乐观锁更新，防止并发问题。
     * </p>
     *
     * @param dto 发货参数（包含快递单号和快递公司）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivery(DeliveryDTO dto) {
        // 查询订单
        OrderInfo order = orderInfoMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 使用状态机校验：只有"待发货"状态才能发货
        OrderStatusEnum fromStatus = OrderStatusEnum.getByCode(order.getStatus());
        OrderStatusEnum.checkTransit(fromStatus, OrderStatusEnum.SHIPPING, "商家发货");

        // 创建物流记录
        OrderLogistics logistics = new OrderLogistics();
        logistics.setOrderId(order.getId());
        logistics.setOrderNo(order.getOrderNo());
        logistics.setLogisticsNo(dto.getLogisticsNo());
        logistics.setLogisticsCompany(dto.getLogisticsCompany());

        // 初始化物流轨迹：第一条记录"已揽收"
        List<OrderLogistics.LogisticsDetail> detailList = new ArrayList<>();
        OrderLogistics.LogisticsDetail detail = new OrderLogistics.LogisticsDetail();
        detail.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        detail.setDesc("快递已揽收");
        detailList.add(detail);
        logistics.setDetail(detailList);

        orderLogisticsMapper.insert(logistics);

        // 乐观锁更新订单状态为"运输中"
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, order.getId())
                        .eq(OrderInfo::getStatus, OrderStatusEnum.PAID.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.SHIPPING.getCode())
                        .set(OrderInfo::getDeliveryTime, LocalDateTime.now())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "发货失败，订单状态已变更");
        }

        // 记录状态日志
        saveOrderLog(order.getId(), order.getOrderNo(), OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.SHIPPING.getCode(),
                "商家发货", null, OPERATOR_TYPE_MERCHANT, "快递公司: " + dto.getLogisticsCompany());

        log.info("商家发货成功: orderNo={}, logisticsNo={}", order.getOrderNo(), dto.getLogisticsNo());

        // 发货成功后通知用户（异步容忍，失败不影响发货主流程）
        sendDeliveryNotification(order, dto);
    }

    /**
     * 发送发货通知给用户
     * <p>
     * 发货成功后调用通知服务，给下单用户发一条"订单已发货"的站内通知。
     * 通知发送失败不影响发货主流程（发货已经成功），通过try-catch吞掉异常。
     * </p>
     *
     * @param order 订单信息
     * @param dto   发货参数（含快递公司和单号）
     */
    private void sendDeliveryNotification(OrderInfo order, DeliveryDTO dto) {
        try {
            NotificationSendDTO notification = new NotificationSendDTO();
            notification.setReceiverType(ReceiverTypeEnum.USER.getCode());
            notification.setReceiverId(order.getUserId());
            notification.setType(NotificationTypeEnum.ORDER.getCode());
            notification.setTitle("您的订单已发货");
            notification.setContent("订单 " + order.getOrderNo() + " 已由"
                    + dto.getLogisticsCompany() + "发出，快递单号：" + dto.getLogisticsNo());
            notification.setBizType("order");
            notification.setBizId(order.getOrderNo());
            notificationFeignClient.sendNotification(notification);
            log.info("发货通知发送成功: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            // 通知发送失败不影响发货主流程，记录日志即可
            log.error("发货通知发送异常: orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 查看物流信息
     *
     * @param orderId 订单ID
     * @return 物流信息VO
     */
    @Override
    public OrderDetailVO.OrderLogisticsVO getLogistics(Long orderId) {
        OrderLogistics logistics = orderLogisticsMapper.selectOne(
                new LambdaQueryWrapper<OrderLogistics>().eq(OrderLogistics::getOrderId, orderId)
        );

        if (logistics == null) {
            return null;
        }

        OrderDetailVO.OrderLogisticsVO vo = new OrderDetailVO.OrderLogisticsVO();
        vo.setLogisticsNo(logistics.getLogisticsNo());
        vo.setLogisticsCompany(logistics.getLogisticsCompany());

        if (logistics.getDetail() != null) {
            List<OrderDetailVO.LogisticsDetailVO> detailVOs = logistics.getDetail().stream()
                    .map(d -> {
                        OrderDetailVO.LogisticsDetailVO detailVO = new OrderDetailVO.LogisticsDetailVO();
                        detailVO.setTime(d.getTime());
                        detailVO.setDesc(d.getDesc());
                        return detailVO;
                    }).toList();
            vo.setDetail(detailVOs);
        }

        return vo;
    }

    // ==================== 私有方法 ====================

    /**
     * 保存订单状态日志
     *
     * @param orderId      订单ID
     * @param orderNo      订单号
     * @param fromStatus   变化前状态
     * @param toStatus     变化后状态
     * @param action       操作类型
     * @param operatorId   操作人ID
     * @param operatorType 操作人类型
     * @param note         备注
     */
    private void saveOrderLog(Long orderId, String orderNo, Integer fromStatus, Integer toStatus,
                              String action, Long operatorId, Integer operatorType, String note) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderId);
        orderLog.setOrderNo(orderNo);
        orderLog.setFromStatus(fromStatus);
        orderLog.setToStatus(toStatus);
        orderLog.setAction(action);
        orderLog.setOperatorId(operatorId);
        orderLog.setOperatorType(operatorType);
        orderLog.setNote(note);
        orderLogMapper.insert(orderLog);
    }
}
