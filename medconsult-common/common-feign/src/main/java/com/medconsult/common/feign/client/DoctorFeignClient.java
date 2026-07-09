package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.EntityIdDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * outpatient-service 的 Feign 客户端（架构文档 §2.3）。
 *
 * <p>供 medical-record-service 把 doctor_no / department_no 反查为真实 BIGINT 主键落库，
 * 替代正哈希占位。name = {@code "outpatient-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>身份透传 / 错误处理</b>：同 {@link DrugFeignClient}。
 */
@FeignClient(name = "outpatient-service", contextId = "doctorFeignClient")
public interface DoctorFeignClient {

    /** 内部：按 doctor_no 反查 BIGINT 主键 */
    @GetMapping("/internal/doctors/no/{doctorNo}/id")
    Result<EntityIdDTO> resolveDoctorId(@PathVariable("doctorNo") String doctorNo);

    /** 内部：按 department_no 反查 BIGINT 主键 */
    @GetMapping("/internal/departments/no/{departmentNo}/id")
    Result<EntityIdDTO> resolveDepartmentId(@PathVariable("departmentNo") String departmentNo);
}
