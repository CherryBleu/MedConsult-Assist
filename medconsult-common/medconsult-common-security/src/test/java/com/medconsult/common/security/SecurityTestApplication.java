package com.medconsult.common.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PermissionAspectTest 的 Spring Boot 入口（顶层类，避免嵌套 @SpringBootApplication
 * 致 component scan 起点异常——common-web 同款踩坑）。
 */
@SpringBootApplication
public class SecurityTestApplication {
}
