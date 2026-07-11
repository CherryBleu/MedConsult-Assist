import request from '@/utils/request'
import { mockDrugList, mockStockList, mockAddDrug, mockStockOperate } from '@/mock/drug'
import { mockStockWarningList, mockStockFlowList } from '@/mock/drug'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 药品列表（对齐后端 GET /drugs）
export const getDrugListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDrugList())
  }
  return request({
    url: '/drugs',
    method: 'get',
    params
  })
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
export const getStockListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockList())
  }
  return request({
    url: '/drugs',
    method: 'get',
    params
  })
}

// 库存入库（对齐后端 POST /drugs/{drugId}/stock/inbound）
export const stockInApi = (id, quantity, remark) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockOperate(id, 'IN', quantity, remark))
  }
  return request({
    url: `/drugs/${id}/stock/inbound`,
    method: 'post',
    data: { quantity, remark }
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
export const getStockWarningApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockWarningList())
  }
  return request({
    url: '/drugs/stock/alerts',
    method: 'get',
    params
  })
}

// 库存流水列表（对齐后端 GET /drugs/{drugId}/stock/flows）
export const getStockFlowApi = (drugId, params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockFlowList(params))
  }
  return request({
    url: `/drugs/${drugId}/stock/flows`,
    method: 'get',
    params
  })
}
