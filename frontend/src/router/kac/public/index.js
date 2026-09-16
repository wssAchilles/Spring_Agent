import Layout from '@/layout/index.vue';

export default [
    {
        path: '/kac',
        component: Layout,
        hidden: true,
        children: [
            {
                path: 'horizontal/horizontalDetail',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'HorizontalDetail',
                meta: { title: '应用详情', activeMenu: '/kac/horizontal', dynamicTitle: true }
            },
            {
                path: 'horizontal/detail',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'HorizontalDetailAlias',
                meta: { title: '应用详情', activeMenu: '/kac/horizontal', dynamicTitle: true }
            },
            {
                path: 'horizontal/pluginApply',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'PluginApply',
                meta: { title: '插件应用', activeMenu: '/kac/horizontal', dynamicTitle: true }
            },
            {
                path: 'vertical/verticalDetail',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'VerticalDetail',
                meta: { title: '行业应用详情', activeMenu: '/kac/vertical', dynamicTitle: true }
            },
            {
                path: 'myApp/myAppDetail',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'MyAppDetail',
                meta: { title: '我的应用详情', activeMenu: '/kac/myApp', dynamicTitle: true }
            },
            {
                path: 'vertical/pluginApply',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'VerticalPluginApply',
                meta: { title: '行业应用', activeMenu: '/kac/vertical', dynamicTitle: true }
            },
            {
                path: 'myApp/pluginApply',
                component: () => import('@/views/kac/horizontal/detail/index.vue'),
                name: 'MyAppPluginApply',
                meta: { title: '我的应用', activeMenu: '/kac/myApp', dynamicTitle: true }
            },
            {
                path: 'mySolution/mySolutionDetail',
                component: () => import('@/views/kac/mySolution/detail/index.vue'),
                name: 'MySolutionDetail',
                meta: { title: '我的解决方案详情', activeMenu: '/kac/mySolution', dynamicTitle: true }
            },
            {
                path: 'solution/solutionDetail',
                component: () => import('@/views/kac/mySolution/detail/index.vue'),
                name: 'SolutionDetail',
                meta: { title: '解决方案详情', activeMenu: '/kac/solution', dynamicTitle: true }
            },
        ]
    },
];
