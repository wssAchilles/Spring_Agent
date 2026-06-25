import request from '@/utils/request'

// 查询知识推荐标签列表
export function listLabel(query) {
    return request({
        url: '/app/label/list',
        method: 'get',
        params: query
    })
}

// 查询知识推荐标签详细
export function getLabel(id) {
    return request({
        url: '/app/label/' + id,
        method: 'get'
    })
}

// 新增知识推荐标签
export function addLabel(data) {
    return request({
        url: '/app/label',
        method: 'post',
        data: data
    })
}

// 修改知识推荐标签
export function updateLabel(data) {
    return request({
        url: '/app/label',
        method: 'put',
        data: data
    })
}

// 删除知识推荐标签
export function delLabel(id) {
    return request({
        url: '/app/label/' + id,
        method: 'delete'
    })
}

// 修改搜索次数
export function updateSearchCount(data) {
    return request({
        url: '/app/label/updateSearchCount',
        method: 'put',
        data: data,
    })
}

export function labelAllList() {
    return request({
        url: '/app/label/allList',
        method: 'get',
    })
}
