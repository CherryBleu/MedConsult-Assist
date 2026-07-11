package com.medconsult.outpatient.department.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.outpatient.department.dto.DepartmentDTO;
import com.medconsult.outpatient.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 科室对外接口（对齐《接口文档》§2.3.1）。
 *
 * <p>路径前缀 /api/v1/departments（对外，走 Gateway 鉴权）。
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "科室接口", description = "科室基础信息查询（§2.3）")
public class DepartmentController {

    private final DepartmentService departmentService;

    /** §2.3.1 查询科室列表 */
    @GetMapping
    @Operation(summary = "查询科室列表")
    public Result<PageResult<DepartmentDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "是否启用", required = false) @RequestParam(required = false) Boolean enabled) {
        return Result.ok(departmentService.list(page, pageSize, enabled));
    }
}
