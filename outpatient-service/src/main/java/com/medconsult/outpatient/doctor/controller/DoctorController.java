package com.medconsult.outpatient.doctor.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.outpatient.doctor.dto.DoctorDTO;
import com.medconsult.outpatient.doctor.service.DoctorService;
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
public class DoctorController {

    private final DoctorService doctorService;

    /** §2.3.2 查询医生列表 */
    @GetMapping
    public Result<PageResult<DoctorDTO.ListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Boolean enabled) {
        return Result.ok(doctorService.list(page, pageSize, departmentId, enabled));
    }
}
