package tech.qiantong.qknow.hermes.grpc;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.proto.*;

/**
 * Hermes gRPC 服务实现
 * 实现 hermes.proto 中定义的 HermesService
 */
@Slf4j
@GrpcService
public class HermesGrpcService extends HermesServiceGrpc.HermesServiceImplBase {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Override
    public void chat(ChatRequest request, StreamObserver<ChatEvent> responseObserver) {
        log.info("收到 Chat 请求: requestId={}, botId={}, question={}",
                request.getRequestId(), request.getBotId(), request.getQuestion());

        try {
            agentOrchestrator.chat(request).subscribe(
                    event -> {
                        responseObserver.onNext(event);
                    },
                    error -> {
                        log.error("Chat 流处理错误", error);
                        responseObserver.onNext(ChatEvent.newBuilder()
                                .setRequestId(request.getRequestId())
                                .setError(ErrorEvent.newBuilder()
                                        .setCode(500)
                                        .setMessage(error.getMessage())
                                        .build())
                                .build());
                        responseObserver.onCompleted();
                    },
                    () -> {
                        responseObserver.onCompleted();
                    }
            );
        } catch (Exception e) {
            log.error("Chat 请求处理失败", e);
            responseObserver.onNext(ChatEvent.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setError(ErrorEvent.newBuilder()
                            .setCode(500)
                            .setMessage(e.getMessage())
                            .build())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void executeFlow(FlowRequest request, StreamObserver<FlowEvent> responseObserver) {
        log.info("收到 ExecuteFlow 请求: requestId={}, flowId={}", request.getRequestId(), request.getFlowId());

        // Phase 2: DAG 流程执行
        responseObserver.onNext(FlowEvent.newBuilder()
                .setRequestId(request.getRequestId())
                .setError(ErrorEvent.newBuilder()
                        .setCode(501)
                        .setMessage("DAG 流程执行尚未实现")
                        .build())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void healthCheck(HealthRequest request, StreamObserver<HealthResponse> responseObserver) {
        responseObserver.onNext(HealthResponse.newBuilder()
                .setStatus("UP")
                .setVersion("2.2.1")
                .build());
        responseObserver.onCompleted();
    }
}
