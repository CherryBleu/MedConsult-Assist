package com.medconsult.outpatient.doctor.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医生对外接口（对齐《接口文档》§2.3.2）。
 *
 * <p>路径前缀 /api/v1/doctors（对外，走 Gateway 鉴权）。
 */
@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Tag(name = "医生接口", description = "医生基础信息查询（§2.3）")
public class DoctorController {

    private final DoctorService doctorService;

    /** §2.3.2 查询医生列表 */
    @GetMapping
    @Operation(summary = "查询医生列表")
    public Result<PageResult<DoctorDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "科室编号", required = false) @RequestParam(required = false) String departmentId,
            @Parameter(description = "是否启用", required = false) @RequestParam(required = false) Boolean enabled) {
        return Result.ok(doctorService.list(page, pageSize, departmentId, enabled));
    }
}
