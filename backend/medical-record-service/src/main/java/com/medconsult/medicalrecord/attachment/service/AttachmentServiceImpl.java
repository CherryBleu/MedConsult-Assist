package com.medconsult.medicalrecord.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO;
import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO.ListItem;
import com.medconsult.medicalrecord.attachment.entity.Attachment;
import com.medconsult.medicalrecord.attachment.mapper.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 附件服务实现（对齐《接口文档》§2.8.4 / §2.8.5）。
 *
 * <p>本服务只维护附件<b>元数据</b>（业务类型 + 业务编号 + 文件 URL），实际文件流上传走
 * ai-service 的 {@code POST /files/upload}（§3.8）。前端典型流程：先 /files/upload 拿到 fileUrl，
 * 再 POST /attachments 把 fileUrl 关联到具体业务记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentMapper attachmentMapper;

    /** §2.8.4 创建附件记录 */
    @Override
    @Transactional
    public AttachmentDTO.CreateResponse create(AttachmentDTO.CreateRequest req) {
        Attachment a = new Attachment();
        a.setAttachmentNo(generateAttachmentNo());
        a.setBizType(req.getBizType());
        a.setBizId(req.getBizId());
        a.setFileName(req.getFileName());
        a.setFileType(req.getFileType());
        a.setFileUrl(req.getFileUrl());
        a.setFileSize(req.getFileSize());
        // uploaded_by 取当前登录用户（无登录态时留 null，不阻断创建）
        a.setUploadedBy(SecurityContext.currentUserId());
        attachmentMapper.insert(a);
        return new AttachmentDTO.CreateResponse(a.getAttachmentNo(), a.getBizType(), a.getBizId());
    }

    /** §2.8.5 按业务类型 + 业务编号查询附件列表 */
    @Override
    public List<ListItem> list(String bizType, String bizId) {
        QueryWrapper<Attachment> qw = new QueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            qw.eq("biz_type", bizType);
        }
        if (bizId != null && !bizId.isBlank()) {
            qw.eq("biz_id", bizId);
        }
        qw.orderByDesc("created_at");
        return attachmentMapper.selectList(qw).stream()
                .map(a -> new ListItem(a.getAttachmentNo(), a.getFileName(), a.getFileType(), a.getFileUrl()))
                .toList();
    }

    /** 生成附件编号：ATT + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_attachment_no 兜底 */
    private static String generateAttachmentNo() {
        long id = IdWorker.getId();
        return "ATT" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}
