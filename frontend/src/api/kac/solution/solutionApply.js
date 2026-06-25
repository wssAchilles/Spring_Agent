import request from '@/utils/request'

// 查询解决方案关联应用列表
export function listSolutionApply(query) {
    return request({
        url: '/kac/solutionApply/list',
        method: 'get',
        params: query
    })
}

// 查询解决方案关联应用详细
export function getSolutionApply(id) {
    return request({
        url: '/kac/solutionApply/' + id,
        method: 'get'
    })
}

// 新增解决方案关联应用
export function addSolutionApply(data) {
    return request({
        url: '/kac/solutionApply',
        method: 'post',
        data: data
    })
}

// 修改解决方案关联应用
export function updateSolutionApply(data) {
    return request({
        url: '/kac/solutionApply',
        method: 'put',
        data: data
    })
}

// 删除解决方案关联应用
export function delSolutionApply(id) {
    return request({
        url: '/kac/solutionApply/' + id,
        method: 'delete'
    })
}
