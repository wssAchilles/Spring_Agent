import request from '@/utils/request'

// 查询 bot 访问日志
export function botLogPage(query) {
  return request({
    url: '/kb/runtime/list',
    method: 'get',
    params: query
  })
}

