import request, { API_BASE_URL } from '@/utils/request'
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getToken } from '@/utils/auth';

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

export function sendMessageStream(data, onMessage, onError, onClose) {
  const token = getToken();
  const ctrl = new AbortController();

  return fetchEventSource(`${API_BASE_URL}/kb/conversation/send`, {
    method: 'post',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    openWhenHidden: true,
    body: JSON.stringify(data),
    onmessage: onMessage,
    onerror: onError,
    onclose: onClose,
    signal: ctrl.signal
  });
}
