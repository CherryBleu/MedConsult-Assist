package com.medconsult.medicalrecord.attachment.service;

import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO;
import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO.ListItem;

import java.util.List;

/**
 * 附件服务接口（对齐《接口文档》§2.8.4 / §2.8.5）。
 */
public interface AttachmentService {

    /** §2.8.4 创建附件记录 */
    AttachmentDTO.CreateResponse create(AttachmentDTO.CreateRequest req);

    /** §2.8.5 按业务类型 + 业务编号查询附件列表 */
    List<ListItem> list(String bizType, String bizId);
}
