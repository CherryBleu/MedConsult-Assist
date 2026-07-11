package com.medconsult.outpatient.schedule.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.outpatient.schedule.dto.ScheduleDTO;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;

import java.time.LocalDate;
import java.util.List;

/**
 * 医生排班服务接口（对齐《接口文档》§2.4）。
 *
 * <p>方法对应 4 个对外接口（create/list/available/updateStatus）。
 * <p>内部 {@link #requireByNo(String)} 供 appointment 服务校验排班可用。
 */
public interface ScheduleService {

    /** §2.4.1 创建排班 */
    ScheduleDTO.CreateResponse create(ScheduleDTO.CreateRequest req);

    /** §2.4.2 分页查询排班，可按 departmentId(department_no)/dateFrom/dateTo 过滤 */
    PageResult<ScheduleDTO.ListItem> list(int page, int pageSize, String departmentId,
                                          LocalDate dateFrom, LocalDate dateTo);

    /** §2.4.3 查询可预约号源（status=AVAILABLE 且 remaining>0），按 date 过滤 */
    List<ScheduleDTO.AvailableItem> available(String departmentId, LocalDate date);

    /** §2.4.4 更新排班状态，SUSPENDED 时返回 notifiedAppointments */
    ScheduleDTO.StatusResponse updateStatus(String scheduleNo, ScheduleDTO.StatusUpdateRequest req);

    /**
     * 按排班编号查询，未找到抛 NOT_FOUND。
     * <p>供 AppointmentService 创建预约时校验排班。
     */
    DoctorSchedule requireByNo(String scheduleNo);
}
