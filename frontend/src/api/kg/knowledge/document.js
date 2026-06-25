import request from '@/utils/request'

// 查询知识文件列表
export function listDocument(query) {
  return request({
    url: '/kg/document/list',
    method: 'get',
    params: query
  })
}

export function getFileTypes() {
  return request({
    url: '/kg/document/getFileTypes',
    method: 'get',
  })
}

// 查询知识文件详细
export function getDocument(id) {
  return request({
    url: '/kg/document/' + id,
    method: 'get'
  })
}

// 新增知识文件
export function addDocument(data) {
  return request({
    url: '/kg/document',
    method: 'post',
    data: data
  })
}

// 修改知识文件
export function updateDocument(data) {
  return request({
    url: '/kg/document',
    method: 'put',
    data: data
  })
}

// 删除知识文件
export function delDocument(id) {
  return request({
    url: '/kg/document/' + id,
    method: 'delete'
  })
}

export function kgTrackCount(data) {
  return request({
    url: '/kg/document/trackCount',
    method: 'post',
    data: data
  })
}
