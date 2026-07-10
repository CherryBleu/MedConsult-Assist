// 科室列表（对齐 department 表字段）
export const mockDepartmentList = () => {
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, departmentNo: 'DEP_CARDIOLOGY', name: '心血管内科', description: '高血压、冠心病、心律失常', location: '门诊楼3层', enabled: 1 },
      { id: 2, departmentNo: 'DEP_RESPIRATORY', name: '呼吸内科', description: '肺炎、哮喘、慢性支气管炎', location: '门诊楼3层', enabled: 1 },
      { id: 3, departmentNo: 'DEP_GASTRO', name: '消化内科', description: '胃炎、胃溃疡、肝病', location: '门诊楼4层', enabled: 1 },
      { id: 4, departmentNo: 'DEP_ORTHOPEDICS', name: '骨科', description: '骨折、颈椎病、腰椎病', location: '门诊楼5层', enabled: 1 },
      { id: 5, departmentNo: 'DEP_PEDIATRICS', name: '儿科', description: '儿童常见病、生长发育', location: '门诊楼2层', enabled: 1 },
      { id: 6, departmentNo: 'DEP_DERMATOLOGY', name: '皮肤科', description: '皮炎、湿疹、痤疮', location: '门诊楼4层', enabled: 1 }
    ]
  }
}