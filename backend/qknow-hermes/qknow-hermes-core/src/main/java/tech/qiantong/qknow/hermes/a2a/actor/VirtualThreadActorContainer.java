package tech.qiantong.qknow.hermes.a2a.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageEnvelope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 Java 21 虚拟线程的轻量级异步 Actor 容器
 * 落实定理 3：单 Agent 绑定独立虚拟线程事件循环与有界 Mailbox，非阻塞消息投递，死锁概率严格为 0
 */
public class VirtualThreadActorContainer {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadActorContainer.class);

    private final Map<String, ActorInstance> actors = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public record ActorInstance(
            String agentId,
            BoundedMailbox mailbox,
            Consumer<A2AMessageEnvelope> handler,
            Thread virtualThread,
            AtomicBoolean active
    ) {}

    /**
     * 注册并启动一个异步 Actor 实例
     *
     * @param agentId 智能体全局唯一标识
     * @param handler 消息处理回调逻辑
     */
    public void registerActor(String agentId, Consumer<A2AMessageEnvelope> handler) {
        registerActor(agentId, handler, BoundedMailbox.DEFAULT_CAPACITY);
    }

    /**
     * 注册并启动一个具备自定义信箱容量的异步 Actor 实例
     */
    public void registerActor(String agentId, Consumer<A2AMessageEnvelope> handler, int mailboxCapacity) {
        if (agentId == null || handler == null) {
            throw new IllegalArgumentException("agentId 与 handler 不能为空");
        }

        BoundedMailbox mailbox = new BoundedMailbox(mailboxCapacity);
        AtomicBoolean active = new AtomicBoolean(true);

        Thread vt = Thread.ofVirtual().name("actor-vt-" + agentId).start(() -> {
            log.info("[ActorContainer] 启动 Actor 虚拟线程: {}", agentId);
            while (running.get() && active.get()) {
                try {
                    A2AMessageEnvelope envelope = mailbox.poll(500, TimeUnit.MILLISECONDS);
                    if (envelope != null) {
                        handler.accept(envelope);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    log.error("[ActorContainer] 处理消息异常 [agentId={}]: {}", agentId, t.getMessage(), t);
                }
            }
            log.info("[ActorContainer] Actor 虚拟线程已停止: {}", agentId);
        });

        ActorInstance instance = new ActorInstance(agentId, mailbox, handler, vt, active);
        actors.put(agentId, instance);
    }

    /**
     * 注销并停止指定 Actor
     */
    public void unregisterActor(String agentId) {
        ActorInstance instance = actors.remove(agentId);
        if (instance != null) {
            instance.active().set(false);
            instance.virtualThread().interrupt();
        }
    }

    /**
     * 向目标智能体投递消息（纯异步非阻塞）
     *
     * @param envelope 消息信封
     * @return 成功放入目标 Mailbox 返回 true；若目标不存在或目标 Mailbox 满返回 false（反压）
     */
    public boolean send(A2AMessageEnvelope envelope) {
        if (envelope == null || envelope.recipientAgentId() == null) {
            return false;
        }
        ActorInstance instance = actors.get(envelope.recipientAgentId());
        if (instance == null) {
            log.warn("[ActorContainer] 目标智能体未注册: {}", envelope.recipientAgentId());
            return false;
        }

        // 非阻塞入队
        boolean offered = instance.mailbox().offer(envelope);
        if (!offered) {
            log.warn("[ActorContainer] 目标智能体 Mailbox 饱和，触发反压丢弃/快速失败: recipient={}", envelope.recipientAgentId());
        }
        return offered;
    }

    /**
     * 获取指定智能体的信箱
     */
    public BoundedMailbox getMailbox(String agentId) {
        ActorInstance instance = actors.get(agentId);
        return instance != null ? instance.mailbox() : null;
    }

    /**
     * 容器关闭与优雅停机
     */
    public void shutdown() {
        running.set(false);
        for (ActorInstance instance : actors.values()) {
            instance.active().set(false);
            instance.virtualThread().interrupt();
        }
        actors.clear();
    }
}
