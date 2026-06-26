package com.shop.cart.controller;

import com.shop.cart.service.CartService;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.model.cart.dto.CartAddDTO;
import com.shop.model.cart.dto.CartCheckDTO;
import com.shop.model.cart.dto.CartUpdateDTO;
import com.shop.model.cart.vo.CartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 * <p>
 * 处理购物车的所有接口，包括加入购物车、修改数量、删除商品、
 * 勾选/取消勾选、获取购物车列表、清空购物车等。
 * 所有接口都需要登录后才能访问（由SaTokenConfig拦截器统一校验）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Tag(name = "购物车", description = "购物车的增删改查和勾选操作")
public class CartController {

    /** 购物车服务 */
    private final CartService cartService;

    /**
     * 加入购物车
     * <p>
     * 用户点击"加入购物车"按钮时调用。
     * 同一SKU多次加购会累加数量，不会新增记录。
     * 加购时会校验：购物车SKU数量上限、商品是否上架、库存是否充足。
     * </p>
     *
     * @param dto 加入购物车参数（商品ID、SKU ID、数量）
     * @return 操作结果
     */
    @PostMapping
    @Operation(summary = "加入购物车", description = "将商品加入购物车，同一SKU数量累加，校验库存和商品状态")
    public Result<Void> addToCart(@Validated @RequestBody CartAddDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.addToCart(userId, dto);
        return Result.success("加入购物车成功", null);
    }

    /**
     * 修改购物车项
     * <p>
     * 修改某个商品的数量或勾选状态，传哪个就改哪个。
     * </p>
     *
     * @param id  购物车项ID
     * @param dto 修改参数（数量、勾选状态）
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改购物车项", description = "修改购物车中商品的数量或勾选状态")
    public Result<Void> updateCartItem(
            @Parameter(description = "购物车项ID", required = true) @PathVariable Long id,
            @Validated @RequestBody CartUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.updateCartItem(userId, id, dto);
        return Result.success("修改成功", null);
    }

    /**
     * 删除购物车项
     * <p>
     * 从购物车中移除某个商品。
     * </p>
     *
     * @param id 购物车项ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除购物车项", description = "从购物车中移除指定商品")
    public Result<Void> deleteCartItem(
            @Parameter(description = "购物车项ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.deleteCartItem(userId, id);
        return Result.success("删除成功", null);
    }

    /**
     * 批量勾选/取消勾选
     * <p>
     * 勾选多个商品准备结算，或者取消勾选。
     * </p>
     *
     * @param dto 勾选参数（购物车项ID列表、勾选状态）
     * @return 操作结果
     */
    @PutMapping("/check")
    @Operation(summary = "批量勾选", description = "批量勾选或取消勾选购物车商品")
    public Result<Void> batchCheck(@Validated @RequestBody CartCheckDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.batchCheck(userId, dto);
        return Result.success("操作成功", null);
    }

    /**
     * 全选/取消全选
     * <p>
     * 一键勾选或取消勾选购物车里的所有商品。
     * </p>
     *
     * @param checked 是否全选（true全选，false取消全选）
     * @return 操作结果
     */
    @PutMapping("/check-all")
    @Operation(summary = "全选/取消全选", description = "一键勾选或取消勾选购物车所有商品")
    public Result<Void> checkAll(
            @Parameter(description = "是否全选", required = true) @RequestParam Boolean checked) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.checkAll(userId, checked);
        return Result.success("操作成功", null);
    }

    /**
     * 获取购物车列表
     * <p>
     * 返回购物车中所有商品的详细信息，包括实时价格和库存状态。
     * </p>
     *
     * @return 购物车响应（含商品列表、总数量、选中总价、是否全选）
     */
    @GetMapping("/list")
    @Operation(summary = "购物车列表", description = "获取当前用户的购物车列表，含实时价格和库存")
    public Result<CartVO> getCartList() {
        Long userId = SecurityUtils.getCurrentUserId();
        CartVO cartVO = cartService.getCartList(userId);
        return Result.success(cartVO);
    }

    /**
     * 清空购物车
     * <p>
     * 删除购物车中的所有商品。
     * </p>
     *
     * @return 操作结果
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清空购物车", description = "清空当前用户的购物车")
    public Result<Void> clearCart() {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.clearCart(userId);
        return Result.success("清空成功", null);
    }

    /**
     * 获取购物车商品数量
     * <p>
     * 用于Header角标显示，比如购物车图标上显示"3"表示有3件商品。
     * </p>
     *
     * @return 购物车商品总数量
     */
    @GetMapping("/count")
    @Operation(summary = "购物车数量", description = "获取购物车商品总数量，用于Header角标显示")
    public Result<Integer> getCartCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        Integer count = cartService.getCartCount(userId);
        return Result.success(count);
    }

    /**
     * 合并购物车
     * <p>
     * 用户未登录时在浏览器本地存了购物车数据，登录后需要把这些数据
     * 合并到服务端的购物车中。同一SKU数量累加，不会重复。
     * </p>
     *
     * @param items 未登录时的购物车项列表（商品ID、SKU ID、数量）
     * @return 操作结果
     */
    @PostMapping("/merge")
    @Operation(summary = "合并购物车", description = "登录后合并本地购物车到服务端，同一SKU数量累加")
    public Result<Void> mergeCart(@Validated @RequestBody List<CartAddDTO> items) {
        Long userId = SecurityUtils.getCurrentUserId();
        cartService.mergeCart(userId, items);
        return Result.success("合并成功", null);
    }
}
