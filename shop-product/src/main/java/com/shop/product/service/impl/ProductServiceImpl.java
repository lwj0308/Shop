package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.entity.*;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.feign.MerchantFeignClient;
import com.shop.product.mapper.*;
import com.shop.product.service.ProductCacheService;
import com.shop.product.service.ProductSearchService;
import com.shop.product.service.ProductService;
import com.shop.product.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 * <p>
 * 实现商品的发布、编辑、上下架、详情、列表、库存操作等核心业务逻辑。
 * 商品发布是事务操作，同时创建SPU+规格+SKU。
 * 商品详情使用Redis+Caffeine二级缓存，减少数据库压力。
 * 商品变更时通过RocketMQ异步同步ES，并执行延迟双删保证缓存一致性。
 * 库存扣减使用Redis Lua脚本保证不超卖，支持幂等。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 商品Mapper */
    private final ProductMapper productMapper;

    /** SKU Mapper */
    private final ProductSkuMapper productSkuMapper;

    /** 规格模板Mapper */
    private final ProductSpecMapper productSpecMapper;

    /** 规格值Mapper */
    private final ProductSpecValueMapper productSpecValueMapper;

    /** 商品图片Mapper */
    private final ProductImageMapper productImageMapper;

    /** 评价Mapper */
    private final ProductCommentMapper productCommentMapper;

    /** 分类Mapper */
    private final CategoryMapper categoryMapper;

    /** 品牌Mapper */
    private final BrandMapper brandMapper;

    /** 搜索服务，商品变更时同步到ES */
    private final ProductSearchService productSearchService;

    /** 缓存服务，封装商品详情的缓存逻辑 */
    private final ProductCacheService productCacheService;

    /** 库存服务，Redis Lua脚本扣减库存 */
    private final StockService stockService;

    /** RocketMQ模板，发送延迟双删消息 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 商家服务Feign客户端，查询店铺的merchantId（下单时写入订单） */
    private final MerchantFeignClient merchantFeignClient;

    /** 用户服务Feign客户端，记录浏览足迹+查足迹分类（用于猜你喜欢推荐） */
    private final com.shop.product.feign.UserFeignClient userFeignClient;

    /** ES同步MQ主题 */
    private static final String TOPIC_PRODUCT_SYNC = "topic_product_sync";

    /**
     * 发布商品
     * <p>
     * 同时创建SPU+规格+SKU，需要事务保证数据一致性。
     * 流程：
     * 1. 创建商品SPU
     * 2. 创建规格模板和规格值
     * 3. 创建SKU，同时初始化库存到Redis
     * 4. 通过MQ异步同步到ES
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(Long shopId, ProductCreateDTO dto) {
        // 1. 创建商品SPU
        Product product = new Product();
        product.setCategoryId(dto.getCategoryId());
        product.setBrandId(dto.getBrandId());
        product.setShopId(shopId);
        product.setName(dto.getName());
        product.setSubtitle(dto.getSubtitle());
        product.setMainImage(dto.getMainImage());
        product.setImages(dto.getImages());
        product.setDetail(dto.getDetail());
        product.setStatus(0); // 默认下架状态，需要手动上架
        productMapper.insert(product);

        Long productId = product.getId();

        // 2. 创建规格模板和规格值
        if (dto.getSpecs() != null && !dto.getSpecs().isEmpty()) {
            for (ProductCreateDTO.SpecDTO specDTO : dto.getSpecs()) {
                // 创建规格模板（如"颜色"）
                ProductSpec spec = new ProductSpec();
                spec.setProductId(productId);
                spec.setName(specDTO.getName());
                productSpecMapper.insert(spec);

                // 创建规格值（如"红色"、"蓝色"）
                if (specDTO.getValues() != null) {
                    for (String value : specDTO.getValues()) {
                        ProductSpecValue specValue = new ProductSpecValue();
                        specValue.setSpecId(spec.getId());
                        specValue.setValue(value);
                        productSpecValueMapper.insert(specValue);
                    }
                }
            }
        }

        // 3. 创建SKU，同时初始化库存到Redis
        if (dto.getSkus() != null && !dto.getSkus().isEmpty()) {
            for (ProductCreateDTO.SkuDTO skuDTO : dto.getSkus()) {
                ProductSku sku = new ProductSku();
                sku.setProductId(productId);
                sku.setSpecValues(skuDTO.getSpecValues());
                sku.setPrice(skuDTO.getPrice());
                sku.setOriginalPrice(skuDTO.getOriginalPrice());
                sku.setStock(skuDTO.getStock());
                sku.setImage(skuDTO.getImage());
                sku.setVersion(0); // 初始版本号
                sku.setStatus(1); // 默认启用
                productSkuMapper.insert(sku);

                // 初始化SKU库存到Redis，方便后续用Lua脚本扣减
                stockService.initStock(sku.getId(), skuDTO.getStock());
            }
        }

        // 4. 异步同步到ES（通过RocketMQ）
        sendSyncMessage(productId);

        log.info("发布商品成功: id={}, name={}", productId, product.getName());
        return productId;
    }

    /**
     * 编辑商品
     * <p>
     * 只更新传了的字段，没传的保持不变。
     * 如果传了specs或skus，会先删除旧的再创建新的。
     * 更新后执行延迟双删保证缓存一致性。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long productId, ProductUpdateDTO dto, Long shopId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 归属校验：只能编辑自己店铺的商品
        // 小白讲解：防止商家A通过修改URL中的商品ID，去编辑商家B的商品
        if (shopId != null && !shopId.equals(product.getShopId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该商品");
        }

        // 更新SPU字段
        if (dto.getCategoryId() != null) {
            product.setCategoryId(dto.getCategoryId());
        }
        if (dto.getBrandId() != null) {
            product.setBrandId(dto.getBrandId());
        }
        if (dto.getName() != null) {
            product.setName(dto.getName());
        }
        if (dto.getSubtitle() != null) {
            product.setSubtitle(dto.getSubtitle());
        }
        if (dto.getMainImage() != null) {
            product.setMainImage(dto.getMainImage());
        }
        if (dto.getImages() != null) {
            product.setImages(dto.getImages());
        }
        if (dto.getDetail() != null) {
            product.setDetail(dto.getDetail());
        }
        productMapper.updateById(product);

        // 如果传了规格，先删旧规格再创建新规格
        if (dto.getSpecs() != null) {
            // 删除旧规格值
            List<ProductSpec> oldSpecs = productSpecMapper.selectList(
                    new LambdaQueryWrapper<ProductSpec>().eq(ProductSpec::getProductId, productId)
            );
            for (ProductSpec oldSpec : oldSpecs) {
                productSpecValueMapper.delete(
                        new LambdaQueryWrapper<ProductSpecValue>().eq(ProductSpecValue::getSpecId, oldSpec.getId())
                );
            }
            // 删除旧规格模板
            productSpecMapper.delete(
                    new LambdaQueryWrapper<ProductSpec>().eq(ProductSpec::getProductId, productId)
            );

            // 创建新规格
            for (ProductCreateDTO.SpecDTO specDTO : dto.getSpecs()) {
                ProductSpec spec = new ProductSpec();
                spec.setProductId(productId);
                spec.setName(specDTO.getName());
                productSpecMapper.insert(spec);

                if (specDTO.getValues() != null) {
                    for (String value : specDTO.getValues()) {
                        ProductSpecValue specValue = new ProductSpecValue();
                        specValue.setSpecId(spec.getId());
                        specValue.setValue(value);
                        productSpecValueMapper.insert(specValue);
                    }
                }
            }
        }

        // 如果传了SKU，先删旧SKU再创建新SKU
        if (dto.getSkus() != null) {
            productSkuMapper.delete(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, productId)
            );

            for (ProductCreateDTO.SkuDTO skuDTO : dto.getSkus()) {
                ProductSku sku = new ProductSku();
                sku.setProductId(productId);
                sku.setSpecValues(skuDTO.getSpecValues());
                sku.setPrice(skuDTO.getPrice());
                sku.setOriginalPrice(skuDTO.getOriginalPrice());
                sku.setStock(skuDTO.getStock());
                sku.setImage(skuDTO.getImage());
                sku.setVersion(0);
                sku.setStatus(1);
                productSkuMapper.insert(sku);

                // 初始化新SKU库存到Redis
                stockService.initStock(sku.getId(), skuDTO.getStock());
            }
        }

        // 延迟双删缓存（先删一次，通过MQ延迟再删一次）
        productCacheService.delayDoubleEvict(productId);

        // 异步同步到ES
        sendSyncMessage(productId);

        log.info("编辑商品成功: id={}", productId);
    }

    /**
     * 上架商品
     */
    @Override
    public void onShelf(Long productId, Long shopId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // 归属校验：只能上架自己店铺的商品
        if (shopId != null && !shopId.equals(product.getShopId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该商品");
        }
        productMapper.updateStatus(productId, 1);

        // 延迟双删缓存
        productCacheService.delayDoubleEvict(productId);

        // 异步同步到ES
        sendSyncMessage(productId);

        log.info("商品上架成功: id={}", productId);
    }

    /**
     * 下架商品
     */
    @Override
    public void offShelf(Long productId, Long shopId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        // 归属校验：只能下架自己店铺的商品
        if (shopId != null && !shopId.equals(product.getShopId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该商品");
        }
        productMapper.updateStatus(productId, 0);

        // 延迟双删缓存
        productCacheService.delayDoubleEvict(productId);

        // 异步同步到ES（下架后ES中也不可搜索）
        sendSyncMessage(productId);

        log.info("商品下架成功: id={}", productId);
    }

    /**
     * 商品详情（含SKU、规格、评价摘要）
     * <p>
     * 优先从缓存获取，缓存没有再查数据库。
     * 使用Redis + Caffeine二级缓存，大部分请求在本地缓存就能命中。
     * </p>
     */
    @Override
    public ProductDetailVO getProductDetail(Long productId) {
        // 优先从缓存获取
        return productCacheService.getProductDetailFromCache(productId);
    }

    /**
     * 商品列表（分页）
     * <p>
     * 如果传了categoryId，会递归查出该分类及其所有子分类下的商品。
     * 比如点击"手机"父分类，能查到挂在"智能手机"子分类下的商品。
     * </p>
     */
    @Override
    public PageResult<ProductVO> getProductList(Long categoryId, PageRequest pageRequest) {
        Page<Product> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            // 收集该分类及其所有子分类的ID，用IN查询而不是精确匹配
            List<Long> allCategoryIds = collectCategoryIdsWithChildren(categoryId);
            if (allCategoryIds.size() == 1) {
                // 没有子分类，直接用eq
                wrapper.eq(Product::getCategoryId, categoryId);
            } else {
                // 有子分类，用IN查询包含所有子分类
                wrapper.in(Product::getCategoryId, allCategoryIds);
            }
        }
        wrapper.eq(Product::getStatus, 1); // 只查上架商品
        wrapper.orderByDesc(Product::getCreateTime);

        Page<Product> result = productMapper.selectPage(page, wrapper);

        // 转换为VO
        List<ProductVO> voList = result.getRecords().stream().map(product -> {
            ProductVO vo = new ProductVO();
            vo.setId(product.getId());
            vo.setCategoryId(product.getCategoryId());
            vo.setBrandId(product.getBrandId());
            vo.setShopId(product.getShopId());
            vo.setName(product.getName());
            vo.setSubtitle(product.getSubtitle());
            vo.setMainImage(product.getMainImage());
            vo.setImages(product.getImages());
            vo.setStatus(product.getStatus());
            vo.setCreateTime(product.getCreateTime());

            // 查询分类名称
            Category cat = categoryMapper.selectById(product.getCategoryId());
            if (cat != null) {
                vo.setCategoryName(cat.getName());
            }

            // 查询品牌名称
            if (product.getBrandId() != null) {
                Brand brand = brandMapper.selectById(product.getBrandId());
                if (brand != null) {
                    vo.setBrandName(brand.getName());
                }
            }

            // 查询SKU最低价和总库存
            List<ProductSku> skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, product.getId())
            );
            if (!skus.isEmpty()) {
                vo.setMinPrice(skus.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                vo.setTotalStock(skus.stream().mapToInt(ProductSku::getStock).sum());
            }

            return vo;
        }).collect(Collectors.toList());

        PageResult<ProductVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setPagination(result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
        return pageResult;
    }

    /**
     * 递归收集某个分类及其所有子分类的ID
     * <p>
     * 小白理解：比如分类树是 手机 -> 智能手机 -> 5G手机，
     * 传入"手机"的ID，返回 [手机ID, 智能手机ID, 5G手机ID]。
     * 这样查商品时用 IN(...) 就能把子分类下的商品也查出来。
     * </p>
     *
     * @param rootId 要查找的根分类ID
     * @return 包含根分类及所有子孙分类的ID列表
     */
    private List<Long> collectCategoryIdsWithChildren(Long rootId) {
        // 先把根分类自己放进去
        List<Long> result = new ArrayList<>();
        result.add(rootId);

        // 一次性查出所有分类，在内存中找子分类，避免递归查数据库（N+1问题）
        List<Category> allCategories = categoryMapper.selectList(null);
        if (allCategories.isEmpty()) {
            return result;
        }

        // 按parentId分组，key是父分类ID，value是该父分类下的所有子分类
        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        // 递归收集所有子分类ID
        collectChildIds(rootId, parentMap, result);

        return result;
    }

    /**
     * 递归收集子分类ID（内部辅助方法）
     *
     * @param parentId  父分类ID
     * @param parentMap 分类按parentId分组的Map
     * @param result    收集结果的列表
     */
    private void collectChildIds(Long parentId, Map<Long, List<Category>> parentMap, List<Long> result) {
        List<Category> children = parentMap.get(parentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Category child : children) {
            result.add(child.getId());
            // 递归找子分类的子分类
            collectChildIds(child.getId(), parentMap, result);
        }
    }

    /**
     * 扣减库存（乐观锁，兼容旧接口）
     * <p>
     * 使用乐观锁防止超卖：两个人同时买最后一件商品，只有一个人能成功。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long skuId, Integer quantity) {
        // 先查出SKU，获取当前version
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "SKU不存在");
        }

        // 乐观锁扣减库存
        int rows = productSkuMapper.deductStock(skuId, quantity, sku.getVersion());
        if (rows == 0) {
            log.warn("扣减库存失败（乐观锁冲突或库存不足）: skuId={}, quantity={}", skuId, quantity);
            return false;
        }

        log.info("扣减库存成功: skuId={}, quantity={}", skuId, quantity);
        return true;
    }

    /**
     * 回滚库存（取消订单时调用，兼容旧接口）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addStock(Long skuId, Integer quantity) {
        int rows = productSkuMapper.addStock(skuId, quantity);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "SKU不存在");
        }
        log.info("回滚库存成功: skuId={}, quantity={}", skuId, quantity);
    }

    /**
     * 扣减库存（带幂等，Redis Lua脚本）
     * <p>
     * 先在Redis中用Lua脚本原子扣减库存，成功后再异步更新数据库。
     * 同一个订单号不会重复扣减，保证幂等性。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @param orderNo  订单号（用于幂等去重）
     * @return 是否成功
     */
    @Override
    public boolean deductStockWithIdempotent(Long skuId, Integer quantity, String orderNo) {
        // 1. 先在Redis中扣减库存（Lua脚本原子操作）
        boolean redisResult = stockService.deductStock(skuId, quantity, orderNo);
        if (!redisResult) {
            return false;
        }

        // 2. Redis扣减成功后，异步更新数据库库存
        // 这里用数据库乐观锁兜底，保证数据库库存最终一致
        try {
            boolean dbResult = deductStock(skuId, quantity);
            if (!dbResult) {
                // 数据库扣减失败，需要回滚Redis库存
                log.warn("数据库扣减库存失败，回滚Redis库存: skuId={}, orderNo={}", skuId, orderNo);
                stockService.addStock(skuId, quantity, "rollback_" + orderNo);
                return false;
            }
        } catch (Exception e) {
            // 数据库异常，回滚Redis库存
            log.error("数据库扣减库存异常，回滚Redis库存: skuId={}, orderNo={}", skuId, orderNo, e);
            stockService.addStock(skuId, quantity, "rollback_" + orderNo);
            return false;
        }

        return true;
    }

    /**
     * 回滚库存（带幂等）
     * <p>
     * 先在Redis中回滚库存，再更新数据库库存。
     * 同一个订单号不会重复回滚，保证幂等性。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @param orderNo  订单号（用于幂等去重）
     */
    @Override
    public void addStockWithIdempotent(Long skuId, Integer quantity, String orderNo) {
        // 1. 先在Redis中回滚库存
        stockService.addStock(skuId, quantity, orderNo);

        // 2. 再更新数据库库存
        try {
            addStock(skuId, quantity);
        } catch (Exception e) {
            log.error("数据库回滚库存异常: skuId={}, orderNo={}", skuId, orderNo, e);
            // 数据库回滚失败不影响Redis回滚，后续可以通过对账修复
        }
    }

    /**
     * 根据SKU ID获取SKU信息（供Feign调用）
     *
     * @param skuId SKU ID
     * @return SKU信息
     */
    @Override
    public ProductSkuVO getSkuById(Long skuId) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "SKU不存在");
        }
        return convertSkuToVO(sku);
    }

    // ==================== 推荐相关方法 ====================

    /**
     * 热销推荐（按销量降序）
     * <p>
     * 查询销量最高的上架商品，用于首页"热销推荐"区域。
     * 小白理解：就是按 sales 字段从高到低排，取前 N 个。
     * </p>
     */
    @Override
    public List<ProductVO> getHotProducts(int limit) {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)       // 只查上架商品
                        .orderByDesc(Product::getSales)  // 按销量降序
                        .last("LIMIT " + limit)          // 只取前N个
        );
        return convertToVOList(products);
    }

    /**
     * 新品推荐（按创建时间降序）
     * <p>
     * 查询最新上架的商品，用于首页"新品推荐"区域。
     * 小白理解：就是按 create_time 从新到旧排，取前 N 个。
     * </p>
     */
    @Override
    public List<ProductVO> getNewProducts(int limit) {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)            // 只查上架商品
                        .orderByDesc(Product::getCreateTime)  // 按创建时间降序
                        .last("LIMIT " + limit)
        );
        return convertToVOList(products);
    }

    /**
     * 相关推荐（同分类排除当前商品，按销量降序）
     * <p>
     * 查询与当前商品同分类的其他商品，用于商品详情页"看了又看"区域。
     * 小白理解：比如用户在看iPhone，就推荐同分类（手机）下其他卖得好的商品。
     * </p>
     */
    @Override
    public List<ProductVO> getRelatedProducts(Long productId, int limit) {
        // 先查出当前商品的分类
        Product current = productMapper.selectById(productId);
        if (current == null) {
            return Collections.emptyList();
        }
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)                // 只查上架商品
                        .eq(Product::getCategoryId, current.getCategoryId())  // 同分类
                        .ne(Product::getId, productId)            // 排除当前商品
                        .orderByDesc(Product::getSales)          // 按销量降序
                        .last("LIMIT " + limit)
        );
        return convertToVOList(products);
    }

    /**
     * 猜你喜欢（基于足迹分类查热销，无足迹降级为全站热销）
     * <p>
     * 推荐逻辑：
     * 1. 先通过Feign查用户浏览过的商品分类
     * 2. 如果有足迹分类，在这些分类下查热销商品
     * 3. 如果没有足迹（未登录或没浏览记录），降级为全站热销推荐
     * 小白理解：根据你之前看过的商品类型，推荐同类型的热门商品；
     * 如果你啥都没看过，就推荐全站最火的。
     * </p>
     */
    @Override
    public List<ProductVO> getGuessProducts(Long userId, int limit) {
        // 未登录用户直接降级为全站热销
        if (userId == null) {
            return getHotProducts(limit);
        }

        // 通过Feign查用户浏览过的商品分类
        List<Long> categoryIds = Collections.emptyList();
        try {
            Result<List<Long>> result = userFeignClient.getFootprintCategories(userId);
            if (result != null && result.getData() != null) {
                categoryIds = result.getData();
            }
        } catch (Exception e) {
            log.warn("查询用户足迹分类失败，降级为全站热销: userId={}", userId, e);
        }

        // 没有足迹分类，降级为全站热销
        if (categoryIds.isEmpty()) {
            return getHotProducts(limit);
        }

        // 在用户浏览过的分类下查热销商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .in(Product::getCategoryId, categoryIds)
                        .orderByDesc(Product::getSales)
                        .last("LIMIT " + limit)
        );
        return convertToVOList(products);
    }

    /**
     * 记录用户浏览（累加浏览量 + 记录足迹）
     * <p>
     * 用户查看商品详情时调用：
     * - 累加商品的浏览量（本地操作，必定执行）
     * - 如果用户已登录，通过Feign调用shop-user记录足迹（弱依赖，失败只记日志不抛异常）
     * 小白理解：每次有人看商品，浏览量+1；如果是登录用户，还要记一笔"他看过这个商品"。
     * </p>
     */
    @Override
    public void recordView(Long productId, Long userId) {
        // 1. 累加浏览量（本地操作，不影响主流程）
        try {
            productMapper.incrViewCount(productId);
        } catch (Exception e) {
            log.warn("累加浏览量失败，不影响商品查看: productId={}", productId, e);
        }

        // 2. 登录用户记录足迹（Feign远程调用，弱依赖）
        if (userId != null) {
            try {
                // 查商品的分类ID（记录到足迹表，用于猜你喜欢按分类推荐）
                Product product = productMapper.selectById(productId);
                if (product != null) {
                    userFeignClient.recordFootprint(userId, productId, product.getCategoryId());
                }
            } catch (Exception e) {
                log.warn("记录浏览足迹失败，不影响商品查看: userId={}, productId={}", userId, productId, e);
            }
        }
    }

    /**
     * 销量累加（下单成功后调用）
     * <p>
     * 按购买数量累加商品销量，用于热销推荐排序。
     * 小白理解：用户买了2件，销量就+2。
     * </p>
     */
    @Override
    public void incrSales(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            return;
        }
        productMapper.incrSales(productId, quantity);
    }

    /**
     * 批量获取SKU信息（供 Feign 调用）
     * <p>
     * 使用 MyBatis-Plus 的 selectBatchIds 一次 IN 查询，避免 N+1。
     * 小白理解：原来循环 N 次查 1 条 SQL，现在 1 条 SQL 查 N 条。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU 信息列表
     */
    @Override
    public List<ProductSkuVO> batchGetSkuByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 用 IN 一次查出全部 SKU
        List<ProductSku> skuList = productSkuMapper.selectBatchIds(skuIds);
        return skuList.stream().map(this::convertSkuToVO).collect(Collectors.toList());
    }

    /**
     * 批量扣减库存（带补偿回退，供 Feign 调用）
     * <p>
     * 内部循环调用 deductStockWithIdempotent（每个 SKU 仍然走 Redis Lua 原子扣减），
     * 如果某个 SKU 扣减失败，回滚已扣减的 SKU，保证最终一致性。
     * 把补偿回退逻辑放在 shop-product 内部，shop-order 只需一次 Feign 调用即可。
     * 小白理解：原来 shop-order 要给每个 SKU 打电话扣库存，
     * 现在打包一次性扣完，出问题在 shop-product 内部就回退好。
     * </p>
     *
     * @param items   扣减项列表
     * @param orderNo 订单号（用于幂等去重）
     * @return 是否全部成功
     */
    @Override
    public boolean batchDeductStock(List<StockDeductItemDTO> items, String orderNo) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        // 记录已扣减成功的 SKU，失败时回退
        List<StockDeductItemDTO> deductedItems = new ArrayList<>();
        try {
            for (StockDeductItemDTO item : items) {
                boolean success = deductStockWithIdempotent(item.getSkuId(), item.getQuantity(), orderNo);
                if (!success) {
                    throw new BusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(),
                            "SKU 库存不足: " + item.getSkuId());
                }
                deductedItems.add(item);
            }
            return true;
        } catch (Exception e) {
            // 补偿回退已扣减的库存
            log.warn("批量扣减库存失败，开始回滚已扣减 SKU: orderNo={}", orderNo, e);
            for (StockDeductItemDTO item : deductedItems) {
                try {
                    addStockWithIdempotent(item.getSkuId(), item.getQuantity(), orderNo);
                } catch (Exception ex) {
                    log.error("补偿回退库存失败，需定时任务对账: skuId={}, quantity={}",
                            item.getSkuId(), item.getQuantity(), ex);
                }
            }
            return false;
        }
    }

    /**
     * 批量销量累加（下单成功后调用，供 Feign 调用）
     * <p>
     * 内部循环调用 incrSales，避免 shop-order 循环 N 次 Feign 调用。
     * 小白理解：原来 shop-order 要给每个商品打电话累加销量，
     * 现在打包一次性累加完。
     * </p>
     *
     * @param productQuantities key=商品ID， value=累加数量
     */
    @Override
    public void incrSalesBatch(Map<Long, Integer> productQuantities) {
        if (productQuantities == null || productQuantities.isEmpty()) {
            return;
        }
        productQuantities.forEach(this::incrSales);
    }

    /**
     * 批量将Product实体列表转为ProductVO列表
     * <p>
     * 批量查询分类名、品牌名、SKU最低价和总库存，避免N+1查询问题。
     * 小白理解：一次性把所有商品的相关信息查出来，而不是一个一个查，提高效率。
     * </p>
     *
     * @param products 商品实体列表
     * @return 商品VO列表
     */
    private List<ProductVO> convertToVOList(List<Product> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有商品ID、分类ID、品牌ID，用于批量查询
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        List<Long> categoryIds = products.stream().map(Product::getCategoryId).distinct().collect(Collectors.toList());
        List<Long> brandIds = products.stream().map(Product::getBrandId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 批量查分类名
        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
            categoryNameMap = categories.stream().collect(Collectors.toMap(Category::getId, Category::getName));
        }

        // 批量查品牌名
        Map<Long, String> brandNameMap = new HashMap<>();
        if (!brandIds.isEmpty()) {
            List<Brand> brands = brandMapper.selectBatchIds(brandIds);
            brandNameMap = brands.stream().collect(Collectors.toMap(Brand::getId, Brand::getName));
        }

        // 批量查SKU（算最低价和总库存）
        List<ProductSku> allSkus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().in(ProductSku::getProductId, productIds));
        Map<Long, List<ProductSku>> skuMap = allSkus.stream()
                .collect(Collectors.groupingBy(ProductSku::getProductId));

        // 转换为VO
        Map<Long, String> finalCategoryNameMap = categoryNameMap;
        Map<Long, String> finalBrandNameMap = brandNameMap;
        return products.stream().map(product -> {
            ProductVO vo = new ProductVO();
            vo.setId(product.getId());
            vo.setCategoryId(product.getCategoryId());
            vo.setBrandId(product.getBrandId());
            vo.setShopId(product.getShopId());
            vo.setName(product.getName());
            vo.setSubtitle(product.getSubtitle());
            vo.setMainImage(product.getMainImage());
            vo.setImages(product.getImages());
            vo.setStatus(product.getStatus());
            vo.setSales(product.getSales());
            vo.setViewCount(product.getViewCount());
            vo.setCreateTime(product.getCreateTime());
            vo.setCategoryName(finalCategoryNameMap.get(product.getCategoryId()));
            if (product.getBrandId() != null) {
                vo.setBrandName(finalBrandNameMap.get(product.getBrandId()));
            }
            // 算最低价和总库存
            List<ProductSku> skus = skuMap.get(product.getId());
            if (skus != null && !skus.isEmpty()) {
                vo.setMinPrice(skus.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                vo.setTotalStock(skus.stream().mapToInt(ProductSku::getStock).sum());
            } else {
                vo.setMinPrice(BigDecimal.ZERO);
                vo.setTotalStock(0);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 发送ES同步消息到RocketMQ
     * <p>
     * 通过MQ异步同步ES，商品主流程不需要等ES同步完成。
     * 如果发送失败，记录日志但不影响主流程。
     * </p>
     *
     * @param productId 商品ID
     */
    private void sendSyncMessage(Long productId) {
        try {
            rocketMQTemplate.convertAndSend(TOPIC_PRODUCT_SYNC, String.valueOf(productId));
        } catch (Exception e) {
            log.warn("发送ES同步消息失败，不影响主流程: productId={}", productId, e);
        }
    }

    /**
     * ProductSku实体转ProductSkuVO
     * <p>
     * 转换过程中会通过Feign远程查询商家ID（merchantId）：
     * 1. 先用productId查出商品SPU，拿到shopId（店铺ID）
     * 2. 再用shopId调用商家服务，拿到店铺归属的merchantId
     * 3. 把merchantId塞进VO，下单时订单服务会读取这个值写入订单
     * </p>
     * <p>
     * 查询失败时merchantId保持为null，不阻塞商品查询主流程
     * （下单时merchantId为null比硬编码为0更安全，便于后续排查）。
     * </p>
     *
     * @param sku SKU实体
     * @return SKU VO
     */
    private ProductSkuVO convertSkuToVO(ProductSku sku) {
        ProductSkuVO vo = new ProductSkuVO();
        vo.setId(sku.getId());
        vo.setProductId(sku.getProductId());
        vo.setSpecValues(sku.getSpecValues());
        vo.setPrice(sku.getPrice());
        vo.setOriginalPrice(sku.getOriginalPrice());
        vo.setStock(sku.getStock());
        vo.setImage(sku.getImage());
        vo.setStatus(sku.getStatus());
        // 查询并设置merchantId，失败时不阻塞主流程
        vo.setMerchantId(queryMerchantIdByProductId(sku.getProductId()));
        return vo;
    }

    /**
     * 根据商品ID查询商家ID
     * <p>
     * 调用链：productId → Product表查shopId → Feign调用商家服务查ShopVO → 取merchantId。
     * 整个过程用try-catch包裹，任何一步失败都只记日志返回null，
     * 保证商品查询和下单主流程不被商家服务故障阻塞。
     * </p>
     *
     * @param productId 商品ID
     * @return 商家ID，查询失败返回null
     */
    private Long queryMerchantIdByProductId(Long productId) {
        try {
            // 1. 先查出商品SPU，拿到店铺ID
            Product product = productMapper.selectById(productId);
            if (product == null || product.getShopId() == null) {
                log.warn("查询merchantId失败：商品或店铺ID为空, productId={}", productId);
                return null;
            }

            // 2. 通过Feign调用商家服务，查询店铺信息
            Result<ShopVO> shopResult = merchantFeignClient.getShopById(product.getShopId());
            if (shopResult == null || !shopResult.isSuccess() || shopResult.getData() == null) {
                log.warn("查询merchantId失败：店铺信息为空, shopId={}", product.getShopId());
                return null;
            }

            // 3. 返回店铺归属的商家ID
            return shopResult.getData().getMerchantId();
        } catch (Exception e) {
            // Feign调用失败（服务挂了、超时等）只记日志，不抛异常
            log.warn("查询merchantId异常，不影响主流程: productId={}", productId, e);
            return null;
        }
    }
}
