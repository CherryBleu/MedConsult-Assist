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
     * 修改当前登录用户密码（#19）。
     *
     * <p>校验原密码正确后用 BCrypt 重新编码新密码；改密后旧 access token 仍有效至过期
     * （当前无服务端 access token 失效机制，前端应提示用户重新登录）。
     *
     * @param userId 当前登录用户 ID
     * @param req    原/新密码
     */
    void changePassword(Long userId, AuthDTO.ChangePasswordRequest req);

    /**
     * 修改当前登录用户手机号。
     *
     * @param userId 当前登录用户 ID
     * @param req    新手机号
     * @return 更新后的当前用户信息
     */
    AuthDTO.MeResponse updatePhone(Long userId, AuthDTO.UpdatePhoneRequest req);

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
     * 管理员创建用户（#20/#21）。
     *
     * <p>与 {@link #register} 区别：仅 HOSPITAL_ADMIN 可调用（service 内手动校验）；
     * role 校验用 {@link AuthDTO#ALLOWED_ROLES}（含管理类角色），而非自助注册白名单。
     * 支持创建所有角色（PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN）。
     * 建号后写 Redis 角色 key，否则登录后角色兜底 PATIENT 导致跳转异常。
     *
     * @param req 注册信息（account/name/phone/password/role/status）
     * @return 新用户信息
     */
    AuthDTO.UserInfo createUser(AuthDTO.RegisterRequest req);
}
