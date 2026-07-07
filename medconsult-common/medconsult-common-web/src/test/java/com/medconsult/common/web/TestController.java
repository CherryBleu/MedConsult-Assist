package com.medconsult.common.web;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/test/required-param")
    public String requiredParam(@RequestParam String id) {
        return "ok:" + id;
    }
}
