import request from '@/utils/request'
import { mockDrugList, mockStockList, mockAddDrug, mockStockOperate } from '@/mock/drug'
import { mockStockWarningList, mockStockFlowList } from '@/mock/drug'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 药品列表
export const getDrugListApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockDrugList())
  }
  return request({
    url: '/drug/list',
    method: 'get'
  })
}

// 新增药品
export const addDrugApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddDrug(data))
  }
  return request({
    url: '/drug/add',
    method: 'post',
    data
  })
}

// 库存列表
export const getStockListApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockList())
  }
  return request({
    url: '/drug/stock/list',
    method: 'get'
  })
}

// 库存入库
export const stockInApi = (id, quantity, remark) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockOperate(id, 'IN', quantity, remark))
  }
  return request({
    url: `/drug/stock/in/${id}`,
    method: 'post',
    data: { quantity, remark }
  })
}

// 库存出库
export const stockOutApi = (id, quantity, remark) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockOperate(id, 'OUT', quantity, remark))
  }
  return request({
    url: `/drug/stock/out/${id}`,
    method: 'post',
    data: { quantity, remark }
  })
}

// 库存预警列表
export const getStockWarningApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockWarningList())
  }
  return request({
    url: '/drug/stock/warning',
    method: 'get'
  })
}

// 库存流水列表
export const getStockFlowApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockStockFlowList(params))
  }
  return request({
    url: '/drug/stock-flow',
    method: 'get',
    params
  })
}