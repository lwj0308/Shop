package com.shop.product.service;

import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.vo.CategoryVO;

import java.util.List;

/**
 * 商品分类服务接口
 * <p>
 * 定义分类相关的业务方法，包括增删改查和获取分类树。
 * 实现类在 CategoryServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface CategoryService {

    /**
     * 添加分类
     *
     * @param dto 分类参数（parentId, name, icon, sort）
     */
    void addCategory(CategoryDTO dto);

    /**
     * 修改分类
     *
     * @param id  分类ID
     * @param dto 分类参数
     */
    void updateCategory(Long id, CategoryDTO dto);

    /**
     * 删除分类
     * <p>
     * 删除前需要检查是否有子分类或商品，有的话不能删。
     * </p>
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);

    /**
     * 获取分类树
     * <p>
     * 一次性查出所有分类，在内存中构建树形结构。
     * 不要递归查数据库，那样太慢了。
     * </p>
     *
     * @return 树形分类列表
     */
    List<CategoryVO> getCategoryTree();
}
