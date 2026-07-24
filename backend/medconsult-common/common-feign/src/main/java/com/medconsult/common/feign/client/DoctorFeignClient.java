package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.DoctorProfileDTO;
import com.medconsult.common.feign.dto.EntityIdDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * outpatient-service 的 Feign 客户端（架构文档 §2.3）。
 *
 * <p>供 medical-record-service 把 doctor_no / department_no 反查为真实 BIGINT 主键落库，
 * 替代正哈希占位。name = {@code "outpatient-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>身份透传 / 错误处理</b>：/internal/** 固定注入 SERVICE JWT；其余同 {@link DrugFeignClient}。
 */
@FeignClient(name = "outpatient-service", contextId = "doctorFeignClient")
public interface DoctorFeignClient {

    /** 内部：按 doctor_no 反查 BIGINT 主键 */
    @GetMapping("/internal/doctors/no/{doctorNo}/id")
    Result<EntityIdDTO> resolveDoctorId(@PathVariable("doctorNo") String doctorNo);

    /** 内部：按 department_no 反查 BIGINT 主键 */
    @GetMapping("/internal/departments/no/{departmentNo}/id")
    Result<EntityIdDTO> resolveDepartmentId(@PathVariable("departmentNo") String departmentNo);

    /**
     * 内部：查询有启用医生的科室编号集合（department_no）。
     * <p>供 ai-service 智能分诊过滤"无医生可预约"的空科室。
     */
    @GetMapping("/internal/departments/with-doctors")
    Result<List<String>> departmentNosWithDoctors();

    /** Internal display lookup: doctor primary key -> doctor name and department name. */
    @GetMapping("/internal/doctors/profiles")
    Result<Map<Long, DoctorProfileDTO>> profilesByIds(@RequestParam("ids") List<Long> ids);
}
