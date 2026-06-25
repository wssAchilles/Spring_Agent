import request from '@/utils/request'

// 查询检索的资源列表
export function listResources(query) {
  return request({
    url: '/app/resources/list',
    method: 'get',
    params: query
  })
}

export function listByMessage(query) {
  return request({
    url: '/app/resources/listByMessage',
    method: 'get',
    params: query
  })
}

// 查询检索的资源详细
export function getResources(id) {
  return request({
    url: '/app/resources/' + id,
    method: 'get'
  })
}

// 新增检索的资源
export function addResources(data) {
  return request({
    url: '/app/resources',
    method: 'post',
    data: data
  })
}

// 修改检索的资源
export function updateResources(data) {
  return request({
    url: '/app/resources',
    method: 'put',
    data: data
  })
}

// 删除检索的资源
export function delResources(id) {
  return request({
    url: '/app/resources/' + id,
    method: 'delete'
  })
}
