package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shop.common.exception.BusinessException;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.AddressDTO;
import com.shop.model.user.entity.UserAddress;
import com.shop.model.user.vo.AddressVO;
import com.shop.user.mapper.UserAddressMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收货地址服务 AddressServiceImpl 的单元测试
 * <p>
 * 这个测试类验证收货地址的添加、修改、删除、查询、设置默认地址等功能。
 * 小白理解：我们把真正访问数据库的 Mapper "假装"一下（Mock），
 * 这样测试不需要真的连数据库，跑得又快又稳定。
 * </p>
 * <p>
 * 重点说明：
 * - 地址操作会校验"归属权"（地址必须属于当前用户），防止用户改别人的地址。
 * - 每个用户只能有一个默认地址，设置新默认时会自动取消旧默认。
 * - Lambda 查询条件（比如 UserAddress::getUserId）需要 MyBatis-Plus 的字段缓存，
 *   所以用 @BeforeAll 提前初始化，否则会报 "can not find lambda cache" 错误。
 * </p>
 */
@DisplayName("收货地址服务 AddressServiceImpl 单元测试")
@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    /** 假装操作 user_address 表的 Mapper */
    @Mock
    private UserAddressMapper addressMapper;

    /** 被测试的地址服务，Mockito 会自动把上面的 Mock 注入进来 */
    @InjectMocks
    private AddressServiceImpl addressService;

    /** 当前用户ID */
    private static final Long USER_ID = 1001L;

    /** 另一个用户ID（测试越权用） */
    private static final Long OTHER_USER_ID = 1002L;

    /** 测试用地址ID */
    private static final Long ADDRESS_ID = 5001L;

    /**
     * 在所有测试运行前，初始化 MyBatis-Plus 的 Lambda 缓存
     * <p>
     * 小白理解：AddressServiceImpl 大量用到 .eq(UserAddress::getUserId, ...) 这种写法，
     * MyBatis-Plus 需要知道 UserAddress 的字段对应数据库哪一列。
     * 正常启动 Spring 时框架会自动做，单元测试没有 Spring 环境，所以要手动初始化。
     * </p>
     */
    @BeforeAll
    static void initMybatisPlusCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserAddress.class);
    }

    // ==================== 辅助方法：构造测试数据 ====================

    /**
     * 构造一个地址请求DTO
     *
     * @param name      收货人姓名
     * @param phone     收货人手机号
     * @param isDefault 是否设为默认地址
     * @return 构造好的 AddressDTO
     */
    private AddressDTO buildAddressDTO(String name, String phone, Boolean isDefault) {
        AddressDTO dto = new AddressDTO();
        dto.setName(name);
        dto.setPhone(phone);
        dto.setProvince("广东省");
        dto.setCity("深圳市");
        dto.setDistrict("南山区");
        dto.setDetail("科技园路1号");
        dto.setIsDefault(isDefault);
        return dto;
    }

    /**
     * 构造一个地址实体
     *
     * @param id        地址ID
     * @param userId    所属用户ID
     * @param isDefault 是否默认：0否 1是
     * @return 构造好的 UserAddress
     */
    private UserAddress buildAddress(Long id, Long userId, Integer isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setName("原收货人");
        address.setPhone("13800000000");
        address.setProvince("原省");
        address.setCity("原市");
        address.setDistrict("原区");
        address.setDetail("原详细地址");
        address.setIsDefault(isDefault);
        return address;
    }

    // ==================== 1. addAddress 添加收货地址 ====================

    @Nested
    @DisplayName("addAddress 添加收货地址")
    class AddAddressTest {

        @Test
        @DisplayName("正常添加地址（非首条、非默认）→ 调用 insert 且 isDefault=0")
        void addAddress_normal_insert() {
            // 场景：用户已有地址，再添加一条非默认地址
            AddressDTO dto = buildAddressDTO("张三", "13812345678", false);
            // 假装用户已有2条地址（不是第一条，不会自动设为默认）
            when(addressMapper.selectCount(any())).thenReturn(2L);

            // 执行添加
            AddressVO vo = addressService.addAddress(USER_ID, dto);

            // 验证：调用了 insert，且传入的实体字段正确、isDefault=0
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(addressMapper).insert(captor.capture());
            UserAddress saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getName()).isEqualTo("张三");
            assertThat(saved.getPhone()).isEqualTo("13812345678");
            assertThat(saved.getIsDefault()).isEqualTo(0);
            // 验证：返回的 VO 字段也正确
            assertThat(vo).isNotNull();
            assertThat(vo.getName()).isEqualTo("张三");
            assertThat(vo.getIsDefault()).isEqualTo(0);
            // 非默认地址不会触发取消旧默认
            verify(addressMapper, never()).update(any(UserAddress.class), any());
        }
    }

    // ==================== 2. updateAddress 修改收货地址 ====================

    @Nested
    @DisplayName("updateAddress 修改收货地址")
    class UpdateAddressTest {

        @Test
        @DisplayName("地址不存在 → 抛出 NOT_FOUND 异常")
        void updateAddress_notExists_throwsException() {
            // 场景：修改一个不存在的地址
            AddressDTO dto = buildAddressDTO("张三", "13812345678", false);
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(null);

            // 验证：抛出"资源不存在"异常，错误码 404
            assertThatThrownBy(() -> addressService.updateAddress(USER_ID, ADDRESS_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

            // 验证：没有调用 updateById
            verify(addressMapper, never()).updateById(any(UserAddress.class));
        }

        @Test
        @DisplayName("非本人地址 → 抛出 FORBIDDEN 异常")
        void updateAddress_notOwner_throwsForbidden() {
            // 场景：地址存在，但属于另一个用户，当前用户无权修改
            AddressDTO dto = buildAddressDTO("张三", "13812345678", false);
            UserAddress address = buildAddress(ADDRESS_ID, OTHER_USER_ID, 0);
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(address);

            // 验证：抛出"无权限"异常，错误码 403
            assertThatThrownBy(() -> addressService.updateAddress(USER_ID, ADDRESS_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            // 验证：没有调用 updateById
            verify(addressMapper, never()).updateById(any(UserAddress.class));
        }

        @Test
        @DisplayName("正常修改地址 → 调用 updateById 且字段更新正确")
        void updateAddress_normal_update() {
            // 场景：修改自己的地址，更新姓名和手机号
            AddressDTO dto = buildAddressDTO("李四", "13912345678", false);
            UserAddress address = buildAddress(ADDRESS_ID, USER_ID, 0);
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(address);

            // 执行修改
            AddressVO vo = addressService.updateAddress(USER_ID, ADDRESS_ID, dto);

            // 验证：调用了 updateById
            verify(addressMapper).updateById(any(UserAddress.class));
            // 验证：返回的 VO 字段已更新为新值
            assertThat(vo.getName()).isEqualTo("李四");
            assertThat(vo.getPhone()).isEqualTo("13912345678");
            assertThat(vo.getProvince()).isEqualTo("广东省");
        }
    }

    // ==================== 3. deleteAddress 删除收货地址 ====================

    @Nested
    @DisplayName("deleteAddress 删除收货地址")
    class DeleteAddressTest {

        @Test
        @DisplayName("地址不存在 → 抛出 NOT_FOUND 异常")
        void deleteAddress_notExists_throwsException() {
            // 场景：删除一个不存在的地址
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(null);

            // 验证：抛出"资源不存在"异常
            assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.NOT_FOUND.getCode());

            // 验证：没有调用 deleteById
            verify(addressMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("非本人地址 → 抛出 FORBIDDEN 异常")
        void deleteAddress_notOwner_throwsForbidden() {
            // 场景：地址属于别人，当前用户无权删除
            UserAddress address = buildAddress(ADDRESS_ID, OTHER_USER_ID, 0);
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(address);

            // 验证：抛出"无权限"异常
            assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, ADDRESS_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCode.FORBIDDEN.getCode());

            // 验证：没有调用 deleteById
            verify(addressMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("正常删除非默认地址 → 调用 deleteById，不重新指派默认")
        void deleteAddress_normal_delete() {
            // 场景：删除自己的非默认地址，正常删除
            UserAddress address = buildAddress(ADDRESS_ID, USER_ID, 0); // 非默认
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(address);

            // 执行删除
            addressService.deleteAddress(USER_ID, ADDRESS_ID);

            // 验证：调用了 deleteById
            verify(addressMapper).deleteById(ADDRESS_ID);
            // 验证：删除的不是默认地址，不需要重新查第一条来指派默认
            verify(addressMapper, never()).selectOne(any());
        }
    }

    // ==================== 4. getAddressList 获取地址列表 ====================

    @Nested
    @DisplayName("getAddressList 获取地址列表")
    class GetAddressListTest {

        @Test
        @DisplayName("获取地址列表 → 返回所有地址，默认地址排前面")
        void getAddressList_returnsList() {
            // 场景：用户有2条地址，其中1条是默认地址
            UserAddress defaultAddr = buildAddress(1L, USER_ID, 1); // 默认地址
            defaultAddr.setName("默认地址");
            UserAddress normalAddr = buildAddress(2L, USER_ID, 0);  // 非默认地址
            normalAddr.setName("其他地址");
            when(addressMapper.selectList(any())).thenReturn(Arrays.asList(defaultAddr, normalAddr));

            // 执行查询
            List<AddressVO> list = addressService.getAddressList(USER_ID);

            // 验证：返回2条地址，默认地址排在第一位
            assertThat(list).hasSize(2);
            assertThat(list.get(0).getName()).isEqualTo("默认地址");
            assertThat(list.get(0).getIsDefault()).isEqualTo(1);
            assertThat(list.get(1).getName()).isEqualTo("其他地址");
            assertThat(list.get(1).getIsDefault()).isEqualTo(0);
        }
    }

    // ==================== 5. setDefaultAddress 设为默认地址 ====================

    @Nested
    @DisplayName("setDefaultAddress 设为默认地址")
    class SetDefaultAddressTest {

        @Test
        @DisplayName("设置默认地址 → 先取消旧默认，再设置新默认")
        void setDefaultAddress_cancelOldAndSetNew() {
            // 场景：把某条地址设为默认，需要先把原来的默认地址取消
            UserAddress address = buildAddress(ADDRESS_ID, USER_ID, 0); // 当前不是默认
            when(addressMapper.selectById(ADDRESS_ID)).thenReturn(address);

            // 执行设置默认
            addressService.setDefaultAddress(USER_ID, ADDRESS_ID);

            // 验证：先调用了 update 取消旧默认（cancelDefaultAddress 内部用 update + LambdaUpdateWrapper）
            verify(addressMapper).update(any(UserAddress.class), any());
            // 验证：再调用了 updateById 设置新默认，且 isDefault=1
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(addressMapper).updateById(captor.capture());
            UserAddress updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(ADDRESS_ID);
            assertThat(updated.getIsDefault()).isEqualTo(1);
        }
    }
}
