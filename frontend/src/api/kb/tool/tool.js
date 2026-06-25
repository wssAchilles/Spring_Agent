import request from '@/utils/request'

// 查询工具管理列表
export function listTool(query) {
  return request({
    url: '/kb/tool/list',
    method: 'get',
    params: query
  })
}

// 查询工具管理详细
export function getTool(id) {
  return request({
    url: '/kb/tool/' + id,
    method: 'get'
  })
}

// 新增工具管理
export function addTool(data) {
  return request({
    url: '/kb/tool',
    method: 'post',
    data: data
  })
}

// 修改工具管理
export function updateTool(data) {
  return request({
    url: '/kb/tool',
    method: 'put',
    data: data
  })
}

// 删除工具管理
export function delTool(id) {
  return request({
    url: '/kb/tool/' + id,
    method: 'delete'
  })
}
