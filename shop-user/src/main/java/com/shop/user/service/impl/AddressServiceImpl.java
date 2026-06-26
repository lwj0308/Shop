package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.AddressDTO;
import com.shop.model.user.entity.UserAddress;
import com.shop.model.user.vo.AddressVO;
import com.shop.user.mapper.UserAddressMapper;
import com.shop.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 收货地址服务实现类
 * <p>
 * 实现收货地址的增删改查和设置默认地址等业务逻辑。
 * 每个用户只能有一个默认地址，设置新的默认地址会自动取消旧的。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    /** 收货地址Mapper，操作user_address表 */
    private final UserAddressMapper addressMapper;

    /**
     * 添加收货地址
     * <p>
     * 如果是用户的第一条地址，自动设为默认地址。
     * 如果DTO中指定了isDefault为true，需要先取消旧的默认地址。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO addAddress(Long userId, AddressDTO dto) {
        // 查询用户当前有多少地址
        Long addressCount = addressMapper.selectCount(
                new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId)
        );

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setName(dto.getName());
        address.setPhone(dto.getPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());

        // 如果是第一条地址，自动设为默认；否则看DTO中的设置
        if (addressCount == 0) {
            address.setIsDefault(1);
        } else if (Boolean.TRUE.equals(dto.getIsDefault())) {
            // 如果新地址要设为默认，先取消旧的默认地址
            cancelDefaultAddress(userId);
            address.setIsDefault(1);
        } else {
            address.setIsDefault(0);
        }

        addressMapper.insert(address);
        log.info("添加收货地址: userId={}, addressId={}", userId, address.getId());
        return convertToVO(address);
    }

    /**
     * 修改收货地址
     * <p>
     * 只能修改自己的地址，如果设为默认需要先取消旧的默认地址。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO updateAddress(Long userId, Long addressId, AddressDTO dto) {
        // 查询地址，确保存在且属于当前用户
        UserAddress address = getAndCheckOwner(userId, addressId);

        // 更新字段
        address.setName(dto.getName());
        address.setPhone(dto.getPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());

        // 如果要设为默认地址
        if (Boolean.TRUE.equals(dto.getIsDefault()) && address.getIsDefault() == 0) {
            cancelDefaultAddress(userId);
            address.setIsDefault(1);
        }

        addressMapper.updateById(address);
        log.info("修改收货地址: userId={}, addressId={}", userId, addressId);
        return convertToVO(address);
    }

    /**
     * 删除收货地址（逻辑删除）
     * <p>
     * 不是真的从数据库删掉，而是把deleted字段设为1。
     * 如果删除的是默认地址，自动把第一条地址设为默认。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        // 确保地址存在且属于当前用户
        UserAddress address = getAndCheckOwner(userId, addressId);
        boolean wasDefault = address.getIsDefault() == 1;

        // 逻辑删除
        addressMapper.deleteById(addressId);

        // 如果删除的是默认地址，自动把第一条地址设为默认
        if (wasDefault) {
            UserAddress firstAddress = addressMapper.selectOne(
                    new LambdaQueryWrapper<UserAddress>()
                            .eq(UserAddress::getUserId, userId)
                            .orderByAsc(UserAddress::getCreateTime)
                            .last("LIMIT 1")
            );
            if (firstAddress != null) {
                firstAddress.setIsDefault(1);
                addressMapper.updateById(firstAddress);
            }
        }

        log.info("删除收货地址: userId={}, addressId={}", userId, addressId);
    }

    /**
     * 获取用户的所有收货地址列表
     * <p>
     * 默认地址排在最前面，其他按创建时间倒序排列。
     * </p>
     */
    @Override
    public List<AddressVO> getAddressList(Long userId) {
        List<UserAddress> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault) // 默认地址排前面
                        .orderByDesc(UserAddress::getCreateTime) // 新添加的排前面
        );
        return addresses.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 设为默认地址
     * <p>
     * 先取消旧的默认地址，再设置新的默认地址。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long userId, Long addressId) {
        // 确保地址存在且属于当前用户
        getAndCheckOwner(userId, addressId);

        // 先取消旧的默认地址
        cancelDefaultAddress(userId);

        // 设置新的默认地址
        UserAddress updateAddress = new UserAddress();
        updateAddress.setId(addressId);
        updateAddress.setIsDefault(1);
        addressMapper.updateById(updateAddress);

        log.info("设置默认地址: userId={}, addressId={}", userId, addressId);
    }

    /**
     * 取消当前用户的默认地址
     * <p>
     * 把用户所有地址的isDefault都设为0，
     * 这样后面设置新的默认地址时就不会冲突了。
     * </p>
     *
     * @param userId 用户ID
     */
    private void cancelDefaultAddress(Long userId) {
        UserAddress update = new UserAddress();
        update.setIsDefault(0);
        addressMapper.update(update,
                new LambdaUpdateWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getIsDefault, 1)
        );
    }

    /**
     * 查询地址并校验归属权
     * <p>
     * 确保地址存在，且属于当前登录用户。
     * 防止用户通过修改地址ID来操作别人的地址。
     * </p>
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     * @return 地址实体
     */
    private UserAddress getAndCheckOwner(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此地址");
        }
        return address;
    }

    /**
     * UserAddress实体转AddressVO
     *
     * @param address 地址实体
     * @return 地址VO
     */
    private AddressVO convertToVO(UserAddress address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setName(address.getName());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetail(address.getDetail());
        vo.setIsDefault(address.getIsDefault());
        vo.setCreateTime(address.getCreateTime());
        return vo;
    }
}
