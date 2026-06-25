import request from '@/utils/request'

// 查询知识库角色关联列表
export function listRole(query) {
  return request({
    url: '/kmc/role/list',
    method: 'get',
    params: query
  })
}

// 查询知识库角色关联详细
export function getRole(id) {
  return request({
    url: '/kmc/role/' + id,
    method: 'get'
  })
}

// 新增知识库角色关联
export function addRole(data) {
  return request({
    url: '/kmc/role',
    method: 'post',
    data: data
  })
}

// 修改知识库角色关联
export function updateRole(data) {
  return request({
    url: '/kmc/role',
    method: 'put',
    data: data
  })
}

// 删除知识库角色关联
export function delRole(id) {
  return request({
    url: '/kmc/role/' + id,
    method: 'delete'
  })
}
