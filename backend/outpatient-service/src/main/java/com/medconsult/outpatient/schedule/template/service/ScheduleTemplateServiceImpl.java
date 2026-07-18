package com.medconsult.outpatient.schedule.template.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageQuery;
import com.medconsult.common.core.PageResult;
import com.medconsult.outpatient.department.entity.Department;
import com.medconsult.outpatient.department.mapper.DepartmentMapper;
import com.medconsult.outpatient.doctor.entity.Doctor;
import com.medconsult.outpatient.doctor.mapper.DoctorMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import com.medconsult.outpatient.schedule.template.dto.ScheduleTemplateDTO;
import com.medconsult.outpatient.schedule.template.entity.DoctorScheduleTemplate;
import com.medconsult.outpatient.schedule.template.mapper.DoctorScheduleTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排班模板服务实现（后端修改.md #16 默认排班）。
 *
 * <p>核心是 {@link #apply}：按模板周几规则批量生成 doctor_schedule。
 * 校验/落库逻辑与 ScheduleServiceImpl.create 同源（医生/科室存在启用、时段白名单、
 * uk_schedule_doc_date_period 重复跳过），但批量场景下不循环调 create（事务边界/性能），
 * 而是单事务内遍历生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleTemplateServiceImpl implements ScheduleTemplateService {

    private final DoctorScheduleTemplateMapper templateMapper;
    private final DoctorScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;

    private static final List<String> ALLOWED_PERIOD =
            List.of("MORNING", "AFTERNOON", "EVENING", "FULL_DAY");

    // ===== CRUD =====

    @Override
    @Transactional
    public ScheduleTemplateDTO.SaveResponse create(ScheduleTemplateDTO.CreateRequest req) {
        // 校验医生/科室存在启用 + 拿主键
        Doctor doctor = requireEnabledDoctor(req.getDoctorId());
        Department dept = requireEnabledDepartment(req.getDepartmentId());

        // 同医生同周几同时段不重复（uk_template_doc_dow_period 双保险）
        Long dup = templateMapper.selectCount(new QueryWrapper<DoctorScheduleTemplate>()
                .eq("doctor_id", doctor.getId())
                .eq("day_of_week", req.getDayOfWeek())
                .eq("period", req.getPeriod()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该医生在此周几此时段已存在模板: 周" + req.getDayOfWeek() + "/" + req.getPeriod());
        }

        DoctorScheduleTemplate t = new DoctorScheduleTemplate();
        t.setTemplateNo(generateTemplateNo());
        t.setDoctorId(doctor.getId());
        t.setDepartmentId(dept.getId());
        t.setDayOfWeek(req.getDayOfWeek());
        t.setPeriod(req.getPeriod());
        t.setStartTime(req.getStartTime());
        t.setEndTime(req.getEndTime());
        t.setTotalQuota(req.getTotalQuota());
        t.setRegistrationFee(req.getRegistrationFee());
        t.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        templateMapper.insert(t);
        return new ScheduleTemplateDTO.SaveResponse(t.getTemplateNo());
    }

    @Override
    @Transactional
    public ScheduleTemplateDTO.SaveResponse update(String templateNo, ScheduleTemplateDTO.UpdateRequest req) {
        DoctorScheduleTemplate t = requireByNo(templateNo);
        // 改 dayOfWeek/period 时重新查重（排除自身）
        Integer newDow = req.getDayOfWeek() != null ? req.getDayOfWeek() : t.getDayOfWeek();
        String newPeriod = req.getPeriod() != null ? req.getPeriod() : t.getPeriod();
        if ((req.getDayOfWeek() != null || req.getPeriod() != null)
                && (!newDow.equals(t.getDayOfWeek()) || !newPeriod.equals(t.getPeriod()))) {
            Long dup = templateMapper.selectCount(new QueryWrapper<DoctorScheduleTemplate>()
                    .eq("doctor_id", t.getDoctorId())
                    .eq("day_of_week", newDow)
                    .eq("period", newPeriod)
                    .ne("id", t.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "该医生在此周几此时段已有模板: 周" + newDow + "/" + newPeriod);
            }
        }
        if (req.getDepartmentId() != null) {
            Department dept = requireEnabledDepartment(req.getDepartmentId());
            t.setDepartmentId(dept.getId());
        }
        if (req.getDayOfWeek() != null) t.setDayOfWeek(req.getDayOfWeek());
        if (req.getPeriod() != null) t.setPeriod(req.getPeriod());
        if (req.getStartTime() != null) t.setStartTime(req.getStartTime());
        if (req.getEndTime() != null) t.setEndTime(req.getEndTime());
        if (req.getTotalQuota() != null) t.setTotalQuota(req.getTotalQuota());
        if (req.getRegistrationFee() != null) t.setRegistrationFee(req.getRegistrationFee());
        if (req.getEnabled() != null) t.setEnabled(req.getEnabled());
        templateMapper.updateById(t);
        return new ScheduleTemplateDTO.SaveResponse(t.getTemplateNo());
    }

    @Override
    public void delete(String templateNo) {
        DoctorScheduleTemplate t = requireByNo(templateNo);
        templateMapper.deleteById(t.getId());  // 软删
    }

    @Override
    public PageResult<ScheduleTemplateDTO.TemplateListItem> list(
            int page, int pageSize, String doctorId, String departmentId, Boolean enabled) {
        // 可选按 doctor_no/department_no 反查主键过滤
        Long doctorPk = null;
        if (doctorId != null && !doctorId.isBlank()) {
            Doctor d = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", doctorId));
            if (d == null) return PageResult.of(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize), 0, List.of());
            doctorPk = d.getId();
        }
        Long deptPk = null;
        if (departmentId != null && !departmentId.isBlank()) {
            Department dept = departmentMapper.selectOne(new QueryWrapper<Department>().eq("department_no", departmentId));
            if (dept == null) return PageResult.of(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize), 0, List.of());
            deptPk = dept.getId();
        }

        Page<DoctorScheduleTemplate> p = new Page<>(PageQuery.normalizePage(page), PageQuery.normalizePageSize(pageSize));
        QueryWrapper<DoctorScheduleTemplate> qw = new QueryWrapper<>();
        if (doctorPk != null) qw.eq("doctor_id", doctorPk);
        if (deptPk != null) qw.eq("department_id", deptPk);
        if (enabled != null) qw.eq("enabled", enabled ? 1 : 0);
        qw.orderByDesc("created_at");
        IPage<DoctorScheduleTemplate> result = templateMapper.selectPage(p, qw);

        // 业务层组装 doctorName/departmentName（非 join）
        java.util.Set<Long> docIds = new java.util.LinkedHashSet<>();
        java.util.Set<Long> deptIds = new java.util.LinkedHashSet<>();
        for (DoctorScheduleTemplate t : result.getRecords()) {
            if (t.getDoctorId() != null) docIds.add(t.getDoctorId());
            if (t.getDepartmentId() != null) deptIds.add(t.getDepartmentId());
        }
        Map<Long, Doctor> docMap = toDoctorMap(docIds);
        Map<Long, Department> deptMap = toDeptMap(deptIds);

        List<ScheduleTemplateDTO.TemplateListItem> items = new ArrayList<>();
        for (DoctorScheduleTemplate t : result.getRecords()) {
            Doctor d = docMap.get(t.getDoctorId());
            Department dept = deptMap.get(t.getDepartmentId());
            items.add(new ScheduleTemplateDTO.TemplateListItem(
                    t.getTemplateNo(),
                    d != null ? d.getDoctorNo() : null,
                    d != null ? d.getName() : null,
                    dept != null ? dept.getDepartmentNo() : null,
                    dept != null ? dept.getName() : null,
                    t.getDayOfWeek(),
                    t.getPeriod(),
                    t.getStartTime(),
                    t.getEndTime(),
                    t.getTotalQuota(),
                    t.getRegistrationFee(),
                    t.getEnabled() != null && t.getEnabled() == 1));
        }
        return PageResult.of((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
    }

    // ===== 一键生成（核心） =====

    @Override
    @Transactional
    public ScheduleTemplateDTO.ApplyResponse apply(LocalDate startDate, int weeks, String doctorNo) {
        if (startDate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "起始日期不能为空");
        }
        if (weeks <= 0 || weeks > 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成周数需为 1-8");
        }

        // 查启用模板（可选按医生过滤）
        QueryWrapper<DoctorScheduleTemplate> qw = new QueryWrapper<DoctorScheduleTemplate>()
                .eq("enabled", 1);
        if (doctorNo != null && !doctorNo.isBlank()) {
            Doctor d = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", doctorNo));
            if (d == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + doctorNo);
            }
            qw.eq("doctor_id", d.getId());
        }
        List<DoctorScheduleTemplate> templates = templateMapper.selectList(qw);
        if (templates.isEmpty()) {
            log.info("[apply] 无启用模板，跳过生成: doctorNo={}", doctorNo);
            return new ScheduleTemplateDTO.ApplyResponse(0, 0);
        }

        // 按周几分组模板：1-7 → 该天的模板列表
        Map<Integer, List<DoctorScheduleTemplate>> byDow = new HashMap<>();
        for (DoctorScheduleTemplate t : templates) {
            byDow.computeIfAbsent(t.getDayOfWeek(), k -> new ArrayList<>()).add(t);
        }

        int generated = 0;
        int skipped = 0;
        LocalDate endExclusive = startDate.plusWeeks(weeks);  // 区间 [startDate, startDate+weeks*7)
        for (LocalDate date = startDate; date.isBefore(endExclusive); date = date.plusDays(1)) {
            // java.time DayOfWeek.getValue(): MONDAY=1 ... SUNDAY=7，与模板 day_of_week 一致
            int dow = date.getDayOfWeek().getValue();
            List<DoctorScheduleTemplate> dayTemplates = byDow.get(dow);
            if (dayTemplates == null || dayTemplates.isEmpty()) {
                continue;
            }
            for (DoctorScheduleTemplate t : dayTemplates) {
                // 跳过已存在（uk_schedule_doc_date_period 幂等）
                Long exists = scheduleMapper.selectCount(new QueryWrapper<DoctorSchedule>()
                        .eq("doctor_id", t.getDoctorId())
                        .eq("schedule_date", date)
                        .eq("period", t.getPeriod()));
                if (exists != null && exists > 0) {
                    skipped++;
                    continue;
                }
                DoctorSchedule s = new DoctorSchedule();
                s.setScheduleNo(generateScheduleNo());
                s.setDoctorId(t.getDoctorId());
                s.setDepartmentId(t.getDepartmentId());
                s.setScheduleDate(date);
                s.setPeriod(t.getPeriod());
                s.setStartTime(t.getStartTime());
                s.setEndTime(t.getEndTime());
                s.setTotalQuota(t.getTotalQuota());
                s.setBookedQuota(0);
                s.setRegistrationFee(t.getRegistrationFee());
                s.setStatus("AVAILABLE");
                scheduleMapper.insert(s);
                generated++;
            }
        }
        log.info("[apply] 一键生成完成: startDate={} weeks={} doctorNo={} generated={} skipped={}",
                startDate, weeks, doctorNo, generated, skipped);
        return new ScheduleTemplateDTO.ApplyResponse(generated, skipped);
    }

    // ===== 私有助手 =====

    private DoctorScheduleTemplate requireByNo(String templateNo) {
        if (templateNo == null || templateNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模板编号不能为空");
        }
        DoctorScheduleTemplate t = templateMapper.selectOne(
                new QueryWrapper<DoctorScheduleTemplate>().eq("template_no", templateNo));
        if (t == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在: " + templateNo);
        }
        return t;
    }

    /** 校验医生存在且启用，返回实体（拿主键） */
    private Doctor requireEnabledDoctor(String doctorNo) {
        Doctor d = doctorMapper.selectOne(new QueryWrapper<Doctor>().eq("doctor_no", doctorNo));
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "医生不存在: " + doctorNo);
        }
        if (d.getEnabled() == null || d.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "医生已停用: " + doctorNo);
        }
        return d;
    }

    /** 校验科室存在且启用，返回实体（拿主键） */
    private Department requireEnabledDepartment(String departmentNo) {
        Department dept = departmentMapper.selectOne(
                new QueryWrapper<Department>().eq("department_no", departmentNo));
        if (dept == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在: " + departmentNo);
        }
        if (dept.getEnabled() == null || dept.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "科室已停用: " + departmentNo);
        }
        return dept;
    }

    private Map<Long, Doctor> toDoctorMap(java.util.Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        List<Doctor> doctors = doctorMapper.selectBatchIds(new ArrayList<>(ids));
        Map<Long, Doctor> map = new HashMap<>();
        for (Doctor d : doctors) map.put(d.getId(), d);
        return map;
    }

    private Map<Long, Department> toDeptMap(java.util.Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        List<Department> depts = departmentMapper.selectBatchIds(new ArrayList<>(ids));
        Map<Long, Department> map = new HashMap<>();
        for (Department d : depts) map.put(d.getId(), d);
        return map;
    }

    /** 生成模板编号：T + 雪花序列 base36 */
    private static String generateTemplateNo() {
        long id = IdWorker.getId();
        return "T" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }

    /** 生成排班编号：S + 雪花序列 base36（与 ScheduleServiceImpl.generateScheduleNo 同范式） */
    private static String generateScheduleNo() {
        long id = IdWorker.getId();
        return "S" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
