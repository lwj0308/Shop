package com.shop.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.GlobalExceptionHandler;
import com.shop.common.result.ErrorCode;
import com.shop.model.user.dto.AddressDTO;
import com.shop.model.user.vo.AddressVO;
import com.shop.user.service.AddressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AddressController 切片测试（B-S-02）
 * <p>
 * 小白理解：切片测试是"只测Controller这一层"，不启动整个SpringBoot应用。
 * 我们用 Standalone MockMvc 的方式，手动把Controller和它的依赖组装起来，
 * 这样测试跑得快，又不需要真的连数据库、Redis、Nacos等中间件。
 * </p>
 * <p>
 * 测试重点：
 * 1. 请求路由是否正确（URL能否映射到对应方法）
 * 2. 参数校验是否生效（@Valid校验不通过时返回400）
 * 3. 返回格式是否正确（统一Result格式，code=200表示成功）
 * 4. Service调用是否正确（参数传递、调用次数）
 * 5. 异常处理是否正确（BusinessException返回业务错误码）
 * </p>
 * <p>
 * 注意：AddressController 通过 UserContext.getUserId() 获取当前登录用户ID，
 * 而 UserContext.getUserId() 内部调用 StpUtil.getLoginIdAsLong()，
 * 所以我们 mock StpUtil 即可。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AddressController 切片测试")
class AddressControllerTest {

    /** MockMvc：用来模拟HTTP请求，不需要真的启动Tomcat */
    private MockMvc mockMvc;

    /** ObjectMapper：把Java对象转成JSON字符串（请求体用） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 假装收货地址服务 */
    @Mock
    private AddressService addressService;

    /** Sa-Token静态方法mock（模拟登录状态） */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试中模拟的登录用户ID */
    private static final Long MOCK_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 1. 创建真实的 AddressController 实例，注入 mock 依赖
        AddressController controller = new AddressController(addressService);

        // 2. 用 Standalone 方式构建 MockMvc，手动注册全局异常处理器
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 3. mock StpUtil.getLoginIdAsLong()，让 UserContext.getUserId() 返回模拟的用户ID
        stpUtilMock = org.mockito.Mockito.mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(MOCK_USER_ID);
    }

    @AfterEach
    void tearDown() {
        // 每个测试结束后关闭静态mock，避免影响其他测试
        stpUtilMock.close();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造一个合法的地址请求DTO（所有必填字段都填好）
     *
     * @return 构造好的 AddressDTO
     */
    private AddressDTO buildValidAddressDTO() {
        AddressDTO dto = new AddressDTO();
        dto.setName("张三");
        dto.setPhone("13812345678");
        dto.setProvince("广东省");
        dto.setCity("深圳市");
        dto.setDistrict("南山区");
        dto.setDetail("科技园路1号A栋3楼");
        dto.setIsDefault(false);
        return dto;
    }

    /**
     * 构造一个地址VO（用于Service返回值）
     *
     * @param id        地址ID
     * @param isDefault 是否默认：0否 1是
     * @return 构造好的 AddressVO
     */
    private AddressVO buildAddressVO(Long id, Integer isDefault) {
        AddressVO vo = new AddressVO();
        vo.setId(id);
        vo.setName("张三");
        vo.setPhone("13812345678");
        vo.setProvince("广东省");
        vo.setCity("深圳市");
        vo.setDistrict("南山区");
        vo.setDetail("科技园路1号A栋3楼");
        vo.setIsDefault(isDefault);
        vo.setCreateTime(LocalDateTime.of(2024, 1, 1, 10, 0));
        return vo;
    }

    // ==================== 添加收货地址 ====================

    @Nested
    @DisplayName("添加收货地址 POST /user/address")
    class AddAddressTest {

        @Test
        @DisplayName("正常添加收货地址 → 返回200和新地址信息")
        void addAddress_success_returnsAddress() throws Exception {
            AddressDTO dto = buildValidAddressDTO();
            AddressVO addressVO = buildAddressVO(5001L, 0);

            when(addressService.addAddress(eq(MOCK_USER_ID), any(AddressDTO.class)))
                    .thenReturn(addressVO);

            mockMvc.perform(post("/user/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("添加成功"))
                    .andExpect(jsonPath("$.data.id").value(5001))
                    .andExpect(jsonPath("$.data.name").value("张三"))
                    .andExpect(jsonPath("$.data.phone").value("13812345678"));

            verify(addressService).addAddress(eq(MOCK_USER_ID), any(AddressDTO.class));
        }

        @Test
        @DisplayName("收货人姓名为空 → 参数校验失败返回400")
        void addAddress_nameEmpty_returns400() throws Exception {
            AddressDTO dto = buildValidAddressDTO();
            dto.setName(""); // 收货人姓名为空

            mockMvc.perform(post("/user/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("手机号格式错误 → 参数校验失败返回400")
        void addAddress_phoneInvalid_returns400() throws Exception {
            AddressDTO dto = buildValidAddressDTO();
            dto.setPhone("12345"); // 不是11位手机号

            mockMvc.perform(post("/user/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("省份为空 → 参数校验失败返回400")
        void addAddress_provinceEmpty_returns400() throws Exception {
            AddressDTO dto = buildValidAddressDTO();
            dto.setProvince(null); // 省份为空

            mockMvc.perform(post("/user/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_VALID_FAIL.getCode()));
        }

        @Test
        @DisplayName("Service抛业务异常 → 返回业务错误码")
        void addAddress_serviceThrowsBusinessException_returnsErrorCode() throws Exception {
            AddressDTO dto = buildValidAddressDTO();

            // mock Service抛出"操作失败"业务异常（比如地址数量超限）
            when(addressService.addAddress(anyLong(), any(AddressDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.OPERATION_FAIL, "地址数量超限"));

            mockMvc.perform(post("/user/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OPERATION_FAIL.getCode()))
                    .andExpect(jsonPath("$.message").value("操作失败: 地址数量超限"));
        }
    }

    // ==================== 修改收货地址 ====================

    @Nested
    @DisplayName("修改收货地址 PUT /user/address/{id}")
    class UpdateAddressTest {

        @Test
        @DisplayName("正常修改收货地址 → 返回200和修改后的地址")
        void updateAddress_success() throws Exception {
            AddressDTO dto = buildValidAddressDTO();
            dto.setName("李四");
            AddressVO addressVO = buildAddressVO(5001L, 0);
            addressVO.setName("李四");

            when(addressService.updateAddress(eq(MOCK_USER_ID), eq(5001L), any(AddressDTO.class)))
                    .thenReturn(addressVO);

            mockMvc.perform(put("/user/address/{id}", 5001L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"))
                    .andExpect(jsonPath("$.data.id").value(5001))
                    .andExpect(jsonPath("$.data.name").value("李四"));

            verify(addressService).updateAddress(eq(MOCK_USER_ID), eq(5001L), any(AddressDTO.class));
        }

        @Test
        @DisplayName("地址不存在 → Service抛业务异常 → 返回业务错误码")
        void updateAddress_notFound_throwsBusinessException() throws Exception {
            AddressDTO dto = buildValidAddressDTO();

            when(addressService.updateAddress(anyLong(), eq(9999L), any(AddressDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND));

            mockMvc.perform(put("/user/address/{id}", 9999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.DATA_NOT_FOUND.getCode()));
        }
    }

    // ==================== 删除收货地址 ====================

    @Nested
    @DisplayName("删除收货地址 DELETE /user/address/{id}")
    class DeleteAddressTest {

        @Test
        @DisplayName("正常删除收货地址 → 返回200")
        void deleteAddress_success() throws Exception {
            mockMvc.perform(delete("/user/address/{id}", 5001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("删除成功"));

            verify(addressService).deleteAddress(eq(MOCK_USER_ID), eq(5001L));
        }

        @Test
        @DisplayName("地址不存在 → Service抛业务异常 → 返回业务错误码")
        void deleteAddress_notFound_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND))
                    .when(addressService).deleteAddress(anyLong(), eq(9999L));

            mockMvc.perform(delete("/user/address/{id}", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.DATA_NOT_FOUND.getCode()));
        }
    }

    // ==================== 获取地址列表 ====================

    @Nested
    @DisplayName("获取地址列表 GET /user/address/list")
    class GetAddressListTest {

        @Test
        @DisplayName("正常获取地址列表 → 返回200和列表数据")
        void getAddressList_success_returnsList() throws Exception {
            // 准备两条地址：默认地址排在前面
            AddressVO defaultAddr = buildAddressVO(5001L, 1);
            AddressVO normalAddr = buildAddressVO(5002L, 0);
            normalAddr.setName("李四");

            when(addressService.getAddressList(eq(MOCK_USER_ID)))
                    .thenReturn(List.of(defaultAddr, normalAddr));

            mockMvc.perform(get("/user/address/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(5001))
                    .andExpect(jsonPath("$.data[0].isDefault").value(1))
                    .andExpect(jsonPath("$.data[1].id").value(5002))
                    .andExpect(jsonPath("$.data[1].isDefault").value(0));

            verify(addressService).getAddressList(eq(MOCK_USER_ID));
        }

        @Test
        @DisplayName("地址列表为空 → 返回200和空数组")
        void getAddressList_empty_returnsEmptyList() throws Exception {
            when(addressService.getAddressList(eq(MOCK_USER_ID)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/user/address/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================== 设为默认地址 ====================

    @Nested
    @DisplayName("设为默认地址 PUT /user/address/{id}/default")
    class SetDefaultAddressTest {

        @Test
        @DisplayName("正常设置默认地址 → 返回200")
        void setDefaultAddress_success() throws Exception {
            mockMvc.perform(put("/user/address/{id}/default", 5001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("设置成功"));

            verify(addressService).setDefaultAddress(eq(MOCK_USER_ID), eq(5001L));
        }

        @Test
        @DisplayName("地址不存在 → Service抛业务异常 → 返回业务错误码")
        void setDefaultAddress_notFound_throwsBusinessException() throws Exception {
            doThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND))
                    .when(addressService).setDefaultAddress(anyLong(), eq(9999L));

            mockMvc.perform(put("/user/address/{id}/default", 9999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.DATA_NOT_FOUND.getCode()));
        }
    }
}
