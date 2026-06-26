package com.shop.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.merchant.mapper.ShopMapper;
import com.shop.merchant.service.ShopService;
import com.shop.model.merchant.dto.ShopDTO;
import com.shop.model.merchant.entity.Shop;
import com.shop.model.merchant.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 店铺服务实现类
 * <p>
 * 实现店铺创建、更新、查询等业务逻辑。
 * 商家审核通过后系统自动创建默认店铺，商家也可以修改店铺信息。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    /** 店铺信息Mapper，操作shop表 */
    private final ShopMapper shopMapper;

    /**
     * 创建店铺
     * <p>
     * 商家审核通过时自动调用，创建一个默认店铺。
     * 如果shopDTO为null，则使用默认值（店铺名称为"商家名称+旗舰店"）。
     * 创建前会检查店铺名称是否重复。
     * </p>
     */
    @Override
    public ShopVO createShop(Long merchantId, ShopDTO shopDTO) {
        // 如果传了店铺名称，检查名称是否重复
        if (shopDTO != null && shopDTO.getName() != null) {
            checkShopNameUnique(shopDTO.getName(), null);
        }

        Shop shop = new Shop();
        shop.setMerchantId(merchantId);
        shop.setStatus(1); // 正常状态

        // 如果传了店铺信息就用传的，否则用默认值
        if (shopDTO != null) {
            shop.setName(shopDTO.getName());
            shop.setLogo(shopDTO.getLogo());
            shop.setBanner(shopDTO.getBanner());
            shop.setDescription(shopDTO.getDescription());
        }

        shopMapper.insert(shop);
        log.info("店铺创建成功，店铺ID：{}，商家ID：{}", shop.getId(), merchantId);

        return convertToVO(shop);
    }

    /**
     * 更新店铺信息
     * <p>
     * 只更新非空字段，不传的字段保持不变。
     * 更新前会检查店铺状态（关闭的店铺不能修改）和名称唯一性。
     * </p>
     */
    @Override
    public void updateShop(Long shopId, ShopDTO shopDTO) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }

        // 关闭的店铺不能修改信息
        if (shop.getStatus() != null && shop.getStatus() == 0) {
            throw new BusinessException(ErrorCode.SHOP_CLOSED);
        }

        // 如果修改了店铺名称，检查名称是否重复
        if (shopDTO.getName() != null && !shopDTO.getName().equals(shop.getName())) {
            checkShopNameUnique(shopDTO.getName(), shopId);
        }

        // 只更新非空字段，避免把已有数据覆盖为null
        if (shopDTO.getName() != null) {
            shop.setName(shopDTO.getName());
        }
        if (shopDTO.getLogo() != null) {
            shop.setLogo(shopDTO.getLogo());
        }
        if (shopDTO.getBanner() != null) {
            shop.setBanner(shopDTO.getBanner());
        }
        if (shopDTO.getDescription() != null) {
            shop.setDescription(shopDTO.getDescription());
        }

        shopMapper.updateById(shop);
        log.info("店铺信息已更新，店铺ID：{}", shopId);
    }

    /**
     * 获取店铺信息
     * <p>
     * 根据店铺ID查询，公开接口，任何人都能查看。
     * </p>
     */
    @Override
    public ShopVO getShopInfo(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }
        return convertToVO(shop);
    }

    /**
     * 根据商家ID获取店铺
     * <p>
     * 商家登录后查看自己的店铺信息。
     * 如果商家还没有店铺（比如还没审核通过），返回null。
     * </p>
     */
    @Override
    public ShopVO getShopByMerchantId(Long merchantId) {
        Shop shop = shopMapper.selectOne(
                new LambdaQueryWrapper<Shop>().eq(Shop::getMerchantId, merchantId)
        );
        if (shop == null) {
            return null;
        }
        return convertToVO(shop);
    }

    /**
     * 检查店铺名称是否唯一
     * <p>
     * 两个店铺不能叫同一个名字，避免用户混淆。
     * 更新时排除自身，只检查其他店铺是否用了这个名字。
     * </p>
     *
     * @param name   店铺名称
     * @param shopId 当前店铺ID（更新时传入，新增时传null）
     */
    private void checkShopNameUnique(String name, Long shopId) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getName, name);
        // 更新时排除自身
        if (shopId != null) {
            wrapper.ne(Shop::getId, shopId);
        }
        Long count = shopMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.SHOP_NAME_EXISTS);
        }
    }

    /**
     * 将Shop实体转换为ShopVO
     *
     * @param shop 店铺实体
     * @return 店铺VO
     */
    private ShopVO convertToVO(Shop shop) {
        ShopVO vo = new ShopVO();
        BeanUtils.copyProperties(shop, vo);
        return vo;
    }
}
