package com.medconsult.common.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PermissionAspectTest 的 Spring Boot 入口（顶层类）。
 * @SpringBootApplication 的 component scan 起点为本类所在包，顶层放可覆盖同包的
 * SecurityTestController / SecurityTestFilterConfig；本模块还需 @Import web 层的
 * GlobalExceptionHandler（web 包不在默认扫描范围）。
 */
@SpringBootApplication
public class SecurityTestApplication {
}
