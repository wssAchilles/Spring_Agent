package tech.qiantong.qknow.mcp.client.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 语义工具注册表 (SemanticToolRegistry)
 * 线程安全维护全量已注册工具的千问 1536 维超球面嵌入元数据
 */
@Slf4j
@Component
public class SemanticToolRegistry {

    private final List<ToolEmbeddingEntry> toolEntries = new CopyOnWriteArrayList<>();

    public void registerTool(ToolEmbeddingEntry entry) {
        if (entry != null) {
            toolEntries.removeIf(t -> t.toolName().equals(entry.toolName()) && t.serverId().equals(entry.serverId()));
            toolEntries.add(entry);
            log.debug("[ToolRegistry] 注册工具: {} ({})", entry.toolName(), entry.serverId());
        }
    }

    public void registerAll(Collection<ToolEmbeddingEntry> entries) {
        if (entries != null) {
            for (ToolEmbeddingEntry e : entries) {
                registerTool(e);
            }
        }
    }

    public List<ToolEmbeddingEntry> getAllTools() {
        return List.copyOf(toolEntries);
    }

    public int getToolCount() {
        return toolEntries.size();
    }

    public void clear() {
        toolEntries.clear();
    }
}
