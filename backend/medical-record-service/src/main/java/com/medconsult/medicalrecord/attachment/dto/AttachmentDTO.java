package com.medconsult.medicalrecord.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 附件相关请求/响应 DTO（对齐《接口文档》§2.8.4 / §2.8.5）。
 */
public class AttachmentDTO {

    /** §2.8.4 创建附件记录请求 */
    @Data
    @Schema(description = "创建附件记录请求")
    public static class CreateRequest {
        /** 业务类型：MEDICAL_RECORD / EXAM_REPORT / IMAGING_REPORT */
        @NotBlank(message = "业务类型不能为空")
        @Schema(description = "业务类型：MEDICAL_RECORD / EXAM_REPORT / IMAGING_REPORT") private String bizType;
        /** 业务编号（如 record_no） */
        @NotBlank(message = "业务编号不能为空")
        @Schema(description = "业务编号") private String bizId;
        @Schema(description = "文件名") private String fileName;
        @Schema(description = "文件类型") private String fileType;
        @NotBlank(message = "文件地址不能为空")
        @Size(max = 1000, message = "文件地址不能超过 1000 字")
        @Schema(description = "文件访问地址") private String fileUrl;
        @Schema(description = "文件大小（Byte）") private Long fileSize;
    }

    /** §2.8.4 创建响应 */
    @Schema(description = "创建附件记录响应")
    public record CreateResponse(
            @Schema(description = "附件编号") String attachmentId,
            @Schema(description = "业务类型") String bizType,
            @Schema(description = "业务编号") String bizId
    ) {}

    /** §2.8.5 列表项 */
    @Schema(description = "附件列表项")
    public record ListItem(
            @Schema(description = "附件编号") String attachmentId,
            @Schema(description = "文件名") String fileName,
            @Schema(description = "文件类型") String fileType,
            @Schema(description = "文件访问地址") String fileUrl
    ) {}
}
