package com.medconsult.common.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MybatisPlusFlowTest 的 Spring Boot 入口（顶层类，确保 component scan 覆盖 Mapper）。
 *
 * <p>{@code @MapperScan} 扫描本包下的 Mapper 接口（@Mapper 注解亦可，双保险）。
 */
@SpringBootApplication
@MapperScan("com.medconsult.common.mybatis")
public class MybatisTestApplication {
}
