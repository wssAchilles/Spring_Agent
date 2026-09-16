package tech.qiantong.qknow.hermes.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.qiantong.qknow.hermes.streaming.dto.DagExecutionSnapshot;
import tech.qiantong.qknow.hermes.streaming.dto.DagNodeExecutionEvent;
import tech.qiantong.qknow.hermes.streaming.engine.RealtimeDagEventStreamer;

import java.io.IOException;

/**
 * Hermes DAG 实时事件流式推流控制器
 * 提供标准 Server-Sent Events (SSE) 协议端点，向前端可视化画布实时推送工作流节点状态
 */
@RestController
@RequestMapping("/api/hermes/stream/dag")
public class HermesDagStreamController {

    private static final Logger log = LoggerFactory.getLogger(HermesDagStreamController.class);
    private static final Long DEFAULT_TIMEOUT = 180_000L; // 3分钟超时

    private final RealtimeDagEventStreamer eventStreamer;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public HermesDagStreamController(RealtimeDagEventStreamer eventStreamer) {
        this.eventStreamer = eventStreamer != null ? eventStreamer : new RealtimeDagEventStreamer();
    }

    public HermesDagStreamController() {
        this(new RealtimeDagEventStreamer());
    }

    /**
     * 订阅指定会话的实时 DAG 状态变迁事件流 (SSE)
     */
    @GetMapping(value = "/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDagEvents(@PathVariable("sessionId") String sessionId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        try {
            // 先推送当前会话的即时快照 (避免断线重连白屏)
            DagExecutionSnapshot snapshot = eventStreamer.buildSnapshot(sessionId, 0);
            emitter.send(SseEmitter.event()
                    .name("SNAPSHOT")
                    .data(snapshot));
        } catch (IOException e) {
            log.warn("向 SSE 客户端推送初始快照异常: sessionId={}, err={}", sessionId, e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        // 注册事件流监听器
        AutoCloseable subscription = eventStreamer.subscribe(sessionId, event -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("NODE_EVENT")
                        .id(event.eventId())
                        .data(event));
            } catch (IOException ex) {
                log.info("SSE 客户端连接断开: sessionId={}, err={}", sessionId, ex.getMessage());
                emitter.complete();
            }
        });

        emitter.onCompletion(() -> closeSubscription(subscription));
        emitter.onTimeout(() -> {
            log.info("SSE 连接超时自动关闭: sessionId={}", sessionId);
            emitter.complete();
            closeSubscription(subscription);
        });
        emitter.onError(throwable -> closeSubscription(subscription));

        return emitter;
    }

    /**
     * 手动触发模拟事件注入 (用于可视化调试与连通性验证)
     */
    @PostMapping(value = "/inject")
    public String injectEvent(@RequestBody DagNodeExecutionEvent event) {
        long elapsedMicros = eventStreamer.publishEvent(event);
        return "EVENT_PUBLISHED_IN_" + elapsedMicros + "US";
    }

    /**
     * 获取指定会话最新状态快照
     */
    @GetMapping(value = "/{sessionId}/snapshot")
    public DagExecutionSnapshot getSnapshot(@PathVariable("sessionId") String sessionId,
                                           @RequestParam(value = "totalNodes", defaultValue = "0") int totalNodes) {
        return eventStreamer.buildSnapshot(sessionId, totalNodes);
    }

    private void closeSubscription(AutoCloseable subscription) {
        try {
            if (subscription != null) {
                subscription.close();
            }
        } catch (Exception e) {
            log.warn("关闭 SSE 订阅器异常: {}", e.getMessage());
        }
    }
}
