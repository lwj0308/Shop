package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminBannerService;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminBannerCreateDTO;
import com.shop.model.admin.dto.AdminBannerUpdateDTO;
import com.shop.model.admin.vo.AdminBannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Banner管理控制器
 * <p>
 * 提供Banner图片的CRUD接口，包括列表查询、详情查询、新增、修改、删除等。
 * Banner就是首页轮播图，所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/content/banner")
@Tag(name = "Banner管理", description = "Banner图片管理接口")
@RequiredArgsConstructor
public class ContentBannerController {

    /** Banner服务，处理Banner相关的业务逻辑 */
    private final AdminBannerService adminBannerService;

    /**
     * 获取Banner列表
     * <p>
     * 查询所有Banner，按排序号升序排列。
     * Banner数量通常不多，不需要分页，直接返回列表。
     * 需要 banner:list 权限。
     * </p>
     *
     * @return Banner列表
     */
    @Operation(summary = "获取Banner列表", description = "查询所有Banner，按排序号升序排列")
    @GetMapping("/list")
    @RequirePermission("banner:list")
    public Result<List<AdminBannerVO>> getBannerList() {
        List<AdminBannerVO> list = adminBannerService.getBannerList();
        return Result.success(list);
    }

    /**
     * 根据ID查询Banner详情
     * <p>
     * 查询单个Banner的详细信息。
     * 需要 banner:list 权限。
     * </p>
     *
     * @param id Banner ID
     * @return Banner详细信息
     */
    @Operation(summary = "查询Banner详情", description = "根据ID查询Banner详细信息")
    @GetMapping("/{id}")
    @RequirePermission("banner:list")
    public Result<AdminBannerVO> getBannerById(@PathVariable Long id) {
        AdminBannerVO vo = adminBannerService.getBannerById(id);
        return Result.success(vo);
    }

    /**
     * 新增Banner
     * <p>
     * 创建一个新的Banner，比如上传一张促销活动图。
     * 需要 banner:add 权限。
     * </p>
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @Operation(summary = "新增Banner", description = "创建一个新的Banner")
    @PostMapping
    @RequirePermission("banner:add")
    @OperationLog(module = "Banner管理", type = OperationType.CREATE, description = "新增Banner：#dto.title")
    public Result<Void> createBanner(@Validated @RequestBody AdminBannerCreateDTO dto) {
        adminBannerService.createBanner(dto);
        return Result.success(null);
    }

    /**
     * 修改Banner信息
     * <p>
     * 修改Banner的基本信息，只更新传入的字段。
     * 需要 banner:edit 权限。
     * </p>
     *
     * @param id  Banner ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @Operation(summary = "修改Banner信息", description = "修改Banner基本信息")
    @PutMapping("/{id}")
    @RequirePermission("banner:edit")
    @OperationLog(module = "Banner管理", type = OperationType.UPDATE, description = "修改Banner：#id")
    public Result<Void> updateBanner(@PathVariable Long id, @Validated @RequestBody AdminBannerUpdateDTO dto) {
        adminBannerService.updateBanner(id, dto);
        return Result.success(null);
    }

    /**
     * 删除Banner
     * <p>
     * 逻辑删除Banner，删除后前端不再显示。
     * 需要 banner:delete 权限。
     * </p>
     *
     * @param id Banner ID
     * @return 操作结果
     */
    @Operation(summary = "删除Banner", description = "逻辑删除Banner")
    @DeleteMapping("/{id}")
    @RequirePermission("banner:delete")
    @OperationLog(module = "Banner管理", type = OperationType.DELETE, description = "删除Banner：#id")
    public Result<Void> deleteBanner(@PathVariable Long id) {
        adminBannerService.deleteBanner(id);
        return Result.success(null);
    }
}
