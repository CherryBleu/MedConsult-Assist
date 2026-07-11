package com.medconsult.drug;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 药品库存服务启动入口（架构文档 §1.1 drug-service）。
 *
 * <p>{@link EnableDiscoveryClient} 注册到 Nacos（架构文档 §1.3）。
 * <p>{@link MapperScan} 扫描 drug 包下的 Mapper。
 * <p>{@code scanBasePackages = {"com.medconsult.drug", "com.medconsult.common.web"}} 让 common-web 的
 * GlobalExceptionHandler / TraceIdFilter / ResultBodyAdvice 等 @Component 被扫描
 * （@SpringBootApplication 默认只扫本类所在包 com.medconsult.drug，common-* 模块的
 * @Component 不会自动发现；AutoConfig 走 SPI 会装配，但 @RestControllerAdvice 需要 scan）。
 *
 * <p>本服务的核心职责（《需求文档》§4.1.5 药品库存管理 / 《接口文档》§2.7 / §2.3 内部接口）：
 * <ul>
 *   <li>药品基础信息维护与查询（§2.7.1/§2.7.2）</li>
 *   <li>批次库存入库 / 出库（FEFO，Redis 分布式锁防超卖，§7.1）（§2.7.3/§2.7.4）</li>
 *   <li>库存流水查询（§2.7.5）</li>
 *   <li>库存不足 / 近效期预警（§2.7.6）</li>
 *   <li>对内提供用药风险信息 / FEFO 选批 / 当前库存（架构文档 §2.3）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.medconsult.drug", "com.medconsult.common.web"})
@EnableDiscoveryClient
@MapperScan("com.medconsult.drug.**.mapper")
public class DrugServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DrugServiceApplication.class, args);
    }
}
