/* Layout */
import Layout from '@/layout/index.vue'

// 系统模块动态路由，基于用户权限动态去加载
export default [
  {
    path: '/kmc',
    component: Layout,
    permissions: ['kmc:knowledgeBase:knowledgebase:list'],
    children: [
      {
        path: 'knowledgeBase',
        component: () => import('@/views/kmc/knowledgeBase/index.vue'),
        meta: { title: '知识库', activeMenu: '/kmc/knowledgeBase', tagsView: false, sidebar: false } // 标记此页面不使用标签视图
      }
    ]
  }
]
