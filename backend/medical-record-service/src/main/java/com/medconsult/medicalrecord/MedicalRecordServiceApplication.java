package com.medconsult.medicalrecord;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 电子病历 + 处方服务启动入口（架构文档 §1.1 medical-record-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link MapperScan} 扫描 medicalrecord 包下所有领域子包的 Mapper
 *     （medicalrecord / prescription 两个领域的 **.mapper）。
 * <p>{@code scanBasePackages} 让 common-web 的 GlobalExceptionHandler / TraceIdFilter / ResultBodyAdvice，
 * 以及 common-mq 的审计 outbox 自动装配组件被扫描。
 *
 * <p>本服务第 1 批核心职责（架构文档 §2.3 / 《需求文档》§4.1.3-4.1.4 / 《接口文档》§2.6 / 《修改建议》§2.1）：
 * <ul>
 *   <li>电子病历：创建/详情/分页/更新草稿/归档（对外 5 接口）</li>
 *   <li>处方流转：开方后直接 APPROVED，患者缴费，药房调剂发药；submit/review 仅作历史待审兼容</li>
 *   <li>内部接口 /internal/medical-records/{id}/full 供 ai-service 做病历摘要（架构文档 §2.3）</li>
 * </ul>
 *
 * <p>审计生产端使用 common-mq 的 @AuditLog + local_message outbox，把病历、附件和处方写操作投递到 audit.log。
 */
@SpringBootApplication(scanBasePackages = {
        "com.medconsult.medicalrecord",
        "com.medconsult.common.web",
        "com.medconsult.common.mq"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.medconsult.common.feign.client"})
@MapperScan({"com.medconsult.medicalrecord.**.mapper", "com.medconsult.common.mq"})
public class MedicalRecordServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicalRecordServiceApplication.class, args);
    }
}
