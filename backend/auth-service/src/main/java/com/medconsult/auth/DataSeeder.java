package com.medconsult.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 演示数据初始化器：启动时检查 sys_user 表，为空则插入演示账号并写入 Redis 角色缓存。
 *
 * <p><b>存在原因</b>：schema.sql 只建表无 sys_user 种子，部署后没有任何账号可登录；
 * 且 {@code login} 从 Redis 读角色 key（{@code medconsult:auth:role:{userId}}），
 * 读失败兜底为 PATIENT——纯 SQL 种子无法写 Redis，admin 会变成 PATIENT。
 * 用 Java 代码补种可同时：(1) 用 BCrypt 正确编码密码；(2) 写入角色 key。
 *
 * <p><b>幂等</b>：只在 sys_user 表为空时执行，已有数据则跳过（生产环境不会被清空触发）。
 *
 * <p>预置账号（密码统一 123456）：
 * <ul>
 *   <li>admin / HOSPITAL_ADMIN（userId=1）</li>
 *   <li>doctor / DOCTOR，关联 doctor_id=2001（与 outpatient-service 种子对齐）</li>
 *   <li>patient / PATIENT，关联 patient_id=3001（与 patient-service 种子对齐）</li>
 *   <li>yaofang / PHARMACY_ADMIN，关联 pharmacist_id=4001（药房管理员）</li>
 * </ul>
 *
 * <p><b>跨服务 ID 引用</b>：doctor_id/patient_id 引用 outpatient/patient 服务种子固定主键，
 * 各服务 schema.sql 用同样的固定主键常量保证一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;

    @Override
    public void run(String... args) {
        long existing = userMapper.selectCount(new QueryWrapper<>());
        if (existing > 0) {
            log.info("[DataSeeder] sys_user 已有 {} 条记录，跳过种子初始化", existing);
            return;
        }
        log.info("[DataSeeder] sys_user 为空，开始插入演示账号");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode("123456");

        // admin（医院管理员，不关联 patient/doctor 档案）
        seedUser(1L, "U0000001", "admin", "13800000000", hash, "系统管理员",
                null, null, null, "HOSPITAL_ADMIN");
        // doctor（对应 outpatient-service 种子 doctor id=2001 张心明）
        seedUser(2L, "U0000002", "doctor", "13800000002", hash, "张心明",
                null, 2001L, null, "DOCTOR");
        // patient（对应 patient-service 种子 patient id=3001 赵演示）
        seedUser(3L, "U0000003", "patient", "13800000001", hash, "赵演示",
                3001L, null, null, "PATIENT");
        // pharmacy admin（药房管理员，关联 pharmacist_id=4001）
        seedUser(4L, "U0000004", "yaofang", "13800000003", hash, "药房管理员",
                null, null, 4001L, "PHARMACY_ADMIN");

        log.info("[DataSeeder] 演示账号初始化完成：admin / doctor / patient / yaofang（密码均 123456）");
    }

    /**
     * 插入单个用户并写 Redis 角色缓存。
     */
    private void seedUser(Long id, String userNo, String account, String phone,
                          String passwordHash, String name,
                          Long patientId, Long doctorId, Long pharmacistId, String role) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUserNo(userNo);
        u.setAccount(account);
        u.setPhone(phone);
        u.setPasswordHash(passwordHash);
        u.setName(name);
        u.setPatientId(patientId);
        u.setDoctorId(doctorId);
        u.setPharmacistId(pharmacistId);
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        // 角色暂存 Redis（与 AuthServiceImpl.register 一致：冒烟期角色走 Redis，TTL 365 天）
        redis.opsForValue().set("medconsult:auth:role:" + id, role, Duration.ofDays(365));
    }
}
