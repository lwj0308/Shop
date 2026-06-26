package com.shop.user.service;

import com.shop.model.user.dto.AddressDTO;
import com.shop.model.user.vo.AddressVO;

import java.util.List;

/**
 * 收货地址服务接口
 * <p>
 * 定义收货地址相关的业务方法，包括增删改查和设置默认地址。
 * </p>
 */
public interface AddressService {

    /**
     * 添加收货地址
     *
     * @param userId 用户ID
     * @param dto    地址请求参数
     * @return 新添加的地址信息
     */
    AddressVO addAddress(Long userId, AddressDTO dto);

    /**
     * 修改收货地址
     *
     * @param userId     用户ID（确保只能改自己的地址）
     * @param addressId  地址ID
     * @param dto        地址请求参数
     * @return 修改后的地址信息
     */
    AddressVO updateAddress(Long userId, Long addressId, AddressDTO dto);

    /**
     * 删除收货地址（逻辑删除）
     *
     * @param userId    用户ID（确保只能删自己的地址）
     * @param addressId 地址ID
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 获取用户的所有收货地址列表
     *
     * @param userId 用户ID
     * @return 地址列表
     */
    List<AddressVO> getAddressList(Long userId);

    /**
     * 设为默认地址
     * <p>
     * 每个用户只能有一个默认地址，设置新的默认地址会取消旧的。
     * </p>
     *
     * @param userId    用户ID
     * @param addressId 要设为默认的地址ID
     */
    void setDefaultAddress(Long userId, Long addressId);
}
