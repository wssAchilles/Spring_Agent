import request from '@/utils/request'

// 查询应用关联知识图谱列表
export function listKacGraph(query) {
    return request({
        url: '/kac/graph/list',
        method: 'get',
        params: query
    })
}

// 查询应用关联知识图谱详细
export function getKacGraph(id) {
    return request({
        url: '/kac/graph/' + id,
        method: 'get'
    })
}

// 新增应用关联知识图谱
export function addKacGraph(data) {
    return request({
        url: '/kac/graph',
        method: 'post',
        data: data
    })
}

// 修改应用关联知识图谱
export function updateKacGraph(data) {
    return request({
        url: '/kac/graph',
        method: 'put',
        data: data
    })
}

// 删除应用关联知识图谱
export function delKacGraph(id) {
    return request({
        url: '/kac/graph/' + id,
        method: 'delete'
    })
}
