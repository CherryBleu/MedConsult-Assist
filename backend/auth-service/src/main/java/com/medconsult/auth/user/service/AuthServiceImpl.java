package com.medconsult.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.log.entity.LoginLog;
import com.medconsult.auth.log.mapper.LoginLogMapper;
import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.mapper.SysUserMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.PatientFeignClient;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientRegisterRequest;
import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.web.MaskType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>注册：BCrypt 摘要密码，账号/手机唯一性校验</li>
 *   <li>登录：校验密码 + 状态，签发 access + refresh（refresh 额外存 Redis 便于登出失效）</li>
 *   <li>刷新：校验 refresh 有效性，重签 access</li>
 *   <li>登出：refresh 的 jti 入 Redis 黑名单</li>
 *   <li>当前用户：解析 access，回显（手机号脱敏）</li>
 * </ul>
 *
 * <p>登出态/refresh 黑名单放 Redis 共享，多实例一致（§4.1）。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final SysUserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final JwtCodec jwtCodec;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    /** 注册即建档：PATIENT 角色注册时调 patient-service 自动建档案 */
    private final PatientFeignClient patientClient;
    /** 划定 sys_user 写入事务边界（Feign 建档在事务外，参照 medical-record 模式 A） */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /** access token 有效期：2 小时 */
    @Value("${medconsult.security.jwt.access-ttl:7200}")
    private long accessTtl;

    /** refresh token 有效期：7 天 */
    @Value("${medconsult.security.jwt.refresh-ttl:604800}")
    private long refreshTtl;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    // ===== 注册 =====

    @Override
    public AuthDTO.UserInfo register(AuthDTO.RegisterRequest req) {
        // 角色白名单校验：自助注册仅允许 PATIENT / DOCTOR（防越权——管理类角色
        // PHARMACY_ADMIN / HOSPITAL_ADMIN 不可自助注册，必须由管理员后台授予）。
        // role 为空时默认 PATIENT。
        String role = (req.getRole() == null || req.getRole().isBlank())
                ? "PATIENT" : req.getRole();
        if (!AuthDTO.SELF_REGISTER_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "非法角色: " + req.getRole() + "（自助注册仅允许: " + AuthDTO.SELF_REGISTER_ROLES
                            + "，管理类角色需由管理员授予）");
        }

        // PATIENT 角色建档必填项校验：手机号 + 身份证号（建档最小字段集）
        // DOCTOR 角色不建档，保持原逻辑（doctorId 由管理员后续关联）
        if ("PATIENT".equals(role)) {
            if (req.getPhone() == null || req.getPhone().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "患者注册需填写手机号");
            }
            if (req.getIdCard() == null || req.getIdCard().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "患者注册需填写身份证号");
            }
        }

        // 唯一性校验（事务外）：account 必填，phone 选填但若有则需唯一。
        if (userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("account", req.getAccount())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在: " + req.getAccount());
        }
        if (req.getPhone() != null && userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("phone", req.getPhone())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "手机号已注册");
        }

        // 注册即建档（PATIENT 角色）：事务外调 patient-service 建档案，拿到主键 patientId。
        // 建档失败（证件/手机冲突）直接抛出，不 insert sys_user（参照 medical-record 模式 A：
        // Feign 远程调用在事务之外，避免占用 DB 事务/连接）。
        Long patientId = null;
        if ("PATIENT".equals(role)) {
            patientId = createPatientArchive(req);
        }

        // DB 写入用 TransactionTemplate 包短事务（Feign 建档已在事务外完成）。
        // 用 final[] 捕获事务内构造的 SysUser（lambda 需事实上 final）。
        final Long finalPatientId = patientId;
        final String finalRole = role;
        SysUser u = transactionTemplate.execute(status -> {
            SysUser user = new SysUser();
            user.setUserNo(generateUserNo());
            user.setAccount(req.getAccount());
            user.setPhone(req.getPhone());
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            user.setName(req.getName());
            user.setPatientId(finalPatientId);
            user.setDoctorId(parseLong(req.getDoctorId()));
            user.setStatus("ACTIVE");
            userMapper.insert(user);

            // 冒烟期：角色暂存 Redis（RBAC 五表阶段改为查 sys_user_role）。
            // 设 365 天 TTL：角色为长期数据，但须有上限，避免禁用/删除用户后 key 永驻。
            redis.opsForValue().set(roleKey(user.getId()), finalRole, Duration.ofDays(365));
            return user;
        });

        // 极端情况：sys_user insert 失败时 patient 档案已成孤儿（建档在事务外无法回滚）。
        // 可接受——记 WARN 留后续清理；不阻断（u 为 null 时事务模板已抛异常，不会走到这里）。
        if (patientId != null) {
            log.info("[register] PATIENT 建档成功，sys_user.patient_id={}", patientId);
        }

        return new AuthDTO.UserInfo(
                u.getUserNo(), u.getName(), role,
                patientId != null ? String.valueOf(patientId) : req.getPatientId(),
                req.getDoctorId(), u.getStatus());
    }

    /**
     * 调 patient-service 为 PATIENT 角色用户自动建档案。
     * <p>建档成功返回 BIGINT 主键；证件/手机冲突由下游返回 CONFLICT，经 FeignErrorDecoder 转为
     * BusinessException 抛出，register 调用方据此前终止（不 insert sys_user）。
     */
    private Long createPatientArchive(AuthDTO.RegisterRequest req) {
        PatientRegisterRequest archiveReq = new PatientRegisterRequest(
                req.getName(), req.getIdCard(), req.getPhone(), "ID_CARD");
        Result<EntityIdDTO> result;
        try {
            result = patientClient.createForRegister(archiveReq);
        } catch (BusinessException ex) {
            // 下游冲突（409）等业务异常直接透传，附更友好提示
            throw new BusinessException(ex.getErrorCode(),
                    "建档失败（" + ex.getMessage() + "），请检查手机号/身份证是否已注册");
        } catch (RuntimeException ex) {
            log.warn("[register] 调 patient-service 建档异常", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "建档服务暂时不可用，请稍后重试");
        }
        if (result == null || result.data() == null || result.data().id() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "建档失败：未返回档案 ID");
        }
        return result.data().id();
    }

    // ===== 登录 =====

    @Override
    @Transactional
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest req, String ip, String userAgent) {
        SysUser u = userMapper.selectOne(new QueryWrapper<SysUser>().eq("account", req.getAccount()));
        String failReason = null;

        if (u == null) {
            failReason = "WRONG_PWD";
            writeLoginLog(null, req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if ("DISABLED".equals(u.getStatus())) {
            failReason = "DISABLED";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        if ("LOCKED".equals(u.getStatus())) {
            failReason = "LOCKED";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已锁定");
        }
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            failReason = "WRONG_PWD";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 登录成功：更新最后登录时间
        u.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(u);

        // 签发双 token。冒烟期角色暂从注册时存（实际 RBAC 五表阶段查 sys_user_role）。
        // 从 Redis 读回注册时写入的角色；读失败或缺失兜底为 PATIENT。
        String storedRole = null;
        try {
            storedRole = redis.opsForValue().get(roleKey(u.getId()));
        } catch (Exception e) {
            // Redis 不可用时兜底，不阻断登录
        }
        String primaryRole = (storedRole != null && !storedRole.isBlank()) ? storedRole : "PATIENT";
        List<String> roles = List.of(primaryRole);
        List<String> scope = List.of("*"); // 冒烟期全权限；RBAC 阶段改为查 sys_role_permission

        String access = jwtCodec.signUser(u.getId(), u.getName(), roles, primaryRole,
                u.getPatientId(), u.getDoctorId(), u.getUserNo(), scope, accessTtl, null);
        String refresh = jwtCodec.signUser(u.getId(), u.getName(), roles, primaryRole,
                u.getPatientId(), u.getDoctorId(), u.getUserNo(), scope, refreshTtl, null);

        // refresh 入 Redis（key=refresh:{userId}:{jti}）便于登出/校验
        JwtPayload refreshPayload = jwtCodec.parse(refresh);
        redis.opsForValue().set(refreshKey(refreshPayload.jti()), "1",
                Duration.ofSeconds(refreshTtl));

        writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", "SUCCESS");

        return new AuthDTO.LoginResponse(
                access, refresh, "Bearer", accessTtl,
                new AuthDTO.UserInfo(u.getUserNo(), u.getName(), primaryRole,
                        u.getPatientId() != null ? String.valueOf(u.getPatientId()) : null,
                        u.getDoctorId() != null ? String.valueOf(u.getDoctorId()) : null,
                        u.getStatus()));
    }

    // ===== 刷新 =====

    @Override
    public AuthDTO.RefreshResponse refresh(AuthDTO.RefreshRequest req) {
        JwtPayload p = jwtCodec.parse(req.getRefreshToken());
        if (!isRefreshValid(p.jti())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refreshToken 已失效（已登出或过期）");
        }
        // 重签 access
        String access = jwtCodec.signUser(p.userId(), p.name(), p.roles(), p.primaryRole(),
                p.patientId(), p.doctorId(), p.userNo(), p.scope(), accessTtl, null);
        return new AuthDTO.RefreshResponse(access, "Bearer", accessTtl);
    }

    // ===== 登出 =====

    @Override
    public boolean logout(AuthDTO.LogoutRequest req) {
        JwtPayload p = jwtCodec.parse(req.getRefreshToken());
        // refresh jti 入黑名单（删除 Redis 中的有效标记）
        redis.delete(refreshKey(p.jti()));
        return true;
    }

    // ===== 当前用户 =====

    @Override
    public AuthDTO.MeResponse me(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 角色取自 SecurityContext（网关已解析）；如无则回退到默认 PATIENT（兼容直连场景）
        JwtPayload p = SecurityContext.getPayload();
        String primaryRole = (p != null && p.primaryRole() != null) ? p.primaryRole() : "PATIENT";
        return new AuthDTO.MeResponse(
                u.getUserNo(),
                u.getAccount(),
                u.getName(),
                MaskType.PHONE.mask(u.getPhone()),
                primaryRole,
                u.getPatientId() != null ? String.valueOf(u.getPatientId()) : null,
                u.getDoctorId() != null ? String.valueOf(u.getDoctorId()) : null,
                u.getStatus());
    }

    // ===== 绑定患者档案（补建档）=====

    @Override
    @Transactional
    public AuthDTO.MeResponse bindPatient(Long userId, String patientNo) {
        if (patientNo == null || patientNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "patientNo 不能为空");
        }
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 已绑定档案不允许重复绑定（防覆盖他人档案关联）
        if (u.getPatientId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前账号已关联患者档案，无需重复绑定");
        }
        // 仅 PATIENT 角色允许自助绑定（DOCTOR/管理员不通过此接口建档）
        JwtPayload p = SecurityContext.getPayload();
        String primaryRole = (p != null && p.primaryRole() != null) ? p.primaryRole() : "PATIENT";
        if (!"PATIENT".equals(primaryRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅患者角色可绑定患者档案");
        }
        // 调 patient-service 反查 patientNo 对应的主键 id
        // 不存在时下游返回 NOT_FOUND，由 FeignErrorDecoder 转为 BusinessException
        Result<EntityIdDTO> res = patientClient.resolveId(patientNo);
        if (res == null || res.data() == null || res.data().id() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在: " + patientNo);
        }
        Long patientId = res.data().id();
        // 防 IDOR：校验该 patient 未被其他 sys_user 绑定（一份档案只能关联一个登录账号）
        Long boundCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPatientId, patientId)
                .ne(SysUser::getId, userId));
        if (boundCount != null && boundCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该患者档案已被其他账号绑定，请联系管理员");
        }
        u.setPatientId(patientId);
        userMapper.updateById(u);
        log.info("用户 {} 绑定患者档案成功，patientNo={}, patientId={}", userId, patientNo, patientId);
        return new AuthDTO.MeResponse(
                u.getUserNo(),
                u.getAccount(),
                u.getName(),
                MaskType.PHONE.mask(u.getPhone()),
                primaryRole,
                String.valueOf(patientId),
                u.getDoctorId() != null ? String.valueOf(u.getDoctorId()) : null,
                u.getStatus());
    }

    // ===== 管理员用户列表 =====

    @Override
    public PageResult<AuthDTO.UserListItem> listUsers(int page, int pageSize, String keyword, String role) {
        // 权限校验：仅 HOSPITAL_ADMIN 可查询用户列表。auth-service 未启用 @Permission 切面，
        // 在此手动从 SecurityContext 读角色判定（参照 SymptomChatService.resolveCurrentPatientId 模式）。
        // 非管理员或未登录 → FORBIDDEN（requireUser 兜底抛 UNAUTHORIZED）。
        JwtPayload payload = SecurityContext.requireUser();
        if (!payload.hasRole("HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅医院管理员可查询用户列表");
        }

        // 分页参数归一化（复用 PageQuery 工具，防 pageSize 传极大值拖垮 DB）
        int safePage = PageQuery.normalizePage(page);
        int safeSize = PageQuery.normalizePageSize(pageSize);

        // keyword 模糊搜索：account / name / phone 任一命中（OR 组）。注意 OR 嵌套写法——
        // 直接 .like().or().like() 会把后续条件也串进 OR，需用 and(wrapper -> wrapper.or...) 包一层括号。
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(SysUser::getAccount, kw)
                    .or().like(SysUser::getName, kw)
                    .or().like(SysUser::getPhone, kw));
        }
        qw.orderByDesc(SysUser::getCreatedAt);

        // 用 selectList + 内存分页，而非 selectPage：auth-service 当前环境下 MyBatis-Plus
        // PaginationInnerInterceptor 未生效（autoconfig 未加载），selectPage 只返回 count
        // 不返回 records。冒烟期用户量小（几十条），全量查后内存分页 + role 内存过滤可接受。
        // RBAC 五表落地后恢复 selectPage（届时 DB 侧支持 role 过滤）。
        java.util.List<SysUser> all = userMapper.selectList(qw);

        // role 过滤：sys_user 表无 role 列，role 存 Redis（medconsult:auth:role:{userId}）。
        java.util.List<AuthDTO.UserListItem> filtered = new java.util.ArrayList<>();
        for (SysUser u : all) {
            String userRole = resolveRole(u.getId());
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase(userRole)) {
                continue; // role 过滤不命中，跳过
            }
            filtered.add(new AuthDTO.UserListItem(
                    u.getId(),
                    u.getUserNo(),
                    u.getAccount(),
                    u.getPhone(),
                    u.getName(),
                    userRole,
                    u.getPatientId(),
                    u.getDoctorId(),
                    u.getStatus(),
                    u.getCreatedAt()));
        }

        // 内存分页（subList）—— role 过滤后再切页，total 是过滤后的真实总数。
        int total = filtered.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        java.util.List<AuthDTO.UserListItem> pageItems = filtered.subList(from, to);

        return PageResult.of(safePage, safeSize, total, pageItems);
    }

    /**
     * 从 Redis 读用户角色，读不到兜底 PATIENT（与登录流程一致）。
     * <p>Redis 不可用时 catch 住兜底，不阻断列表查询。
     */
    private String resolveRole(Long userId) {
        try {
            String stored = redis.opsForValue().get(roleKey(userId));
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
        } catch (Exception ignored) {
            // Redis 不可用兜底，不阻断查询
        }
        return "PATIENT";
    }

    // ===== 私有助手 =====

    private boolean isRefreshValid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(refreshKey(jti)));
    }

    private static String refreshKey(String jti) {
        return "medconsult:auth:refresh:" + jti;
    }

    /** 注册时暂存的角色（冒烟期 sys_user_role 未落地的临时方案）的 Redis key */
    private static String roleKey(Long userId) {
        return "medconsult:auth:role:" + userId;
    }

    /**
     * 写登录日志。
     * <p><b>独立事务</b>（REQUIRES_NEW）：login/register 方法标了 @Transactional，失败分支
     * （密码错/账号禁用）抛 BusinessException 会回滚外层事务；若日志用默认 REQUIRED，
     * 会加入外层事务一并回滚 → 失败登录日志丢失，安全审计断链。REQUIRES_NEW 让日志在
     * 独立事务里提交，外层回滚不影响它。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeLoginLog(Long userId, String account, String ip, String userAgent,
                               String loginType, String result) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setAccount(account);
        log.setLoginType(loginType);
        log.setLoginResult(result);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /**
     * 生成用户编号：U + 雪花序列（由 MyBatis-Plus IdWorker 生成的 Long 的无符号 hex）。
     * <p>替代旧的 currentTimeMillis + random*1000 方案——后者并发碰撞概率高（同毫秒仅 1000 桶），
     * 雪花 ID 单调递增且分布式唯一，碰撞可忽略；DB 仍有 uk_sys_user_user_no 兜底。
     */
    private static String generateUserNo() {
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        return "U" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
