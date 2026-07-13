package com.medconsult.medicalrecord.attachment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.medicalrecord.attachment.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件 Mapper（纯 BaseMapper，无自定义 SQL，无 XML）。
 *
 * <p>查询走 QueryWrapper + 内置方法；逻辑删除由 {@code @TableLogic} 自动过滤。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
