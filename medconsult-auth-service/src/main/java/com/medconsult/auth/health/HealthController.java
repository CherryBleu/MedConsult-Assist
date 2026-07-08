package com.medconsult.auth.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点（冒烟用）。真实健康检查走 /actuator/health。
 */
@RestController
public class HealthController {

    @GetMapping("/health/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "medconsult-auth-service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        );
    }
}
