package com.medconsult.common.web;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GlobalWebFlowTest 的被测 Controller 桩。放顶层确保被 component scan 扫到。
 */
@RestController
public class TestController {

    @GetMapping("/test/pojo")
    public SampleDTO pojo() {
        return new SampleDTO();
    }

    @GetMapping("/test/result")
    public Result<String> result() {
        return Result.ok("payload");
    }

    @GetMapping("/test/not-found")
    public String notFound() {
        throw new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在: P001");
    }

    @GetMapping("/test/conflict")
    public String conflict() {
        throw new BusinessException(ErrorCode.CONFLICT, "号源已被抢占");
    }

    @GetMapping("/test/boom")
    public String boom() {
        throw new IllegalStateException("DB connection lost");
    }

    @GetMapping(value = "/actuator/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> actuatorHealth() {
        return Map.of("status", "UP");
    }

    @GetMapping("/test/required-param")
    public String requiredParam(@RequestParam String id) {
        return "ok:" + id;
    }

    /**
     * 回归测试桩：返回 String，produces JSON。
     * 验证 ResultBodyAdvice 不把 String 包成 Result（否则 StringHttpMessageConverter 写 Result 抛异常）。
     * 期望：200 + body 原样为 "plain-text"，不被包装。
     */
    @GetMapping(value = "/test/plain-string", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public String plainString() {
        return "plain-text";
    }
}
