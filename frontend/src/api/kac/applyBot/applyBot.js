import request from '@/utils/request'

// 查询应用关联BOT列表
export function listKacBot(query) {
    return request({
        url: '/kac/bot/list',
        method: 'get',
        params: query
    })
}

// 查询应用关联BOT详细
export function getKacBot(id) {
    return request({
        url: '/kac/bot/' + id,
        method: 'get'
    })
}

// 新增应用关联BOT
export function addKacBot(data) {
    return request({
        url: '/kac/bot',
        method: 'post',
        data: data
    })
}

// 修改应用关联BOT
export function updateKacBot(data) {
    return request({
        url: '/kac/bot',
        method: 'put',
        data: data
    })
}

// 删除应用关联BOT
export function delKacBot(id) {
    return request({
        url: '/kac/bot/' + id,
        method: 'delete'
    })
}
