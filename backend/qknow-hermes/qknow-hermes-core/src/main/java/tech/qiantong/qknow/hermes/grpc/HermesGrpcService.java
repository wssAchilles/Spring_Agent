package tech.qiantong.qknow.hermes.grpc;

import com.alibaba.fastjson2.JSONObject;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.flow.FlowExecutor;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.proto.*;

import java.util.List;
import java.util.Map;

/**
 * Hermes gRPC 服务实现
 * 实现 hermes.proto 中定义的 HermesService
 */
@Slf4j
@GrpcService
public class HermesGrpcService extends HermesServiceGrpc.HermesServiceImplBase {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private FlowExecutor flowExecutor;

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

        try {
            List<NodeRunResultBO> results = flowExecutor.execute(request);
            FlowCompleted.Builder completed = FlowCompleted.newBuilder();
            for (NodeRunResultBO result : results) {
                NodeResult nodeResult = toNodeResult(result);
                responseObserver.onNext(FlowEvent.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setNodeResult(nodeResult)
                        .build());
                completed.addResults(nodeResult);
            }
            responseObserver.onNext(FlowEvent.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setFlowCompleted(completed.build())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("ExecuteFlow 请求处理失败", e);
            responseObserver.onNext(FlowEvent.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setError(ErrorEvent.newBuilder()
                            .setCode(500)
                            .setMessage(e.getMessage())
                            .build())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private NodeResult toNodeResult(NodeRunResultBO result) {
        Map<String, Object> output = result.getOutput() != null ? result.getOutput() : Map.of();
        return NodeResult.newBuilder()
                .setNodeUuid(result.getNodeUuid() != null ? result.getNodeUuid() : "")
                .setNodeName(result.getNodeName() != null ? result.getNodeName() : "")
                .setStatus(result.getStatus() != null ? result.getStatus() : 0)
                .setOutputJson(JSONObject.toJSONString(output))
                .setDurationMs(result.getDuration() != null ? result.getDuration() : 0L)
                .build();
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
