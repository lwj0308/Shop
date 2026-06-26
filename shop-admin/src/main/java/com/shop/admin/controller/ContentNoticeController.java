package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminNoticeService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminNoticeCreateDTO;
import com.shop.model.admin.dto.AdminNoticeUpdateDTO;
import com.shop.model.admin.vo.AdminNoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 公告管理控制器
 * <p>
 * 提供系统公告的CRUD接口，包括分页查询、详情查询、新增、修改、删除等。
 * 公告数量可能较多，列表接口支持分页和按类型、状态筛选。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/content/notice")
@Tag(name = "公告管理", description = "系统公告管理接口")
@RequiredArgsConstructor
public class ContentNoticeController {

    /** 公告服务，处理公告相关的业务逻辑 */
    private final AdminNoticeService adminNoticeService;

    /**
     * 分页查询公告列表
     * <p>
     * 支持按类型和状态筛选，返回分页结果。
     * 需要 notice:list 权限。
     * </p>
     *
     * @param type   公告类型：1通知 2活动 3维护，不传则查全部
     * @param status 状态：0禁用 1正常，不传则查全部
     * @param page   当前页码，默认1
     * @param size   每页条数，默认10
     * @return 分页结果
     */
    @Operation(summary = "查询公告列表", description = "分页查询公告列表，支持按类型和状态筛选")
    @GetMapping("/list")
    @RequirePermission("notice:list")
    public Result<PageResult<AdminNoticeVO>> getNoticeList(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<AdminNoticeVO> pageResult = adminNoticeService.getNoticeList(type, status, page, size);
        return Result.success(pageResult);
    }

    /**
     * 根据ID查询公告详情
     * <p>
     * 查询单条公告的详细信息。
     * 需要 notice:list 权限。
     * </p>
     *
     * @param id 公告ID
     * @return 公告详细信息
     */
    @Operation(summary = "查询公告详情", description = "根据ID查询公告详细信息")
    @GetMapping("/{id}")
    @RequirePermission("notice:list")
    public Result<AdminNoticeVO> getNoticeById(@PathVariable Long id) {
        AdminNoticeVO vo = adminNoticeService.getNoticeById(id);
        return Result.success(vo);
    }

    /**
     * 新增公告
     * <p>
     * 创建一条新的系统公告，比如发布"系统维护通知"。
     * 需要 notice:add 权限。
     * </p>
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @Operation(summary = "新增公告", description = "创建一条新的系统公告")
    @PostMapping
    @RequirePermission("notice:add")
    @OperationLog(module = "公告管理", type = OperationType.CREATE, description = "新增公告：#dto.title")
    public Result<Void> createNotice(@Validated @RequestBody AdminNoticeCreateDTO dto) {
        adminNoticeService.createNotice(dto);
        return Result.success(null);
    }

    /**
     * 修改公告信息
     * <p>
     * 修改公告的基本信息，只更新传入的字段。
     * 需要 notice:edit 权限。
     * </p>
     *
     * @param id  公告ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @Operation(summary = "修改公告信息", description = "修改公告基本信息")
    @PutMapping("/{id}")
    @RequirePermission("notice:edit")
    @OperationLog(module = "公告管理", type = OperationType.UPDATE, description = "修改公告：#id")
    public Result<Void> updateNotice(@PathVariable Long id, @Validated @RequestBody AdminNoticeUpdateDTO dto) {
        adminNoticeService.updateNotice(id, dto);
        return Result.success(null);
    }

    /**
     * 删除公告
     * <p>
     * 逻辑删除公告，删除后前端不再显示。
     * 需要 notice:delete 权限。
     * </p>
     *
     * @param id 公告ID
     * @return 操作结果
     */
    @Operation(summary = "删除公告", description = "逻辑删除公告")
    @DeleteMapping("/{id}")
    @RequirePermission("notice:delete")
    @OperationLog(module = "公告管理", type = OperationType.DELETE, description = "删除公告：#id")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        adminNoticeService.deleteNotice(id);
        return Result.success(null);
    }
}
