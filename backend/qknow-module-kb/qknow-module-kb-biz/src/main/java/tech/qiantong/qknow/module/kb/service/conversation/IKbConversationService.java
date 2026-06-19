package tech.qiantong.qknow.module.kb.service.conversation;

import com.baomidou.mybatisplus.extension.service.IService;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbConversationDO;

import java.util.List;

public interface IKbConversationService extends IService<KbConversationDO> {

    List<KbConversationDO> getConversationsByBotId(Long botId, Long workspaceId);

    KbConversationDO createConversation(Long botId, Long workspaceId, String title);

    void deleteConversation(Long id);
}
