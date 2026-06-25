import request from '@/utils/request'

// 查询agent配置列表
export function listConfig(query) {
    return request({
        url: '/kb/agent/list',
        method: 'get',
        params: query
    })
}

// 查询 agent 配置详细
export function getConfig(id) {
    return request({
        url: '/kb/agent/' + id,
        method: 'get'
    })
}

// 根据 botId 查询 agent 配置详细
export function getConfigByBotId(botId) {
    return request({
        url: '/kb/agent/byBot/' + botId,
        method: 'get'
    })
}

// 新增agent配置
export function addConfig(data) {
    return request({
        url: '/kb/agent',
        method: 'post',
        data: data
    })
}

// 修改agent配置
export function updateConfig(data) {
    return request({
        url: '/kb/agent',
        method: 'put',
        data: data
    })
}

// 删除agent配置
export function delConfig(id) {
    return request({
        url: '/kb/agent/' + id,
        method: 'delete'
    })
}
