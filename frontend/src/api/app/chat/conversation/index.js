import request from "@/utils/request.js";

// AI 聊天对话 API
export const ChatConversationApi = {
  // 获得【我的】聊天对话
  getChatConversationMy: (id) => {
    return request({
      url: `/app/conversation/` + id,
      method: 'get',
    });
  },

// 新增【我的】聊天对话
  createChatConversationMy: (data) => {
    return request({
      url: `/app/conversation`,
      method: 'post',
      data: data
    });
  },

// 更新【我的】聊天对话
  updateChatConversationMy: (data) => {
    return request({
      url: `/app/conversation`,
      method: 'put',
      data: data
    });
  },

// 删除【我的】聊天对话
  deleteChatConversationMy: (id) => {
    return request({
      url: `/app/conversation/` + id,
      method: 'delete',
    });
  },

// 获得【我的】聊天对话列表
  getChatConversationMyList: () => {
    return request({
      url: `/app/conversation/myList`,
      method: 'get'
    });
  },

}
