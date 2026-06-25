import request from '@/utils/request'

// 查询应用管理列表
export function listApply(query) {
    return request({
        url: '/kac/apply/list',
        method: 'get',
        params: query
    })
}

// 查询应用管理详细
export function getApply(id) {
    return request({
        url: '/kac/apply/' + id,
        method: 'get'
    })
}

// 新增应用管理
export function addApply(data) {
    return request({
        url: '/kac/apply',
        method: 'post',
        data: data
    })
}

// 修改应用管理
export function updateApply(data) {
    return request({
        url: '/kac/apply',
        method: 'put',
        data: data
    })
}

// 删除应用管理
export function delApply(id) {
    return request({
        url: '/kac/apply/' + id,
        method: 'delete'
    })
}

// 查询应用管理详细
export function getByApplyIdId(applyId) {
    return request({
        url: '/kac/apply/apply/' + applyId,
        method: 'get'
    })
}

// 复制应用管理
export function copy(data) {
    return request({
        url: '/kac/apply/copy',
        method: 'post',
        data: data
    })
}

