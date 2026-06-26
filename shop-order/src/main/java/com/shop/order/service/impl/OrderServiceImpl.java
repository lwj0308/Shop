package com.shop.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.order.dto.OrderCancelDTO;
import com.shop.model.order.dto.OrderCreateDTO;
import com.shop.model.order.entity.*;
import com.shop.model.order.enums.OrderStatusEnum;
import com.shop.model.order.enums.OrderTypeEnum;
import com.shop.model.order.vo.OrderDetailVO;
import com.shop.model.order.vo.OrderItemVO;
import com.shop.model.order.vo.OrderVO;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.model.user.vo.AddressVO;
import com.shop.order.feign.CartFeignClient;
import com.shop.order.feign.CouponFeignClient;
import com.shop.order.feign.MerchantFeignClient;
import com.shop.order.feign.NotificationFeignClient;
import com.shop.order.feign.ProductFeignClient;
import com.shop.order.feign.PromotionFeignClient;
import com.shop.order.feign.UserFeignClient;
import com.shop.order.mapper.*;
import com.shop.order.service.OrderService;
import com.shop.order.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * <p>
 * 实现订单的创建、取消、查询、确认收货等核心业务逻辑。
 * 核心优化点：
 * 1. 创建订单使用Seata分布式事务保证订单创建和库存扣减的数据一致性
 * 2. 下单幂等：基于Redis分布式锁防止重复下单
 * 3. 订单状态机：使用枚举定义合法状态转换，防止非法操作
 * 4. 乐观锁：状态更新时使用条件更新，防止并发问题
 * 5. 超时取消：RocketMQ延时消息确保可靠投递
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 订单主表Mapper */
    private final OrderInfoMapper orderInfoMapper;

    /** 订单明细Mapper */
    private final OrderItemMapper orderItemMapper;

    /** 订单地址快照Mapper */
    private final OrderAddressMapper orderAddressMapper;

    /** 物流信息Mapper */
    private final OrderLogisticsMapper orderLogisticsMapper;

    /** 订单状态日志Mapper */
    private final OrderLogMapper orderLogMapper;

    /** 商品服务Feign客户端 */
    private final ProductFeignClient productFeignClient;

    /** 用户服务Feign客户端 */
    private final UserFeignClient userFeignClient;

    /** 购物车服务Feign客户端 */
    private final CartFeignClient cartFeignClient;

    /** 商家服务Feign客户端，用于确认收货后触发结算 */
    private final MerchantFeignClient merchantFeignClient;

    /** 通知服务Feign客户端，用于订单状态变更时通知用户 */
    private final NotificationFeignClient notificationFeignClient;

    /** 优惠券服务Feign客户端，用于下单核销优惠券和取消订单时回退优惠券 */
    private final CouponFeignClient couponFeignClient;

    /** 满减活动Feign客户端，用于下单时计算满减优惠金额 */
    private final PromotionFeignClient promotionFeignClient;

    /** 订单号生成器 */
    private final OrderNoGenerator orderNoGenerator;

    /** RocketMQ消息模板，用来发送延时消息 */
    private final RocketMQTemplate rocketMQTemplate;

    /** Redis模板，用于分布式锁和幂等校验 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 延时消息Topic */
    private static final String TOPIC_ORDER_TIMEOUT = "topic_order_timeout";

    /** 下单分布式锁key前缀：order:create:{userId}:{skuIds哈希} */
    private static final String LOCK_ORDER_CREATE = "order:create:";

    /** 下单锁超时时间（秒）：防止死锁，30秒后自动释放 */
    private static final long LOCK_ORDER_CREATE_TIMEOUT = 30;

    /** 幂等Token key前缀：order:idempotent:{token} */
    private static final String IDEMPOTENT_TOKEN_PREFIX = "order:idempotent:";

    /** 幂等Token有效期（秒）：5分钟内有效 */
    private static final long IDEMPOTENT_TOKEN_EXPIRE = 300;

    /** 操作人类型：1用户 2商家 3系统 */
    private static final int OPERATOR_TYPE_USER = 1;
    private static final int OPERATOR_TYPE_SYSTEM = 3;

    /**
     * 创建订单
     * <p>
     * 这是电商系统最核心的链路，整个流程：
     * 1. 幂等校验（防止重复下单）
     * 2. 分布式锁（防止同一用户并发下单）
     * 3. 获取商品信息（Feign调用商品服务）
     * 4. 获取收货地址（Feign调用用户服务）
     * 5. 计算订单金额
     * 6. 生成订单号
     * 7. 获取商家ID + 满减计算（Feign调用商家服务，弱依赖，失败不影响下单）
     * 8. 优惠券核销（如果用户选了优惠券，基于满减后金额判断门槛）
     * 9. 创建订单主表 + 订单明细 + 地址快照
     * 10. 扣减库存（Feign调用商品服务，失败时补偿回退已扣减库存）
     * 11. 发送30分钟延时消息（超时自动取消）
     * 12. 下单成功后删除购物车
     * </p>
     * <p>
     * 事务策略说明：
     * - 使用本地事务 @Transactional 保证订单主表/明细/地址/日志的写入原子性（任一步失败全部回滚）
     * - Seata分布式事务暂时禁用（Docker未启动Seata Server），跨服务的库存扣减采用"补偿回退"策略：
     *   如果某个SKU扣减失败，已扣减的SKU会调用addStock回退，再抛异常让本地事务回滚订单数据
     * - 优惠券核销是强依赖：核销失败直接抛异常回滚（避免券被占用但订单没创建）
     * - 满减计算是弱依赖：失败时降级为0，不影响下单
     * </p>
     *
     * @param userId 用户ID
     * @param dto    创建订单参数
     * @return 订单详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createOrder(Long userId, OrderCreateDTO dto) {
        log.info("开始创建订单: userId={}, items={}", userId, dto.getItems());

        // ========== 1. 幂等校验：如果前端传了幂等Token，校验是否重复请求 ==========
        checkIdempotentToken(dto.getIdempotentToken());

        // ========== 2. 分布式锁：防止同一用户同时创建多个订单 ==========
        // 锁的key是 order:create:{userId}，同一用户同一时刻只能有一个下单请求
        String lockKey = LOCK_ORDER_CREATE + userId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, String.valueOf(System.currentTimeMillis()),
                        LOCK_ORDER_CREATE_TIMEOUT, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "操作太频繁，请稍后再试");
        }

        try {
            // ========== 3. 获取所有SKU信息 ==========
            // 从下单参数中提取所有SKU ID（后续用于批量查询SKU、删除购物车、传给满减计算）
            List<Long> skuIds = dto.getItems().stream()
                    .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                    .collect(Collectors.toList());
            Map<Long, ProductSkuVO> skuMap = fetchSkuInfo(skuIds);

            // ========== 4. 获取收货地址信息 ==========
            Result<AddressVO> addressResult = userFeignClient.getAddressById(dto.getAddressId());
            if (addressResult == null || addressResult.getCode() != 200 || addressResult.getData() == null) {
                throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "收货地址获取失败");
            }
            AddressVO address = addressResult.getData();

            // ========== 5. 生成订单号 ==========
            String orderNo = orderNoGenerator.generate();

            // ========== 6. 计算订单金额并创建订单明细 ==========
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();

            for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
                ProductSkuVO sku = skuMap.get(itemDTO.getSkuId());

                // 检查库存是否充足
                if (sku.getStock() < itemDTO.getQuantity()) {
                    throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
                }

                // 计算小计金额 = 单价 × 数量
                BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
                totalAmount = totalAmount.add(subtotal);

                // 创建订单明细
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderNo(orderNo);
                orderItem.setProductId(sku.getProductId());
                orderItem.setSkuId(sku.getId());
                orderItem.setProductName("");
                orderItem.setSkuSpec(sku.getSpecValues() != null ? sku.getSpecValues().toString() : "");
                orderItem.setProductImage(sku.getImage());
                orderItem.setPrice(sku.getPrice());
                orderItem.setQuantity(itemDTO.getQuantity());
                orderItem.setSubtotal(subtotal);
                orderItems.add(orderItem);
            }

            // ========== 7. 获取商家ID + 满减计算 ==========
            // 从商品信息获取商家ID（假设一个订单只包含一个商家的商品）
            // 取第一个有效的 merchantId，如果获取失败则降级为 0（不影响下单主流程）
            Long merchantId = skuMap.values().stream()
                    .map(ProductSkuVO::getMerchantId)
                    .filter(id -> id != null && id > 0)
                    .findFirst()
                    .orElse(0L);
            log.info("订单关联商家ID: orderNo={}, merchantId={}", orderNo, merchantId);

            BigDecimal promotionDiscount = calculatePromotion(merchantId, totalAmount, skuIds, orderNo);

            // ========== 8. 优惠券核销（如果用户选了优惠券） ==========
            // 小白讲解：如果下单时传了 userCouponId，就调用用户服务核销这张优惠券
            // 优惠券基于"满减后金额"判断门槛：比如满200减20后是280元，用满100减10的券，280>=100可以减10
            // 如果核销失败（比如券被用了、过期了），直接抛异常让整个事务回滚
            BigDecimal couponDiscount = useCoupon(userId, dto.getUserCouponId(), orderNo,
                    totalAmount.subtract(promotionDiscount));

            // ========== 9. 创建订单主表 ==========
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setOrderNo(orderNo);
            orderInfo.setUserId(userId);
            orderInfo.setMerchantId(merchantId);
            orderInfo.setTotalAmount(totalAmount);
            // 实付金额 = 订单总金额 - 满减优惠 - 优惠券优惠
            orderInfo.setPayAmount(totalAmount.subtract(promotionDiscount).subtract(couponDiscount));
            orderInfo.setFreightAmount(BigDecimal.ZERO);
            // 总优惠金额 = 满减优惠 + 优惠券优惠
            orderInfo.setDiscountAmount(promotionDiscount.add(couponDiscount));
            // 单独记录满减优惠金额，取消订单时用于区分满减和优惠券（只有优惠券需要回退）
            orderInfo.setPromotionDiscount(promotionDiscount);
            orderInfo.setStatus(OrderStatusEnum.UNPAID.getCode());
            orderInfo.setRemark(dto.getRemark());
            orderInfoMapper.insert(orderInfo);

            Long orderId = orderInfo.getId();
            for (OrderItem item : orderItems) {
                item.setOrderId(orderId);
                orderItemMapper.insert(item);
            }

            // ========== 9. 创建地址快照 ==========
            OrderAddress orderAddress = new OrderAddress();
            orderAddress.setOrderId(orderId);
            orderAddress.setOrderNo(orderNo);
            orderAddress.setName(address.getName());
            orderAddress.setPhone(address.getPhone());
            orderAddress.setProvince(address.getProvince());
            orderAddress.setCity(address.getCity());
            orderAddress.setDistrict(address.getDistrict());
            orderAddress.setDetail(address.getDetail());
            orderAddressMapper.insert(orderAddress);

            // ========== 10. 记录订单状态日志 ==========
            saveOrderLog(orderId, orderNo, null, OrderStatusEnum.UNPAID.getCode(),
                    "创建订单", userId, OPERATOR_TYPE_USER, null);

            // ========== 11. 扣减库存（补偿回退策略） ==========
            // 一次性批量扣减所有 SKU 库存，shop-product 内部带补偿回退
            deductStockWithCompensation(dto.getItems(), orderNo);

            // ========== 12. 发送30分钟延时消息（超时自动取消） ==========
            sendTimeoutMessage(orderNo);

            // ========== 13. 下单成功后删除购物车 ==========
            try {
                cartFeignClient.deleteBySkuIds(userId, skuIds);
            } catch (Exception e) {
                log.warn("删除购物车失败: userId={}, skuIds={}", userId, skuIds, e);
            }

            // ========== 14. 销量累加（弱依赖，失败不影响下单） ==========
            // 小白讲解：用户买了2件，商品的销量就+2，用于首页"热销推荐"排序
            // 这是弱依赖：如果商品服务挂了导致销量没累加上，也不影响下单成功
            // 批量累加：把订单里所有商品的销量一次性传给商品服务，避免循环 N 次 Feign 调用
            try {
                Map<Long, Integer> salesMap = new java.util.HashMap<>();
                for (OrderItem item : orderItems) {
                    // 同一个商品可能有多个 SKU，销量需要合并累加
                    salesMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
                }
                productFeignClient.incrSalesBatch(salesMap);
            } catch (Exception e) {
                log.warn("销量累加失败，不影响下单: orderNo={}", orderNo, e);
            }

            log.info("订单创建成功: orderNo={}, totalAmount={}", orderNo, totalAmount);
            return getOrderDetail(userId, orderId);
        } finally {
            // 无论成功失败，都要释放分布式锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 批量获取SKU信息
     * <p>
     * 一次性把所有 SKU ID 传给 shop-product 查询，避免循环 N 次 Feign 调用（N+1 远程调用问题）。
     * 小白理解：原来买 3 件商品要打 3 次电话给商品服务查 SKU，现在 1 次电话查完所有 SKU。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU ID 到 SKU 信息的映射
     */
    private Map<Long, ProductSkuVO> fetchSkuInfo(List<Long> skuIds) {
        Result<List<ProductSkuVO>> result = productFeignClient.batchGetSkuByIds(skuIds);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "商品信息批量获取失败");
        }
        List<ProductSkuVO> skuList = result.getData();
        if (skuList.size() != skuIds.size()) {
            // 返回数量少于请求数量，说明部分 SKU 不存在
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "部分商品信息不存在");
        }
        // 校验商品是否上架，并转成 Map 方便按 skuId 查询
        Map<Long, ProductSkuVO> skuMap = skuList.stream()
                .collect(Collectors.toMap(ProductSkuVO::getId, vo -> vo));
        for (ProductSkuVO sku : skuList) {
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF.getCode(), "商品已下架，SKU ID: " + sku.getId());
            }
        }
        return skuMap;
    }

    /**
     * 计算满减优惠
     * <p>
     * 调用 shop-merchant 内部接口计算当前订单可享受的满减优惠。
     * 满减是弱依赖：如果 shop-merchant 服务挂了，降级返回0，用户依然能下单。
     * </p>
     *
     * @param merchantId  商家ID
     * @param totalAmount 订单总金额
     * @param skuIds      SKU ID列表
     * @param orderNo     订单号（用于日志）
     * @return 满减优惠金额（失败时返回0）
     */
    private BigDecimal calculatePromotion(Long merchantId, BigDecimal totalAmount, List<Long> skuIds, String orderNo) {
        PromotionCalculateDTO promotionDTO = new PromotionCalculateDTO();
        promotionDTO.setMerchantId(merchantId);
        promotionDTO.setOrderAmount(totalAmount);
        promotionDTO.setSkuIds(skuIds);
        try {
            Result<BigDecimal> promotionResult = promotionFeignClient.calculatePromotion(promotionDTO);
            if (promotionResult != null && promotionResult.isSuccess() && promotionResult.getData() != null) {
                log.info("满减计算成功: orderNo={}, promotionDiscount={}", orderNo, promotionResult.getData());
                return promotionResult.getData();
            }
        } catch (Exception e) {
            log.warn("满减计算失败，跳过满减优惠: orderNo={}", orderNo, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 核销优惠券
     * <p>
     * 如果用户选了优惠券，调用用户服务核销。
     * 优惠券是强依赖：核销失败直接抛异常让整个事务回滚。
     * </p>
     *
     * @param userId           用户ID
     * @param userCouponId     用户优惠券ID（null表示不使用优惠券）
     * @param orderNo          订单号
     * @param amountAfterPromotion 满减后金额（用于判断优惠券门槛）
     * @return 优惠券优惠金额（不使用优惠券时返回0）
     */
    private BigDecimal useCoupon(Long userId, Long userCouponId, String orderNo, BigDecimal amountAfterPromotion) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        CouponUseDTO couponUseDTO = new CouponUseDTO();
        couponUseDTO.setUserId(userId);
        couponUseDTO.setUserCouponId(userCouponId);
        couponUseDTO.setOrderNo(orderNo);
        couponUseDTO.setOrderAmount(amountAfterPromotion);
        Result<BigDecimal> couponResult = couponFeignClient.useCoupon(couponUseDTO);
        if (couponResult == null || !couponResult.isSuccess() || couponResult.getData() == null) {
            String msg = couponResult != null ? couponResult.getMessage() : "优惠券服务不可用";
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "优惠券核销失败：" + msg);
        }
        log.info("优惠券核销成功: orderNo={}, userCouponId={}, couponDiscount={}", orderNo, userCouponId, couponResult.getData());
        return couponResult.getData();
    }

    /**
     * 扣减库存（补偿回退策略）
     * <p>
     * 一次性把订单里所有 SKU 的扣减请求打包传给 shop-product，
     * shop-product 内部循环扣减并带补偿回退（任一失败回滚已扣减的）。
     * 相比原来循环 N 次 Feign 调用，现在只调用 1 次，大幅减少网络开销。
     * 小白理解：原来买 3 件商品要打 3 次电话扣库存，现在 1 次电话扣完，出问题商品服务自己回退。
     * </p>
     *
     * @param items   订单商品列表
     * @param orderNo 订单号（用于幂等去重）
     */
    private void deductStockWithCompensation(List<OrderCreateDTO.OrderItemDTO> items, String orderNo) {
        // 把订单明细转换成批量扣减参数
        List<StockDeductItemDTO> deductItems = items.stream().map(item -> {
            StockDeductItemDTO deductItem = new StockDeductItemDTO();
            deductItem.setSkuId(item.getSkuId());
            deductItem.setQuantity(item.getQuantity());
            return deductItem;
        }).collect(Collectors.toList());

        Result<Void> result = productFeignClient.batchDeductStock(deductItems, orderNo);
        if (result == null || result.getCode() != 200) {
            String msg = result != null ? result.getMessage() : "库存扣减失败";
            throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(), msg);
        }
    }

    /**
     * 取消订单
     * <p>
     * 只有"待付款"状态的订单才能取消。
     * 使用乐观锁（条件更新）防止并发问题：更新时检查状态是否还是待付款。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @param dto     取消原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId, OrderCancelDTO dto) {
        OrderInfo order = getAndCheckOwner(userId, orderId);

        // 使用状态机校验状态转换是否合法
        OrderStatusEnum fromStatus = OrderStatusEnum.getByCode(order.getStatus());
        OrderStatusEnum.checkTransit(fromStatus, OrderStatusEnum.CANCELLED, "取消订单");

        // 乐观锁更新：只有状态还是待付款时才能取消，防止并发问题
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, orderId)
                        .eq(OrderInfo::getStatus, OrderStatusEnum.UNPAID.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderInfo::getCancelReason, dto.getReason())
                        .set(OrderInfo::getCancelTime, LocalDateTime.now())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "取消订单失败，订单状态已变更");
        }

        // 回滚库存
        rollbackStock(orderId);

        // 如果是秒杀订单，回退 Redis 秒杀库存
        // 小白讲解：普通订单取消只需回退商品库存，秒杀订单还要把Redis里的秒杀库存加回去
        rollbackSeckillStock(order);

        // 回退优惠券（如果下单时用了优惠券，取消时要把券恢复成"未使用"状态）
        // 小白讲解：discountAmount 是"总优惠"（满减+优惠券），promotionDiscount 是"满减优惠"
        // 优惠券优惠 = 总优惠 - 满减优惠，只有优惠券优惠 > 0 才需要回退（满减不需要回退）
        // 用 try-catch 包裹，即使回退失败也不影响取消订单的主流程
        BigDecimal promotionDiscount = order.getPromotionDiscount() != null
                ? order.getPromotionDiscount() : BigDecimal.ZERO;
        BigDecimal couponDiscount = order.getDiscountAmount() != null
                ? order.getDiscountAmount().subtract(promotionDiscount) : BigDecimal.ZERO;
        if (couponDiscount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                Result<Void> rollbackResult = couponFeignClient.rollbackCoupon(order.getOrderNo());
                if (rollbackResult == null || !rollbackResult.isSuccess()) {
                    log.warn("优惠券回退失败: orderNo={}", order.getOrderNo());
                } else {
                    log.info("优惠券回退成功: orderNo={}", order.getOrderNo());
                }
            } catch (Exception e) {
                log.warn("优惠券回退异常，不影响取消订单: orderNo={}", order.getOrderNo(), e);
            }
        }

        // 记录状态日志
        saveOrderLog(orderId, order.getOrderNo(), OrderStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.CANCELLED.getCode(),
                "用户取消订单", userId, OPERATOR_TYPE_USER, dto.getReason());

        log.info("订单取消成功: orderNo={}, reason={}", order.getOrderNo(), dto.getReason());
    }

    /**
     * 获取订单详情
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单详情
     */
    @Override
    public OrderDetailVO getOrderDetail(Long userId, Long orderId) {
        OrderInfo order = getAndCheckOwner(userId, orderId);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );

        OrderAddress address = orderAddressMapper.selectOne(
                new LambdaQueryWrapper<OrderAddress>().eq(OrderAddress::getOrderId, orderId)
        );

        OrderLogistics logistics = orderLogisticsMapper.selectOne(
                new LambdaQueryWrapper<OrderLogistics>().eq(OrderLogistics::getOrderId, orderId)
        );

        return convertToDetailVO(order, items, address, logistics);
    }

    /**
     * 获取订单列表
     *
     * @param userId      用户ID
     * @param status      订单状态（null表示查所有状态）
     * @param pageRequest 分页参数
     * @return 分页订单列表
     */
    @Override
    public PageResult<OrderVO> getOrderList(Long userId, Integer status, PageRequest pageRequest) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getCreateTime);

        Page<OrderInfo> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        Page<OrderInfo> result = orderInfoMapper.selectPage(page, wrapper);

        List<OrderVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<OrderVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());

        return pageResult;
    }

    /**
     * 确认收货
     * <p>
     * 只有"运输中"状态的订单才能确认收货。
     * 使用乐观锁更新，防止并发问题。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long userId, Long orderId) {
        OrderInfo order = getAndCheckOwner(userId, orderId);

        // 使用状态机校验状态转换是否合法
        OrderStatusEnum fromStatus = OrderStatusEnum.getByCode(order.getStatus());
        OrderStatusEnum.checkTransit(fromStatus, OrderStatusEnum.RECEIVED, "确认收货");

        // 乐观锁更新：只有状态还是运输中时才能确认收货
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, orderId)
                        .eq(OrderInfo::getStatus, OrderStatusEnum.SHIPPING.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.RECEIVED.getCode())
                        .set(OrderInfo::getReceiveTime, LocalDateTime.now())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR.getCode(), "确认收货失败，订单状态已变更");
        }

        saveOrderLog(orderId, order.getOrderNo(), OrderStatusEnum.SHIPPING.getCode(),
                OrderStatusEnum.RECEIVED.getCode(),
                "确认收货", userId, OPERATOR_TYPE_USER, null);

        log.info("确认收货成功: orderNo={}", order.getOrderNo());

        // 确认收货后触发商家结算（异步容忍，失败不影响收货主流程）
        // 商家服务会生成结算流水并增加商家可用余额
        triggerSettlement(order);

        // 确认收货后通知用户（异步容忍，失败不影响收货主流程）
        sendReceiveNotification(order);
    }

    /**
     * 发送确认收货通知给用户
     * <p>
     * 确认收货成功后调用通知服务，给用户发一条"订单已确认收货"的站内通知。
     * 通知发送失败不影响收货主流程，通过try-catch吞掉异常。
     * </p>
     *
     * @param order 订单信息
     */
    private void sendReceiveNotification(OrderInfo order) {
        try {
            NotificationSendDTO notification = new NotificationSendDTO();
            notification.setReceiverType(ReceiverTypeEnum.USER.getCode());
            notification.setReceiverId(order.getUserId());
            notification.setType(NotificationTypeEnum.ORDER.getCode());
            notification.setTitle("订单已确认收货");
            notification.setContent("订单 " + order.getOrderNo() + " 已确认收货，感谢您的惠顾！");
            notification.setBizType("order");
            notification.setBizId(order.getOrderNo());
            notificationFeignClient.sendNotification(notification);
            log.info("确认收货通知发送成功: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            // 通知发送失败不影响收货主流程，记录日志即可
            log.error("确认收货通知发送异常: orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 触发商家结算
     * <p>
     * 确认收货成功后调用商家服务生成结算记录。
     * 结算失败不影响收货主流程（收货已经成功），通过try-catch吞掉异常，
     * 后续可通过定时任务扫描"已收货但未结算"的订单进行补偿。
     * </p>
     * <p>
     * 历史订单的merchantId为0（没有商家信息），跳过结算。
     * 只有merchantId > 0的订单才触发结算。
     * </p>
     *
     * @param order 订单信息
     */
    private void triggerSettlement(OrderInfo order) {
        // merchantId为0表示历史订单（没有商家信息），跳过结算
        if (order.getMerchantId() == null || order.getMerchantId() <= 0) {
            log.warn("订单无商家信息，跳过结算: orderNo={}", order.getOrderNo());
            return;
        }
        try {
            Result<Void> result = merchantFeignClient.settleOrder(
                    order.getMerchantId(),
                    order.getOrderNo(),
                    order.getPayAmount()
            );
            if (result.getCode() == 200) {
                log.info("订单结算成功: orderNo={}, merchantId={}", order.getOrderNo(), order.getMerchantId());
            } else {
                log.warn("订单结算失败: orderNo={}, message={}", order.getOrderNo(), result.getMessage());
            }
        } catch (Exception e) {
            // 结算失败不影响收货主流程，记录日志即可
            log.error("订单结算异常，需通过定时任务补偿: orderNo={}, merchantId={}",
                    order.getOrderNo(), order.getMerchantId(), e);
        }
    }

    /**
     * 自动取消超时订单
     * <p>
     * MQ消费者调用：30分钟后检查订单是否已支付，未支付则自动取消。
     * 使用乐观锁更新，防止和用户手动取消产生并发问题。
     * </p>
     *
     * @param orderNo 订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoCancelOrder(String orderNo) {
        OrderInfo order = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo)
        );

        if (order == null || order.getStatus() != OrderStatusEnum.UNPAID.getCode()) {
            log.info("订单无需自动取消: orderNo={}, status={}", orderNo,
                    order != null ? order.getStatus() : "不存在");
            return;
        }

        // 乐观锁更新：只有状态还是待付款时才能自动取消
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, order.getId())
                        .eq(OrderInfo::getStatus, OrderStatusEnum.UNPAID.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.CANCELLED.getCode())
                        .set(OrderInfo::getCancelReason, "超时未支付，系统自动取消")
                        .set(OrderInfo::getCancelTime, LocalDateTime.now())
        );
        if (updated == 0) {
            log.info("自动取消失败，订单状态已变更: orderNo={}", orderNo);
            return;
        }

        // 回滚库存
        rollbackStock(order.getId());

        // 如果是秒杀订单，回退 Redis 秒杀库存（超时取消也要回退秒杀库存）
        rollbackSeckillStock(order);

        saveOrderLog(order.getId(), orderNo, OrderStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.CANCELLED.getCode(),
                "超时自动取消", null, OPERATOR_TYPE_SYSTEM, "超时未支付，系统自动取消");

        log.info("订单超时自动取消: orderNo={}", orderNo);
    }

    /**
     * 支付成功回调
     * <p>
     * 支付服务调用：用户支付成功后，更新订单状态为"待发货"。
     * 使用乐观锁更新，防止重复回调导致状态异常。
     * </p>
     *
     * @param orderNo 订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String orderNo) {
        OrderInfo order = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 幂等校验：如果已经支付过了，直接返回成功
        if (order.getStatus() == OrderStatusEnum.PAID.getCode()) {
            log.info("订单已支付，忽略重复回调: orderNo={}", orderNo);
            return;
        }

        // 使用状态机校验状态转换是否合法
        OrderStatusEnum fromStatus = OrderStatusEnum.getByCode(order.getStatus());
        OrderStatusEnum.checkTransit(fromStatus, OrderStatusEnum.PAID, "支付成功");

        // 乐观锁更新：只有状态还是待付款时才能变为待发货
        int updated = orderInfoMapper.update(null,
                new LambdaUpdateWrapper<OrderInfo>()
                        .eq(OrderInfo::getId, order.getId())
                        .eq(OrderInfo::getStatus, OrderStatusEnum.UNPAID.getCode())
                        .set(OrderInfo::getStatus, OrderStatusEnum.PAID.getCode())
                        .set(OrderInfo::getPayTime, LocalDateTime.now())
        );
        if (updated == 0) {
            log.warn("支付回调更新失败，订单状态已变更: orderNo={}", orderNo);
            return;
        }

        saveOrderLog(order.getId(), orderNo, OrderStatusEnum.UNPAID.getCode(),
                OrderStatusEnum.PAID.getCode(),
                "支付成功", order.getUserId(), OPERATOR_TYPE_SYSTEM, null);

        log.info("订单支付成功: orderNo={}", orderNo);
    }

    // ==================== 私有方法 ====================

    /**
     * 幂等Token校验
     * <p>
     * 前端在下单页面加载时先获取一个幂等Token，
     * 提交订单时带上这个Token，后端校验Token是否有效。
     * 如果Token已经被使用过（Redis中不存在），说明是重复请求，直接拒绝。
     * 这样可以防止用户因为网络卡顿而重复点击"提交订单"按钮。
     * </p>
     *
     * @param token 幂等Token，前端传过来的
     */
    private void checkIdempotentToken(String token) {
        if (token == null || token.isEmpty()) {
            // 没有传Token时不强制校验（兼容旧版前端）
            return;
        }
        // 尝试删除Token，删除成功说明是第一次请求，删除失败说明是重复请求
        Boolean deleted = stringRedisTemplate.delete(IDEMPOTENT_TOKEN_PREFIX + token);
        if (deleted == null || !deleted) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_FAIL.getCode(), "请勿重复提交订单");
        }
    }

    /**
     * 发送超时自动取消的延时消息
     * <p>
     * 使用RocketMQ延时消息，30分钟后自动投递。
     * 如果发送失败，记录错误日志，不影响下单流程。
     * 可以通过定时任务补偿（扫描超时未支付的订单）。
     * </p>
     *
     * @param orderNo 订单号
     */
    private void sendTimeoutMessage(String orderNo) {
        try {
            // 使用 Spring 的 MessageBuilder 构建消息（不是 RocketMQ 的）
            rocketMQTemplate.syncSend(
                    TOPIC_ORDER_TIMEOUT,
                    org.springframework.messaging.support.MessageBuilder.withPayload(orderNo).build(),
                    3000,
                    16    // 延时等级16 = 30分钟
            );
            log.info("发送延时消息成功: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("发送延时消息失败: orderNo={}，需要通过定时任务补偿", orderNo, e);
        }
    }

    /**
     * 查询订单并校验归属权
     * <p>
     * 确保订单存在，且属于当前登录用户。
     * 防止用户通过修改订单ID来查看别人的订单。
     * </p>
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单实体
     */
    private OrderInfo getAndCheckOwner(Long userId, Long orderId) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此订单");
        }
        return order;
    }

    /**
     * 回滚库存
     * <p>
     * 订单取消或退款时，把之前扣的库存加回去。
     * 如果回滚失败，记录错误日志，可以通过定时任务补偿。
     * </p>
     *
     * @param orderId 订单ID
     */
    private void rollbackStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );

        for (OrderItem item : items) {
            try {
                productFeignClient.addStock(item.getSkuId(), item.getQuantity());
            } catch (Exception e) {
                log.error("库存回滚失败: skuId={}, quantity={}，需要通过定时任务补偿",
                        item.getSkuId(), item.getQuantity(), e);
            }
        }
    }

    /**
     * 回退Redis秒杀库存
     * <p>
     * 秒杀订单取消（用户手动取消或超时自动取消）时，需要把Redis里的秒杀库存加回去。
     * 普通订单不需要这一步，只有orderType=2（秒杀订单）且seckillId不为null时才回退。
     * </p>
     * <p>
     * 小白讲解：秒杀抢购时在Redis里扣了1个秒杀库存，如果订单取消了，
     * 这1个库存要加回去，让别的用户还能抢。
     * </p>
     *
     * @param order 订单信息
     */
    private void rollbackSeckillStock(OrderInfo order) {
        // 只有秒杀订单（orderType=2）且记录了秒杀活动ID才需要回退Redis秒杀库存
        if (order.getOrderType() == null
                || order.getOrderType() != OrderTypeEnum.SECKILL.getCode()
                || order.getSeckillId() == null) {
            return;
        }
        try {
            String stockKey = "seckill:stock:" + order.getSeckillId();
            stringRedisTemplate.opsForValue().increment(stockKey);
            log.info("秒杀库存回退成功: seckillId={}", order.getSeckillId());
        } catch (Exception e) {
            // 回退失败不影响取消订单主流程，记录日志后续可通过定时任务补偿
            log.warn("秒杀库存回退失败: seckillId={}", order.getSeckillId(), e);
        }
    }

    /**
     * 保存订单状态日志
     * <p>
     * 每次订单状态变化都记录一条日志，方便追溯订单的完整生命周期。
     * </p>
     *
     * @param orderId      订单ID
     * @param orderNo      订单号
     * @param fromStatus   变化前的状态
     * @param toStatus     变化后的状态
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

    /**
     * 获取订单状态描述
     * <p>
     * 使用枚举替代原来的switch，更优雅也更安全。
     * </p>
     *
     * @param status 状态码
     * @return 状态描述
     */
    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        OrderStatusEnum statusEnum = OrderStatusEnum.getByCode(status);
        return statusEnum != null ? statusEnum.getDesc() : "未知";
    }

    /**
     * OrderInfo转OrderVO（列表展示用）
     *
     * @param order 订单实体
     * @return 订单列表VO
     */
    private OrderVO convertToVO(OrderInfo order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(getStatusDesc(order.getStatus()));
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );
        if (!items.isEmpty()) {
            vo.setFirstItemImage(items.get(0).getProductImage());
            vo.setTotalQuantity(items.stream().mapToInt(OrderItem::getQuantity).sum());
        }

        return vo;
    }

    /**
     * 组装订单详情VO
     *
     * @param order     订单主表
     * @param items     订单明细列表
     * @param address   地址快照
     * @param logistics 物流信息
     * @return 订单详情VO
     */
    private OrderDetailVO convertToDetailVO(OrderInfo order, List<OrderItem> items,
                                            OrderAddress address, OrderLogistics logistics) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(getStatusDesc(order.getStatus()));
        vo.setRemark(order.getRemark());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDeliveryTime(order.getDeliveryTime());
        vo.setReceiveTime(order.getReceiveTime());
        vo.setFinishTime(order.getFinishTime());

        List<OrderItemVO> itemVOs = items.stream().map(item -> {
            OrderItemVO itemVO = new OrderItemVO();
            itemVO.setId(item.getId());
            itemVO.setProductId(item.getProductId());
            itemVO.setSkuId(item.getSkuId());
            itemVO.setProductName(item.getProductName());
            itemVO.setSkuSpec(item.getSkuSpec());
            itemVO.setProductImage(item.getProductImage());
            itemVO.setPrice(item.getPrice());
            itemVO.setQuantity(item.getQuantity());
            itemVO.setSubtotal(item.getSubtotal());
            return itemVO;
        }).collect(Collectors.toList());
        vo.setItems(itemVOs);

        if (address != null) {
            OrderDetailVO.OrderAddressVO addressVO = new OrderDetailVO.OrderAddressVO();
            addressVO.setName(address.getName());
            addressVO.setPhone(address.getPhone());
            addressVO.setProvince(address.getProvince());
            addressVO.setCity(address.getCity());
            addressVO.setDistrict(address.getDistrict());
            addressVO.setDetail(address.getDetail());
            vo.setAddress(addressVO);
        }

        if (logistics != null) {
            OrderDetailVO.OrderLogisticsVO logisticsVO = new OrderDetailVO.OrderLogisticsVO();
            logisticsVO.setLogisticsNo(logistics.getLogisticsNo());
            logisticsVO.setLogisticsCompany(logistics.getLogisticsCompany());
            if (logistics.getDetail() != null) {
                List<OrderDetailVO.LogisticsDetailVO> detailVOs = logistics.getDetail().stream()
                        .map(d -> {
                            OrderDetailVO.LogisticsDetailVO detailVO = new OrderDetailVO.LogisticsDetailVO();
                            detailVO.setTime(d.getTime());
                            detailVO.setDesc(d.getDesc());
                            return detailVO;
                        }).collect(Collectors.toList());
                logisticsVO.setDetail(detailVOs);
            }
            vo.setLogistics(logisticsVO);
        }

        return vo;
    }
}
