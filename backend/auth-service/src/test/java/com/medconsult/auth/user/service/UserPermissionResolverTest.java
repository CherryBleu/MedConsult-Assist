package com.medconsult.auth.user.service;

import com.medconsult.auth.user.entity.SysPermission;
import com.medconsult.auth.user.entity.SysRole;
import com.medconsult.auth.user.entity.SysRolePermission;
import com.medconsult.auth.user.entity.SysUserRole;
import com.medconsult.auth.user.mapper.SysPermissionMapper;
import com.medconsult.auth.user.mapper.SysRoleMapper;
import com.medconsult.auth.user.mapper.SysRolePermissionMapper;
import com.medconsult.auth.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserPermissionResolver 单元测试（Mockito，不启动 Spring 上下文）。
 *
 * <p>聚焦兜底逻辑与聚合逻辑——这是 RBAC 落地的安全核心：
 * <ul>
 *   <li>无 sys_user_role 记录 → 必须兜底 scope=["*"]（绝不能落空导致全 403）</li>
 *   <li>有角色但角色无权限点 → 仍兜底 scope=["*"]</li>
 *   <li>有角色有权限点 → 返回聚合去重的 scope</li>
 *   <li>主角色判定：is_primary=1 优先</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserPermissionResolverTest {

    @Mock private SysUserRoleMapper sysUserRoleMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysRolePermissionMapper sysRolePermissionMapper;
    @Mock private SysPermissionMapper sysPermissionMapper;

    @InjectMocks private UserPermissionResolver resolver;

    @Test
    void resolve_用户无角色关联_兜底全权限() {
        // 关键安全场景：新用户漏种 sys_user_role，绝不能返回空 scope（会全 403）
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of());
        UserPermissionResolver.UserPermission r = resolver.resolve(999L);
        assertEquals(List.of("*"), r.scope(), "无角色记录必须兜底 scope=*");
        assertEquals("PATIENT", r.primaryRole());
        assertEquals(List.of("PATIENT"), r.roles());
    }

    @Test
    void resolve_userId为null_兜底全权限() {
        UserPermissionResolver.UserPermission r = resolver.resolve(null);
        assertEquals(List.of("*"), r.scope());
        assertEquals("PATIENT", r.primaryRole());
    }

    @Test
    void resolve_有角色但无权限点_仍兜底全权限() {
        // 有角色（如 PATIENT），但 sys_role_permission 没配权限点（本轮 sys_permission 留空正是此景）
        SysUserRole ur = new SysUserRole();
        ur.setUserId(1L);
        ur.setRoleId(1L);          // PATIENT
        ur.setIsPrimary(1);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(ur));

        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("PATIENT");
        role.setEnabled(1);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));

        when(sysRolePermissionMapper.selectList(any())).thenReturn(List.of()); // 无权限点关联

        UserPermissionResolver.UserPermission r = resolver.resolve(1L);
        assertEquals(List.of("PATIENT"), r.roles());
        assertEquals("PATIENT", r.primaryRole());
        assertEquals(List.of("*"), r.scope(), "有角色但无权限点配置时仍兜底 *");
    }

    @Test
    void resolve_有角色有权限点_聚合去重() {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(2L);
        ur.setRoleId(2L);          // DOCTOR
        ur.setIsPrimary(1);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(ur));

        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleCode("DOCTOR");
        role.setEnabled(1);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));

        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(10L);
        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(2L);
        rp2.setPermissionId(11L);
        when(sysRolePermissionMapper.selectList(any())).thenReturn(List.of(rp1, rp2));

        SysPermission p1 = new SysPermission();
        p1.setId(10L);
        p1.setPermissionCode("prescription:write");
        SysPermission p2 = new SysPermission();
        p2.setId(11L);
        p2.setPermissionCode("medical-record:read");
        when(sysPermissionMapper.selectList(any())).thenReturn(List.of(p1, p2));

        UserPermissionResolver.UserPermission r = resolver.resolve(2L);
        assertEquals(List.of("DOCTOR"), r.roles());
        assertEquals("DOCTOR", r.primaryRole());
        assertEquals(List.of("prescription:write", "medical-record:read"), r.scope());
    }

    @Test
    void resolve_多角色_聚合所有角色的权限并取主角色() {
        // 一人多角色：医生同时也是患者
        SysUserRole urDoctor = new SysUserRole();
        urDoctor.setUserId(3L);
        urDoctor.setRoleId(2L);    // DOCTOR
        urDoctor.setIsPrimary(1);  // 主角色是 DOCTOR
        SysUserRole urPatient = new SysUserRole();
        urPatient.setUserId(3L);
        urPatient.setRoleId(1L);   // PATIENT
        urPatient.setIsPrimary(0);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(urDoctor, urPatient));

        SysRole doctorRole = new SysRole();
        doctorRole.setId(2L);
        doctorRole.setRoleCode("DOCTOR");
        doctorRole.setEnabled(1);
        SysRole patientRole = new SysRole();
        patientRole.setId(1L);
        patientRole.setRoleCode("PATIENT");
        patientRole.setEnabled(1);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(doctorRole, patientRole));

        // 两个角色各自有权限点
        SysRolePermission rp1 = new SysRolePermission();
        rp1.setRoleId(2L);
        rp1.setPermissionId(10L);
        SysRolePermission rp2 = new SysRolePermission();
        rp2.setRoleId(1L);
        rp2.setPermissionId(20L);
        when(sysRolePermissionMapper.selectList(any())).thenReturn(List.of(rp1, rp2));

        SysPermission p1 = new SysPermission();
        p1.setId(10L);
        p1.setPermissionCode("prescription:write");
        SysPermission p2 = new SysPermission();
        p2.setId(20L);
        p2.setPermissionCode("ai:symptom-chat");
        when(sysPermissionMapper.selectList(any())).thenReturn(List.of(p1, p2));

        UserPermissionResolver.UserPermission r = resolver.resolve(3L);
        // 多角色聚合（保序去重）
        assertTrue(r.roles().contains("DOCTOR") && r.roles().contains("PATIENT"));
        assertEquals("DOCTOR", r.primaryRole(), "is_primary=1 的 DOCTOR 是主角色");
        assertEquals(2, r.scope().size());
        assertTrue(r.scope().contains("prescription:write"));
        assertTrue(r.scope().contains("ai:symptom-chat"));
    }

    @Test
    void resolve_无isPrimary标记_取第一个角色为主角色() {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(4L);
        ur.setRoleId(2L);
        ur.setIsPrimary(0);  // 无主角色标记
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(ur));

        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleCode("DOCTOR");
        role.setEnabled(1);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));
        when(sysRolePermissionMapper.selectList(any())).thenReturn(List.of());

        UserPermissionResolver.UserPermission r = resolver.resolve(4L);
        assertEquals("DOCTOR", r.primaryRole(), "无 is_primary 标记时取第一个角色");
    }
}
