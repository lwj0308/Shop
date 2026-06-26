package com.shop.merchant.service;

import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.vo.ShopVO;

/**
 * 店铺服务接口
 * <p>
 * 定义店铺相关的业务方法，包括创建店铺、更新店铺信息、查询店铺等。
 * 商家审核通过后系统会自动创建一个默认店铺，商家也可以修改店铺信息。
 * </p>
 */
public interface ShopService {

    /**
     * 创建店铺
     * <p>
     * 商家审核通过时自动调用，创建一个默认店铺。
     * 默认店铺名称为"商家名称+旗舰店"，状态为正常(1)。
     * </p>
     *
     * @param merchantId 商家ID
     * @param shopDTO    店铺信息，可以为null（使用默认值）
     * @return 创建的店铺信息
     */
    ShopVO createShop(Long merchantId, ShopDTO shopDTO);

    /**
     * 更新店铺信息
     * <p>
     * 商家修改自己店铺的名称、Logo、Banner、描述等信息。
     * 只传需要修改的字段，不传的字段保持不变。
     * </p>
     *
     * @param shopId 店铺ID
     * @param shopDTO 店铺更新参数
     */
    void updateShop(Long shopId, ShopDTO shopDTO);

    /**
     * 获取店铺信息
     * <p>
     * 根据店铺ID查询店铺详情，公开接口，任何人都能查看。
     * </p>
     *
     * @param shopId 店铺ID
     * @return 店铺信息
     */
    ShopVO getShopInfo(Long shopId);

    /**
     * 根据商家ID获取店铺
     * <p>
     * 商家登录后查看自己的店铺信息。
     * </p>
     *
     * @param merchantId 商家ID
     * @return 店铺信息，如果商家还没有店铺则返回null
     */
    ShopVO getShopByMerchantId(Long merchantId);
}
