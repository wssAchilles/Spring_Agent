import request from '@/utils/request'

export function getKnowledgeAssetDashboard() {
  return request({
    url: '/kd/dashboard/knowledge-asset',
    method: 'get'
  })
}

export function getBotOperationDashboard() {
  return request({
    url: '/kd/dashboard/bot-operation',
    method: 'get'
  })
}

export function getAppOperationDashboard() {
  return request({
    url: '/kd/dashboard/app-operation',
    method: 'get'
  })
}
