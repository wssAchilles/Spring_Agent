import Layout from '@/layout/index.vue';

export default [
    {
        path: '/kmc',
        component: Layout,
        redirect: 'knowledgeBaseEdit',
        children: [
            {
                path: 'knowledgeBase/add',
                component: () => import('@/views/kmc/knowledgeBase/components/settings.vue'),
                name: 'settingsAdd',
                meta: { title: '添加知识库', tagsView: false, sidebar: false },
                hidden: true
            },
        ]
    },
    {
        path: '/kmc/:kbId',
        component: Layout,
        children: [
            {
                path: 'recallLog',
                component: () => import('@/views/kmc/knowledgeBase/components/recallLog.vue'),
                name: 'recallLog',
                meta: { title: '召回测试记录', activeMenu: '/kmc/:kbId/recall' },
                hidden: true
            },
            {
                path: 'knowledgeSegment/index',
                component: () => import('@/views/kmc/knowledgeSegment/index.vue'),
                name: 'segmentIndex',
                meta: { title: '分段列表', activeMenu: '/kmc/:kbId/kmcDocument' },
                hidden: true
            },
            {
                path: 'knowledgeSegment/detail',
                component: () => import('@/views/kmc/knowledgeSegment/detail/index.vue'),
                name: 'segmentEdit',
                meta: { title: '分段详情', activeMenu: '/kmc/:kbId/knowledgeSegment/detail' },
                hidden: true
            },
            {
                path: 'kmcDocument/add',
                component: () => import('@/views/kmc/kmcDocument/selection/add.vue'),
                name: 'kmcDocumentAdd',
                meta: { title: '新增知识文件', activeMenu: '/kmc/:kbId/kmcDocument' },
                hidden: true
            },
            {
                path: 'kmcDocument/edit',
                component: () => import('@/views/kmc/kmcDocument/selection/add.vue'),
                name: 'kmcDocumentEdit',
                meta: { title: '修改知识文件', activeMenu: '/kmc/:kbId/kmcDocument' },
                hidden: true
            }
        ]
    },
];
