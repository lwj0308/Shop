package com.shop.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.common.result.Result;
import com.shop.model.product.dto.ProductCreateDTO;
import com.shop.model.product.dto.ProductSearchDTO;
import com.shop.model.product.dto.ProductUpdateDTO;
import com.shop.model.product.entity.Product;
import com.shop.model.product.entity.ProductSku;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductSearchVO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.model.product.vo.ProductVO;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.ProductSkuMapper;
import com.shop.product.service.ProductSearchService;
import com.shop.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品控制器
 * <p>
 * 处理商品的发布、编辑、上下架、详情、列表、搜索等接口。
 * 写操作（发布/编辑/上下架）需要商家登录，读操作（详情/列表/搜索）公开访问。
 * 同时提供Feign接口供订单服务远程调用（获取SKU信息、扣减/回滚库存）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@Tag(name = "商品管理", description = "商品发布、编辑、上下架、详情、搜索")
public class ProductController {

    /** 商品服务 */
    private final ProductService productService;

    /** 搜索服务 */
    private final ProductSearchService productSearchService;

    /** 商品Mapper（管理端直接查数据库，不走Service） */
    private final ProductMapper productMapper;

    /** SKU Mapper（查商品列表时需要算最低价和总库存） */
    private final ProductSkuMapper productSkuMapper;

    // ==================== 商家端接口（需要登录） ====================

    /**
     * 发布商品
     * <p>
     * 商家发布新商品，同时创建SPU+规格+SKU。需要商家登录。
     * </p>
     *
     * @param dto 商品发布参数
     * @return 商品ID
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "发布商品", description = "商家发布新商品，同时创建SPU+规格+SKU，需要商家登录")
    public Result<Long> createProduct(@Validated @RequestBody ProductCreateDTO dto) {
        // 从请求头获取店铺ID（Gateway会传递X-Shop-Id）
        Long shopId = getShopId();
        Long productId = productService.createProduct(shopId, dto);
        return Result.success(productId);
    }

    /**
     * 编辑商品
     * <p>
     * 编辑商品信息，只更新传了的字段。需要商家登录。
     * </p>
     *
     * @param id  商品ID
     * @param dto 商品编辑参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "编辑商品", description = "编辑商品信息，只更新传了的字段，需要商家登录")
    public Result<Void> updateProduct(
            @Parameter(description = "商品ID") @PathVariable Long id,
            @Validated @RequestBody ProductUpdateDTO dto) {
        // 从X-Shop-Id头获取当前商家店铺ID，传入Service做归属校验
        productService.updateProduct(id, dto, getShopId());
        return Result.success("修改成功", null);
    }

    /**
     * 上架商品
     * <p>
     * 将商品状态改为上架，用户可以搜索到。需要商家登录。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/on-shelf")
    @SaCheckLogin
    @Operation(summary = "上架商品", description = "将商品状态改为上架，用户可以搜索到，需要商家登录")
    public Result<Void> onShelf(@Parameter(description = "商品ID") @PathVariable Long id) {
        productService.onShelf(id, getShopId());
        return Result.success("上架成功", null);
    }

    /**
     * 下架商品
     * <p>
     * 将商品状态改为下架，用户搜索不到。需要商家登录。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @PutMapping("/{id}/off-shelf")
    @SaCheckLogin
    @Operation(summary = "下架商品", description = "将商品状态改为下架，用户搜索不到，需要商家登录")
    public Result<Void> offShelf(@Parameter(description = "商品ID") @PathVariable Long id) {
        productService.offShelf(id, getShopId());
        return Result.success("下架成功", null);
    }

    // ==================== C端接口（公开访问） ====================

    /**
     * 商品详情
     * <p>
     * 获取商品详情，包含SKU列表、规格列表、评价摘要。公开访问，不需要登录。
     * </p>
     *
     * @param id 商品ID
     * @return 商品详情（含SKU、规格、评价摘要）
     */
    @GetMapping("/{id}")
    @Operation(summary = "商品详情", description = "获取商品详情，包含SKU列表、规格列表、评价摘要，公开访问")
    public Result<ProductDetailVO> getProductDetail(
            @Parameter(description = "商品ID") @PathVariable Long id) {
        ProductDetailVO vo = productService.getProductDetail(id);
        // 记录用户浏览：累加浏览量 + 登录用户记录足迹（弱依赖，失败不影响商品查看）
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        productService.recordView(id, userId);
        return Result.success(vo);
    }

    /**
     * 商品列表（分页）
     * <p>
     * 获取商品列表，支持按分类筛选，分页返回。公开访问。
     * </p>
     *
     * @param categoryId  分类ID（可选）
     * @param pageRequest 分页参数
     * @return 分页商品列表
     */
    @GetMapping("/list")
    @Operation(summary = "商品列表", description = "获取商品列表，支持按分类筛选，分页返回，公开访问")
    public Result<PageResult<ProductVO>> getProductList(
            @Parameter(description = "分类ID，可选") @RequestParam(required = false) Long categoryId,
            PageRequest pageRequest) {
        return Result.success(productService.getProductList(categoryId, pageRequest));
    }

    /**
     * 搜索商品
     * <p>
     * ES全文检索，支持关键词、分类、品牌、价格区间筛选，高亮显示。公开访问。
     * </p>
     *
     * @param dto 搜索参数
     * @return 搜索结果（分页+高亮）
     */
    @GetMapping("/search")
    @Operation(summary = "搜索商品", description = "ES全文检索，支持关键词、分类、品牌、价格区间筛选，高亮显示，公开访问")
    public Result<PageResult<ProductSearchVO>> search(ProductSearchDTO dto) {
        return Result.success(productSearchService.search(dto));
    }

    /**
     * 搜索建议
     * <p>
     * 用户输入时实时返回搜索建议词。公开访问。
     * </p>
     *
     * @param keyword 输入的关键词
     * @return 建议词列表
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索建议", description = "用户输入时实时返回搜索建议词，公开访问")
    public Result<List<String>> suggest(
            @Parameter(description = "输入的关键词") @RequestParam String keyword) {
        return Result.success(productSearchService.suggest(keyword));
    }

    /**
     * 热门搜索词
     * <p>
     * 获取搜索次数最多的关键词。公开访问。
     * </p>
     *
     * @return 热门搜索词列表
     */
    @GetMapping("/hot-keywords")
    @Operation(summary = "热门搜索词", description = "获取搜索次数最多的关键词，公开访问")
    public Result<List<String>> getHotKeywords() {
        return Result.success(productSearchService.getHotKeywords());
    }

    // ==================== Feign接口（供订单服务远程调用） ====================

    /**
     * 根据SKU ID获取SKU信息（Feign接口）
     * <p>
     * 供订单服务下单时调用，获取商品的价格、名称等信息。
     * </p>
     *
     * @param skuId SKU ID
     * @return SKU信息
     */
    @GetMapping("/sku/{skuId}")
    @Operation(summary = "获取SKU信息", description = "根据SKU ID获取SKU信息，供订单服务Feign调用")
    public Result<ProductSkuVO> getSkuById(
            @Parameter(description = "SKU ID") @PathVariable Long skuId) {
        return Result.success(productService.getSkuById(skuId));
    }

    /**
     * 扣减库存（Feign接口）
     * <p>
     * 供订单服务下单成功后调用，扣减商品库存，防止超卖。
     * 使用Redis分布式锁 + Lua脚本保证并发安全。
     * 支持幂等：同一个订单号不会重复扣减。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @param orderNo  订单号（用于幂等去重）
     * @return 操作结果
     */
    @PostMapping("/sku/{skuId}/deduct")
    @Operation(summary = "扣减库存", description = "扣减SKU库存，支持幂等（基于订单号去重），供订单服务Feign调用")
    public Result<Void> deductStock(
            @Parameter(description = "SKU ID") @PathVariable Long skuId,
            @Parameter(description = "扣减数量") @RequestParam Integer quantity,
            @Parameter(description = "订单号，用于幂等去重") @RequestParam String orderNo) {
        boolean success = productService.deductStockWithIdempotent(skuId, quantity, orderNo);
        if (!success) {
            return Result.fail(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH.getCode(), "库存不足或扣减失败");
        }
        return Result.success("扣减成功", null);
    }

    /**
     * 回滚库存（Feign接口）
     * <p>
     * 供订单服务取消订单时调用，把之前扣的库存加回去。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @param orderNo  订单号（用于幂等去重）
     * @return 操作结果
     */
    @PostMapping("/sku/{skuId}/add")
    @Operation(summary = "回滚库存", description = "回滚SKU库存（取消订单时调用），供订单服务Feign调用")
    public Result<Void> addStock(
            @Parameter(description = "SKU ID") @PathVariable Long skuId,
            @Parameter(description = "回滚数量") @RequestParam Integer quantity,
            @Parameter(description = "订单号，用于幂等去重") @RequestParam String orderNo) {
        productService.addStockWithIdempotent(skuId, quantity, orderNo);
        return Result.success("回滚成功", null);
    }

    // ==================== 管理接口 ====================

    /**
     * 全量同步商品到ES（管理接口）
     * <p>
     * 将数据库中所有上架商品同步到ES索引，一般用于ES数据重建。
     * 需要管理员权限，谨慎调用，数据量大时耗时较长。
     * </p>
     *
     * @return 同步的商品数量
     */
    @PostMapping("/sync-all")
    @SaCheckLogin
    @Operation(summary = "全量同步ES", description = "将所有上架商品同步到ES，管理用，数据量大时耗时较长")
    public Result<Integer> syncAllToES() {
        int count = productSearchService.syncAllToES();
        return Result.success("同步完成", count);
    }

    /**
     * 分页查询商品列表（管理后台专用）
     * <p>
     * 管理员在后台查看所有商品，支持按分类、状态和关键词筛选。
     * 和C端列表的区别：C端只查上架商品，管理端可以查所有状态的商品。
     * </p>
     *
     * @param page       页码（从1开始）
     * @param size       每页条数
     * @param categoryId 分类ID（可选，不传就查所有分类）
     * @param status     商品状态（可选）：0下架 1上架，不传就查所有状态
     * @param keyword    搜索关键词（可选，按商品名称模糊搜索）
     * @return 分页商品列表
     */
    @GetMapping("/admin/list")
    public Result<PageResult<ProductVO>> adminListProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        // 第1步：构建分页对象，page是页码，size是每页条数
        Page<Product> pageObj = new Page<>(page, size);

        // 第2步：构建查询条件，支持按分类、状态、关键词筛选
        // eq(条件, 字段, 值) 表示"条件为true时才加这个筛选"
        // like(条件, 字段, 值) 表示"条件为true时按关键词模糊搜索"
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .like(keyword != null && !keyword.isEmpty(), Product::getName, keyword)
                .orderByDesc(Product::getCreateTime);

        // 第3步：执行分页查询，MyBatis-Plus会自动拼接SQL并分页
        Page<Product> result = productMapper.selectPage(pageObj, wrapper);

        // 第3.5步：批量查询这些商品的SKU，算出最低价和总库存（避免N+1查询）
        List<Long> productIds = result.getRecords().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        Map<Long, List<ProductSku>> skuMap = Map.of();
        if (!productIds.isEmpty()) {
            // 一次性查出所有商品的SKU，按productId分组
            List<ProductSku> allSkus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>()
                            .in(ProductSku::getProductId, productIds));
            skuMap = allSkus.stream()
                    .collect(Collectors.groupingBy(ProductSku::getProductId));
        }

        // 第4步：把查出来的实体转成VO返回给前端
        Map<Long, List<ProductSku>> finalSkuMap = skuMap;
        List<ProductVO> voList = result.getRecords().stream().map(product -> {
            ProductVO vo = new ProductVO();
            vo.setId(product.getId());
            vo.setName(product.getName());
            vo.setSubtitle(product.getSubtitle());
            vo.setMainImage(product.getMainImage());
            vo.setStatus(product.getStatus());
            vo.setCategoryId(product.getCategoryId());
            vo.setBrandId(product.getBrandId());
            vo.setShopId(product.getShopId());
            vo.setCreateTime(product.getCreateTime());

            // 从SKU列表中计算最低价和总库存
            List<ProductSku> skus = finalSkuMap.get(product.getId());
            if (skus != null && !skus.isEmpty()) {
                // minPrice = 所有SKU中最低的价格
                BigDecimal minPrice = skus.stream()
                        .map(ProductSku::getPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                vo.setMinPrice(minPrice);
                // totalStock = 所有SKU库存之和
                int totalStock = skus.stream()
                        .mapToInt(ProductSku::getStock)
                        .sum();
                vo.setTotalStock(totalStock);
            } else {
                vo.setMinPrice(BigDecimal.ZERO);
                vo.setTotalStock(0);
            }
            return vo;
        }).collect(Collectors.toList());

        // 第5步：用PageResult.from方便地构建分页结果（自动填充total、pageNum等）
        return Result.success(PageResult.from(result, voList));
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前店铺ID
     * <p>
     * 从请求头获取Gateway传递的X-Shop-Id，
     * 如果没有则从Sa-Token中获取用户ID作为兜底。
     * </p>
     *
     * @return 店铺ID
     */
    private Long getShopId() {
        // 尝试从Header获取（Gateway传递）
        jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                        .getRequest();
        String shopIdStr = request.getHeader("X-Shop-Id");
        if (shopIdStr != null && !shopIdStr.isEmpty()) {
            return Long.parseLong(shopIdStr);
        }
        // 兜底：从Sa-Token获取用户ID
        return StpUtil.getLoginIdAsLong();
    }
}
