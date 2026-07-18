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
     * 后端自动用 sys_user 已有的 name/phone 建档，前端只需补充 idCard 等缺失字段。
     * 绑定成功后重签 JWT（含新的 patientId），前端存储新 token 后无需重新登录。
     *
     * @param userId 当前登录用户 ID
     * @param req    建档补充信息（idCard/gender/birthDate）
     * @return 含新 token 和用户信息的响应
     */
    AuthDTO.BindPatientResponse bindPatient(Long userId, AuthDTO.BindPatientRequest req);

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

    /**
     * 管理员创建账号（用户管理页/药房管理员管理页）。
     *
     * <p>与 register 区别：允许 HOSPITAL_ADMIN / PHARMACY_ADMIN 管理类角色
     * （register 仅允许 PATIENT / DOCTOR 自助注册）；不建档（非患者）。
     * 权限：仅 HOSPITAL_ADMIN。
     */
    AuthDTO.UserInfo createUserByAdmin(AuthDTO.RegisterRequest req);

    /**
     * 管理员删除用户（软删）。
     *
     * <p>约束：不能删自己（防自锁）；患者软删后档案留存。
     * 权限：仅 HOSPITAL_ADMIN。
     */
    void deleteUser(Long userId);
}
