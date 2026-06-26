package com.shop.user.controller;

import com.shop.common.model.PageRequest;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.user.entity.UserFootprint;
import com.shop.user.service.FootprintService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览足迹控制器
 * <p>
 * 处理浏览足迹列表等接口。
 * 添加足迹的接口由内部服务调用，不对外暴露。
 * 这些接口都需要登录后才能访问（由SaTokenConfig拦截器校验）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/footprint")
@RequiredArgsConstructor
@Tag(name = "浏览足迹", description = "浏览足迹列表")
public class FootprintController {

    /** 浏览足迹服务 */
    private final FootprintService footprintService;

    /**
     * 获取浏览足迹列表（分页）
     * <p>
     * 按浏览时间倒序排列，最近浏览的排在最前面。
     * </p>
     *
     * @param pageRequest 分页参数（pageNum, pageSize）
     * @return 足迹分页结果
     */
    @GetMapping("/list")
    @Operation(summary = "浏览足迹列表", description = "分页获取当前用户的浏览足迹，按浏览时间倒序")
    public Result<PageResult<UserFootprint>> getFootprintList(@Validated PageRequest pageRequest) {
        Long userId = UserContext.getUserId();
        PageResult<UserFootprint> pageResult = footprintService.getFootprintList(userId, pageRequest);
        return Result.success(pageResult);
    }
}
