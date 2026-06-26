package com.shop.product.service;

import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.vo.BrandVO;

import java.util.List;

/**
 * 品牌服务接口
 * <p>
 * 定义品牌相关的业务方法，包括增删改查。
 * 实现类在 BrandServiceImpl 中，具体逻辑去看那里。
 * </p>
 */
public interface BrandService {

    /**
     * 添加品牌
     *
     * @param dto 品牌参数（name, logo, description）
     */
    void addBrand(BrandDTO dto);

    /**
     * 修改品牌
     *
     * @param id  品牌ID
     * @param dto 品牌参数
     */
    void updateBrand(Long id, BrandDTO dto);

    /**
     * 删除品牌
     *
     * @param id 品牌ID
     */
    void deleteBrand(Long id);

    /**
     * 品牌列表
     *
     * @return 所有品牌列表
     */
    List<BrandVO> getBrandList();

    /**
     * 品牌详情
     *
     * @param id 品牌ID
     * @return 品牌详情
     */
    BrandVO getBrandById(Long id);
}
