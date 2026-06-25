import request from '@/utils/request'

// 查询抽取任务执行日志列表
export function listTaskLog(query) {
  return request({
    url: '/ext/taskLog/list',
    method: 'get',
    params: query
  })
}

// 查询抽取任务执行日志详细
export function getDetailPage(query) {
  return request({
    url: '/ext/taskLog/detail/page',
    method: 'get',
    params: query
  })
}

// 删除抽取任务执行日志
export function delTaskLog(id) {
  return request({
    url: '/ext/taskLog/' + id,
    method: 'delete'
  })
}