package com.medconsult.outpatient.schedule.template.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.outpatient.schedule.template.dto.ScheduleTemplateDTO;

import java.time.LocalDate;

/**
 * 排班模板服务（后端修改.md #16 默认排班）。
 *
 * <p>管理员维护"每周固定出诊规律"模板，并一键按模板生成未来 N 周的实际排班。
 */
public interface ScheduleTemplateService {

    /** 创建模板（template_no 自动生成，doctor_no/department_no 反查主键） */
    ScheduleTemplateDTO.SaveResponse create(ScheduleTemplateDTO.CreateRequest req);

    /** 更新模板（按 template_no，不改归属医生），仅更新非 null 字段 */
    ScheduleTemplateDTO.SaveResponse update(String templateNo, ScheduleTemplateDTO.UpdateRequest req);

    /** 删除模板（软删） */
    void delete(String templateNo);

    /** 分页查询模板，可按 doctorId(doctor_no)/departmentId(department_no)/enabled 过滤 */
    PageResult<ScheduleTemplateDTO.TemplateListItem> list(int page, int pageSize, String doctorId, String departmentId, Boolean enabled);

    /**
     * 一键生成排班（核心）：按启用模板的周几规则，遍历 [startDate, startDate+weeks*7) 逐天，
     * 命中模板 day_of_week 则生成一条 doctor_schedule；已存在的跳过（幂等可重入）。
     *
     * @param startDate 起始日期（含）
     * @param weeks     生成周数（1-8）
     * @param doctorNo  可选，限定单个医生；null 则处理全部启用模板
     */
    ScheduleTemplateDTO.ApplyResponse apply(LocalDate startDate, int weeks, String doctorNo);
}
