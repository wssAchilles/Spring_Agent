package tech.qiantong.qknow.module.kb.service.agent;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.proto.*;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbChatMessageSendRespVO;
import tech.qiantong.qknow.ai.enums.model.MessageTypeEnums;
import tech.qiantong.qknow.common.utils.DateUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class HermesGrpcClient {

    @Value("${hermes.grpc.host:localhost}")
    private String hermesHost;

    @Value("${hermes.grpc.port:9090}")
    private int hermesPort;

    @Value("${hermes.grpc.chat-deadline-seconds:120}")
    private long chatDeadlineSeconds;

    @Value("${hermes.grpc.auth.token:${HERMES_GRPC_AUTH_TOKEN:}}")
    private String hermesAuthToken;

    @Value("${hermes.grpc.tls.enabled:${HERMES_GRPC_TLS_ENABLED:false}}")
    private boolean grpcTlsEnabled;

    @Value("${hermes.grpc.tls.trust-cert-path:${HERMES_GRPC_TLS_TRUST_CERT_PATH:}}")
    private String grpcTrustCertPath;

    @Value("${hermes.grpc.tls.client-cert-path:${HERMES_GRPC_TLS_CLIENT_CERT_PATH:}}")
    private String grpcClientCertPath;

    @Value("${hermes.grpc.tls.client-key-path:${HERMES_GRPC_TLS_CLIENT_KEY_PATH:}}")
    private String grpcClientKeyPath;

    private ManagedChannel channel;
    private HermesServiceGrpc.HermesServiceStub asyncStub;

    @PostConstruct
    public void init() {
        ManagedChannelBuilder<?> channelBuilder;
        if (grpcTlsEnabled) {
            // P0-04：TLS/mTLS 通道（生产必须开启，杜绝 token 与 proto 明文传输）
            try {
                io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder nettyBuilder =
                        io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder.forAddress(hermesHost, hermesPort);
                io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder sslBuilder =
                        io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.forClient();
                if (grpcTrustCertPath != null && !grpcTrustCertPath.isBlank()) {
                    sslBuilder.trustManager(new java.io.File(grpcTrustCertPath));
                }
                if (grpcClientCertPath != null && !grpcClientCertPath.isBlank()
                        && grpcClientKeyPath != null && !grpcClientKeyPath.isBlank()) {
                    sslBuilder.keyManager(new java.io.File(grpcClientCertPath), new java.io.File(grpcClientKeyPath));
                }
                nettyBuilder.sslContext(sslBuilder.build());
                channelBuilder = nettyBuilder;
                log.info("Hermes gRPC 客户端启用 TLS: {}:{}", hermesHost, hermesPort);
            } catch (Exception e) {
                throw new IllegalStateException("Hermes gRPC TLS 初始化失败: " + e.getMessage(), e);
            }
        } else {
            channelBuilder = ManagedChannelBuilder.forAddress(hermesHost, hermesPort).usePlaintext();
            log.warn("Hermes gRPC 客户端使用明文通道（仅限开发/内网测试，生产必须开启 HERMES_GRPC_TLS_ENABLED）");
        }
        channel = channelBuilder
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

    public Flux<KbChatMessageSendRespVO> chat(ChatRequest request) {
        return Flux.create(emitter -> {
            AtomicBoolean emittedText = new AtomicBoolean(false);
            CallOptions options = CallOptions.DEFAULT
                    .withDeadlineAfter(chatDeadlineSeconds, TimeUnit.SECONDS);
            ClientCall<ChatRequest, ChatEvent> call =
                    channel.newCall(HermesServiceGrpc.getChatMethod(), options);
            ClientCall.Listener<ChatEvent> listener = new ClientCall.Listener<>() {
                @Override
                public void onMessage(ChatEvent event) {
                    KbChatMessageSendRespVO respVO = convertToRespVO(event, request.getQuestion(), emittedText);
                    if (respVO != null) {
                        emitter.next(respVO);
                    }
                }

                @Override
                public void onClose(Status status, Metadata trailers) {
                    if (status.isOk() || status.getCode() == Status.Code.CANCELLED) {
                        emitter.complete();
                    } else {
                        log.error("Hermes gRPC 调用失败: {}", status);
                        emitter.error(status.asRuntimeException());
                    }
                }
            };
            Metadata headers = new Metadata();
            if (hermesAuthToken != null && !hermesAuthToken.isBlank()) {
                headers.put(Metadata.Key.of("x-hermes-auth-token", Metadata.ASCII_STRING_MARSHALLER), hermesAuthToken);
            }
            call.start(listener, headers);
            call.request(Integer.MAX_VALUE);
            call.sendMessage(request);
            call.halfClose();
            emitter.onDispose(() -> {
                try {
                    call.cancel("client disposed", null);
                } catch (Exception e) {
                    log.debug("Hermes chat cancel failed: {}", e.getMessage());
                }
            });
        });
    }

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

    private KbChatMessageSendRespVO convertToRespVO(ChatEvent event, String question, AtomicBoolean emittedText) {
        KbChatMessageSendRespVO sendRespVO = new KbChatMessageSendRespVO();

        KbChatMessageSendRespVO.Message messageUser = new KbChatMessageSendRespVO.Message();
        messageUser.setType(MessageTypeEnums.USER.code);
        messageUser.setContent(question);
        messageUser.setCreateTime(new Date());
        sendRespVO.setSend(messageUser);

        if (event.hasChunk()) {
            StreamingChunk chunk = event.getChunk();
            KbChatMessageSendRespVO.Message message = new KbChatMessageSendRespVO.Message();
            message.setType(MessageTypeEnums.ROBOT.code);
            if (chunk.getIsThinking() || (chunk.getReasoningContent() != null && !chunk.getReasoningContent().isEmpty())) {
                message.setEventType("thinking");
                message.setContent(chunk.getReasoningContent());
            } else {
                message.setContent(chunk.getText());
                if (chunk.getText() != null && !chunk.getText().isBlank()) {
                    emittedText.set(true);
                }
            }
            message.setCreateTime(DateUtils.getNowDate());
            sendRespVO.setReceive(message);
        } else if (event.hasToolInvoked()) {
            ToolInvoked tool = event.getToolInvoked();
            KbChatMessageSendRespVO.Message message = new KbChatMessageSendRespVO.Message();
            message.setType(MessageTypeEnums.ROBOT.code);
            message.setEventType("tool_call");
            message.setToolName(tool.getToolName());
            message.setToolStatus(tool.getStatus());
            message.setContent("工具调用: " + tool.getToolName());
            message.setCreateTime(DateUtils.getNowDate());
            sendRespVO.setReceive(message);
        } else if (event.hasFinished()) {
            String fullText = event.getFinished().getFullText();
            if (emittedText.get() || fullText == null || fullText.isBlank()) {
                return null;
            }
            KbChatMessageSendRespVO.Message message = new KbChatMessageSendRespVO.Message();
            message.setType(MessageTypeEnums.ROBOT.code);
            message.setContent(fullText);
            message.setCreateTime(DateUtils.getNowDate());
            sendRespVO.setReceive(message);
            emittedText.set(true);
        } else if (event.hasError()) {
            ErrorEvent error = event.getError();
            KbChatMessageSendRespVO.Message message = new KbChatMessageSendRespVO.Message();
            message.setType(MessageTypeEnums.ROBOT.code);
            message.setEventType("error");
            message.setToolStatus("error");
            message.setContent("错误：" + (error.getMessage() != null ? error.getMessage() : "模型调用失败"));
            message.setCreateTime(DateUtils.getNowDate());
            sendRespVO.setReceive(message);
        } else {
            return null;
        }

        return sendRespVO;
    }
}
