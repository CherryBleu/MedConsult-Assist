package com.medconsult.common.mq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MqFlowTest 的 Spring Boot 入口（顶层类）。
 */
@SpringBootApplication
@MapperScan("com.medconsult.common.mq")
public class MqTestApplication {
}
