import request from '@/utils/request'

// 查询bot 管理列表
export function listBot(query) {
  return request({
    url: '/kb/bot/list',
    method: 'get',
    params: query
  })
}

// 查询bot 管理详细
export function getBot(id) {
  return request({
    url: '/kb/bot/' + id,
    method: 'get'
  })
}

// 新增bot 管理
export function addBot(data) {
  return request({
    url: '/kb/bot',
    method: 'post',
    data: data
  })
}

// 修改bot 管理
export function updateBot(data) {
  return request({
    url: '/kb/bot',
    method: 'put',
    data: data
  })
}

// 删除bot 管理
export function delBot(id) {
  return request({
    url: '/kb/bot/' + id,
    method: 'delete'
  })
}

// 复制bot 管理
export function copyBot(data) {
  return request({
    url: '/kb/bot/copyBot',
    method: 'post',
    data: data
  })
}
