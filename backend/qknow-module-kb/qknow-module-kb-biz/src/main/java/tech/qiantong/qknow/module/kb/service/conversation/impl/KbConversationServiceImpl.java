package tech.qiantong.qknow.module.kb.service.conversation.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbConversationDO;
import tech.qiantong.qknow.module.kb.dal.mapper.conversation.KbConversationMapper;
import tech.qiantong.qknow.module.kb.service.conversation.IKbConversationService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.List;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KbConversationServiceImpl extends ServiceImpl<KbConversationMapper, KbConversationDO> implements IKbConversationService {

    @Override
    public List<KbConversationDO> getConversationsByBotId(Long botId, Long workspaceId) {
        return list(new LambdaQueryWrapperX<KbConversationDO>()
                .eq(KbConversationDO::getBotId, botId)
                .eq(KbConversationDO::getWorkspaceId, workspaceId)
                .eq(KbConversationDO::getDelFlag, false)
                .orderByDesc(KbConversationDO::getCreateTime));
    }

    @Override
    public KbConversationDO createConversation(Long botId, Long workspaceId, String title) {
        KbConversationDO conversation = KbConversationDO.builder()
                .botId(botId)
                .workspaceId(workspaceId)
                .title(title != null ? title : "新对话")
                .status(0)
                .validFlag(true)
                .delFlag(false)
                .build();
        save(conversation);
        return conversation;
    }

    @Override
    public void deleteConversation(Long id) {
        removeById(id);
    }
}
