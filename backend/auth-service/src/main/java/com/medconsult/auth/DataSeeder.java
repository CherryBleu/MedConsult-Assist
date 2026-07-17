package com.medconsult.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.user.entity.SysRole;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.entity.SysUserRole;
import com.medconsult.auth.user.mapper.SysRoleMapper;
import com.medconsult.auth.user.mapper.SysUserMapper;
import com.medconsult.auth.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化器：启动时检查 sys_user / sys_user_role 表，按需插入演示账号与角色映射。
 *
 * <p><b>RBAC 落地后变更</b>：角色不再写 Redis（{@code medconsult:auth:role:{userId}}），
 * 改写 sys_user_role（权威源），登录时 UserPermissionResolver 据此聚合权限。
 *
 * <p><b>存在原因</b>：schema.sql 只建表无 sys_user 种子，部署后没有账号可登录；
 * 用 Java 补种可同时：(1) 用 BCrypt 正确编码密码；(2) 写入 sys_user_role 角色映射。
 *
 * <p><b>幂等（可重入）</b>：
 * <ul>
 *   <li>sys_user 为空时插入 3 个演示账号（admin/doctor/patient，密码 123456）。</li>
 *   <li>sys_user_role 为空时补种角色映射（即使老库已有 sys_user 但缺角色映射，也能补上）。</li>
 *   <li>角色映射按 role_code 反查 sys_role.id（sys_role 由 schema.sql 的 INSERT IGNORE 种入）。</li>
 * </ul>
 *
 * <p>预置账号（密码统一 123456）：
 * <ul>
 *   <li>admin / HOSPITAL_ADMIN（userId=1）</li>
 *   <li>doctor / DOCTOR，关联 doctor_id=2001（与 outpatient-service 种子对齐）</li>
 *   <li>patient / PATIENT，关联 patient_id=3001（与 patient-service 种子对齐）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public void run(String... args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode("123456");

        // 1. sys_user 为空 → 插入演示账号（仅首次部署触发）
        long existingUsers = userMapper.selectCount(new QueryWrapper<>());
        if (existingUsers == 0) {
            log.info("[DataSeeder] sys_user 为空，开始插入演示账号");
            seedUser(1L, "U0000001", "admin", "13800000000", hash, "系统管理员",
                    null, null);
            seedUser(2L, "U0000002", "doctor", "13800000002", hash, "张心明",
                    null, 2001L);
            seedUser(3L, "U0000003", "patient", "13800000001", hash, "赵演示",
                    3001L, null);
            log.info("[DataSeeder] 演示账号初始化完成：admin / doctor / patient（密码均 123456）");
        } else {
            log.info("[DataSeeder] sys_user 已有 {} 条记录，跳过账号种子", existingUsers);
        }

        // 2. sys_user_role 为空 → 补种角色映射（可重入：老库已有 sys_user 但缺角色映射也能补）
        long existingRoles = sysUserRoleMapper.selectCount(new QueryWrapper<>());
        if (existingRoles == 0) {
            log.info("[DataSeeder] sys_user_role 为空，补种角色映射");
            assignRole(1L, "HOSPITAL_ADMIN", true);
            assignRole(2L, "DOCTOR", true);
            assignRole(3L, "PATIENT", true);
            log.info("[DataSeeder] 角色映射补种完成");
        } else {
            log.info("[DataSeeder] sys_user_role 已有 {} 条记录，跳过角色映射种子", existingRoles);
        }
    }

    /** 插入单个用户（不再写 Redis 角色缓存，角色走 sys_user_role） */
    private void seedUser(Long id, String userNo, String account, String phone,
                          String passwordHash, String name,
                          Long patientId, Long doctorId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUserNo(userNo);
        u.setAccount(account);
        u.setPhone(phone);
        u.setPasswordHash(passwordHash);
        u.setName(name);
        u.setPatientId(patientId);
        u.setDoctorId(doctorId);
        u.setStatus("ACTIVE");
        userMapper.insert(u);
    }

    /** 按角色编码反查 sys_role.id，挂到 sys_user_role */
    private void assignRole(Long userId, String roleCode, boolean isPrimary) {
        SysRole role = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            log.warn("[DataSeeder] 角色 {} 在 sys_role 不存在，跳过（userId={}）", roleCode, userId);
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        ur.setIsPrimary(isPrimary ? 1 : 0);
        sysUserRoleMapper.insert(ur);
    }
}
