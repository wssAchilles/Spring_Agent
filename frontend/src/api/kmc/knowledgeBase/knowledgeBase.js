import request from '@/utils/request'

// 查询知识库列表
export function listKnowledgeBase(query) {
  return request({
    url: '/kmc/knowledgeBase/list',
    method: 'get',
    params: query
  })
}

// 查询知识库详细
export function getKnowledgeBase(id) {
  return request({
    url: '/kmc/knowledgeBase/' + id,
    method: 'get'
  })
}

// 新增知识库
export function addKnowledgeBase(data) {
  return request({
    url: '/kmc/knowledgeBase',
    method: 'post',
    data: data
  })
}

// 修改知识库
export function updateKnowledgeBase(data) {
  return request({
    url: '/kmc/knowledgeBase',
    method: 'put',
    data: data
  })
}

// 删除知识库
export function delKnowledgeBase(id) {
  return request({
    url: '/kmc/knowledgeBase/' + id,
    method: 'delete'
  })
}

// 获取文本嵌入模型
export function getTextEmbedding() {
  return request({
    url: '/kmc/knowledgeBase/getTextEmbedding',
    method: 'get'
  })
}

// 获取文本嵌入模型
export function getRerank() {
  return request({
    url: '/kmc/knowledgeBase/getRerank',
    method: 'get'
  })
}

export function getRole(id) {
  return request({
    url: '/kmc/knowledgeBase/role/' + id,
    method: 'get'
  })
}

// 修改知识库
export function updateKnowledgeBaseRole(data) {
  return request({
    url: '/kmc/knowledgeBase/role',
    method: 'put',
    data: data
  })
}

// 召回测试
export function recallTest(data) {
  return request({
    url: '/kmc/knowledgeBase/recallTest',
    method: 'post',
    data: data
  })
}

// 根据权限获取知识库列表
export function getKmcKnowledgeBaseList(isValid) {
  return request({
    url: '/kmc/knowledgeBase/getKmcKnowledgeBaseList',
    method: 'get',
    params: { isValid }
  })
}

export function changeKnowledgeValid(id, validFlag) {
  return request({
    url: '/kmc/knowledgeBase/changeKnowledgeValid',
    method: 'put',
    params: { id, validFlag }
  })
}

// 召回调试
export function recallDebug(data) {
  return request({
    url: '/kmc/knowledgeBase/recallDebug',
    method: 'post',
    data: data
  })
}

// 清除RAG缓存
export function clearRagCache(id) {
  return request({
    url: '/kmc/knowledgeBase/' + id + '/ragCache',
    method: 'delete'
  })
}
