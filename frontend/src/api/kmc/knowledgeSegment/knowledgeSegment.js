import request from '@/utils/request'

// 查询文件分段列表
export function listKnowledgeSegment(query) {
  return request({
    url: '/kmc/knowledgeSegment/list',
    method: 'get',
    params: query
  })
}

// 查询文件分段列表树形结构
export function listKnowledgeSegmentTree(query) {
  return request({
    url: '/kmc/knowledgeSegment/listTree',
    method: 'get',
    params: query
  })
}

// 获取文件分段所有顶级节点
export function getAllLevelNodes(query) {
  return request({
    url: '/kmc/knowledgeSegment/getAllLevelNodes',
    method: 'get',
    params: query
  })
}

// 查询文件分段详细
export function getKnowledgeSegment(id) {
  return request({
    url: '/kmc/knowledgeSegment/' + id,
    method: 'get'
  })
}

// 新增文件分段
export function addKnowledgeSegment(data) {
  return request({
    url: '/kmc/knowledgeSegment',
    method: 'post',
    data: data
  })
}

// 修改文件分段
export function updateKnowledgeSegment(data) {
  return request({
    url: '/kmc/knowledgeSegment',
    method: 'put',
    data: data
  })
}

// 删除文件分段
export function delKnowledgeSegment(id) {
  return request({
    url: '/kmc/knowledgeSegment/' + id,
    method: 'delete'
  })
}
