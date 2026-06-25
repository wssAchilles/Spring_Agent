import Layout from '@/layout/index.vue';
/* 知识抽取模块公共路由 */
import extPublicRouter from '../../ext/public/index.js';

export default [
    {
        path: '/kg',
        component: Layout,
        children: [
            ...extPublicRouter,
        ]
    },
]
