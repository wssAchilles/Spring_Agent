import request from '@/utils/request'

// 查询bot 管理列表
export function apiKeyPage(query) {
    return request({
        url: '/kb/apikey/list',
        method: 'get',
        params: query
    })
}

// 生成 apiKey
export function genBotApiKey(botId) {
    return request({
        url: '/kb/apikey',
        method: 'post',
        data: {
            botId: botId
        }
    })
}
// 删除 apiKey
export function delBotApiKey(id) {
    return request({
        url: '/kb/apikey/' + id,
        method: 'delete'
    })
}

