import request from '@/utils/request'

// 查询ai应用类型列表
export function listType(query) {
  return request({
    url: '/app/type/list',
    method: 'get',
    params: query
  })
}

// 查询ai应用类型详细
export function getType(id) {
  return request({
    url: '/app/type/' + id,
    method: 'get'
  })
}

// 新增ai应用类型
export function addType(data) {
  return request({
    url: '/app/type',
    method: 'post',
    data: data
  })
}

// 修改ai应用类型
export function updateType(data) {
  return request({
    url: '/app/type',
    method: 'put',
    data: data
  })
}

// 删除ai应用类型
export function delType(id) {
  return request({
    url: '/app/type/' + id,
    method: 'delete'
  })
}

// 修改ai应用类型状态
export function updateTypeStatus(data) {
  return request({
    url: '/app/type/editStatus',
    method: 'put',
    data: data
  })
}

export function getChatFlowEnableList() {
  return request({
    url: '/app/type/getChatFlowEnableList',
    method: 'get',
  })
}

export function getWorkFlowEnableList() {
  return request({
    url: '/app/type/getWorkFlowEnableList',
    method: 'get',
  })
}
