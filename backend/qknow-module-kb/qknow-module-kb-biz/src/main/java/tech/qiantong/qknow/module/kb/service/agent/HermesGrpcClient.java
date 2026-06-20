package tech.qiantong.qknow.module.kb.service.agent;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tech.qiantong.qknow.hermes.proto.*;
import tech.qiantong.qknow.module.kb.controller.admin.conversation.vo.KbChatMessageSendRespVO;
import tech.qiantong.qknow.ai.enums.model.MessageTypeEnums;
import tech.qiantong.qknow.common.utils.DateUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Hermes gRPC 客户端
 * 控制面通过此客户端调用 Hermes 认知内核微服务
 */
@Slf4j
@Service
public class HermesGrpcClient {

    @Value("${hermes.grpc.host:localhost}")
    private String hermesHost;

    @Value("${hermes.grpc.port:9090}")
    private int hermesPort;

    private ManagedChannel channel;
    private HermesServiceGrpc.HermesServiceStub asyncStub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(hermesHost, hermesPort)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .maxInboundMessageSize(16 * 1024 * 1024)
                .build();
        asyncStub = HermesServiceGrpc.newStub(channel);
        log.info("Hermes gRPC 客户端初始化完成: {}:{}", hermesHost, hermesPort);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
            }
        }
    }

    /**
     * 调用 Hermes Chat RPC，返回 SSE 流
     */
    public Flux<KbChatMessageSendRespVO> chat(ChatRequest request) {
        return Flux.create(emitter -> {
            asyncStub.chat(request, new StreamObserver<>() {
                @Override
                public void onNext(ChatEvent event) {
                    KbChatMessageSendRespVO respVO = convertToRespVO(event, request.getQuestion(), request.getCreateTime());
                    if (respVO != null) {
                        emitter.next(respVO);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("Hermes gRPC 调用失败", t);
                    emitter.error(t);
                }

                @Override
                public void onCompleted() {
                    emitter.complete();
                }
            });
        });
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            HermesServiceGrpc.HermesServiceBlockingStub blockingStub =
                    HermesServiceGrpc.newBlockingStub(channel);
            HealthResponse response = blockingStub.healthCheck(HealthRequest.newBuilder().build());
            return "UP".equals(response.getStatus());
        } catch (Exception e) {
            log.warn("Hermes 健康检查失败", e);
            return false;
        }
    }

    private KbChatMessageSendRespVO convertToRespVO(ChatEvent event, String question, String createTime) {
        if (!event.hasChunk()) {
            return null;
        }

        StreamingChunk chunk = event.getChunk();
        KbChatMessageSendRespVO sendRespVO = new KbChatMessageSendRespVO();

        // 机器人回复消息
        KbChatMessageSendRespVO.Message message = new KbChatMessageSendRespVO.Message();
        message.setType(MessageTypeEnums.ROBOT.code);
        message.setContent(chunk.getText());
        message.setCreateTime(DateUtils.getNowDate());
        sendRespVO.setReceive(message);

        // 用户发送消息
        KbChatMessageSendRespVO.Message messageUser = new KbChatMessageSendRespVO.Message();
        messageUser.setType(MessageTypeEnums.USER.code);
        messageUser.setContent(question);
        messageUser.setCreateTime(new Date());
        sendRespVO.setSend(messageUser);

        return sendRespVO;
    }
}
