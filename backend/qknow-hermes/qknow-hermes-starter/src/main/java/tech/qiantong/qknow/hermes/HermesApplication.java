package tech.qiantong.qknow.hermes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Hermes 认知内核微服务启动类
 * 独立于控制面（Spring Boot 单体），通过 gRPC 提供 Agent 推理能力
 */
@Slf4j
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"tech.qiantong.qknow.hermes"})
public class HermesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesApplication.class, args);
        log.info("========== Hermes 认知内核启动成功 ==========");
    }
}
