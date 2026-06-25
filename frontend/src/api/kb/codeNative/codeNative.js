import request from '@/utils/request'


// 查询bot 管理详细
export function getByBotId(botId) {
  return request({
    url: '/kb/codeNative/' + botId,
    method: 'get'
  })
}

// 修改bot 管理
export function submitCodeNative(data) {
  return request({
    url: '/kb/codeNative/submit',
    method: 'post',
    data: data
  })
}

