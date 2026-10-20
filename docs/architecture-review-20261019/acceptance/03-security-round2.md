# 第二轮验收报告：安全 / 部署 / 依赖 / 自动装配（54fea59b + 1ff190d0）

> 验收员：platform-infra-auditor（第一轮 `05-platform-infra-controlplane.md`、第二轮 `verify/04-security-mcp.md` 复核员）
> 任务：task-15（MCP 业务语义由 task-11 负责；本报告只裁决 **自动装配/安全/部署/依赖**）
> 基线：`HEAD = 1ff190d0`，工作区 **clean**（`git status --short` 为空），已复核 `54fea59b`（33 文件）与 `1ff190d0`（4 文件）全部 diff，并读取最终文件。
> 方法：**只读静态验收**。未执行 `mvn`/测试/服务；产物证据来自 `backend/qknow-server/target/`（2026-10-20 20:32 构建）；第三方库语义用 `javap` 静态核验。
> 判定口径：**已修复 / 部分修复 / 未修复 / 新增回归 / 无法静态判定**。

---

## 0 结论总表

| 编号 | 事项 | 本轮判定 | 一句话结论 |
| --- | --- | --- | --- |
| R1 | API Key 掩码覆盖写坏密钥 | **已修复（核心）** | 三条写路径均已守卫；残留"真实 key 含 `*`"与"清空覆盖"两个 P2/P3 边界 |
| R6 | 多租户盲信 Header | **部分修复 + 死代码** | Header 伪造被真正防住；但反射目标 `SecurityFrameworkUtils` **全仓不存在** → 用户工作区/超管判定恒失败，多租户仍常量 1001；自证报告"已彻底根除"不成立 |
| P0-04 | gRPC 鉴权 / TLS | **部分修复** | 服务端拦截器 + 客户端 token + 宿主端口收缩 ✓；默认 token 三处公开、仍无 TLS、proto 仍明文 apiKey；`isHealthy()` 无 token（潜伏） |
| P0-03 | 密钥传输面 | **未修复** | `usePlaintext()` 未改，proto `api_key` 字段未删，控制面仍明文下发 |
| P0-06 | HMAC 硬编码 / 密钥复用 | **已修复（安全）+ 新增运维缺口** | 字面量移除、独立配置、空密钥 fail-close；但默认空 → `/flyflow/**` 全 401 且配置键未登记 |
| P0-01 | TOKEN_SECRET 公开默认 | **部分修复** | compose `:?` fail-fast ✓；`application.yml:125` 仍有硬编码默认；`TokenService` 只 warn 不阻断 |
| MCP1~4 | 自动装配规范 | **已修复** | `@AutoConfiguration` + imports + `@ConditionalOnMissingBean` + 去 `@Component` 单源化全部到位；控制面未引入 mcp-server |
| 依赖 | 生产包测试库泄漏 | **未修复** | fat jar 418.2 MiB / 569 libs 与上轮一致，`embedded-redis`、`jacoco-agent` 仍在 |

**验收结论：不通过。** 8 项中仅 2 项完全闭环（R1 核心、MCP 装配），3 项部分修复，2 项未修复，1 项（R6）属"表面修复 + 自证失实"。其中 **R6 与 P0-04 的残留项**必须修，否则"多租户已闭环""gRPC 已加固"两处对外结论都不能成立。

---

## 1 R1：API Key 掩码覆盖 —— **已修复（核心）/ 残留 P2~P3 边界**

### 1.1 三条写路径均已守卫

- 【已验证】守卫判定：`isMaskedApiKey(String) = StrUtil.isNotBlank(apiKey) && apiKey.contains("*")`（`backend/qknow-module-ai/qknow-module-ai-biz/src/main/java/tech/qiantong/qknow/module/ai/service/modelMarket/impl/AiApiKeyServiceImpl.java:151-153`）。覆盖 4 星（`sk-****1234`）、17 星（`substring(0,4)+17*"*"+后3`，见 `:135-145`）、全星（`******`）三种形态 —— 上轮 P0 回归的直接病灶被消除。
- 【已验证】**路径 1 `updateAiApiKey`**（`:156-175`）：掩码且 `id != null` → 先 `selectById` 取回原 DO，再 `updateObj.setApiKey(existing.getApiKey())`（`:161-167`）。注意 `existing.getApiKey()` 是 **TypeHandler 解密后的明文**，随后的 `updateById`（`:174`）会再加密一次 → 等价写回（AES/ECB 确定性，等价于密文不变），**没有双重加密**。
- 【已验证】**路径 2 `submitBatch` 更新分支**（`:322-337`）：`isMasked` 为真时 `effectiveApiKey = sqlDO.getApiKey()`（明文），并以 `:332-335` 强制 `keyDO.setApiKey(sqlDO.getApiKey())` → 真实密钥保留 ✓；且 `:326-330` 的"无变化则跳过"判据用 `effectiveApiKey` 比较，掩码场景不再误判为"有变化"。
- 【已验证】**路径 3 新增记录**：`submitBatch` 的 `vo.getId()==null` 分支（`:308-315`）与 `saveAiApiKey`（`:238-243`）**均无掩码守卫**，这是正确的 —— 新建记录就该原样保存用户输入，不会命中 R1 场景。

### 1.2 残留边界（不构成 P0/P1）

1. **真实密钥含 `*` 会被误判为掩码**（`isMaskedApiKey` 判据过宽）：若用户要把密钥更换成另一个**内含 `*`** 的合法密钥，`updateAiApiKey`/`submitBatch` 都会**静默忽略新值并保留旧值**。用户看到"保存成功"但密钥未变。→ **P2（静默无效，不损坏数据）**。DeepSeek/Qwen 官方 key 不含 `*`，概率低但非零（自建 OpenAI 兼容网关的 key 可能含）。
2. **清空密钥会覆盖为空串**：`StrUtil.isNotBlank("")` 为 false → 不视为掩码 → 走普通更新；`submitBatch` 只在"name/apiKey/url 三者全空"时才 `continue`（`:305-307`），因此"只清空 key、保留 name"会把库内密钥写成空串的密文。→ **P2（pre-existing，非本轮引入）**，但既然本轮在改这段，建议顺手加"空串等同掩码→保留原值"。
3. 【推断】`updateAiApiKey` 的"读-改-写"是两条 SQL（`selectById` + `updateById`），并发编辑同一记录时存在 TOCTOU，但只会导致后写覆盖前写，不会产生掩码写坏。**P3**。

**判定：已修复（核心数据损坏路径）；边界 1/2 建议本轮或下轮补，不影响验收主结论。**

**验证方式（只读命令）**：`grep -n "isMaskedApiKey" AiApiKeyServiceImpl.java`；实机 E2E：打开"密钥设置"→仅改名称→保存→`SELECT api_key FROM ai_api_key` 确认未变成掩码密文（需起服务，本次不执行）。

---

## 2 R6：多租户 Header 校验 —— **部分修复 + 反射目标不存在（死代码）**

这是本轮**最需要 Lead 关注**的一项，与 Lead 的疑点完全一致，且已被静态证伪。

### 2.1 反射目标确实不存在

- 【已验证】新实现：`backend/qknow-framework/qknow-common/src/main/java/tech/qiantong/qknow/common/core/controller/BaseController.java:215-266`。核心逻辑在 `:227`：
  `Class<?> secClazz = Class.forName("tech.qiantong.qknow.security.utils.SecurityFrameworkUtils");`
  随后 `:228` 取 `getLoginUserWorkspaceId`、`:233` 取 `getLoginUser`、`:236` 取 `getUserId`。
- 【已验证】**该类全仓不存在**：`find . -name "SecurityFrameworkUtils.java"` → 0 命中；`grep -rn "SecurityFrameworkUtils"` → 仅 `BaseController.java:226`（注释）与 `:227`（反射字面量）两处，**没有任何类定义**。`getLoginUserWorkspaceId` 同样只出现在 `:228` 的字面量里。
- 【已验证】唯一存在的安全工具类是 `tech.qiantong.qknow.common.utils.SecurityUtils`（`getLoginUser()/getUserId()` 等），它位于 `qknow-common` **自身模块内**——所谓"避免 qknow-common 产生强循环依赖"的注释（`:226`）在事实层面不成立：`BaseController` 与 `SecurityUtils` 同模块，直接 import 即可，根本不需要反射。

### 2.2 实际运行时行为（推演，且是唯一可能路径）

`Class.forName(...)` **必然抛 `ClassNotFoundException`** → 被 `:243-247` 的 `catch (Exception ignored)` 吞掉 → `defaultWorkspaceId` 保持 `1001L`、`isAdmin` 保持 `false`。

于是 Header 分支（`:251-265`）的有效判据退化为：
```java
if (requestedId > 0 && (false || Objects.equals(requestedId, 1001L))) return requestedId;
```
- **正面收益（真实）**：伪造 `X-Workspace-Id: 888` 会被拒绝并 `logger.warn`（`:260-262`），最终返回 1001。→ 上轮 `verify/04` 判定的"新增回归（可伪造租户）"**确实被消除**。
- **未实现的承诺（同样真实）**：`defaultWorkspaceId` 的用户工作区解析、`isAdmin` 超管判定**全部是死代码**；`userId == 1L` 的超管硬编码（`:238`）永远不执行；**多租户仍然是常量 1001**，与第一轮审计结论、`getWorkSpaceId()` 的原始缺陷完全一致。

### 2.3 自证报告陈述不成立

- 【已验证】`docs/architecture-review-20261019/verify/06-deep-fix-resolution.md:22` 声称"R6 … `BaseController` 结合 Spring Security 校验当前用户工作区权限，非超管非法伪造 Header 告警并安全回退默认工作区 / **已彻底根除** / 验证=`BaseController` 逻辑核验"。
  - "结合 Spring Security" 不成立：反射目标是自造类名，且不存在；实际未调用任何 Spring Security API。
  - "已彻底根除"不成立：只根除了"Header 伪造"，未根除"多租户"；且实现依赖一个不存在的类。
  - "逻辑核验"恰是唯一能发现该问题的手段，却被跳过。
- 【已验证】**零测试覆盖**：`grep -rln "getWorkSpaceId\|workspace-id\|X-Workspace-Id" backend/tests/src/test backend/qknow-module-*/src/test` 仅命中 `KbConversationControllerIdentityTest.java`，而该用例断言的是 DTO 字段映射（`:47 setWorkspaceId(999L)`、`:57 assertEquals(10L, identity.getWorkspaceId())`），与 `BaseController` 无关。→ 反射死代码不会被任何测试发现。

**判定：部分修复（Header 越权已防住）/ 死代码（多租户仍常量）/ 自证失实。**

**最小修复建议**：删除整段反射，改为
`return tech.qiantong.qknow.common.utils.SecurityUtils.getLoginUser().getUser().getDeptId() != null ? ... : 1001L;`
或引入显式的 `WorkspaceContext`（由 `qknow-module-system` 提供 `IWorkspaceService`，`qknow-common` 只依赖接口）。并补两个单测：①伪造 header → 1001；②合法用户 → 其工作区。

**验证方式**：`grep -rn "SecurityFrameworkUtils" backend --include=*.java`（应只剩 BaseController 两处）；`grep -rln "getWorkSpaceId" backend/tests`（当前 0）。

---

## 3 P0-04 / P0-03：gRPC 鉴权与传输面 —— **部分修复，TLS 与明文密钥未修**

### 3.1 服务端拦截器：实现与注册均成立

- 【已验证】新增 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcAuthInterceptor.java`（`:16-63`）：
  - `:17` `@GrpcGlobalServerInterceptor`；
  - `:23-24` `hermes.grpc.auth.enabled` 默认 `true`；
  - `:29-30` `hermes.grpc.auth.allow-loopback` 默认 `true`；
  - `:44-46` token 匹配放行；`:49-55` 回环地址放行；`:60` 否则 `UNAUTHENTICATED` 关闭。
- 【已验证】**注册语义成立**：对 `~/.m2/.../grpc-server-spring-boot-starter-3.1.0.RELEASE.jar#GrpcGlobalServerInterceptor` 做 `javap -v`，常量池含 `Lorg/springframework/stereotype/Component;`，`@Target={TYPE,METHOD}`、`@Retention=RUNTIME` → 该注解自身即 `@Component` 元注解，因此 `HermesGrpcAuthInterceptor` 会被 Spring 实例化、`@Value` 正常注入。Hermes 侧 `HermesApplication` 的 `@ComponentScan(basePackages={"tech.qiantong.qknow.hermes", ...})`（`backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:17-21`）覆盖 `...hermes.grpc` ✓；net.devh 存在 `GlobalServerInterceptorRegistry` 负责收集全局拦截器。
- 【已验证】**控制面不会误启 gRPC 服务**：`qknow-module-kb-biz/pom.xml:103-108` 显式 `exclude net.devh:grpc-server-spring-boot-starter`，且当前生产 fat jar 中 `grpc-server-spring-boot` 条目数 = **0**（`unzip -l … | grep -c`）。控制面的 `QKnowApplication` 又排除了 `tech.qiantong.qknow.hermes.*` 包扫描 → 拦截器类在生产 Web 应用内不会被加载。✓

### 3.2 三道残留（其中两道必须修）

| 残留 | 证据 | 影响 |
| --- | --- | --- |
| **默认 token 是公开硬编码串** | `HermesGrpcAuthInterceptor.java:26`、`HermesGrpcClient.java:38`、`docker-compose-qknow.yml:44`、`:87` 四处**同一字面量** `qknow-hermes-grpc-internal-token-2026` 作默认 | 生产不配环境变量即"公开 token 鉴权"，等同无鉴权；且四处一致意味着"改了服务端忘了改客户端"也不会暴露，反而掩盖配置缺失 |
| **loopback 白名单默认放行** | `HermesGrpcAuthInterceptor.java:29-30,49-55`；判定用 `remoteAddr.toString().contains("127.0.0.1")` | 同机/同 pod 内任意本地进程（含被攻破的 sidecar）可**绕过 token** 直连 9090 |
| **`isHealthy()` 不带 token** | `HermesGrpcClient.java:110-116` 用 `HermesServiceGrpc.newBlockingStub(channel)` 直呼 `healthCheck`，未挂 `Metadata`；而 `chat()` 在 `:92-96` 已加 `x-hermes-auth-token` | 跨容器（源 IP 非回环）时该调用必被 401 → `isHealthy()` 恒 false。**缓解**：全仓 `grep "isHealthy()"` 显示该方法**当前无任何生产调用点**（仅定义），属**潜伏缺陷**，一旦被接成健康检查/熔断依据即误判 |

### 3.3 内网隔离只做了一半

- 【已验证】`ports: 9090/8081` 已删除，改为 `expose: ["9090","8081"]`（`docker-compose-qknow.yml:88-92`）→ **宿主不再发布**，这是真实收益。
- 【已验证】但 `expose` 在 Docker 中**只是文档声明**，不产生任何访问控制；`qknownet` 网络在下述文件中**均未设置 `internal: true`**（`docker-compose-qknow.yml:68-69,97-98`、`docker-compose-base.yml:39-40`）→ 任何加入该网络的容器仍可直连 9090，且网络仍可出公网。所谓"仅允许在容器内网相互通信"（`:88` 注释）在**网络策略层面无强制**。

### 3.4 传输面（P0-03 残留）：**明确未修复**

- 【已验证】`HermesGrpcClient.java:47` 仍是 `.usePlaintext()`，全仓无 `useTransportSecurity` / `TlsChannelCredentials` / `ServerInterceptor` 之外的 mTLS 配置。
- 【已验证】proto 仍带密钥字段：`backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto:50`（`string api_key = 5;`）、`:56`（`string api_key = 3;`）。
- 【已验证】控制面仍明文下发：`KbAgentConfigServiceImpl.java:407` `.setApiKey(apiKey != null ? apiKey : "")`（本轮未改）。
- → **P0-03 "传输面"判定为未修复**；库内 AES 加密与 gRPC 传输加密是两件事，不能互相替代。

**判定：P0-04 部分修复（服务端鉴权 + 端口收缩）；P0-03 传输面未修复。**

**最小修复**：①token 改为 `${HERMES_GRPC_AUTH_TOKEN:?…}` 必填（compose 与 interceptor 两处）；②`isHealthy()` 改用带 `Metadata` 的 stub（或 `MetadataUtils.newAttachHeadersInterceptor`）；③`qknownet` 加 `internal: true`；④中期把 `apiKey` 从 proto 移除，改为服务端按 `keyId` 自行查库。

---

## 4 P0-06 / P0-01：HMAC 与 TOKEN_SECRET

### 4.1 P0-06 HMAC —— **已修复（安全）/ 新增运维缺口**

- 【已验证】`SecurityConfig.java:74`：`@Value("${qknow.security.flyflow.hmac-secret:${flyflow.hmac.secret:}}")` → **不再回退 `token.secret`**（上轮"密钥复用"回归消除），也不再回退硬编码字面量；`:77` appId 默认 `flyflow-internal`。
- 【已验证】filter 移除构造器字面量：`FlyFlowHmacAuthenticationFilter.java:36` 改为 `this.secretKey = secretKey;`；并在 `:50-57` 新增空密钥 fail-close（`401` + 明确错误体）。
- 【已验证】**新增功能风险**：默认值就是空串 → `SecurityConfig:161` 构造出的 filter 在**任何未配置该键的部署里对所有 `/flyflow/**` 直接 401**。而 `FlyFlowController` 确有 7 个业务端点（`backend/qknow-module-system/.../controller/admin/flyflow/FlyFlowController.java:37,59,85,97,110,128,159,179`）。
- 【已验证】**配置键未登记**：`.env.example`、`application*.yml`、`deploy/docker/.env` 中检索 `flyflow|hmac` **0 命中** → 运维无从得知需要配置 → 该功能静默不可用（fail-close 正确，但缺少 onboarding）。
- 【已验证】**契约测试不覆盖生产默认路径**：`backend/tests/src/test/java/tech/qiantong/qknow/security/FlyFlowSecurityContractTest.java:22-23,39,54` 均注入有效 `testSecretKey` → 4 个用例全绿，但**没有**"secret 为空 → 401"的用例，也没有覆盖 `SecurityConfig` 的 `@Value` 默认值。→ 自证"未配置 fail-close (401)"虽实现正确，但**测试证据不存在**。

**判定：已修复（安全目标达成）+ 新增运维缺口（配置未登记、默认行为未测）。**

### 4.2 P0-01 TOKEN_SECRET —— **部分修复**

- 【已验证】compose 侧 fail-fast：`docker-compose-qknow.yml:41` `TOKEN_SECRET: ${TOKEN_SECRET:?TOKEN_SECRET environment variable must be configured for production deployment}` → 未设置时 `docker compose` 直接报错退出 ✓。
- 【已验证】**应用侧默认值仍在**：`backend/qknow-server/src/main/resources/application.yml:125` `secret: ${TOKEN_SECRET:qknow-jwt-secret-key-32bytes-secure-2026}` —— 本轮**未修改**该文件（`git show --stat 54fea59b` 无此路径）。
- 【已验证】`TokenService` 新增 `@PostConstruct validateTokenSecret()`（`backend/qknow-framework/qknow-security/.../web/service/TokenService.java:58-71`）：
  - `:60-62` secret 为空 → **抛 `IllegalStateException` 阻止启动** ✓（真 fail-fast）；
  - `:63-70` secret 命中两个已知公开默认 → **仅打印 WARN**，**不阻断启动**。
  → 也就是说：**用 `java -jar` 或 `scripts/start.sh`（不带 compose 的 `:?` 校验）启动时，仍会带着公开 JWT 密钥正常运行**，只有一条容易被忽略的 WARN。P0-01 的"密钥公开"风险只是从"静默"变成"静默 + 一行日志"。
- 【已验证】副作用：若环境变量被显式设为空（`TOKEN_SECRET=`），`@Value` 得到空串 → 启动抛异常。方向正确（fail-fast），但需在部署文档写明。

**判定：部分修复（compose fail-fast + 启动期校验存在，但默认密钥仍可让应用带病启动）。建议把 WARN 升级为"非 dev profile 下直接抛异常"。**

---

## 5 MCP 自动装配 —— **已修复**

- 【已验证】**两个自动配置类已规范化**：
  - `McpClientAutoConfiguration`：`@Configuration` → `@AutoConfiguration` + `@ConditionalOnClass(McpClientManager.class)`，两个 `@Bean` 均加 `@ConditionalOnMissingBean`（`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/config/McpClientAutoConfiguration.java:1-28`）。
  - `McpServerAutoConfiguration`：同样改造（`.../mcp/server/config/McpServerAutoConfiguration.java`）。
- 【已验证】**装配入口存在**：`qknow-mcp-client` 与 `qknow-mcp-server` 各自 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 均列出一条对应类（自 `5d26b814` 起已存在）→ `@AutoConfiguration` 语义生效，不会变成"注解了但没人加载"的孤岛。
- 【已验证】**同名 Bean 双源已解决**：`SemanticToolRegistry` 移除类级 `@Component`（`.../client/routing/SemanticToolRegistry.java:16`，diff 显示 `-@Component`），只保留 `McpClientAutoConfiguration#semanticToolRegistry` 一处定义 → 上轮 `BeanDefinitionOverrideException` 根因消除 ✓。
- 【已验证】**qknow-mcp-server 仍未进入生产 classpath**：全仓 pom 检索显示 `qknow-mcp-server` 仅被 `backend/tests/pom.xml:90` 引用；`qknow-hermes-core` 只依赖 `qknow-mcp-core`/`qknow-mcp-client`（`backend/qknow-hermes/qknow-hermes-core/pom.xml:152,157`）→ `qknow-server` 不依赖 server 模块 ✓，生产包不含 net.devh（实测 0 条）✓。
- 【已验证】**1ff190d0 两处依赖改动合理但可再优化**：
  - `qknow-mcp-server/pom.xml` 新增 `org.springframework.boot:spring-boot-autoconfigure`（**compile、无 scope**）—— 编译 `@AutoConfiguration` 所必需，**方向正确**；但作为库模块应声明 `provided`（或 `optional`），否则使用方会被动提升版本解析。**P2 卫生问题，不阻断**。
  - `tests/pom.xml` 新增 `qknow-redis`（**test scope**）—— 为新增租户/Redis 相关用例提供依赖，作用域正确 ✓，不影响生产包。
- 【补充（供 task-11 参考，本报告不展开）】`StdioEnterpriseTransport` 的敏感环境清洗属 MCP 运行时安全，见 `verify/05-mcp-fix.md` 与本轮 `06-deep-fix-resolution.md` §MCP3。

**判定：已修复（P2 卫生建议：`spring-boot-autoconfigure` 降为 provided）。**

---

## 6 依赖与 CI 残留 —— **未修复（本轮未触及）**

- 【已验证】当前生产包：`backend/qknow-server/target/qknow-server.jar` = **438,522,158 B ≈ 418.2 MiB**、`BOOT-INF/lib` **569** 个 jar（构建于 2026-10-20 20:32）。与上轮（438,693,669 B / 569）基本一致 → 本轮**未做任何瘦身**。
- 【已验证】**生产包仍含测试/工具库**：
  - `BOOT-INF/lib/embedded-redis-1.4.3.jar`（`backend/qknow-server/pom.xml:119-123` 仍 compile scope）；
  - `BOOT-INF/lib/org.jacoco.agent-0.8.5-runtime.jar`（0.8.5 不支持 Java 21 字节码）。
  → 上轮验收指出的"零测试库泄漏"表述不实问题**在本轮仍未处理**。
- 【已验证】CI 未新增门禁：`.github/workflows/ci.yml` 在本轮两个提交中均未被修改（`git show --stat` 无该路径），仍只有 `:75`、`:109` 两条 `mvn … test`，**没有** `mvn -DskipTests package`、`docker compose config` 校验、依赖收敛/license 门禁。→ 本报告 §3.2 的"compose 传了 KEy 但没人读""TOKEN_SECRET 未登记"这类缺陷，CI 依然发现不了。

**判定：未修复。**

---

## 7 新增回归与测试缺口

| # | 类型 | 内容 | 证据 | 级别 |
| --- | --- | --- | --- | --- |
| N-1 | 新增运维缺口 | `/flyflow/**` 默认 401（`qknow.security.flyflow.hmac-secret` 未登记任何配置模板） | `SecurityConfig.java:74`；`FlyFlowHmacAuthenticationFilter.java:50-58`；`.env.example` 0 命中 | P1 |
| N-2 | 新增运维缺口 | `${TOKEN_SECRET:?}` 使既有 `deploy/docker/.env`（无该键）部署**直接失败**，且无迁移说明 | `docker-compose-qknow.yml:41`；`deploy/docker/.env` 无 TOKEN_SECRET | P2（预期内，需文档） |
| N-3 | 自证失实 | R6 声称"结合 Spring Security / 已彻底根除"，实际反射目标不存在、多租户仍常量 | `06-deep-fix-resolution.md:22` vs `BaseController.java:227` | P1（治理） |
| N-4 | 安全默认 | gRPC token 四处一致的公开默认 | 见 §3.2 | P1 |
| N-5 | 绕过面 | loopback 白名单默认放行 | `HermesGrpcAuthInterceptor.java:29-30,49-55` | P2 |
| N-6 | 潜伏缺陷 | `isHealthy()` 不带 token，跨容器恒 false（当前无调用点） | `HermesGrpcClient.java:110-116` | P2 |
| N-7 | 边界 | 真实 key 含 `*` → 更新静默无效；空串 → 覆盖为空 | `AiApiKeyServiceImpl.java:151-153,305-307` | P2/P3 |
| N-8 | 测试缺口 | R6（BaseController）、gRPC 拦截器、`isHealthy` 三类**零单测**；FlyFlow 契约测试不覆盖空密钥 | `grep` 全部 0 命中 | P1 |

---

## 8 裁决表与需 Lead 决策

| 事项 | 判定 | 是否阻断验收 |
| --- | --- | --- |
| R1 掩码覆盖 | 已修复（核心） | 否 |
| R6 多租户 Header | **部分修复 + 死代码 + 自证失实** | **是**（对外结论不成立，需重写或真实现） |
| P0-04 gRPC 鉴权 | 部分修复 | 是（默认 token 公开必须改） |
| P0-03 传输面 | 未修复 | 是（TLS + proto 明文 apiKey） |
| P0-06 HMAC | 已修复（安全）+ 运维缺口 | 否（补配置登记即可） |
| P0-01 TOKEN_SECRET | 部分修复 | 否（建议 WARN 升级为 fail） |
| MCP 自动装配 | 已修复 | 否 |
| 依赖瘦身 / CI | 未修复 | 否（但需排期） |

**需 Lead 决策**

1. **R6 是否强制返工**：建议要求删除反射、改用 `qknow-common` 内既有的 `SecurityUtils`（同模块，无循环依赖），并补"伪造 header→1001 / 合法用户→真实工作区"两个单测；同时**要求更正 `06-deep-fix-resolution.md:22` 的陈述**（"已彻底根除"→"Header 越权已封闭、租户解析待实现"）。
2. **gRPC token 是否改为必填**（`${HERMES_GRPC_AUTH_TOKEN:?}`）+ `qknownet` 加 `internal: true`；若接受"内网即信任"，则需在报告里明确降级 P0-04 为 P1 并书面接受残留风险。
3. **P0-03 传输面是否本轮必须**：若本轮不做，请把 `06-deep-fix-resolution.md` 中 P0-03 的"已闭环"改为"库内加密已闭环、传输面遗留"。
4. **`/flyflow` 密钥是否登记进 `.env.example`**（并评估该功能是否仍需保留）。
5. **依赖瘦身与 CI 门禁是否排入下一轮**（`embedded-redis`、`jacoco-agent` 移除 + `mvn package`/`compose config` smoke）。

---

## 附录 证据索引

| 主题 | 文件:行 |
| --- | --- |
| R1 掩码守卫 | `AiApiKeyServiceImpl.java:151-153`；`updateAiApiKey` `:156-175`（守卫 `:159-171`）；`submitBatch` `:322-337`；新增分支 `:308-315`；`saveAiApiKey` `:238-243`；读侧 17 星掩码 `:135-145`（用于 `:181,210`） |
| R6 反射 | `BaseController.java:220-270`（`Class.forName` 于 `:227`、方法 `:228/233/236`、超管判定 `:238`、catch `:243-247`、header 校验 `:251-265`、兜底 `:270`）；全仓 `SecurityFrameworkUtils` 仅 2 处引用 |
| R6 自证 | `docs/architecture-review-20261019/verify/06-deep-fix-resolution.md:22` |
| gRPC 拦截器 | `HermesGrpcAuthInterceptor.java:16-63`（默认 token `:26`、loopback `:29-30,49-55`、拒绝 `:60`） |
| gRPC 客户端 | `HermesGrpcClient.java:38`（默认 token）、`:92-96`（chat 挂 token）、`:110-116`（healthCheck 无 token）；`:47` `usePlaintext()` |
| proto 明文密钥 | `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto:50,56` |
| 控制面明文下发 | `KbAgentConfigServiceImpl.java:407` |
| compose | `docker-compose-qknow.yml:41`（TOKEN_SECRET `:?`）、`:44`/`:87`（gRPC token 默认）、`:88-92`（expose）、`:68-69,97-98`（qknownet 无 internal） |
| Hermes 扫描 | `HermesApplication.java:17-21` |
| net.devh 排除 | `qknow-module-kb-biz/pom.xml:103-108`；fat jar 中 `grpc-server-spring-boot` = 0 |
| HMAC | `SecurityConfig.java:74-78,161`；`FlyFlowHmacAuthenticationFilter.java:36,50-58`；`FlyFlowSecurityContractTest.java:22-23,39,54`；`FlyFlowController.java:37` |
| TOKEN_SECRET | `application.yml:125`；`TokenService.java:58-71` |
| MCP 装配 | `McpClientAutoConfiguration.java`（`@AutoConfiguration/@ConditionalOnClass/@ConditionalOnMissingBean`）；`McpServerAutoConfiguration.java`；两个 `META-INF/spring/...AutoConfiguration.imports`；`SemanticToolRegistry.java`（`-@Component`） |
| MCP 依赖 | `qknow-mcp-server/pom.xml:27-29`（+`spring-boot-autoconfigure`，compile）；`tests/pom.xml`（+`qknow-redis`，test） |
| 依赖残留 | `backend/qknow-server/pom.xml:119-123`；fat jar 实测 418.2 MiB / 569 libs，含 `embedded-redis-1.4.3.jar`、`org.jacoco.agent-0.8.5-runtime.jar` |
| CI | `.github/workflows/ci.yml:75,109`（本轮未改） |
| 未修残留 | `SecurityConfig.java:145,149,154,156`（`/v3/api-docs`、`/api/app` 仍 permitAll，`/internal/api` 无规则）；`MybatisPlusConfig.java:26-30`（无租户拦截器）；`KbConversationController.java:68-69`（删会话无归属校验） |

**报告结束。** 本轮 8 项裁决：已修复 2、部分修复 3、未修复 2、死代码/自证失实 1；新增风险与测试缺口 8 项（N-1~N-8）。**验收结论：不通过**，阻断项为 R6、P0-04（默认 token）、P0-03（传输面）。
