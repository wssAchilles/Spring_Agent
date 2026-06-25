import request from '@/utils/request'

// 查询应用关联知识库列表
export function listKnowledge(query) {
    return request({
        url: '/kac/knowledge/list',
        method: 'get',
        params: query
    })
}

// 查询应用关联知识库详细
export function getKnowledge(id) {
    return request({
        url: '/kac/knowledge/' + id,
        method: 'get'
    })
}

// 新增应用关联知识库
export function addKnowledge(data) {
    return request({
        url: '/kac/knowledge',
        method: 'post',
        data: data
    })
}

// 修改应用关联知识库
export function updateKnowledge(data) {
    return request({
        url: '/kac/knowledge',
        method: 'put',
        data: data
    })
}

// 删除应用关联知识库
export function delKnowledge(id) {
    return request({
        url: '/kac/knowledge/' + id,
        method: 'delete'
    })
}
