const consumeFailOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) !== '1') return false
  localStorage.removeItem(key)
  return true
}

// 药品列表
export const mockDrugList = () => {
  const isDrugCatalogPage = typeof window !== 'undefined' && window.location.pathname === '/pharmacy/drug'
  if (isDrugCatalogPage && consumeFailOnce('mock_drug_list_fail_once')) {
    return Promise.reject(new Error('药品目录加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, drugNo: 'DRG001', name: '阿莫西林胶囊', specification: '0.5g*24粒', manufacturer: '华北制药', category: '抗生素', price: 25.5, status: 'NORMAL' },
      { id: 2, drugNo: 'DRG002', name: '盐酸氨溴索口服溶液', specification: '100ml', manufacturer: '勃林格殷格翰', category: '祛痰药', price: 32.0, status: 'NORMAL' },
      { id: 3, drugNo: 'DRG003', name: '硝苯地平缓释片', specification: '20mg*30片', manufacturer: '拜耳', category: '降压药', price: 45.8, status: 'LOW_STOCK' },
      { id: 4, drugNo: 'DRG004', name: '奥美拉唑肠溶胶囊', specification: '20mg*14粒', manufacturer: '阿斯利康', category: '胃药', price: 56.0, status: 'NORMAL' },
      { id: 5, drugNo: 'DRG005', name: '布洛芬缓释胶囊', specification: '0.3g*20粒', manufacturer: '中美史克', category: '解热镇痛', price: 18.9, status: 'EXPIRED_WARNING' },
      { id: 6, drugNo: 'DRG006', name: '头孢克肟分散片', specification: '0.1g*12片', manufacturer: '白云山制药', category: '抗生素', price: 38.5, status: 'DISABLED' }
    ],
    total: 6
  }
}

// 库存列表
export const mockStockList = () => {
  if (consumeFailOnce('mock_stock_list_fail_once')) {
    return Promise.reject(new Error('库存列表加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, drugId: 1, drugName: '阿莫西林胶囊', specification: '0.5g*24粒', stockQuantity: 1200, warningQuantity: 200, unit: '盒', batchNo: '20260101', expireDate: '2028-01-01', status: 'NORMAL' },
      { id: 2, drugId: 2, drugName: '盐酸氨溴索口服溶液', specification: '100ml', stockQuantity: 800, warningQuantity: 150, unit: '瓶', batchNo: '20260201', expireDate: '2027-08-01', status: 'NORMAL' },
      { id: 3, drugId: 3, drugName: '硝苯地平缓释片', specification: '20mg*30片', stockQuantity: 120, warningQuantity: 200, unit: '盒', batchNo: '20251201', expireDate: '2027-12-01', status: 'LOW_STOCK' },
      { id: 4, drugId: 5, drugName: '布洛芬缓释胶囊', specification: '0.3g*20粒', stockQuantity: 300, warningQuantity: 100, unit: '盒', batchNo: '20250601', expireDate: '2026-08-01', status: 'EXPIRED_WARNING' }
    ],
    total: 4
  }
}

// 新增药品
export const mockAddDrug = (data) => {
  if (consumeFailOnce('mock_drug_save_fail_once')) {
    return Promise.reject(new Error('药品保存失败，请重试'))
  }
  return {
    code: 0,
    message: '新增成功',
    data: { id: Date.now(), ...data }
  }
}

// 库存出入库
export const mockStockOperate = (id, type, quantity, remark) => {
  if (consumeFailOnce('mock_stock_operate_fail_once')) {
    return Promise.reject(new Error('库存操作失败，请重试'))
  }
  return {
    code: 0,
    message: type === 'IN' ? '入库成功' : '出库成功',
    data: { id, quantity, type, remark, flowNo: 'FL' + Date.now() }
  }
}

// 库存预警列表
export const mockStockWarningList = () => {
  if (consumeFailOnce('mock_stock_warning_fail_once')) {
    return Promise.reject(new Error('库存预警加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 3, drugName: '硝苯地平缓释片', specification: '20mg*30片', batchNo: '20251201', stockQuantity: 120, warningQuantity: 200, unit: '盒', warningType: 'LOW_STOCK', expireDate: '2027-12-01', daysLeft: 510 },
      { id: 5, drugName: '布洛芬缓释胶囊', specification: '0.3g*20粒', batchNo: '20250601', stockQuantity: 300, warningQuantity: 100, unit: '盒', warningType: 'EXPIRED_WARNING', expireDate: '2026-08-01', daysLeft: 23 },
      { id: 7, drugName: '维生素C片', specification: '0.1g*100片', batchNo: '20240901', stockQuantity: 50, warningQuantity: 100, unit: '瓶', warningType: 'LOW_STOCK', expireDate: '2026-09-01', daysLeft: 54 }
    ],
    total: 3
  }
}

// 库存流水列表
export const mockStockFlowList = () => {
  if (consumeFailOnce('mock_stock_flow_fail_once')) {
    return Promise.reject(new Error('库存流水加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, flowNo: 'FL202607100001', drugName: '阿莫西林胶囊', specification: '0.5g*24粒', batchNo: '20260101', flowType: 'IN', quantity: 500, unit: '盒', beforeStock: 700, afterStock: 1200, operatorName: '李药师', remark: '月度采购入库', createdAt: '2026-07-10 09:30:00' },
      { id: 2, flowNo: 'FL202607100002', drugName: '盐酸氨溴索口服溶液', specification: '100ml', batchNo: '20260201', flowType: 'OUT', quantity: 50, unit: '瓶', beforeStock: 850, afterStock: 800, operatorName: '李药师', remark: '门诊发药', createdAt: '2026-07-10 10:15:00' },
      { id: 3, flowNo: 'FL202607100003', drugName: '硝苯地平缓释片', specification: '20mg*30片', batchNo: '20251201', flowType: 'OUT', quantity: 30, unit: '盒', beforeStock: 150, afterStock: 120, operatorName: '王药师', remark: '住院药房领药', createdAt: '2026-07-10 11:00:00' },
      { id: 4, flowNo: 'FL202607090001', drugName: '奥美拉唑肠溶胶囊', specification: '20mg*14粒', batchNo: '20251101', flowType: 'IN', quantity: 200, unit: '盒', beforeStock: 300, afterStock: 500, operatorName: '李药师', remark: '补充库存', createdAt: '2026-07-09 14:20:00' },
      { id: 5, flowNo: 'FL202607090002', drugName: '布洛芬缓释胶囊', specification: '0.3g*20粒', batchNo: '20250601', flowType: 'OUT', quantity: 20, unit: '盒', beforeStock: 320, afterStock: 300, operatorName: '王药师', remark: '急诊发药', createdAt: '2026-07-09 16:45:00' },
      { id: 6, flowNo: 'FL202607080001', drugName: '头孢克肟分散片', specification: '0.1g*12片', batchNo: '20260301', flowType: 'IN', quantity: 300, unit: '盒', beforeStock: 100, afterStock: 400, operatorName: '李药师', remark: '新批次入库', createdAt: '2026-07-08 09:00:00' },
      { id: 7, flowNo: 'FL202607080002', drugName: '阿莫西林胶囊', specification: '0.5g*24粒', batchNo: '20260101', flowType: 'OUT', quantity: 80, unit: '盒', beforeStock: 780, afterStock: 700, operatorName: '张药师', remark: '门诊发药', createdAt: '2026-07-08 15:30:00' },
      { id: 8, flowNo: 'FL202607070001', drugName: '维生素C片', specification: '0.1g*100片', batchNo: '20240901', flowType: 'OUT', quantity: 30, unit: '瓶', beforeStock: 80, afterStock: 50, operatorName: '王药师', remark: '病区领药', createdAt: '2026-07-07 10:00:00' }
    ],
    total: 8
  }
}
