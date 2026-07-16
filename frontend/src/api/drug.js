import request from '@/utils/request'
import { mockDrugList, mockStockList, mockAddDrug, mockStockOperate } from '@/mock/drug'
import { mockStockWarningList, mockStockFlowList } from '@/mock/drug'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 Drug 字段 → 前端期望字段映射
// 后端 DrugListItem: drugId/genericName/specification/manufacturer/stockQuantity/unit/status
// 前端 DrugList.vue 期望: drugNo/name/specification/manufacturer/category/price/status
// （category/price 在后端 drug 表无对应列，DB 无此字段——前端表格按后端实际字段渲染）
const mapDrug = (d) => ({
  id: d.drugId ?? d.id,
  drugId: d.drugId ?? d.id,
  drugNo: d.drugId ?? d.drugNo ?? d.id,
  name: d.genericName ?? d.drugName ?? d.name,
  drugName: d.genericName ?? d.drugName,
  genericName: d.genericName,
  specification: d.specification,
  manufacturer: d.manufacturer,
  stock: d.stockQuantity ?? d.stock,
  stockQuantity: d.stockQuantity,
  unit: d.unit,
  status: d.status === 'ACTIVE' ? 'NORMAL' : (d.status ?? 'NORMAL')
})

// 药品列表（对齐后端 GET /drugs）
export const getDrugListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDrugList())
  }
  const res = await request({ url: '/drugs', method: 'get', params })
  // 各药品视图直接把 res.data 当数组用，这里映射后直接返回数组
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapDrug)
  return res
}

// 新增药品（对齐后端 POST /drugs）
export const addDrugApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDrug(data))
  }
  return request({
    url: '/drugs',
    method: 'post',
    data
  })
}

// 库存列表（复用药品列表 GET /drugs）
export const getStockListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockList())
  }
  const res = await request({ url: '/drugs', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapDrug)
  return res
}

// 库存入库（对齐后端 POST /drugs/{drugId}/stock/inbound）
// 后端 InboundRequest 要求 batchNo(@NotBlank) + expireDate(@NotNull @Future) + quantity，
// 缺失会直接 400。供应商 supplier 选填。
export const stockInApi = (id, { quantity, batchNo, expireDate, supplier, remark }) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockOperate(id, 'IN', quantity, remark))
  }
  return request({
    url: `/drugs/${id}/stock/inbound`,
    method: 'post',
    data: {
      quantity,
      batchNo,
      expireDate,
      supplier: supplier || undefined,
      remark: remark || undefined
    }
  })
}

// 库存出库（对齐后端 POST /drugs/{drugId}/stock/outbound）
export const stockOutApi = (id, quantity, remark) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockOperate(id, 'OUT', quantity, remark))
  }
  return request({
    url: `/drugs/${id}/stock/outbound`,
    method: 'post',
    data: { quantity, remark }
  })
}

// 库存预警列表（对齐后端 GET /drugs/stock/alerts）
// 后端库存预警/流水字段 → 前端期望字段映射
const mapStockWarning = (w) => ({
  id: w.alertId ?? w.id,
  drugId: w.drugId,
  drugName: w.drugName ?? w.genericName,
  warningType: w.alertType ?? w.warningType,
  level: w.level ?? w.severity,
  message: w.message ?? w.reason,
  ...w
})
const mapStockFlow = (f) => ({
  id: f.flowId ?? f.id,
  drugId: f.drugId,
  drugName: f.drugName ?? f.genericName,
  type: f.flowType ?? f.type,
  quantity: f.quantity,
  remark: f.remark,
  operator: f.operator,
  createdAt: f.createdAt,
  ...f
})

// 库存预警列表（对齐后端 GET /drugs/stock/alerts）
export const getStockWarningApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockWarningList())
  }
  const res = await request({ url: '/drugs/stock/alerts', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapStockWarning)
  return res
}

// 库存流水列表（对齐后端 GET /drugs/{drugId}/stock/flows）
export const getStockFlowApi = async (drugId, params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockFlowList(params))
  }
  const res = await request({ url: `/drugs/${drugId}/stock/flows`, method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapStockFlow)
  return res
}

// 管理员端库存流水列表（查看所有流水，无需按药品筛选）
export const getStockFlowListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockFlowList(params))
  }
  const res = await request({ url: '/drugs/stock/flows', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapStockFlow)
  return res
}
