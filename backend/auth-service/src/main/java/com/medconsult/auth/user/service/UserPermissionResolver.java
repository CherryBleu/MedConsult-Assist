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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户权限聚合查询（RBAC 五表落地后的权威源，《修改建议》§2.3）。
 *
 * <p>登录时由 AuthServiceImpl 调用，把"用户有哪些角色 + 哪些权限点"聚合为
 * JWT 里的 {@code roles} / {@code primaryRole} / {@code scope}。
 *
 * <p><b>兜底策略（关键安全约束）</b>：用户在 sys_user_role 无记录时，
 * 返回 scope={@code ["*"]}、primaryRole=PATIENT，与冒烟期行为逐字节一致——
 * 避免新建用户漏种角色导致登录后所有带 @Permission(code) 的接口批量 403 回归。
 * JwtPayload.hasPermission 对空 scope 返回 false（全拒绝），所以<b>绝不能让 scope 落空</b>。
 *
 * <p>查询路径：sys_user_role → sys_role（roles/primaryRole）；
 * sys_role_permission → sys_permission（scope 聚合）。
 * 逻辑删除（deleted=0）由 MyBatis-Plus TableLogic 自动过滤。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPermissionResolver {

    /** 兜底：用户无 RBAC 记录时的全权限 scope（对齐冒烟期行为） */
    public static final List<String> FALLBACK_SCOPE = List.of("*");
    /** 兜底：用户无 RBAC 记录时的默认主角色 */
    public static final String FALLBACK_PRIMARY_ROLE = "PATIENT";

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;

    /**
     * 聚合查询用户的角色与权限点。
     *
     * @param userId sys_user.id
     * @return 不可变的 UserPermission（roles/primaryRole/scope）；无记录时返回兜底值
     */
    public UserPermission resolve(Long userId) {
        if (userId == null) {
            return fallback();
        }

        // 1. 查用户的所有角色关联
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles == null || userRoles.isEmpty()) {
            // 无角色记录 → 兜底全权限（关键：不能返回空 scope，否则全 403）
            log.debug("用户 {} 无 sys_user_role 记录，兜底 scope=* ", userId);
            return fallback();
        }

        // 2. 反查角色码
        Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds).eq(SysRole::getEnabled, 1));
        if (roles.isEmpty()) {
            log.debug("用户 {} 的角色关联对应的 sys_role 全部失效/删除，兜底 scope=* ", userId);
            return fallback();
        }
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).filter(c -> c != null && !c.isBlank()).distinct().toList();

        // 3. 主角色：is_primary=1 优先，否则取第一个
        String primaryRole = userRoles.stream()
                .filter(ur -> ur.getIsPrimary() != null && ur.getIsPrimary() == 1)
                .map(SysUserRole::getRoleId)
                .findFirst()
                .flatMap(rid -> roles.stream().filter(r -> r.getId().equals(rid)).map(SysRole::getRoleCode).findFirst())
                .orElse(roleCodes.isEmpty() ? FALLBACK_PRIMARY_ROLE : roleCodes.get(0));

        // 4. 聚合权限点：sys_role_permission → sys_permission
        List<SysRolePermission> rolePerms = sysRolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, roleIds));
        List<String> scope = aggregateScope(rolePerms);

        // 5. scope 仍可能为空（有角色但角色没配权限点）→ 仍兜底 *
        if (scope.isEmpty()) {
            return new UserPermission(roleCodes, primaryRole, FALLBACK_SCOPE);
        }
        return new UserPermission(roleCodes, primaryRole, scope);
    }

    /** 聚合权限点 code（去重、保序） */
    private List<String> aggregateScope(List<SysRolePermission> rolePerms) {
        if (rolePerms == null || rolePerms.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> permIds = rolePerms.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toSet());
        List<SysPermission> perms = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().in(SysPermission::getId, permIds));
        return perms.stream()
                .map(SysPermission::getPermissionCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
    }

    private UserPermission fallback() {
        return new UserPermission(List.of(FALLBACK_PRIMARY_ROLE), FALLBACK_PRIMARY_ROLE, FALLBACK_SCOPE);
    }

    /**
     * 用户权限聚合结果。record 保证不可变。
     * - roles：角色码列表（登录写入 JWT roles claim）
     * - primaryRole：主角色（JWT primary claim）
     * - scope：权限点列表（JWT scope claim）；含 "*" 视为全权限
     */
    public record UserPermission(List<String> roles, String primaryRole, List<String> scope) {
        public UserPermission {
            if (roles == null) roles = new ArrayList<>();
            if (scope == null) scope = new ArrayList<>();
        }
    }
}
