package com.shop.product.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.model.PageResult;
import com.shop.model.product.dto.ProductSearchDTO;
import com.shop.model.product.entity.Product;
import com.shop.model.product.entity.ProductSku;
import com.shop.model.product.vo.ProductSearchVO;
import com.shop.product.mapper.BrandMapper;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.mapper.ProductSkuMapper;
import com.shop.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品搜索服务实现类
 * <p>
 * 使用Elasticsearch实现商品搜索功能，支持：
 * - 全文检索（ik分词）
 * - 分类/品牌/价格筛选
 * - 排序（价格、时间、综合排序等）
 * - 高亮（搜索关键词高亮显示）
 * - 聚合（品牌聚合、分类聚合）
 * - 搜索建议（completion suggester）
 * - 热门搜索词
 * - 全量同步（ES数据重建）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {

    /** ES客户端，用来操作Elasticsearch */
    private final ElasticsearchClient esClient;

    /** 商品Mapper，查数据库用 */
    private final ProductMapper productMapper;

    /** SKU Mapper，查SKU最低价用 */
    private final ProductSkuMapper productSkuMapper;

    /** 分类Mapper，查分类名称用 */
    private final CategoryMapper categoryMapper;

    /** 品牌Mapper，查品牌名称用 */
    private final BrandMapper brandMapper;

    /** Redis模板，用于存储热门搜索词 */
    private final StringRedisTemplate stringRedisTemplate;

    /** ES索引名称 */
    private static final String INDEX_NAME = "product";

    /** 热门搜索词Redis key */
    private static final String HOT_KEYWORDS_KEY = "product:hot_keywords";

    /** 热门搜索词最多存50个 */
    private static final int MAX_HOT_KEYWORDS = 50;

    /**
     * 搜索商品
     * <p>
     * 支持全文检索+筛选+排序+高亮+聚合。
     * 流程：
     * 1. 构建查询条件（关键词、分类、品牌、价格区间）
     * 2. 设置排序（支持综合排序：相关性+销量+价格加权）
     * 3. 设置高亮
     * 4. 设置聚合（品牌聚合、分类聚合）
     * 5. 执行搜索
     * 6. 解析结果，与数据库合并实时数据（价格、库存）
     * </p>
     */
    @Override
    public PageResult<ProductSearchVO> search(ProductSearchDTO dto) {
        try {
            // 记录搜索词到热门搜索
            if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
                recordHotKeyword(dto.getKeyword());
            }

            // 构建bool查询
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            // 只搜索上架商品
            boolBuilder.filter(f -> f.term(t -> t.field("status").value(1)));

            // 关键词搜索（在name和subtitle中搜索）
            // minimumShouldMatch=100%：要求所有分词都必须匹配，避免搜"手机"匹配到"空调机"
            if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
                boolBuilder.must(m -> m.multiMatch(mm -> mm
                        .fields("name", "subtitle")
                        .query(dto.getKeyword())
                        .minimumShouldMatch("100%")
                ));
            }

            // 分类筛选
            if (dto.getCategoryId() != null) {
                boolBuilder.filter(f -> f.term(t -> t.field("categoryId").value(dto.getCategoryId())));
            }

            // 品牌筛选
            if (dto.getBrandId() != null) {
                boolBuilder.filter(f -> f.term(t -> t.field("brandId").value(dto.getBrandId())));
            }

            // 价格区间筛选
            // ES 8.15+ 的 RangeQuery 需要指定类型（number/date/term），不能直接用 field()
            if (dto.getMinPrice() != null || dto.getMaxPrice() != null) {
                boolBuilder.filter(f -> f.range(r -> r
                        .number(n -> {
                            n.field("minPrice");
                            if (dto.getMinPrice() != null) {
                                n.gte(dto.getMinPrice().doubleValue());
                            }
                            if (dto.getMaxPrice() != null) {
                                n.lte(dto.getMaxPrice().doubleValue());
                            }
                            return n;
                        })
                ));
            }

            // 构建搜索请求
            var searchRequestBuilder = new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolBuilder.build()))
                    .from((dto.getPageNum() - 1) * dto.getPageSize())
                    .size(dto.getPageSize());

            // 设置排序
            String sortField = dto.getSortField();
            if (sortField != null && !sortField.isBlank()) {
                SortOrder sortOrder = "asc".equalsIgnoreCase(dto.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
                searchRequestBuilder.sort(s -> s.field(f -> f.field(sortField).order(sortOrder)));
            } else {
                // 默认按相关度排序
                searchRequestBuilder.sort(s -> s.field(f -> f.field("_score").order(SortOrder.Desc)));
            }

            // 设置高亮（搜索关键词用<em>标签包裹）
            searchRequestBuilder.highlight(h -> h
                    .fields("name", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")
                    ))
                    .fields("subtitle", HighlightField.of(hf -> hf
                            .preTags("<em>")
                            .postTags("</em>")
                    ))
            );

            // 设置聚合（品牌聚合 + 分类聚合）
            searchRequestBuilder.aggregations("brand_agg", Aggregation.of(a -> a
                    .terms(t -> t.field("brandId").size(20))
            ));
            searchRequestBuilder.aggregations("category_agg", Aggregation.of(a -> a
                    .terms(t -> t.field("categoryId").size(20))
            ));

            // 执行搜索
            // ES 8.x Java Client 中 SearchRequest 没有 execute() 方法，需要用 esClient.search() 执行
            SearchResponse<Map> response = esClient.search(searchRequestBuilder.build(), Map.class);

            // 解析结果
            List<ProductSearchVO> records = new ArrayList<>();
            // 收集需要合并实时数据的SKU ID列表
            List<Long> productIds = new ArrayList<>();

            for (Hit<Map> hit : response.hits().hits()) {
                ProductSearchVO vo = new ProductSearchVO();
                Map<String, Object> source = hit.source();
                if (source != null) {
                    vo.setId(toLong(source.get("id")));
                    vo.setName(toString(source.get("name")));
                    vo.setSubtitle(toString(source.get("subtitle")));
                    vo.setMainImage(toString(source.get("mainImage")));
                    vo.setMinPrice(toBigDecimal(source.get("minPrice")));
                    vo.setTotalStock(toInteger(source.get("totalStock")));
                    vo.setCategoryId(toLong(source.get("categoryId")));
                    vo.setCategoryName(toString(source.get("categoryName")));
                    vo.setBrandId(toLong(source.get("brandId")));
                    vo.setBrandName(toString(source.get("brandName")));
                    vo.setShopId(toLong(source.get("shopId")));
                    vo.setShopName(toString(source.get("shopName")));
                    productIds.add(vo.getId());
                }

                // 处理高亮 - 商品名称
                if (hit.highlight() != null && hit.highlight().containsKey("name")) {
                    List<String> highlightFragments = hit.highlight().get("name");
                    if (!highlightFragments.isEmpty()) {
                        vo.setName(highlightFragments.get(0));
                    }
                }

                // 处理高亮 - 副标题
                if (hit.highlight() != null && hit.highlight().containsKey("subtitle")) {
                    List<String> highlightFragments = hit.highlight().get("subtitle");
                    if (!highlightFragments.isEmpty()) {
                        vo.setSubtitle(highlightFragments.get(0));
                    }
                }

                records.add(vo);
            }

            // 与数据库合并实时数据（价格和库存实时性要求高）
            mergeRealtimeData(records, productIds);

            // 构建分页结果
            PageResult<ProductSearchVO> pageResult = new PageResult<>();
            pageResult.setRecords(records);
            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            pageResult.setPagination(total, dto.getPageNum(), dto.getPageSize());

            return pageResult;
        } catch (IOException e) {
            log.error("ES搜索失败", e);
            // ES搜索失败时返回空结果，不影响主流程
            return new PageResult<>();
        }
    }

    /**
     * 搜索建议
     * <p>
     * 使用ES的completion suggester实现搜索建议，
     * 用户输入时实时返回建议词。
     * </p>
     */
    @Override
    public List<String> suggest(String keyword) {
        try {
            // ES 8.15+ 的 suggest API：prefix 在 Suggester.Builder 上，不在 CompletionSuggester.Builder 上
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .suggest(su -> su
                                    .suggesters("product_suggest", sgr -> sgr
                                            .prefix(keyword)
                                            .completion(cs -> cs
                                                    .field("name.suggest")
                                                    .size(10)
                                            )
                                    )
                            ),
                    Map.class
            );

            // 解析建议结果
            var suggest = response.suggest();
            if (suggest != null && suggest.containsKey("product_suggest")) {
                return suggest.get("product_suggest").stream()
                        .flatMap(entry -> entry.completion().options().stream())
                        .map(option -> option.text())
                        .distinct()
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (IOException e) {
            log.error("ES搜索建议失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 同步商品数据到ES
     * <p>
     * 商品创建/更新/上下架时调用，保证ES中的数据和数据库一致。
     * 从数据库查出商品完整信息，写入ES索引。
     * </p>
     */
    @Override
    public void syncProductToES(Long productId) {
        try {
            Product product = productMapper.selectById(productId);
            if (product == null) {
                log.warn("同步商品到ES失败，商品不存在: productId={}", productId);
                return;
            }

            // 构建ES文档
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", product.getId());
            doc.put("name", product.getName());
            doc.put("subtitle", product.getSubtitle());
            doc.put("mainImage", product.getMainImage());
            doc.put("categoryId", product.getCategoryId());
            doc.put("brandId", product.getBrandId());
            doc.put("shopId", product.getShopId());
            doc.put("status", product.getStatus());
            // LocalDateTime需要转为字符串，否则ES客户端的Jackson序列化会报错
            doc.put("createTime", product.getCreateTime() != null ? product.getCreateTime().toString() : null);

            // 查询分类名称
            if (product.getCategoryId() != null) {
                var category = categoryMapper.selectById(product.getCategoryId());
                if (category != null) {
                    doc.put("categoryName", category.getName());
                }
            }

            // 查询品牌名称
            if (product.getBrandId() != null) {
                var brand = brandMapper.selectById(product.getBrandId());
                if (brand != null) {
                    doc.put("brandName", brand.getName());
                }
            }

            // 查询SKU最低价和总库存
            List<ProductSku> skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getProductId, productId)
            );
            if (!skus.isEmpty()) {
                doc.put("minPrice", skus.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                doc.put("totalStock", skus.stream().mapToInt(ProductSku::getStock).sum());
            } else {
                doc.put("minPrice", BigDecimal.ZERO);
                doc.put("totalStock", 0);
            }

            // 写入ES
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(productId))
                    .document(doc)
            );

            log.info("同步商品到ES成功: productId={}", productId);
        } catch (IOException e) {
            log.error("同步商品到ES失败: productId={}", productId, e);
        }
    }

    /**
     * 全量同步商品到ES
     * <p>
     * 将数据库中所有上架商品同步到ES索引，一般用于ES数据重建。
     * 数据量大时耗时较长，谨慎调用。
     * </p>
     *
     * @return 同步的商品数量
     */
    @Override
    public int syncAllToES() {
        // 查询所有上架商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)
        );

        int successCount = 0;
        for (Product product : products) {
            try {
                syncProductToES(product.getId());
                successCount++;
            } catch (Exception e) {
                log.warn("全量同步-同步商品到ES失败: productId={}", product.getId(), e);
            }
        }

        log.info("全量同步ES完成: 总数={}, 成功={}", products.size(), successCount);
        return successCount;
    }

    /**
     * 获取热门搜索词
     * <p>
     * 从Redis的有序集合中获取搜索次数最多的关键词。
     * 同时清理分数过低的词（长期未被搜索的词会被淘汰）。
     * </p>
     */
    @Override
    public List<String> getHotKeywords() {
        // 清理分数低于0.1的词（长期未被搜索的词会被淘汰）
        stringRedisTemplate.opsForZSet().removeRangeByScore(HOT_KEYWORDS_KEY, 0, 0.1);

        // 从Redis有序集合中按分数（搜索热度）降序获取
        var keywords = stringRedisTemplate.opsForZSet()
                .reverseRange(HOT_KEYWORDS_KEY, 0, MAX_HOT_KEYWORDS - 1);
        return keywords != null ? new ArrayList<>(keywords) : Collections.emptyList();
    }

    // ==================== 私有方法 ====================

    /**
     * 与数据库合并实时数据
     * <p>
     * ES中的价格和库存可能不是最新的（因为是异步同步的），
     * 所以搜索结果需要和数据库中的实时数据合并，保证价格和库存的准确性。
     * </p>
     *
     * @param records    搜索结果列表
     * @param productIds 商品ID列表
     */
    private void mergeRealtimeData(List<ProductSearchVO> records, List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        // 批量查询SKU数据，获取实时价格和库存
        for (ProductSearchVO record : records) {
            try {
                List<ProductSku> skus = productSkuMapper.selectList(
                        new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, record.getId())
                );
                if (!skus.isEmpty()) {
                    // 用数据库中的实时价格和库存覆盖ES中的数据
                    record.setMinPrice(skus.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                    record.setTotalStock(skus.stream().mapToInt(ProductSku::getStock).sum());
                }
            } catch (Exception e) {
                log.warn("合并实时数据失败: productId={}", record.getId(), e);
            }
        }
    }

    /**
     * 记录搜索词到热门搜索
     * <p>
     * 使用时间衰减算法：分数 = 原始分数 * 0.9 + 1
     * 这样旧的搜索词会逐渐降低分数，新的搜索词会更容易排到前面。
     * 长期未被搜索的词分数会趋近于0，最终被清理。
     * </p>
     *
     * @param keyword 搜索词
     */
    private void recordHotKeyword(String keyword) {
        try {
            // 先获取当前分数
            Double currentScore = stringRedisTemplate.opsForZSet().score(HOT_KEYWORDS_KEY, keyword);
            // 计算新分数：原始分数 * 0.9 + 1（时间衰减 + 计数）
            double newScore = (currentScore != null ? currentScore * 0.9 : 0) + 1;
            // 设置新分数
            stringRedisTemplate.opsForZSet().add(HOT_KEYWORDS_KEY, keyword, newScore);
        } catch (Exception e) {
            log.warn("记录热门搜索词失败: keyword={}", keyword, e);
        }
    }

    // ===== 类型转换辅助方法 =====

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }

    private String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        return new BigDecimal(obj.toString());
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
    }
}
