import request from '@/utils/request'

// 查询知识分类列表
export function listCategory(query) {
  return request({
    url: '/kg/category/list',
    method: 'get',
    params: query
  })
}

export function getAllList(query) {
  return request({
    url: '/kg/category/allList',
    method: 'get',
    params: query
  })
}

// 查询知识分类详细
export function getCategory(id) {
  return request({
    url: '/kg/category/' + id,
    method: 'get'
  })
}

// 新增知识分类
export function addCategory(data) {
  return request({
    url: '/kg/category',
    method: 'post',
    data: data
  })
}

// 修改知识分类
export function updateCategory(data) {
  return request({
    url: '/kg/category',
    method: 'put',
    data: data
  })
}

// 删除知识分类
export function delCategory(id) {
  return request({
    url: '/kg/category/' + id,
    method: 'delete'
  })
}
