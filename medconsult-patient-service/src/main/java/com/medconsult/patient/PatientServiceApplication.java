package com.medconsult.patient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 患者档案服务启动入口（架构文档 §1.1 patient-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link MapperScan} 扫描 patient 包下的 Mapper。
 * <p>{@code scanBasePackages = {"com.medconsult.patient", "com.medconsult.common.web"}} 让 common-web 的
 * GlobalExceptionHandler / TraceIdFilter / ResultBodyAdvice 等 @Component 被扫描
 * （@SpringBootApplication 默认只扫本类所在包 com.medconsult.patient，common-* 模块的
 * @Component 不会自动发现；AutoConfig 走 SPI 会装配，但 @RestControllerAdvice 需要 scan）。
 *
 * <p>本服务的核心职责（架构文档 §2.3 / 《需求文档》§4.1.1 / 《接口文档》§2.2）：
 * <ul>
 *   <li>患者档案创建、查询、修改、状态流转（对外 5 接口）</li>
 *   <li>过敏史 / 既往病史 / 家族病史 / 紧急联系人维护</li>
 *   <li>内部接口 /internal/patients/{id}/context、/internal/patients/{id}/allergies
 *       供 ai-service 做用药/分诊分析（架构文档 §2.3）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.patient", "com.medconsult.common.web"})
@EnableDiscoveryClient
@MapperScan("com.medconsult.patient.**.mapper")
public class PatientServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
