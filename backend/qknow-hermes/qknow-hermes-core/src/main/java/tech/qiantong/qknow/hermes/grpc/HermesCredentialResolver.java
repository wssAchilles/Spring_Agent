package tech.qiantong.qknow.hermes.grpc;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;
import tech.qiantong.qknow.common.utils.security.SensitiveDataCipher;

/**
 * Hermes 侧模型凭据解析器 (P0-04)
 * 控制面不再通过 gRPC 下发明文 API Key，只下发 ai_api_key.id；
 * Hermes 从共享数据库读取加密值，并使用与主应用一致的加密口令本地解密。
 */
@Slf4j
@Component
public class HermesCredentialResolver {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Value("${mybatis-plus.encryptor.password:${token.secret:}}")
    private String encryptorPassword;

    @PostConstruct
    public void init() {
        if (encryptorPassword != null && !encryptorPassword.isBlank()) {
            SensitiveDataCipher.configure(encryptorPassword);
            log.info("Hermes 模型凭据解密口令已初始化");
        } else {
            log.warn("Hermes 未配置模型凭据解密口令 (token.secret / mybatis-plus.encryptor.password)，"
                    + "加密存储的 API Key 将无法解密");
        }
    }

    /**
     * 若请求未携带明文 apiKey，则按 keyId 从共享库解析并回填。
     * 解析失败时 fail-close，避免以空密钥静默调用外部模型。
     */
    public ChatRequest enrich(ChatRequest request) {
        if (!request.hasModelConfig()) {
            return request;
        }
        ModelConfig modelConfig = request.getModelConfig();
        if (modelConfig.getApiKey() != null && !modelConfig.getApiKey().isBlank()) {
            return request;
        }
        long keyId = modelConfig.getKeyId();
        if (keyId <= 0L) {
            return request;
        }
        String apiKey = resolveApiKey(keyId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("模型凭据解析失败: keyId=" + keyId
                    + "，请检查 Hermes 数据库连接与 TOKEN_SECRET 配置");
        }
        return request.toBuilder()
                .setModelConfig(modelConfig.toBuilder().setApiKey(apiKey).build())
                .build();
    }

    public String resolveApiKey(long keyId) {
        if (keyId <= 0L || jdbcTemplate == null) {
            return null;
        }
        try {
            String encrypted = jdbcTemplate.queryForObject(
                    "SELECT api_key FROM ai_api_key WHERE id = ?", String.class, keyId);
            return SensitiveDataCipher.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("读取模型凭据失败: keyId={}, error={}", keyId, e.getMessage());
            return null;
        }
    }
}
