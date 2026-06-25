import request from '@/utils/request'

// 查询召回记录列表
export function listLog(query) {
  return request({
    url: '/kmc/log/list',
    method: 'get',
    params: query
  })
}

// 查询召回记录详细
export function getLog(id) {
  return request({
    url: '/kmc/log/' + id,
    method: 'get'
  })
}

// 新增召回记录
export function addLog(data) {
  return request({
    url: '/kmc/log',
    method: 'post',
    data: data
  })
}

// 修改召回记录
export function updateLog(data) {
  return request({
    url: '/kmc/log',
    method: 'put',
    data: data
  })
}

// 删除召回记录
export function delLog(id) {
  return request({
    url: '/kmc/log/' + id,
    method: 'delete'
  })
}

export function getKnowledgeBaseRecallLogList(query) {
  return request({
    url: '/kmc/log/getKnowledgeBaseRecallLogList',
    method: 'get',
    params: query
  })
}
