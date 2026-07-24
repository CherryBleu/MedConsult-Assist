import MainLayout from '@/layouts/MainLayout.vue'

const pharmacyRoutes = [
  {
    path: '/pharmacy',
    component: MainLayout,
    redirect: '/pharmacy/workbench',
    meta: { role: 'PHARMACY_ADMIN', requiresAuth: true },
    children: [
      {
        path: 'workbench',
        name: 'PharmacyWorkbench',
        component: () => import('@/views/pharmacy/workbench/PharmacyWorkbench.vue'),
        meta: { title: '药房工作台' }
      },
      {
        path: 'prescription-review',
        name: 'PharmacyPrescriptionReview',
        component: () => import('@/views/pharmacy/prescription/PrescriptionReview.vue'),
        meta: { title: '处方发药' }
      },
      {
        path: 'drug',
        name: 'PharmacyDrugList',
        component: () => import('@/views/pharmacy/drug/DrugList.vue'),
        meta: { title: '药品目录' }
      },
      {
        path: 'stock',
        name: 'PharmacyStockManage',
        component: () => import('@/views/pharmacy/drug/StockManage.vue'),
        meta: { title: '库存管理' }
      },
      {
        path: 'stock-warning',
        name: 'PharmacyStockWarning',
        component: () => import('@/views/pharmacy/drug/StockWarning.vue'),
        meta: { title: '库存预警' }
      },
      {
        path: 'stock-flow',
        name: 'PharmacyStockFlow',
        component: () => import('@/views/pharmacy/drug/StockFlow.vue'),
        meta: { title: '库存流水' }
      }
    ]
  }
]

export default pharmacyRoutes
