/* Layout */
import Layout from '@/layout/index.vue'

// 系统模块动态路由，基于用户权限动态去加载
export default [
  {
    path: '/kg',
    component: Layout,
    permissions: ['kg:graph:graph:list'],
    children: [
      {
        path: 'graph',
        component: () => import('@/views/kg/graph/index.vue'),
        meta: { title: '知识图谱', activeMenu: '/kg/graph', tagsView: false, sidebar: false }
      },
    ]
  },
]
