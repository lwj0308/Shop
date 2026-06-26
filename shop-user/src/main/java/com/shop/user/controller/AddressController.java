package com.shop.user.controller;

import com.shop.common.result.Result;
import com.shop.model.user.dto.AddressDTO;
import com.shop.model.user.vo.AddressVO;
import com.shop.user.service.AddressService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址控制器
 * <p>
 * 处理收货地址的增删改查和设置默认地址等接口。
 * 这些接口都需要登录后才能访问（由SaTokenConfig拦截器校验）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
@Tag(name = "收货地址", description = "收货地址的增删改查和设置默认地址")
public class AddressController {

    /** 收货地址服务 */
    private final AddressService addressService;

    /**
     * 添加收货地址
     *
     * @param dto 地址请求参数
     * @return 新添加的地址信息
     */
    @PostMapping
    @Operation(summary = "添加收货地址", description = "添加一个新的收货地址")
    public Result<AddressVO> addAddress(@Validated @RequestBody AddressDTO dto) {
        Long userId = UserContext.getUserId();
        AddressVO addressVO = addressService.addAddress(userId, dto);
        return Result.success("添加成功", addressVO);
    }

    /**
     * 修改收货地址
     *
     * @param id  地址ID
     * @param dto 地址请求参数
     * @return 修改后的地址信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改收货地址", description = "修改指定的收货地址")
    public Result<AddressVO> updateAddress(@PathVariable Long id,
                                            @Validated @RequestBody AddressDTO dto) {
        Long userId = UserContext.getUserId();
        AddressVO addressVO = addressService.updateAddress(userId, id, dto);
        return Result.success("修改成功", addressVO);
    }

    /**
     * 删除收货地址
     *
     * @param id 地址ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除收货地址", description = "删除指定的收货地址")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.deleteAddress(userId, id);
        return Result.success("删除成功", null);
    }

    /**
     * 获取收货地址列表
     * <p>
     * 默认地址排在最前面，其他按创建时间倒序。
     * </p>
     *
     * @return 地址列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取地址列表", description = "获取当前用户的所有收货地址，默认地址排前面")
    public Result<List<AddressVO>> getAddressList() {
        Long userId = UserContext.getUserId();
        List<AddressVO> list = addressService.getAddressList(userId);
        return Result.success(list);
    }

    /**
     * 设为默认地址
     * <p>
     * 每个用户只能有一个默认地址，设置新的会自动取消旧的。
     * </p>
     *
     * @param id 要设为默认的地址ID
     * @return 操作结果
     */
    @PutMapping("/{id}/default")
    @Operation(summary = "设为默认地址", description = "将指定地址设为默认收货地址")
    public Result<Void> setDefaultAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.setDefaultAddress(userId, id);
        return Result.success("设置成功", null);
    }
}
