package com.medconsult.common.security;

import com.medconsult.common.core.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PermissionAspectTest 的被测 Controller（顶层类，确保被 component scan）。
 */
@RestController
public class SecurityTestController {

    @GetMapping("/perm/protected")
    @Permission(code = "prescription:write")
    public Result<String> protectedEndpoint() {
        return Result.ok("ok");
    }

    @GetMapping("/perm/doctor-only")
    @Permission(code = "prescription:write", roles = {"DOCTOR", "PHARMACY_ADMIN"})
    public Result<String> doctorOnly() {
        return Result.ok("ok");
    }

    @GetMapping("/perm/login-only")
    @Permission
    public Result<String> loginOnly() {
        return Result.ok("ok");
    }
}
