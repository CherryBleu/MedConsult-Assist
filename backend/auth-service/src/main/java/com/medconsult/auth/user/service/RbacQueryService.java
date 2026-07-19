package com.medconsult.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medconsult.auth.user.entity.SysRole;
import com.medconsult.auth.user.entity.SysRolePermission;
import com.medconsult.auth.user.entity.SysUserRole;
import com.medconsult.auth.user.mapper.SysRoleMapper;
import com.medconsult.auth.user.mapper.SysRolePermissionMapper;
import com.medconsult.auth.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    private final NamedParameterJdbcTemplate jdbc;
    private final DataSource dataSource;

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
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> permissionsById = queryPermissionCodes(permissionIds);

        return links.stream()
                .map(SysRolePermission::getPermissionId)
                .map(permissionsById::get)
                .filter(permissionCode -> permissionCode != null && !permissionCode.isBlank())
                .distinct()
                .toList();
    }

    private Map<Long, String> queryPermissionCodes(List<Long> permissionIds) {
        String enabledFilter = permissionEnabledColumnExists() ? " AND enabled = 1" : "";
        String sql = """
                SELECT id, permission_code
                FROM sys_permission
                WHERE deleted = 0
                  AND id IN (:permissionIds)
                """ + enabledFilter;
        MapSqlParameterSource params = new MapSqlParameterSource("permissionIds", permissionIds);
        return jdbc.query(sql, params, rs -> {
            Map<Long, String> permissions = new LinkedHashMap<>();
            while (rs.next()) {
                String permissionCode = rs.getString("permission_code");
                if (permissionCode != null && !permissionCode.isBlank()) {
                    permissions.putIfAbsent(rs.getLong("id"), permissionCode);
                }
            }
            return permissions;
        });
    }

    private boolean permissionEnabledColumnExists() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            return hasColumn(metadata, catalog, "sys_permission", "enabled")
                    || hasColumn(metadata, catalog, "SYS_PERMISSION", "ENABLED")
                    || hasColumn(metadata, null, "sys_permission", "enabled")
                    || hasColumn(metadata, null, "SYS_PERMISSION", "ENABLED");
        } catch (SQLException ex) {
            return false;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean hasColumn(DatabaseMetaData metadata, String catalog, String table, String column)
            throws SQLException {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            return columns.next();
        }
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
