package tech.qiantong.qknow.module.kb.service.conversation.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbChatMessageDO;
import tech.qiantong.qknow.module.kb.dal.mapper.conversation.KbChatMessageMapper;
import tech.qiantong.qknow.module.kb.service.conversation.IKbChatMessageService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.List;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class KbChatMessageServiceImpl extends ServiceImpl<KbChatMessageMapper, KbChatMessageDO> implements IKbChatMessageService {

    @Override
    public List<KbChatMessageDO> getMessagesByConversationId(Long conversationId) {
        return list(new LambdaQueryWrapperX<KbChatMessageDO>()
                .eq(KbChatMessageDO::getConversationId, conversationId)
                .eq(KbChatMessageDO::getDelFlag, 0)
                .orderByAsc(KbChatMessageDO::getCreateTime));
    }

    @Override
    public KbChatMessageDO saveMessage(Long conversationId, String role, String content) {
        KbChatMessageDO message = KbChatMessageDO.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .validFlag(1)
                .delFlag(0)
                .build();
        save(message);
        return message;
    }

    @Override
    public void deleteByConversationId(Long conversationId) {
        remove(new LambdaQueryWrapperX<KbChatMessageDO>()
                .eq(KbChatMessageDO::getConversationId, conversationId));
    }
}
