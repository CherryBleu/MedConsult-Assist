package com.medconsult.medicalrecord.attachment.controller;

import com.medconsult.common.core.Result;
import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO;
import com.medconsult.medicalrecord.attachment.dto.AttachmentDTO.ListItem;
import com.medconsult.medicalrecord.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 附件对外接口（对齐《接口文档》§2.8.4 / §2.8.5）。
 *
 * <p>路径前缀 /api/v1/attachments（对外，走 Gateway 鉴权）。
 *
 * <p>本接口只维护附件<b>元数据</b>，实际文件流上传走 ai-service 的 {@code POST /files/upload}（§3.8）。
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Tag(name = "附件接口", description = "附件元数据管理（§2.8 附件）")
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** §2.8.4 创建附件记录（把 fileUrl 关联到业务记录） */
    @PostMapping
    @Operation(summary = "创建附件记录")
    public Result<AttachmentDTO.CreateResponse> create(@Valid @RequestBody AttachmentDTO.CreateRequest req) {
        return Result.ok(attachmentService.create(req));
    }

    /** §2.8.5 按业务类型 + 业务编号查询附件列表 */
    @GetMapping
    @Operation(summary = "查询附件列表")
    public Result<List<ListItem>> list(
            @Parameter(description = "业务类型：MEDICAL_RECORD / EXAM_REPORT / IMAGING_REPORT")
            @RequestParam(required = false) String bizType,
            @Parameter(description = "业务编号") @RequestParam(required = false) String bizId) {
        return Result.ok(attachmentService.list(bizType, bizId));
    }
}
