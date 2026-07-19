package com.medconsult.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.auth.user.entity.SysPermission;
import com.medconsult.auth.user.entity.SysRole;
import com.medconsult.auth.user.entity.SysRolePermission;
import com.medconsult.auth.user.entity.SysUserRole;
import com.medconsult.auth.user.mapper.SysPermissionMapper;
import com.medconsult.auth.user.mapper.SysRoleMapper;
import com.medconsult.auth.user.mapper.SysRolePermissionMapper;
import com.medconsult.auth.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RBAC 五表查询服务，登录与内部鉴权用它读取当前角色和权限事实源。
 */
@Service
@RequiredArgsConstructor
public class RbacQueryService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    public Optional<RbacAccess> findUserAccess(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<SysUserRole> assignments = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .orderByDesc(SysUserRole::getIsPrimary)
                .orderByAsc(SysUserRole::getId));
        if (assignments.isEmpty()) {
            return Optional.empty();
        }

        List<Long> roleIds = assignments.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return Optional.of(RbacAccess.empty());
        }
        Map<Long, SysRole> rolesById = roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getRoleCode() != null && !role.getRoleCode().isBlank())
                .filter(role -> role.getEnabled() == null || role.getEnabled() == 1)
                .collect(Collectors.toMap(SysRole::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<Long> enabledRoleIds = assignments.stream()
                .map(SysUserRole::getRoleId)
                .filter(rolesById::containsKey)
                .distinct()
                .toList();
        List<String> roles = enabledRoleIds.stream()
                .map(rolesById::get)
                .map(SysRole::getRoleCode)
                .distinct()
                .toList();
        if (roles.isEmpty()) {
            return Optional.of(RbacAccess.empty());
        }

        return Optional.of(new RbacAccess(roles, roles.getFirst(), resolvePermissions(enabledRoleIds)));
    }

    private List<String> resolvePermissions(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRolePermission> links = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roleIds)
                .orderByAsc(SysRolePermission::getRoleId)
                .orderByAsc(SysRolePermission::getId));
        if (links.isEmpty()) {
            return List.of();
        }

        List<Long> permissionIds = links.stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .toList();
        Map<Long, SysPermission> permissionsById = permissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> permission.getPermissionCode() != null
                        && !permission.getPermissionCode().isBlank())
                .collect(Collectors.toMap(SysPermission::getId, Function.identity(), (a, b) -> a));

        return links.stream()
                .map(SysRolePermission::getPermissionId)
                .map(permissionsById::get)
                .filter(permission -> permission != null)
                .map(SysPermission::getPermissionCode)
                .distinct()
                .toList();
    }

    public record RbacAccess(List<String> roles, String primaryRole, List<String> scope) {
        static RbacAccess empty() {
            return new RbacAccess(List.of(), null, List.of());
        }

        public boolean hasUsableRole() {
            return roles != null && !roles.isEmpty()
                    && primaryRole != null && !primaryRole.isBlank();
        }
    }
}
