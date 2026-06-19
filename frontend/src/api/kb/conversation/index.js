import request from '@/utils/request'

export function getConversations(botId, workspaceId) {
  return request({
    url: '/kb/conversation/list',
    method: 'get',
    params: { botId, workspaceId }
  })
}

export function createConversation(data) {
  return request({
    url: '/kb/conversation',
    method: 'post',
    data
  })
}

export function deleteConversation(id) {
  return request({
    url: '/kb/conversation/' + id,
    method: 'delete'
  })
}

export function getMessages(conversationId) {
  return request({
    url: '/kb/conversation/' + conversationId + '/messages',
    method: 'get'
  })
}

export function sendMessage(data) {
  return request({
    url: '/kb/conversation/send',
    method: 'post',
    data
  })
}
