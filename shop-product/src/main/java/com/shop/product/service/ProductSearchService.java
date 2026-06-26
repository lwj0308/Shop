package com.shop.product.service;

import com.shop.common.model.PageResult;
import com.shop.model.product.dto.ProductSearchDTO;
import com.shop.model.product.vo.ProductSearchVO;

import java.util.List;

/**
 * 商品搜索服务接口
 * <p>
 * 定义ES搜索相关的业务方法，包括搜索、搜索建议、索引同步、热门搜索词。
 * 实现类在 ProductSearchServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface ProductSearchService {

    /**
     * 搜索商品（全文检索+筛选+排序+高亮+聚合）
     *
     * @param dto 搜索参数
     * @return 搜索结果（分页+高亮）
     */
    PageResult<ProductSearchVO> search(ProductSearchDTO dto);

    /**
     * 搜索建议（ES completion suggester）
     * <p>
     * 用户在搜索框输入时，实时返回建议词，提升搜索体验。
     * </p>
     *
     * @param keyword 输入的关键词
     * @return 建议词列表
     */
    List<String> suggest(String keyword);

    /**
     * 同步商品数据到ES
     * <p>
     * 商品创建/更新/上下架时调用，保证ES中的数据和数据库一致。
     * </p>
     *
     * @param productId 商品ID
     */
    void syncProductToES(Long productId);

    /**
     * 全量同步商品到ES
     * <p>
     * 将数据库中所有上架商品同步到ES索引，一般用于ES数据重建。
     * 数据量大时耗时较长，谨慎调用。
     * </p>
     *
     * @return 同步的商品数量
     */
    int syncAllToES();

    /**
     * 获取热门搜索词
     *
     * @return 热门搜索词列表
     */
    List<String> getHotKeywords();
}
