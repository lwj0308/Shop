package com.shop.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.entity.Brand;
import com.shop.model.product.entity.Product;
import com.shop.model.product.vo.BrandVO;
import com.shop.product.mapper.BrandMapper;
import com.shop.product.mapper.ProductMapper;
import com.shop.product.service.BrandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 品牌服务实现类
 * <p>
 * 实现品牌的增删改查功能。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    /** 品牌Mapper，操作brand表 */
    private final BrandMapper brandMapper;

    /** 商品Mapper，用来检查品牌下是否有商品 */
    private final ProductMapper productMapper;

    /**
     * 添加品牌
     */
    @Override
    public void addBrand(BrandDTO dto) {
        Brand brand = new Brand();
        brand.setName(dto.getName());
        brand.setLogo(dto.getLogo());
        brand.setDescription(dto.getDescription());
        brandMapper.insert(brand);
        log.info("添加品牌成功: id={}, name={}", brand.getId(), brand.getName());
    }

    /**
     * 修改品牌
     */
    @Override
    public void updateBrand(Long id, BrandDTO dto) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "品牌不存在");
        }
        brand.setName(dto.getName());
        brand.setLogo(dto.getLogo());
        brand.setDescription(dto.getDescription());
        brandMapper.updateById(brand);
        log.info("修改品牌成功: id={}", id);
    }

    /**
     * 删除品牌
     * <p>
     * 删除前检查是否有商品属于这个品牌，有的话不能删。
     * </p>
     */
    @Override
    public void deleteBrand(Long id) {
        // 检查是否有商品属于这个品牌
        Long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getBrandId, id)
        );
        if (productCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该品牌下有商品，不能删除");
        }

        brandMapper.deleteById(id);
        log.info("删除品牌成功: id={}", id);
    }

    /**
     * 品牌列表
     */
    @Override
    public List<BrandVO> getBrandList() {
        List<Brand> brands = brandMapper.selectList(
                new LambdaQueryWrapper<Brand>().orderByDesc(Brand::getCreateTime)
        );
        return brands.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 品牌详情
     */
    @Override
    public BrandVO getBrandById(Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "品牌不存在");
        }
        return convertToVO(brand);
    }

    /**
     * Brand实体转BrandVO
     *
     * @param brand 品牌实体
     * @return 品牌VO
     */
    private BrandVO convertToVO(Brand brand) {
        BrandVO vo = new BrandVO();
        vo.setId(brand.getId());
        vo.setName(brand.getName());
        vo.setLogo(brand.getLogo());
        vo.setDescription(brand.getDescription());
        vo.setCreateTime(brand.getCreateTime());
        return vo;
    }
}
