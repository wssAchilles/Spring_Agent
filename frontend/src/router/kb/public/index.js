/* Layout */
import Layout from '@/layout/index.vue';

// 知识应用模块动公共路由
export default [
    {
        path: '/kb',
        component: Layout,
        redirect: 'kbTool',
        hidden: true,
        children: [
            {
                path: 'tool/toolDetail',
                component: () => import('@/views/kb/tool/detail/index.vue'),
                name: 'Method',
                meta: { title: '工具管理详情', activeMenu: '/kb/tool' }
            }
        ]
    },
    {
        path: '/kb',
        component: Layout,
        redirect: 'kbAgent',
        hidden: true,
        children: [
            {
                name: 'agent',
                path: 'bot/agent/build',
                component: () => import('@/views/kb/agent/index.vue'),
                meta: { title: 'Agent编排', activeMenu: '/kb/bot/agent'  }
            }
        ]
    },
    {
        path: '/kb',
        component: Layout,
        children: [
            {
                path: 'bot/processflow',
                component: () => import('@/views/kb/bot/build/index.vue'),
                name: 'ProcessFlow',
                meta: { title: 'Bot构建', activeMenu: '/kb/bot' }
            },
            {
                path: 'bot/codeNative',
                component: () => import('@/views/kb/codeNative/index.vue'),
                name: 'CodeNative',
                meta: { title: '白盒化开发', activeMenu: '/system/bot' }
            },
            {
                path: 'bot/workflow/detail',
                component: () => import('@/views/kb/bot/detail/index.vue'),
                name: 'workflowDetail',
                meta: { title: '工作流详情', activeMenu: '/kb/bot/workflow' }
            },
            {
                path: 'bot/chatflow/detail',
                component: () => import('@/views/kb/bot/detail/index.vue'),
                name: 'chatflowDetail',
                meta: { title: 'Chatflow详情', activeMenu: '/kb/bot/chatflow' }
            },
            {
                path: 'bot/agent/detail',
                component: () => import('@/views/kb/bot/detail/index.vue'),
                name: 'agentDetail',
                meta: { title: 'agent详情', activeMenu: '/kb/bot/agent' }
            },
        ]
    }
];
