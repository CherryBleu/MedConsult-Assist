package com.medconsult.common.security;

/**
 * 数据范围（架构文档 §4.3、§2.3 表 3）。
 *
 * <p>权限矩阵里的"操作-数据范围"维度。{@code @Permission(dataScope=...)} 声明后，
 * 由 {@link PermissionAspect} / MyBatis 拦截器据此注入查询条件。
 */
public enum DataScope {

    /** 全部数据（HOSPITAL_ADMIN） */
    ALL,

    /** 本科室数据（科室管理员） */
    DEPT,

    /** 仅本人数据（PATIENT 查自身） */
    SELF,

    /** 接诊范围内的数据（DOCTOR 查自己接诊的患者，§2.3 权限矩阵） */
    ASSIGNED
}
