package com.shop.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.cart.feign.ProductFeignClient;
import com.shop.cart.mapper.CartItemMapper;
import com.shop.cart.service.CartService;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.cart.dto.CartAddDTO;
import com.shop.model.cart.dto.CartCheckDTO;
import com.shop.model.cart.dto.CartUpdateDTO;
import com.shop.model.cart.entity.CartItem;
import com.shop.model.cart.vo.CartItemVO;
import com.shop.model.cart.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类
 * <p>
 * 实现购物车的核心业务逻辑，包括加购、修改、删除、勾选、清空等操作。
 * 重点注意：
 * - 同一SKU加购时数量累加，不新增记录
 * - 每个用户购物车最多100个不同SKU
 * - 单个购物车项数量最多99件
 * - 获取购物车列表时批量查询商品服务，避免N+1问题
 * - 加购/修改数量时校验商品是否上架、SKU是否存在、库存是否充足
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    /** 购物车项Mapper，操作cart_item表 */
    private final CartItemMapper cartItemMapper;

    /** 商品服务远程调用，用于获取实时价格、库存和商品信息 */
    private final ProductFeignClient productFeignClient;

    /** 购物车SKU数量上限：每个用户最多100个不同SKU */
    private static final int CART_SKU_LIMIT = 100;

    /** 单个购物车项数量上限：同一SKU最多买99件 */
    private static final int CART_ITEM_QUANTITY_LIMIT = 99;

    /**
     * 加入购物车
     * <p>
     * 逻辑说明：
     * 1. 校验加购数量是否超过单条上限
     * 2. 先校验商品是否上架、SKU是否存在、库存是否充足
     * 3. 查一下这个SKU是不是已经在购物车里了
     * 4. 如果在，就把数量累加（比如原来2个，再加3个，变成5个），累加后不超过单条上限
     * 5. 如果不在，就新增一条记录，但要先检查购物车SKU数量是否超过上限
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToCart(Long userId, CartAddDTO dto) {
        // 校验加购数量是否超过单条上限
        validateQuantityLimit(dto.getQuantity());

        // 校验商品是否上架、SKU是否存在、库存是否充足（如果商品服务可用）
        validateProductAndSku(dto.getProductId(), dto.getSkuId(), dto.getQuantity());

        // 查询该SKU是否已在购物车中
        CartItem existItem = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSkuId, dto.getSkuId())
        );

        if (existItem != null) {
            // 同一SKU已存在，数量累加
            int newQuantity = existItem.getQuantity() + dto.getQuantity();
            // 校验累加后的数量是否超过单条上限
            if (newQuantity > CART_ITEM_QUANTITY_LIMIT) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT_EXCEED,
                        "同一商品最多购买" + CART_ITEM_QUANTITY_LIMIT + "件，当前已有" + existItem.getQuantity() + "件");
            }
            existItem.setQuantity(newQuantity);
            cartItemMapper.updateById(existItem);
            log.info("购物车数量累加: userId={}, skuId={}, 新数量={}", userId, dto.getSkuId(), newQuantity);
        } else {
            // 新SKU，先检查购物车SKU数量是否超过上限
            Long skuCount = cartItemMapper.selectCount(
                    new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId)
            );
            if (skuCount >= CART_SKU_LIMIT) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT_EXCEED,
                        "最多只能添加" + CART_SKU_LIMIT + "种商品");
            }

            // 新增购物车项
            CartItem cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setProductId(dto.getProductId());
            cartItem.setSkuId(dto.getSkuId());
            cartItem.setQuantity(dto.getQuantity());
            cartItem.setChecked(1); // 默认勾选
            cartItemMapper.insert(cartItem);
            log.info("新增购物车项: userId={}, skuId={}, quantity={}", userId, dto.getSkuId(), dto.getQuantity());
        }
    }

    /**
     * 修改购物车项
     * <p>
     * 可以修改数量或勾选状态，传哪个就改哪个。
     * 必须确保只能改自己的购物车项。
     * 修改数量时会校验：单条数量上限、库存是否充足。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCartItem(Long userId, Long cartItemId, CartUpdateDTO dto) {
        // 查询并校验归属权
        CartItem cartItem = getAndCheckOwner(userId, cartItemId);

        // 修改数量
        if (dto.getQuantity() != null) {
            // 校验数量上限
            validateQuantityLimit(dto.getQuantity());
            // 校验库存是否充足（如果商品服务可用）
            validateStock(cartItem.getSkuId(), dto.getQuantity());
            cartItem.setQuantity(dto.getQuantity());
        }

        // 修改勾选状态
        if (dto.getChecked() != null) {
            cartItem.setChecked(dto.getChecked());
        }

        cartItemMapper.updateById(cartItem);
        log.info("修改购物车项: userId={}, cartItemId={}", userId, cartItemId);
    }

    /**
     * 删除购物车项
     * <p>
     * 从购物车中移除某个商品。只能删自己的。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartItem(Long userId, Long cartItemId) {
        // 确保购物车项存在且属于当前用户
        getAndCheckOwner(userId, cartItemId);
        cartItemMapper.deleteById(cartItemId);
        log.info("删除购物车项: userId={}, cartItemId={}", userId, cartItemId);
    }

    /**
     * 批量勾选/取消勾选
     * <p>
     * 用户勾选多个商品准备结算，或者取消勾选。
     * 只更新属于当前用户的购物车项，不存在的ID会被SQL条件自动忽略。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCheck(Long userId, CartCheckDTO dto) {
        // 批量更新勾选状态，只更新属于当前用户的
        CartItem update = new CartItem();
        update.setChecked(dto.getChecked());
        cartItemMapper.update(update,
                new LambdaUpdateWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .in(CartItem::getId, dto.getCartItemIds())
        );
        log.info("批量勾选: userId={}, cartItemIds={}, checked={}", userId, dto.getCartItemIds(), dto.getChecked());
    }

    /**
     * 全选/取消全选
     * <p>
     * 一键勾选或取消勾选购物车里的所有商品。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAll(Long userId, Boolean checked) {
        int checkedValue = Boolean.TRUE.equals(checked) ? 1 : 0;
        CartItem update = new CartItem();
        update.setChecked(checkedValue);
        cartItemMapper.update(update,
                new LambdaUpdateWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
        );
        log.info("全选/取消全选: userId={}, checked={}", userId, checked);
    }

    /**
     * 获取购物车列表
     * <p>
     * 返回购物车中所有商品的详细信息。
     * 商品名称、图片、价格、库存状态需要从商品服务实时获取。
     * 使用批量接口查询商品信息，避免N+1问题（原来每个商品查3次，现在批量查2次）。
     * 如果商品服务不可用（降级），则显示"商品信息暂不可用"的友好提示。
     * </p>
     */
    @Override
    public CartVO getCartList(Long userId) {
        // 查询用户的所有购物车项，按创建时间倒序（最新加的排前面）
        List<CartItem> cartItems = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getCreateTime)
        );

        // 转换为VO列表
        List<CartItemVO> itemVOList = new ArrayList<>();
        int totalCount = 0;
        BigDecimal checkedTotalPrice = BigDecimal.ZERO;
        boolean isAllChecked = true;

        // 批量从商品服务获取实时数据，填充到所有CartItemVO中
        // 优化点：原来每个商品调3次Feign，现在批量调2次（1次SKU信息 + N次去重后的商品信息）
        Map<Long, Map<String, Object>> skuInfoMap = batchGetSkuInfo(cartItems);
        Map<Long, Map<String, Object>> productInfoMap = batchGetProductInfo(cartItems);

        for (CartItem item : cartItems) {
            CartItemVO vo = convertToVO(item);

            // 从批量结果中填充商品实时数据
            enrichCartItemVOFromBatch(vo, item, skuInfoMap, productInfoMap);

            // 计算小计金额
            if (vo.getSkuPrice() != null) {
                vo.setSubtotal(vo.getSkuPrice().multiply(BigDecimal.valueOf(vo.getQuantity())));
            }

            itemVOList.add(vo);

            // 累加总数量
            totalCount += item.getQuantity();

            // 累加选中商品总价
            if (item.getChecked() == 1 && vo.getSubtotal() != null) {
                checkedTotalPrice = checkedTotalPrice.add(vo.getSubtotal());
            }

            // 判断是否全选
            if (item.getChecked() != 1) {
                isAllChecked = false;
            }
        }

        // 如果购物车是空的，全选状态为false
        if (cartItems.isEmpty()) {
            isAllChecked = false;
        }

        // 组装CartVO
        CartVO cartVO = new CartVO();
        cartVO.setItems(itemVOList);
        cartVO.setTotalCount(totalCount);
        cartVO.setCheckedTotalPrice(checkedTotalPrice);
        cartVO.setIsAllChecked(isAllChecked);
        return cartVO;
    }

    /**
     * 清空购物车
     * <p>
     * 删除用户购物车中的所有商品。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId)
        );
        log.info("清空购物车: userId={}", userId);
    }

    /**
     * 获取购物车商品数量
     * <p>
     * 用于Header角标显示，比如购物车图标上显示"3"表示有3件商品。
     * 返回的是所有商品的数量之和，不是SKU种类数。
     * </p>
     */
    @Override
    public Integer getCartCount(Long userId) {
        List<CartItem> cartItems = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .select(CartItem::getQuantity) // 只查数量字段，提高效率
        );
        // 把所有商品的数量加起来
        return cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * 根据SKU ID批量删除购物车项
     * <p>
     * 用户下单成功后调用，把已购买的商品从购物车中移除。
     * 传的是SKU ID列表而不是购物车项ID，因为订单里只有SKU ID。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySkuIds(Long userId, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .in(CartItem::getSkuId, skuIds)
        );
        log.info("根据SKU ID批量删除购物车项: userId={}, skuIds={}", userId, skuIds);
    }

    /**
     * 合并购物车
     * <p>
     * 用户未登录时在浏览器本地存了购物车数据，登录后需要把这些数据
     * 合并到服务端的购物车中。逐项调用加购逻辑，同一SKU数量累加。
     * 如果某一项合并失败（比如商品已下架），跳过该项继续合并其他的，
     * 不会因为一个商品的问题导致整个合并失败。
     * </p>
     *
     * @param userId 用户ID
     * @param items  未登录时的购物车项列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeCart(Long userId, List<CartAddDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // 逐项合并，复用加购逻辑（校验+累加+上限检查）
        // 某一项失败不影响其他项的合并
        for (CartAddDTO item : items) {
            try {
                addToCart(userId, item);
            } catch (BusinessException e) {
                // 单项合并失败只记录日志，不中断整个合并流程
                // 比如商品已下架，跳过这个商品，继续合并其他的
                log.warn("合并购物车项失败，跳过: userId={}, skuId={}, 原因={}",
                        userId, item.getSkuId(), e.getMessage());
            }
        }
        log.info("合并购物车完成: userId={}, 总项数={}", userId, items.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 校验加购数量是否超过单条上限
     * <p>
     * 防止用户一次性加购过多商品，比如买9999件。
     * 单条购物车项最多99件，超过就提示用户。
     * </p>
     *
     * @param quantity 加购数量
     */
    private void validateQuantityLimit(Integer quantity) {
        if (quantity != null && quantity > CART_ITEM_QUANTITY_LIMIT) {
            throw new BusinessException(ErrorCode.CART_ITEM_LIMIT_EXCEED,
                    "同一商品最多购买" + CART_ITEM_QUANTITY_LIMIT + "件");
        }
    }

    /**
     * 校验库存是否充足
     * <p>
     * 修改购物车数量时调用，确保用户改的数量不超过库存。
     * 如果商品服务不可用，降级放行（结算时再校验）。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 目标数量
     */
    private void validateStock(Long skuId, Integer quantity) {
        try {
            Result<Boolean> stockResult = productFeignClient.getStockStatus(skuId);
            if (stockResult != null && stockResult.isSuccess() && stockResult.getData() != null) {
                if (!stockResult.getData()) {
                    throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 商品服务不可用时降级放行，结算时再校验
            log.warn("商品服务不可用，跳过库存校验: skuId={}", skuId, e);
        }
    }

    /**
     * 校验商品和SKU是否有效（上架、存在、库存充足）
     * <p>
     * 加购前必须校验，防止用户把已下架、不存在或库存不足的商品加入购物车。
     * 如果商品服务不可用（降级），则跳过校验，允许加购（降级策略：宁可多加不可漏加）。
     * </p>
     *
     * @param productId 商品ID
     * @param skuId     SKU ID
     * @param quantity  加购数量
     */
    private void validateProductAndSku(Long productId, Long skuId, Integer quantity) {
        try {
            // 校验商品是否上架
            Result<Map<String, Object>> basicInfoResult = productFeignClient.getProductBasicInfo(productId);
            if (basicInfoResult != null && basicInfoResult.isSuccess() && basicInfoResult.getData() != null) {
                Map<String, Object> basicInfo = basicInfoResult.getData();
                // 如果商品信息中包含上架状态，检查是否上架
                Object status = basicInfo.get("status");
                if (status != null && Integer.parseInt(status.toString()) != 1) {
                    throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
                }
            }

            // 校验SKU是否存在
            Result<BigDecimal> priceResult = productFeignClient.getSkuPrice(skuId);
            if (priceResult != null && priceResult.isSuccess() && priceResult.getData() == null) {
                // 能调通但返回空数据，说明SKU不存在
                throw new BusinessException(ErrorCode.PRODUCT_SKU_NOT_FOUND);
            }

            // 校验库存是否充足
            Result<Boolean> stockResult = productFeignClient.getStockStatus(skuId);
            if (stockResult != null && stockResult.isSuccess() && stockResult.getData() != null) {
                if (!stockResult.getData()) {
                    throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
                }
            }
        } catch (BusinessException e) {
            // 业务异常直接抛出（商品下架、SKU不存在、库存不足等）
            throw e;
        } catch (Exception e) {
            // 商品服务不可用时，降级处理：只记录日志，不阻止加购
            // 降级策略：宁可多加不可漏加，等用户结算时再校验
            log.warn("商品服务不可用，跳过加购校验: productId={}, skuId={}", productId, skuId, e);
        }
    }

    /**
     * 批量获取SKU信息（价格、库存、规格名称）
     * <p>
     * 一次Feign调用获取所有SKU的信息，替代原来每个SKU调3次的N+1问题。
     * 如果批量接口失败，降级为空Map，购物车列表会显示默认值。
     * </p>
     *
     * @param cartItems 购物车项列表
     * @return key是skuId，value是SKU详情Map
     */
    private Map<Long, Map<String, Object>> batchGetSkuInfo(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Long> skuIds = cartItems.stream()
                    .map(CartItem::getSkuId)
                    .distinct()
                    .collect(Collectors.toList());
            Result<Map<Long, Map<String, Object>>> batchResult = productFeignClient.batchGetSkuInfo(skuIds);
            if (batchResult != null && batchResult.isSuccess() && batchResult.getData() != null) {
                return batchResult.getData();
            }
        } catch (Exception e) {
            log.warn("批量获取SKU信息失败，降级处理", e);
        }
        return Collections.emptyMap();
    }

    /**
     * 批量获取商品基本信息（名称、图片）
     * <p>
     * 按productId去重后逐个查询，避免同一商品重复查询。
     * 虽然不是真正的批量接口，但去重后查询次数大大减少
     * （比如购物车有5个不同规格的iPhone，只查1次商品信息）。
     * </p>
     *
     * @param cartItems 购物车项列表
     * @return key是productId，value是商品基本信息Map
     */
    private Map<Long, Map<String, Object>> batchGetProductInfo(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, Object>> productInfoMap = new HashMap<>();
        // 按productId去重，同一商品只查一次
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .collect(Collectors.toList());
        for (Long productId : productIds) {
            try {
                Result<Map<String, Object>> basicResult = productFeignClient.getProductBasicInfo(productId);
                if (basicResult != null && basicResult.isSuccess() && basicResult.getData() != null) {
                    productInfoMap.put(productId, basicResult.getData());
                }
            } catch (Exception e) {
                log.warn("获取商品基本信息失败: productId={}", productId, e);
            }
        }
        return productInfoMap;
    }

    /**
     * 从批量查询结果中填充CartItemVO的实时数据
     * <p>
     * 用批量查询的结果替代逐个Feign调用，大幅减少网络请求次数。
     * 如果某项数据缺失（商品服务降级），保留默认值并显示友好提示。
     * </p>
     *
     * @param vo             购物车项VO（会被修改）
     * @param cartItem       购物车项实体
     * @param skuInfoMap     批量SKU信息（key是skuId）
     * @param productInfoMap 批量商品信息（key是productId）
     */
    private void enrichCartItemVOFromBatch(CartItemVO vo, CartItem cartItem,
                                           Map<Long, Map<String, Object>> skuInfoMap,
                                           Map<Long, Map<String, Object>> productInfoMap) {
        // 从批量SKU信息中获取价格、库存、规格名称
        Map<String, Object> skuInfo = skuInfoMap.get(cartItem.getSkuId());
        if (skuInfo != null) {
            // SKU价格
            Object price = skuInfo.get("price");
            if (price != null) {
                vo.setSkuPrice(new BigDecimal(price.toString()));
            }
            // 库存状态
            Object stock = skuInfo.get("stock");
            if (stock != null) {
                vo.setInStock(Boolean.parseBoolean(stock.toString()));
            }
            // SKU规格名称
            Object skuName = skuInfo.get("skuName");
            if (skuName != null) {
                vo.setSkuName((String) skuName);
            }
        }

        // 从批量商品信息中获取名称和图片
        Map<String, Object> productInfo = productInfoMap.get(cartItem.getProductId());
        if (productInfo != null) {
            Object name = productInfo.get("name");
            if (name != null) {
                vo.setProductName((String) name);
            }
            Object image = productInfo.get("image");
            if (image != null) {
                vo.setProductImage((String) image);
            }
            // 如果批量SKU信息中没有skuName，尝试从商品信息中获取
            if ((vo.getSkuName() == null || vo.getSkuName().isEmpty()) && productInfo.get("skuName") != null) {
                vo.setSkuName((String) productInfo.get("skuName"));
            }
        }

        // 降级提示：如果关键信息缺失，显示友好提示
        if (vo.getProductName() == null || vo.getProductName().isEmpty()) {
            vo.setProductName("商品信息暂不可用");
        }
        if (vo.getSkuName() == null || vo.getSkuName().isEmpty()) {
            vo.setSkuName("规格信息暂不可用");
        }
    }

    /**
     * 查询购物车项并校验归属权
     * <p>
     * 确保购物车项存在，且属于当前登录用户。
     * 防止用户通过修改ID来操作别人的购物车。
     * </p>
     *
     * @param userId     用户ID
     * @param cartItemId 购物车项ID
     * @return 购物车项实体
     */
    private CartItem getAndCheckOwner(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemMapper.selectById(cartItemId);
        if (cartItem == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
        if (!cartItem.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此购物车项");
        }
        return cartItem;
    }

    /**
     * CartItem实体转CartItemVO
     * <p>
     * 把数据库查出来的实体转成前端需要的VO格式。
     * 商品名称、图片、价格、库存状态等字段设为默认值，
     * 后续会通过 enrichCartItemVOFromBatch 方法从批量查询结果中填充。
     * </p>
     *
     * @param cartItem 购物车项实体
     * @return 购物车项VO
     */
    private CartItemVO convertToVO(CartItem cartItem) {
        CartItemVO vo = new CartItemVO();
        vo.setId(cartItem.getId());
        vo.setProductId(cartItem.getProductId());
        vo.setSkuId(cartItem.getSkuId());
        vo.setQuantity(cartItem.getQuantity());
        vo.setChecked(cartItem.getChecked());
        // 以下字段默认值，后续由 enrichCartItemVOFromBatch 方法填充
        vo.setProductName("");
        vo.setProductImage("");
        vo.setSkuName("");
        vo.setSkuPrice(BigDecimal.ZERO);
        vo.setInStock(true);
        return vo;
    }
}
