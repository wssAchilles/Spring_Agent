package tech.qiantong.qknow.hermes.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 153 模型逻辑别名解析器、平滑演化与密码学防篡改凭单契约测试
 * <p>
 * 验证核心假设 H-153：
 * 1. 逻辑能力语义别名精确映射；
 * 2. 历史退役模型透明平滑向后兼容升级；
 * 3. 纯 Java 21 Record 凭单常量时间自验真防伪；
 * 4. ChatModelFactory 缓存防分裂单例复用。
 * </p>
 *
 * @author Achilles
 * @since Phase 153
 */
public class ModelNameResolverContractTest {

    private ModelNameResolver resolver;
    private AiModelAliasProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiModelAliasProperties();
        resolver = new ModelNameResolver(properties);
    }

    @Test
    @DisplayName("Contract 1: 逻辑能力语义别名解析与凭单自验真")
    void testLogicalAliasResolution() {
        String[] aliases = {"primary", "fast", "reasoning", "default", "chat"};
        for (String alias : aliases) {
            ModelResolutionReceipt receipt = resolver.resolveWithReceipt("DeepSeek", alias);
            assertNotNull(receipt, "解析凭单不能为空");
            assertEquals("deepseek-flash", receipt.resolvedModel(), "别名必须解析为当前官方活跃推荐模型");
            assertEquals(ModelResolutionReceipt.ResolutionType.LOGICAL_ALIAS, receipt.resolutionType(), "解析类型必须为 LOGICAL_ALIAS");
            assertTrue(receipt.verifyReceipt(), "凭单必须通过密码学常量时间防侧信道自验真");
        }
    }

    @Test
    @DisplayName("Contract 2: 历史退役模型向后兼容透明平滑升级")
    void testLegacyModelUpgrade() {
        String[] legacyModels = {"deepseek-chat", "deepseek-reasoner", "deepseek-v3", "deepseek-r1", "deepseek-coder"};
        for (String legacy : legacyModels) {
            ModelResolutionReceipt receipt = resolver.resolveWithReceipt("DeepSeek", legacy);
            assertNotNull(receipt, "解析凭单不能为空");
            assertEquals("deepseek-flash", receipt.resolvedModel(), "退役模型必须透明升迁为官方活跃模型 deepseek-flash");
            assertEquals(ModelResolutionReceipt.ResolutionType.LEGACY_UPGRADE, receipt.resolutionType(), "解析类型必须为 LEGACY_UPGRADE");
            assertTrue(receipt.verifyReceipt(), "凭单必须通过密码学常量时间自验真");
        }
    }

    @Test
    @DisplayName("Contract 3: 未受管模型精确匹配直通")
    void testExactMatchPassthrough() {
        String customModel = "qwen-max-latest";
        ModelResolutionReceipt receipt = resolver.resolveWithReceipt("TongYi", customModel);
        assertNotNull(receipt);
        assertEquals(customModel, receipt.resolvedModel(), "未受管模型必须精确直通保留原名");
        assertEquals(ModelResolutionReceipt.ResolutionType.EXACT_MATCH, receipt.resolutionType());
        assertTrue(receipt.verifyReceipt());
    }

    @Test
    @DisplayName("Contract 4: 空参数默认安全回退")
    void testNullAndBlankFallback() {
        ModelResolutionReceipt receiptNull = resolver.resolveWithReceipt("DeepSeek", null);
        assertNotNull(receiptNull);
        assertEquals("deepseek-flash", receiptNull.resolvedModel(), "null 入参必须回退到默认主力模型");
        assertEquals(ModelResolutionReceipt.ResolutionType.DEFAULT_FALLBACK, receiptNull.resolutionType());
        assertTrue(receiptNull.verifyReceipt());

        ModelResolutionReceipt receiptBlank = resolver.resolveWithReceipt("DeepSeek", "   ");
        assertNotNull(receiptBlank);
        assertEquals("deepseek-flash", receiptBlank.resolvedModel(), "空白字符入参必须回退到默认主力模型");
        assertEquals(ModelResolutionReceipt.ResolutionType.DEFAULT_FALLBACK, receiptBlank.resolutionType());
        assertTrue(receiptBlank.verifyReceipt());
    }

    @Test
    @DisplayName("Contract 5: 密码学凭单防篡改与时序防御验证")
    void testCryptographicAntiTampering() {
        ModelResolutionReceipt receipt = resolver.resolveWithReceipt("DeepSeek", "primary");
        assertTrue(receipt.verifyReceipt(), "原始凭单必须验真通过");

        // 模拟攻击者恶意篡改解析后的目标模型
        ModelResolutionReceipt tamperedReceipt = new ModelResolutionReceipt(
                receipt.requestedPlatform(),
                receipt.requestedModel(),
                "malicious-hijacked-model",
                receipt.resolutionType(),
                receipt.timestampEpochMs(),
                receipt.signatureDigest()
        );

        assertFalse(tamperedReceipt.verifyReceipt(), "被篡改的模型凭单验真必须失败，成功阻断时序侧信道与恶意替换");
    }

    @Test
    @DisplayName("Contract 6: ChatModelFactory 缓存防分裂与单例无损复用")
    void testChatModelFactorySingletonCacheReuse() {
        ChatModelFactory factory = new ChatModelFactory();
        String apiKey = "sk-test-model-resolver-cache";
        String baseUrl = "https://api.deepseek.com/v1";

        // 分别通过退役名称、逻辑别名与真实物理名称调用工厂获取实例
        ChatModel modelFromLegacy = factory.getChatModel("deepseek", baseUrl, apiKey, "deepseek-chat");
        ChatModel modelFromAlias = factory.getChatModel("deepseek", baseUrl, apiKey, "primary");
        ChatModel modelFromPhysical = factory.getChatModel("deepseek", baseUrl, apiKey, "deepseek-flash");

        assertNotNull(modelFromLegacy);
        assertNotNull(modelFromAlias);
        assertNotNull(modelFromPhysical);

        // 验证三者引用严格同一（assertSame），证明底层连接池与客户端单例 100% 复用，彻底消除了缓存裂变
        assertSame(modelFromPhysical, modelFromLegacy, "退役模型入参必须命中 deepseek-flash 单例缓存");
        assertSame(modelFromPhysical, modelFromAlias, "逻辑别名入参必须命中 deepseek-flash 单例缓存");
    }
}
