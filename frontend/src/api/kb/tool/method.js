import request from '@/utils/request'

// 查询工具方法列表
export function listMethod(query) {
  return request({
    url: '/kb/method/list',
    method: 'get',
    params: query
  })
}

// 查询工具方法详细
export function getMethod(id) {
  return request({
    url: '/kb/method/' + id,
    method: 'get'
  })
}

// 新增工具方法
export function addMethod(data) {
  return request({
    url: '/kb/method',
    method: 'post',
    data: data
  })
}

// 修改工具方法
export function updateMethod(data) {
  return request({
    url: '/kb/method',
    method: 'put',
    data: data
  })
}

// 删除工具方法
export function delMethod(id) {
  return request({
    url: '/kb/method/' + id,
    method: 'delete'
  })
}
