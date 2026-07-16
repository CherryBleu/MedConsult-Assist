package com.medconsult.outpatient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 门诊服务启动入口（架构文档 §1.1 outpatient-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link MapperScan} 扫描 outpatient 包下的 Mapper。
 * <p>{@code scanBasePackages = {"com.medconsult.outpatient", "com.medconsult.common.web"}} 让 common-web 的
 * GlobalExceptionHandler / TraceIdFilter / ResultBodyAdvice 等 @Component 被扫描
 * （@SpringBootApplication 默认只扫本类所在包 com.medconsult.outpatient，common-* 模块的
 * @Component 不会自动发现；AutoConfig 走 SPI 会装配，但 @RestControllerAdvice 需要 scan）。
 *
 * <p>本服务的核心职责（《需求文档》§4.1.2 排班 / §4.1.3 预约 / §4.3.1 科室医生 / 《接口文档》§2.3-§2.5）：
 * <ul>
 *   <li>科室 / 医生基础信息只读查询（§2.3）</li>
 *   <li>医生排班管理：创建 / 列表 / 可预约号源 / 状态变更（§2.4）</li>
 *   <li>预约挂号：创建（Redis 分布式锁抢号，§7.1）/ 详情 / 列表 / 取消 / 支付状态 / 就诊状态流转（§2.5）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.outpatient", "com.medconsult.common.web"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.medconsult.common.feign.client"})
@MapperScan("com.medconsult.outpatient.**.mapper")
public class OutpatientServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutpatientServiceApplication.class, args);
    }
}
