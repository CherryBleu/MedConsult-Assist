package com.medconsult.medicalrecord.attachment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 附件元数据表（对应《数据库设计文档》§2.12 attachment）。
 *
 * <p>保存病历/检查报告/影像报告等文件元数据，不约束文件实际存储方式。
 * 实际文件上传走 ai-service 的 {@code POST /files/upload}（§3.8），本表只记录业务级元数据
 * （把文件 URL 关联到具体业务记录，如某条病历的附件清单）。
 *
 * <p>逻辑删除（deleted）由 {@link BaseEntity} 的 {@code @TableLogic} 自动处理。
 */
@Getter
@Setter
@TableName("attachment")
public class Attachment extends BaseEntity {

    /** 附件编号，如 ATT202607060001（业务可读，对外暴露） */
    private String attachmentNo;

    /** 业务类型：MEDICAL_RECORD / EXAM_REPORT / IMAGING_REPORT（docs §5 attachmentBizType） */
    private String bizType;

    /** 业务编号（如 record_no） */
    private String bizId;

    /** 文件名 */
    private String fileName;

    /** 文件类型（PDF / DICOM / JPG 等） */
    private String fileType;

    /** 文件访问地址 */
    private String fileUrl;

    /** 文件大小（Byte） */
    private Long fileSize;

    /** 上传人 ID（sys_user.id） */
    private Long uploadedBy;
}
