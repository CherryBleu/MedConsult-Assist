package com.medconsult.auth.user.service;

import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.common.core.PageResult;

/**
 * 认证服务接口（对齐《接口文档》§2.1）。
 *
 * <p>方法对应 §2.1.1~§2.1.5 的 5 个对外接口：
 * register / login / refresh / logout / me。
 * 另含管理员用户列表查询（用户管理页）。
 */
public interface AuthService {

    AuthDTO.UserInfo register(AuthDTO.RegisterRequest req);

    AuthDTO.LoginResponse login(AuthDTO.LoginRequest req, String ip, String userAgent);

    AuthDTO.RefreshResponse refresh(AuthDTO.RefreshRequest req);

    boolean logout(AuthDTO.LogoutRequest req);

    /**
     * 当前用户信息。
     * @param userId 当前登录用户 ID（由 Controller 从 SecurityContext 传入）
     */
    AuthDTO.MeResponse me(Long userId);

    /**
     * 绑定患者档案到当前登录用户（补建档场景）。
     *
     * <p>用于历史脏账号（sys_user.patient_id 为 NULL）补全患者档案关联。
     * 前端先调 POST /patients 建档拿到 patientNo，再调本接口绑定。
     * 仅 PATIENT 角色且当前 patient_id 为 null 时允许；已绑定则拒绝。
     *
     * @param userId    当前登录用户 ID
     * @param patientNo 患者档案编号（patient_no）
     * @return 绑定后的当前用户信息（含新的 patientId）
     */
    AuthDTO.MeResponse bindPatient(Long userId, String patientNo);

    /**
     * 管理员查询用户列表（用户管理页）。
     *
     * <p>权限：仅 HOSPITAL_ADMIN 可调用（service 内手动校验，不依赖 @Permission 切面）。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword  按账号/姓名/手机号模糊搜索（可空）
     * @param role     角色过滤（可空）
     * @return 分页用户列表（不含密码摘要）
     */
    PageResult<AuthDTO.UserListItem> listUsers(int page, int pageSize, String keyword, String role);
}
