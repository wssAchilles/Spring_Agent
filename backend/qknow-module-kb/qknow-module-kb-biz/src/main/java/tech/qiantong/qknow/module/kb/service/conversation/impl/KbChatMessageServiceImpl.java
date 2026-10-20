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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private tech.qiantong.qknow.module.kb.dal.mapper.conversation.KbConversationMapper conversationMapper;

    @Override
    public List<KbChatMessageDO> getMessagesByConversationId(Long conversationId) {
        if (conversationId == null) {
            return java.util.Collections.emptyList();
        }
        // 多租户越权防护：校验当前会话是否归属于当前登录用户
        try {
            Long currentUserId = tech.qiantong.qknow.common.core.utils.SecurityUtils.getUserId();
            if (currentUserId != null && conversationMapper != null) {
                tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbConversationDO conv = conversationMapper.selectById(conversationId);
                if (conv != null && conv.getUserId() != null && !conv.getUserId().equals(currentUserId)) {
                    log.warn("[越权防护] 用户 {} 试图非法跨租户/跨用户读取会话 {} 的消息历史，已拒绝访问", currentUserId, conversationId);
                    return java.util.Collections.emptyList();
                }
            }
        } catch (Exception ignored) {
            // 无 Web 会话或系统任务时跳过校验
        }

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
