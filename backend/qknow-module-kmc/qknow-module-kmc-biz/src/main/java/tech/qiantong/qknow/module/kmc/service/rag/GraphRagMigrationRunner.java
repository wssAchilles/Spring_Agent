package tech.qiantong.qknow.module.kmc.service.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GraphRagMigrationRunner implements ApplicationRunner {

    @Resource
    private GraphRagProperties properties;
    @Resource
    private GraphRagSyncService graphRagSyncService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            int migrated = graphRagSyncService.migrateExistingMetadata();
            log.info("GraphRAG metadata migration finished: rows={}", migrated);
        } catch (Exception e) {
            log.warn("GraphRAG metadata migration failed, GraphRAG remains best-effort", e);
        }
    }
}
