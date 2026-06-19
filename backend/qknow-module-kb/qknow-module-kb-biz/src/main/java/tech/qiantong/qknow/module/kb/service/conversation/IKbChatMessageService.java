package tech.qiantong.qknow.module.kb.service.conversation;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbChatMessageDO;

import java.util.List;

public interface IKbChatMessageService extends IService<KbChatMessageDO> {

    List<KbChatMessageDO> getMessagesByConversationId(Long conversationId);

    KbChatMessageDO saveMessage(Long conversationId, String role, String content);

    void deleteByConversationId(Long conversationId);
}
