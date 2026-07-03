# 修复剩余架构级 Bug 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task.

**Goal:** 修复 3 类编译/测试失败：(1) FlowExecutorTest/DagE2ETest DAG 状态机 Bug, (2) QueryRouterTest DSPy 参数化缺失, (3) 编译依赖缺失导致 Lombok 方法不可见

**Architecture:** 根因分析发现测试失败分 3 类：FlowExecutor 返回空结果是因为 DagExecutor 的 FlowStateStore 快照逻辑在 stateStore=null 时的 NPE 路径；QueryRouterTest 期望的 DSPy 属性解耦接口（systemPrompt/promptVersion/fewShotExamples + resolveClassifySystemPrompt）完全未实现；编译错误（LiveBackendIntegrationTest 和 RagRetrievalServiceDynamicTopKTest 引用不存在的类）阻塞了整个测试模块编译。

**Tech Stack:** Java 17, Spring Boot 3.5.8, Lombok, JUnit 5, Mockito

---

## Task 1: 修复编译阻塞 — 删除不存在的类引用

**Covers:** 编译错误阻塞所有测试

**Files:**
- Modify: `backend/tests/src/test/java/tech/qiantong/qknow/integration/LiveBackendIntegrationTest.java`
- Modify: `backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalServiceDynamicTopKTest.java`

- [ ] **Step 1: 修复 LiveBackendIntegrationTest 的 SleepTimeMemoryAgent import**

`LiveBackendIntegrationTest.java` 第 17 行引用 `tech.qiantong.qknow.hermes.memory.SleepTimeMemoryAgent`。该类在 `qknow-hermes-core` 模块中，但 `tests` 模块可能无法解析。检查实际包名：

```bash
grep -rn "package.*SleepTimeMemoryAgent\|class SleepTimeMemoryAgent" backend/qknow-hermes/
```

如果类存在但 tests 模块无法解析，需要在 tests/pom.xml 中添加 hermes-core 依赖。

- [ ] **Step 2: 修复 RagRetrievalServiceDynamicTopKTest 引用不存在的 DynamicTopKConfig**

该测试引用 `DynamicTopKConfig` 类，但该类不存在。测试期望：
- `DynamicTopKConfig` 有 `setEnabled(boolean)` 方法
- `RagRetrievalService` 有 `resolveTopK(int, QueryRoute, QueryIntent)` 方法
- `RagRetrievalService` 有 `dynamicTopKConfig` 字段

创建 `DynamicTopKConfig.java` 和 `resolveTopK` 方法。

- [ ] **Step 3: 验证编译通过**

Run: `mvn clean compile -pl tests -am -q`
Expected: BUILD SUCCESS

---

## Task 2: 创建 DynamicTopKConfig + resolveTopK 方法

**Covers:** implementation_plan.md §4 (Dynamic topK)

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/DynamicTopKConfig.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java`

- [ ] **Step 1: 创建 DynamicTopKConfig**

```java
package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qknow.rag.dynamic-top-k")
public class DynamicTopKConfig {
    private boolean enabled = true;
    private int defaultTopK = 10;
    private int minTopK = 3;
    private int maxTopK = 80;
    private int complexMinTopK = 12;
    private double mediumMultiplier = 1.0;
    private double complexMultiplier = 1.8;
    private double temporalMultiplier = 1.3;
    private double keywordMultiplierStep = 0.08;
    private double maxKeywordBonus = 0.5;
}
```

- [ ] **Step 2: 在 RagRetrievalService 中注入 DynamicTopKConfig 并实现 resolveTopK**

在 RagRetrievalService 中添加：
```java
@Resource
private DynamicTopKConfig dynamicTopKConfig;

private int resolveTopK(int requestedTopK, QueryRouter.QueryRoute route, QueryIntent intent) {
    if (dynamicTopKConfig == null || !dynamicTopKConfig.isEnabled()) {
        return requestedTopK;
    }
    double multiplier = switch (route) {
        case COMPLEX -> dynamicTopKConfig.getComplexMultiplier();
        case MEDIUM -> dynamicTopKConfig.getMediumMultiplier();
        default -> 1.0;
    };
    if (intent.getDayNo() != null) {
        multiplier *= dynamicTopKConfig.getTemporalMultiplier();
    }
    int keywordCount = intent.getKeywords() != null ? intent.getKeywords().size() : 0;
    multiplier += Math.min(keywordCount * dynamicTopKConfig.getKeywordMultiplierStep(),
            dynamicTopKConfig.getMaxKeywordBonus());
    int result = (int) Math.round(requestedTopK * multiplier);
    if (route == QueryRouter.QueryRoute.COMPLEX) {
        result = Math.max(result, dynamicTopKConfig.getComplexMinTopK());
    }
    return Math.max(dynamicTopKConfig.getMinTopK(),
            Math.min(result, dynamicTopKConfig.getMaxTopK()));
}
```

- [ ] **Step 3: 验证编译和测试**

Run: `mvn clean test -pl tests -Dtest="RagRetrievalServiceDynamicTopKTest" -DfailIfNoTests=false`
Expected: PASS

---

## Task 3: 实现 QueryRouter DSPy 参数化接口

**Covers:** implementation_plan.md §3 (DSPy), QueryRouterTest 全部期望

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java`

- [ ] **Step 1: 在 QueryRouterConfig 中添加缺失字段**

```java
@Data
@Component
@ConfigurationProperties(prefix = "hermes.rag.router")
public static class QueryRouterConfig {
    private boolean enabled = true;
    private String platform = "DeepSeek";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String modelName = "deepseek-chat";
    private String classifyPrompt;      // 已有
    private String systemPrompt;        // 新增：自定义系统提示词
    private String promptVersion = "manual-v1";  // 新增：Prompt 版本
    private List<String> fewShotExamples = new ArrayList<>();  // 新增：Few-shot 示例
}
```

- [ ] **Step 2: 实现 resolveClassifySystemPrompt 方法**

将 `getClassifySystemPrompt()` 重命名为 `resolveClassifySystemPrompt()`，并支持 systemPrompt/promptVersion/fewShotExamples 注入：

```java
private String resolveClassifySystemPrompt() {
    StringBuilder builder = new StringBuilder();

    // 优先使用自定义 systemPrompt，否则使用默认
    if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
        builder.append(config.getSystemPrompt());
    } else {
        builder.append(DEFAULT_CLASSIFY_SYSTEM);
    }

    // 注入 promptVersion
    if (config.getPromptVersion() != null && !config.getPromptVersion().isBlank()) {
        builder.append("\n\nPrompt version: ").append(config.getPromptVersion());
    }

    // 注入 fewShotExamples
    if (config.getFewShotExamples() != null && !config.getFewShotExamples().isEmpty()) {
        builder.append("\n\nCalibration examples:\n");
        for (String example : config.getFewShotExamples()) {
            if (example != null && !example.isBlank()) {
                builder.append("- ").append(example.trim()).append('\n');
            }
        }
    }

    return builder.toString();
}
```

- [ ] **Step 3: 更新 classify 方法使用新方法名**

将 `.system(getClassifySystemPrompt())` 改为 `.system(resolveClassifySystemPrompt())`

- [ ] **Step 4: 验证编译和测试**

Run: `mvn clean test -pl tests -Dtest="QueryRouterTest" -DfailIfNoTests=false`
Expected: PASS

---

## Task 4: 修复 FlowExecutorTest — DagExecutor Mock 注入问题

**Covers:** FlowExecutorTest / DagE2ETest DAG 状态机

**根因分析:** FlowExecutorTest 创建 `new FlowExecutor(dagExecutor, null)` 并 mock `dagExecutor.executeWithCheckpoint()` 返回结果，但 `flowExecutor.execute(request)` 返回空列表。问题在于 FlowExecutor.execute 在调用 dagExecutor 之前可能抛出异常（如 FlowNodeTypeEnums.getByName 返回 null 后的 NPE），导致 mock 从未被调用。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowExecutor.java`

- [ ] **Step 1: 在 FlowExecutor.execute 中添加防御性空值处理**

在 `convertNodes` 方法中，当 `typeEnum` 为 null 时不应导致后续 NPE。当前代码已有 `Integer typeCode = typeEnum != null ? typeEnum.getCode() : null;` 处理。

真正的问题可能是 `context.setNodeMap(nodeMap)` 中 `context` 为 null。检查 `parseRuntimeContext` 是否返回 null：

```java
private RuntimeContextBO parseRuntimeContext(String runtimeContextJson) {
    JSONObject variables;
    if (runtimeContextJson != null && !runtimeContextJson.isBlank()) {
        variables = JSONObject.parseObject(runtimeContextJson);
    } else {
        variables = new JSONObject();
    }
    return new RuntimeContextBO(null, variables);
}
```

这里 `new RuntimeContextBO(null, variables)` 中第一个参数是 `KbRuntimeDO runtime`，传入 null。如果 `RuntimeContextBO` 构造函数或 `setNodeMap` 对 runtime 有非空假设，会导致 NPE。

- [ ] **Step 2: 检查 RuntimeContextBO 构造函数**

读取 `RuntimeContextBO.java` 确认构造函数是否允许 null runtime。

- [ ] **Step 3: 添加 try-catch 包装 FlowExecutor.execute**

在 FlowExecutor.execute 中添加顶层异常处理，确保异常被正确传播而非静默吞掉：

```java
public List<NodeRunResultBO> execute(FlowRequest request) {
    try {
        // ... existing code ...
    } catch (RuntimeException e) {
        throw e;
    } catch (Exception e) {
        throw new RuntimeException("工作流执行失败: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 4: 验证测试**

Run: `mvn clean test -pl tests -Dtest="FlowExecutorTest" -DfailIfNoTests=false`
Expected: PASS (如果根因是 NPE) 或需要进一步调试

---

## Task 5: 修复 PlanAndSolveOrchestratorTest — Lombok 编译问题

**Covers:** PlanAndSolveOrchestratorTest.configDefaultsDisabled

**根因:** `PlanSolveConfig.isRpReactEnabled()` NoSuchMethod。PlanSolveConfig 有 `@Data` 和 `boolean rpReactEnabled = true`，Lombok 应生成 `isRpReactEnabled()`。

**可能原因:** tests 模块编译时使用了 hermes-core 的旧 class 文件（不含 rpReactEnabled 字段）。

**Files:**
- Verify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/PlanSolveConfig.java`

- [ ] **Step 1: 确认 PlanSolveConfig 字段完整**

当前代码已包含：
```java
private boolean rpReactEnabled = true;
```

- [ ] **Step 2: 清理并重新编译**

Run: `mvn clean compile -pl qknow-hermes/qknow-hermes-core -am -q`
Run: `mvn clean test -pl tests -Dtest="PlanAndSolveOrchestratorTest#configDefaultsDisabled" -DfailIfNoTests=false`
Expected: PASS

如果仍然失败，手动添加 getter：
```java
public boolean isRpReactEnabled() { return rpReactEnabled; }
```

---

## 执行顺序

1. **Task 1** — 修复编译阻塞（最高优先级，所有测试依赖此）
2. **Task 2** — DynamicTopKConfig + resolveTopK
3. **Task 3** — QueryRouter DSPy 参数化
4. **Task 4** — FlowExecutorTest 修复
5. **Task 5** — PlanAndSolveOrchestratorTest 修复
