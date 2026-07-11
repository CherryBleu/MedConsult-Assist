package com.medconsult.outpatient.doctor.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.department.service.DepartmentService;
import com.medconsult.outpatient.doctor.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门诊域对内接口（架构文档 §2.3 补充）。
 *
 * <p>路径前缀 /internal（对内，不走 Gateway 路由，由调用方带服务 JWT 鉴权）。
 * 供 medical-record-service 把 doctor_no / department_no 反查为真实 BIGINT 主键落库。
 *
 * <p><b>鉴权</b>：强制服务身份（{@link SecurityContext#requireService()}），防网关误配暴露。
 *
 * <p>放在 doctor 包下（doctor 是门诊域主实体）；department 的端点也在此暴露，
 * 避免为单方法接口单建 controller。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class DoctorInternalController {

    private final DoctorService doctorService;
    private final DepartmentService departmentService;

    /** 按 doctor_no 反查 BIGINT 主键。未找到返回 404。 */
    @GetMapping("/doctors/no/{doctorNo}/id")
    public Result<EntityIdDTO> resolveDoctorId(@PathVariable String doctorNo) {
        SecurityContext.requireService();
        return Result.ok(doctorService.internalResolveId(doctorNo));
    }

    /** 按 department_no 反查 BIGINT 主键。未找到返回 404。 */
    @GetMapping("/departments/no/{departmentNo}/id")
    public Result<EntityIdDTO> resolveDepartmentId(@PathVariable String departmentNo) {
        SecurityContext.requireService();
        return Result.ok(departmentService.internalResolveId(departmentNo));
    }
}
