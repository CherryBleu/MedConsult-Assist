import request from '@/utils/request'

// 附件 API（对齐后端 medical-record-service AttachmentController，
// docs/接口文档.md §2.8.4 / §2.8.5）
//
// 本接口维护附件「元数据」（业务类型 + 业务编号 + 文件 URL）。
// 实际文件流上传走 ai-service 的 POST /files/upload（§3.8），拿到 fileUrl 后
// 再调 createAttachmentApi 把 fileUrl 关联到具体业务记录（如某条病历的附件）。

// 创建附件记录（对齐 POST /attachments）
// data: { bizType, bizId, fileName?, fileType?, fileUrl, fileSize? }
export const createAttachmentApi = (data) => {
  return request({ url: '/attachments', method: 'post', data })
}

// 查询附件列表（对齐 GET /attachments?bizType=&bizId=）
export const getAttachmentListApi = (params) => {
  return request({ url: '/attachments', method: 'get', params })
}
