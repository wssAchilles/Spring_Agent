import request from '@/utils/request'

// 查询插件管理列表
export function listPlugin(query) {
    return request({
        url: '/kac/plugin/list',
        method: 'get',
        params: query
    })
}

// 查询插件管理详细
export function getPlugin(id) {
    return request({
        url: '/kac/plugin/' + id,
        method: 'get'
    })
}

// 新增插件管理
export function addPlugin(data) {
    return request({
        url: '/kac/plugin',
        method: 'post',
        data: data
    })
}

// 修改插件管理
export function updatePlugin(data) {
    return request({
        url: '/kac/plugin',
        method: 'put',
        data: data
    })
}

// 删除插件管理
export function delPlugin(id) {
    return request({
        url: '/kac/plugin/' + id,
        method: 'delete'
    })
}
