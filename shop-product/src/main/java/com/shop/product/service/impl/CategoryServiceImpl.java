package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.entity.Category;
import com.shop.model.product.entity.Product;
import com.shop.model.product.vo.CategoryVO;
import com.shop.product.mapper.CategoryMapper;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现类
 * <p>
 * 实现分类的增删改查和分类树构建。
 * 分类树是一次性查出所有分类，在内存中构建父子关系，不要递归查数据库。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 分类Mapper，操作category表 */
    private final CategoryMapper categoryMapper;

    /** 商品Mapper，用来检查分类下是否有商品 */
    private final ProductMapper productMapper;

    /**
     * 添加分类
     * <p>
     * 把DTO转成实体类，然后插入数据库。
     * </p>
     */
    @Override
    public void addCategory(CategoryDTO dto) {
        Category category = new Category();
        category.setParentId(dto.getParentId());
        category.setName(dto.getName());
        category.setIcon(dto.getIcon());
        category.setSort(dto.getSort() != null ? dto.getSort() : 0);
        category.setStatus(1); // 默认启用
        categoryMapper.insert(category);
        log.info("添加分类成功: id={}, name={}", category.getId(), category.getName());
    }

    /**
     * 修改分类
     * <p>
     * 先检查分类是否存在，再更新字段。
     * </p>
     */
    @Override
    public void updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "分类不存在");
        }
        category.setParentId(dto.getParentId());
        category.setName(dto.getName());
        category.setIcon(dto.getIcon());
        category.setSort(dto.getSort());
        categoryMapper.updateById(category);
        log.info("修改分类成功: id={}", id);
    }

    /**
     * 删除分类
     * <p>
     * 删除前需要检查：
     * 1. 是否有子分类，有的话不能删
     * 2. 是否有商品属于这个分类，有的话也不能删
     * 防止删了分类后数据变成孤儿。
     * </p>
     */
    @Override
    public void deleteCategory(Long id) {
        // 检查是否有子分类
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该分类下有子分类，不能删除");
        }

        // 检查是否有商品属于这个分类
        Long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, id)
        );
        if (productCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该分类下有商品，不能删除");
        }

        categoryMapper.deleteById(id);
        log.info("删除分类成功: id={}", id);
    }

    /**
     * 获取分类树
     * <p>
     * 一次性查出所有分类，然后在内存中构建树形结构。
     * 不要递归查数据库，那样太慢了（N+1查询问题）。
     * 构建思路：
     * 1. 查出所有分类
     * 2. 转成VO列表
     * 3. 按parentId分组
     * 4. 找到所有顶级分类（parentId=0），递归设置children
     * </p>
     */
    @Override
    public List<CategoryVO> getCategoryTree() {
        // 1. 查出所有分类
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort)
        );

        // 2. 转成VO列表
        List<CategoryVO> voList = categories.stream().map(this::convertToVO).collect(Collectors.toList());

        // 3. 按parentId分组，key是父分类ID，value是该父分类下的所有子分类
        Map<Long, List<CategoryVO>> parentMap = voList.stream()
                .collect(Collectors.groupingBy(CategoryVO::getParentId));

        // 4. 给每个分类设置children
        voList.forEach(vo -> vo.setChildren(parentMap.get(vo.getId())));

        // 5. 返回顶级分类（parentId=0）
        return parentMap.getOrDefault(0L, new ArrayList<>());
    }

    /**
     * Category实体转CategoryVO
     *
     * @param category 分类实体
     * @return 分类VO
     */
    private CategoryVO convertToVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setName(category.getName());
        vo.setIcon(category.getIcon());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setCreateTime(category.getCreateTime());
        return vo;
    }
}
