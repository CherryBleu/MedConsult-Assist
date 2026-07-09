package com.medconsult.common.feign.dto;

/**
 * 通用主键解析响应（架构文档 §2.3 内部接口补充）。
 *
 * <p>供 medical-record-service 把业务编号（patient_no / doctor_no / department_no）
 * 反查为对应表的真实 BIGINT 主键，落库时存真实主键而非哈希，根治正哈希碰撞串号风险。
 *
 * <p>使用方：PatientFeignClient / DoctorFeignClient / DepartmentFeignClient。
 */
public record EntityIdDTO(Long id) {
    public static EntityIdDTO of(Long id) {
        return new EntityIdDTO(id);
    }
}
