package com.medconsult.common.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 入口。common-web 是 library，无 @SpringBootApplication；
 * 集成测试需要一个配置锚点，且让 component scan 覆盖 {@link TestController}。
 *
 * <p>放在顶层包（非嵌套类）确保 {@code @SpringBootApplication} 的扫描起点正确。
 */
@SpringBootApplication
public class TestApplication {
}
