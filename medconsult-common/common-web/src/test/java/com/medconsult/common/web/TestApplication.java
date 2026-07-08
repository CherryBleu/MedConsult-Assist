package com.medconsult.common.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 入口。common-web 是 library，无 @SpringBootApplication；
 * 集成测试需要一个配置锚点，且让 component scan 覆盖 {@link TestController}。
 *
 * <p>放顶层类：@SpringBootApplication 的 component scan 起点为本类所在包，
 * 顶层（com.medconsult.common.web）能覆盖同包的 TestController / GlobalExceptionHandler。
 */
@SpringBootApplication
public class TestApplication {
}
