# 安全 / 部署 / 依赖修复复核报告（58c3144e + cb6e185c）

> 复核员：platform-infra-auditor（原 `05-platform-infra-controlplane.md` 审计员）
> 任务：task-10（MCP 部分由 task-11 / `05-mcp-fix.md` 负责，本报告不重复）
> 复核对象：`HEAD = cb6e185c` + 工作区 8 个未提交改动（均为 MCP/kb 检索文件，未触及本轮安全/部署/依赖文件）
> 复核时间：2026-10-20
> 方法：**只读静态复核**。已 `git show` 两个提交全文，并逐一读取最终工作区文件（而非提交快照）。未执行 `mvn`、未跑测试、未启动服务；产物证据仅来自既有 `backend/qknow-server/target/`。
> 判定口径：**已修复 / 部分修复 / 未修复 / 新增回归 / 无法静态判定**。

## 0 结论速览（先看这三条）

1. **P0-03 出现可从前端 UI 直接触发的新增回归**：脱敏后的 `sk-****abcd` 会被 `submitBatch` 当成真实密钥加密回写，**覆盖并永久损坏原密钥**。证据链完整（见 §2.3）。
2. **"安全默认值"策略把 4 类故障从"启动即失败"换成了"静默使用公开密钥"**：`TOKEN_SECRET`、AES 密钥、HMAC 密钥现在都有**入库字面量默认值**，且两处 `TOKEN_SECRET` 默认值互不相同（配置漂移）。
3. **依赖瘦身只完成 3.3%**：fat jar 从 ≈433 MiB/589 jar 降到 ≈418 MiB/569 jar，仅剔除测试库；`embedded-redis` 与 `jacoco-agent` 仍在生产包，Hadoop/Tika/Lucene/ES/Flexmark（共 137 个 jar）原封未动。CI 未补任何新门禁。

**先明确一条纪律结论**：`git status --short deploy/ .github/ backend/pom.xml backend/qknow-framework backend/qknow-module-ai backend/qknow-server` 输出为空 —— **两个提交修复的内容没有被工作区改回**，不存在"提交里修了但又改回去"的项。

---

## 1 部署与编排

### 1.1 P0-01 TOKEN_SECRET 启动阻断 —— **部分修复（换成了更差的安全姿态）**

| 项 | 事实 | 证据 |
| --- | --- | --- |
| 是否注入 | 已注入，但**带硬编码默认值** | `deploy/docker/docker-compose-qknow.yml:41` |
| 应用侧是否仍有安全默认 | 有，且与 compose 的默认值**不是同一个串** | `backend/qknow-server/src/main/resources/application.yml:125` |
| 启动是否还会失败 | 不再失败（占位符有默认值） | 同上两处 |

- 【已验证】`application.yml:125`：`secret: ${TOKEN_SECRET:qknow-jwt-secret-key-32bytes-secure-2026}`；`docker-compose-qknow.yml:41`：`TOKEN_SECRET: ${TOKEN_SECRET:-qknow-production-jwt-token-secret-key-secure-2026}`。两个默认值不同 → **同一套部署可能因启动路径不同（compose / `java -jar` / `scripts/start.sh`）拿到不同的 JWT 签名密钥**，多实例混跑时会出现"同一用户在不同实例上 token 互不认"的隐蔽故障。
- 【已验证】判定为修复方向错误：原审计的诉求是"凭证缺失必须显式失败"，现在变成"凭证缺失静默使用仓库里公开的字符串"。`TokenService` 仍以 `SignatureAlgorithm.HS512` + 该密钥签名（`backend/qknow-framework/qknow-security/.../web/service/TokenService.java:189-196`），而 jjwt 0.9.1 对 String 密钥先做 Base64 解码再交给 `Mac`。该链路已用字节码静态确认：`io.jsonwebtoken.impl.DefaultJwtBuilder#signWith(SignatureAlgorithm,String)` → `TextCodec.BASE64.decode(secret)`（`~/.m2/.../jjwt-0.9.1.jar`，`javap -c`）→ `io.jsonwebtoken.impl.Base64Codec#decode` → `javax.xml.bind.DatatypeConverter.parseBase64Binary`（由包内 `jaxb-api-2.2.11.jar` 提供）；而 `signWith(SignatureAlgorithm,byte[])` 只做 `Assert.notEmpty`，**无 HS512 最小 512bit 长度校验**。含 `-` 的默认值会被解码器跳过非字母表字符，最终得到一个**由公开字符串派生的短密钥** → **签名可被任何读过本仓库的人复算**。
- 【推断】实际危害上限：JWT 仅承载 `LOGIN_USER_KEY`（UUID），用户态仍要查 Redis（`TokenService.java:76-95`），因此"伪造 JWT"不能直接登录任意账号（还需知道有效会话 UUID）。真正的损失是**签名完整性保护归零**——token 退化为"明文会话 ID 信封"。
- **残留风险**：两个公开默认值、两处不一致、密钥轮换无版本位（无 `kid`），轮换即全量会话失效。
- **验证方式**：`docker compose -f deploy/docker/docker-compose-qknow.yml config | grep TOKEN_SECRET` 看最终生效值；再 `grep -rn "TOKEN_SECRET" deploy backend --include=*.yml` 确认默认值来源。
- **建议**：删除两处默认值，改为 `${TOKEN_SECRET:?TOKEN_SECRET must be set}`（compose 用 `${TOKEN_SECRET:?err}`），让缺失在 `docker compose config` 阶段就报错。

### 1.2 P0-02 Hermes 服务编排 —— **部分修复（声明补齐，但容器实际起不来）**

| 复核点 | 结论 | 证据 |
| --- | --- | --- |
| 镜像基础版本 | **已修复**：`eclipse-temurin:17-jre` → `eclipse-temurin:21-jre-jammy` | `backend/qknow-hermes/qknow-hermes-starter/docker/Dockerfile:1` |
| compose 新增 hermes 服务 | **已修复（仅声明）** | `deploy/docker/docker-compose-qknow.yml:70-96` |
| host 可解析 | **已修复**：backend 注入 `HERMES_GRPC_HOST: hermes`，落到 `application-prod.yml` 的 `${HERMES_GRPC_HOST:hermes}` | `docker-compose-qknow.yml:42-43`；`application-prod.yml:149-153` |
| 健康检查 | **未修复**：hermes 服务无 `healthcheck`；backend 对 hermes 用 `condition: service_started`（不校验就绪） | `docker-compose-qknow.yml:60`、`:91-95` |
| 内网隔离 | **新增回归**：`ports: "9090:9090"`、`"8081:8081"` 把**明文 gRPC 端口发布到宿主** | `docker-compose-qknow.yml:86-88` |
| 构建通道 | **未修复**：全仓 compose 无 `build:` / `dockerfile:` / `context:`；`Dockerfile:5` 要求构建上下文里已有手工放入的 `hermes.jar`；Maven 侧仅 `spring-boot-maven-plugin`，无镜像构建任务 | `grep -n "build:\|dockerfile:\|context:" deploy/docker/docker-compose*.yml` = 空；`Dockerfile:5,7` |
| 运行参数 | **新增回归**：compose 传 `JAVA_OPTS`（含 `MaxRAMPercentage`），但 Dockerfile ENTRYPOINT 硬编码 `-Xms512m -Xmx2g` 且**不引用 `$JAVA_OPTS`** → compose 的 cgroup 感知参数完全失效 | `docker-compose-qknow.yml:76-82` vs `Dockerfile:7` |
| 依赖顺序 | **未修复**：hermes 只 `depends_on: redis`，但 Hermes 自身需要 PostgreSQL（`spring.datasource`），未声明对 postgres 的依赖 | `docker-compose-qknow.yml:91-95`；`qknow-hermes-starter/src/main/resources/application.yml:13-17` |
| 环境变量一致性 | **新增回归（致命）**：compose 给 hermes 传 `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `TONGYI_API_KEY`，而 Hermes 只读 `HERMES_OPENAI_API_KEY` / `HERMES_OPENAI_BASE_URL` / `POSTGRESQL_USERNAME|PASSWORD` → **三个变量全部空转** | `docker-compose-qknow.yml:83-85` vs `qknow-hermes-starter/.../application.yml:15-16,20-21` |

- 【已验证】由此产生的**必然运行结果**：Hermes 容器会以 `${POSTGRESQL_USERNAME:achilles}` / `${POSTGRESQL_PASSWORD:achilles}` 连接 compose 里的 postgres（实际用户是 `${POSTGRES_USER:-postgres}`）→ 认证失败 → `restart: always` 无限重启；即使连上，模型 Key 也是 `sk-placeholder` → 全部 LLM 调用 401。**即"加了 compose 服务"并不等于"问答链路可用"。**
- **残留风险**：`depends_on: service_started` + 无 healthcheck + 容器崩溃循环，会让 backend 在启动时看到"容器在跑"但 gRPC 不可达；而 `HermesGrpcClient.isHealthy()` 无 deadline（`HermesGrpcClient.java:103-113`，本轮未改），调用侧会挂住。
- **验证方式（供 Lead 复验，只读命令）**：`docker compose -f deploy/docker/docker-compose-qknow.yml config` 检查 env 展开；`grep -n "POSTGRESQL_USERNAME\|HERMES_OPENAI_API_KEY" backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml`；或起容器后 `docker logs agent-hermes | head -50`（不在本次只读许可内，建议 CI smoke 做）。

---

## 2 凭据与密钥

### 2.1 P0-03 密钥加密 —— **部分修复，但引入 P0 回归**

**（a）已做对的部分**

- 【已验证】`AiApiKeyDO` 挂上 TypeHandler 且开启 `autoResultMap`（这是 MyBatis-Plus 使用字段级 TypeHandler 的必要条件，缺 `autoResultMap=true` 则查询不生效）：
  `backend/qknow-module-ai/.../dal/dataobject/modelMarket/AiApiKeyDO.java:20`（`@TableName(value="ai_api_key", autoResultMap=true)`）、`:42`（`@TableField(typeHandler = EncryptTypeHandler.class)`）。
- 【已验证】VO 层脱敏生效：`AiApiKeyRespVO.java:38-46` 自定义 `getApiKey()` 返回 `前3位 + "****" + 后4位`（`:45`），且原 `@Excel(name="秘钥")` 已移除（Excel 导出不再外泄密钥）。所有 key 相关 Controller 返回均走 VO（`AiApiKeyController.java:46-48,54-56,62-65,70-73,79-81`），故 REST 面脱敏成立。
- 【已验证】**内部取密钥未被脱敏破坏**：真正用于发起模型调用的是 `AiModelApiServiceImpl`，它直接走 Mapper 拿 DO（`apiKeyMapper.selectById`），不经 VO/服务层掩码，`AiModelApiServiceImpl.java:53,75,132,138,157`；`AiApiKeyServiceImpl.getAiApiKeyById()` 的 17 星掩码（`:162-167`）只被 Controller 使用（`AiApiKeyController.java:72`）。→ 这条链**没有**被"脱敏"改坏。
- 【已验证】gRPC 侧**仍明文传 apiKey**（未修复）：`KbAgentConfigServiceImpl.java:407` `setApiKey(apiKey)` + `HermesGrpcClient.java:44` `usePlaintext()`。**此项判定"未修复"，必须在报告中明确**：库内加密只解决静态面，链路仍是明文。

**（b）加密本身几乎不提供保护**

- 【已验证】**AES 密钥落到公开默认值**：`EncryptTypeHandler.getEncryptor()` 的取值顺序是 `mybatis-plus.encryptor.password` → `token.secret` → 字面量 `"qknow-default-aes-key-32bytes-2026!"`（`EncryptTypeHandler.java:74-84`）。全仓配置中**没有任何一处声明 `mybatis-plus.encryptor.password`**（`grep -rn "encryptor.password" --include=*.yml` = 空），所以生产实际用的是 `token.secret`，而其默认值同样入库公开（§1.1）。→ **任何拿到仓库的人都能解出全库 API Key**，"加密落库"的安全收益基本归零。
- 【已验证】**轮换即静默数据失效**：`decrypt()` 捕获所有异常后**原样返回密文**（`EncryptTypeHandler.java:50-60`）。若轮换 `token.secret`（这是最常规的安全动作），存量密文解不开 → 返回 Base64 密文 → 应用把它当 API Key 发给 DeepSeek → 401；且**启动不报错、无告警**，排查成本极高。
- 【已验证】**算法面弱**：`SecureUtil.aes(keyBytes)` 默认 AES/ECB/PKCS5Padding（无 IV、无认证、确定性加密）。相同明文得相同密文，可做等值推断；密文被篡改无法检测，而 decrypt 又会吞异常。
- 【已验证】**密钥派生的两个隐坑**：`EncryptTypeHandler.java:85-88` 用 `System.arraycopy(pwdBytes, 0, keyBytes, 0, Math.min(len,32))` —— 超过 32 字节的 secret **被静默截断**（不同 secret 若前 32 字节相同则密钥相同）；不足 32 字节则零填充（熵不足）。
- 【已验证】**列宽风险**：`ai_api_key.api_key` 是 `VARCHAR(128)`（`deploy/sql/postgresql/01-schema.sql:363`）。AES/ECB + Base64 后长度 ≈ `4*ceil(ceil(n/16)*16/3)`：明文 64 字符 → 88 字符（安全）；但**存量明文若 >96 字符，回写加密时会超 128 触发 SQL 报错**。当前写入侧有 `@Size(max=64)`（`AiApiKeySaveReqVO.java:34-35`）限制新增，历史数据长度无法静态判定。
- 【已验证】**无存量迁移脚本**：本轮提交未新增任何 SQL/迁移脚本（`git show cb6e185c --stat` 与 `git show 58c3144e --stat` 均无 `.sql`）。迁移策略是"读时兼容明文、写时自动加密"的惰性迁移 —— 方向可接受，但**没有任何进度可观测手段**（无法知道还有多少行未加密）。

### 2.2 P0-04 gRPC TLS / 认证 —— **未修复（明确）**

- 【已验证】全仓检索 `useTransportSecurity|TlsChannelCredentials|ServerInterceptor|withCallCredentials` 在 `qknow-module-kb`、`qknow-hermes`、`qknow-framework` 下**命中 0**；唯一命中的是 `HermesGrpcClient.java:44` 的 `.usePlaintext()`。两个提交均未触及该文件（`git show --stat` 无此路径）。
- **影响**：控制面 → Hermes 的 `ChatRequest` 含 `modelConfig.apiKey`（`KbAgentConfigServiceImpl.java:402-408`），在容器网络内以明文 HTTP/2 传输；再叠加 §1.2 的 `9090:9090` 宿主发布，等于把明文 gRPC 暴露到宿主网络栈。
- **验证方式**：`grep -rn "usePlaintext\|ServerInterceptor\|TlsChannelCredentials" backend --include=*.java | grep -v /target/`（当前应为 1 条 usePlaintext）。
- **建议最小改**：`ManagedChannelBuilder` 加 `.useTransportSecurity()` + 服务端 `@GrpcService` 侧配 mTLS 或在 `ServerInterceptors` 中校验调用方身份；过渡方案至少不要 `ports` 发布 9090。

### 2.3 【新增回归 P0】脱敏串回写 → 密钥被永久覆盖

这是本轮复核**最严重的发现**，且**可从前端页面直接触发**。

- 【已验证】读路径产出 4 星掩码：`AiApiKeyRespVO.java:45` → `sk-****abcd`。
- 【已验证】写路径的"跳过掩码"判据只认 **17 个星号**：`AiApiKeyServiceImpl.java:148-159`（`updateAiApiKey` 中 `getApiKey().contains("*****************")`，17 个 `*`）；另一处掩码 `apiKeyEncrypt` 产出的正是 17 星格式（`AiApiKeyServiceImpl.java:135-145`，`substring(0,4) + "*****************" + 后3位`）。→ **两套掩码格式不一致，4 星掩码不会被任何判据识别。**
- 【已验证】批量保存路径 `submitBatch` **完全没有掩码判据**：`AiApiKeyServiceImpl.java:288-327`。其中 `:305-308` 用 `sqlDO.getApiKey()`（经 TypeHandler 解出的**真实密钥**）与 `vo.getApiKey()`（**掩码串**）比较 → 永不相等 → `:311-314` 把掩码串转成 DO 放入 `updateList` → `:322-324` `updateBatchById` → TypeHandler **把 `sk-****abcd` 加密写库**。
- 【已验证】**前端可达路径**：`frontend/src/views/ai/modelMarket/index.vue:309-335`（`handleUpdate` → `listByPlatform` → 返回值直接塞进 `form.value.platformKeyList`，含掩码 apiKey）→ `:378-392`（`submitForm` → `submitBatch({platform, keyList})`）→ `frontend/src/api/ai/modelMarket/key.js:69-76` → `AiApiKeyController.java:110-114`（`POST /ai/key/submitBatch`，类级映射见 `:36 @RequestMapping("/ai/key")`）。
- 【已验证】同样存在 `myModel/index.vue:328` 的 `apiKey: ""` 初始化模式，属同一类"读-改-写"风险面。
- **后果**：管理员在"密钥设置"弹窗里**只改名称/URL 或什么都不改就点保存**，该平台下所有 API Key 会被写成掩码串的密文；之后 RAG/对话全部 401，且因为 decrypt 成功（密文合法）**不会报错**，表现为"模型突然全挂"。
- **判定：新增回归（P0）**。这是"加密+脱敏"两个修复叠加后产生的次生缺陷，优先级高于本轮任何一项"部分修复"。
- **最小修复建议（不要求本轮执行）**：统一掩码格式并集中判定——所有写入路径共用 `isMasked(String)`（正则 `^.{0,4}\*{4,}.*$`），命中即 `setApiKey(null)`；或更稳妥：VO 根本不返回 `apiKey` 字段，单独提供"是否已配置"布尔位。

### 2.4 P0-06 HMAC 密钥 —— **部分修复 / 新增回归**

- 【已验证】硬编码字面量从 `SecurityConfig` 的方法体挪到了注解默认值：`SecurityConfig.java:74` `@Value("${flyflow.hmac.secret:${token.secret:qknow-flyflow-secret-key-2026}}")`，使用点 `:161`。字面量 `qknow-flyflow-secret-key-2026` **仍在源码里**（只是换了位置）。
- 【已验证】filter 自身的兜底字面量**未改**：`FlyFlowHmacAuthenticationFilter.java:36` 仍在无参/空白时回落到同一字符串。
- 【已验证】**密钥复用回归**：`flyflow.hmac.secret` 的回退链是 → `token.secret`，即 **JWT HS512 签名密钥与 FlyFlow 请求签名（HMAC-SHA256）共用同一密钥**。两套协议共用密钥违反密钥分离原则；一旦 `token.secret` 泄漏，攻击者可同时伪造会话 token 与内部请求签名。
- 【已验证】配置面未声明：全仓 `.yml` 无 `flyflow.hmac.secret`（`grep` = 空），`.env.example` 也未新增该项（仅 `:37 TOKEN_SECRET=your-token-secret`）→ 运维无从得知要配这个键，实际永远走默认值。
- **判定：部分修复**（可配置化完成）+ **新增回归**（密钥复用、字面量未清除、无文档）。
- **验证方式**：`grep -rn "flyflow.hmac" .`（应只剩 `SecurityConfig.java:74,77`）；`grep -n "qknow-flyflow-secret-key-2026" -r backend --include=*.java`（当前 2 处）。

---

## 3 租户与越权（P0-05）—— **部分修复，方向存疑**

### 3.1 `getWorkSpaceId()` —— **部分修复，但引入"可伪造租户"回归**

- 【已验证】新实现读客户端请求头：`backend/qknow-framework/qknow-common/.../BaseController.java:213-235`，优先 `request.getHeader("workspace-id")`（`:222`），其次 `X-Workspace-Id`（`:224`），最后 `return 1001L`（`:234`），全程 `try/catch(Exception ignored)`（`:227-231`）。
- 【已验证】**没有任何成员校验**：拿到 header 后直接 `Long.parseLong` 返回，未与 `LoginUser` 的工作区列表比对。→ 从"恒定 1001"变成"**攻击者可指定任意 workspaceId**"；只要下游 SQL 用 `workspaceId` 过滤，就越权读到别的租户数据。这是**方向性回归**（把常量缺陷换成了可控输入缺陷）。
- 【已验证】前端**不会**发这个头：`frontend/src/utils/request.js:31` 只注入 `Authorization`，另加 `X-HTTP-Method-Override`（`:39`）；`api/kb/conversation/index.js:5-10` 的 `workspaceId` 是**查询参数**不是 header。→ 正常 UI 流量仍回落 1001，"多租户"依旧未真正生效，但 **API 直连者可以伪造**。
- **判定：部分修复（可动态化）/ 新增回归（可伪造且无校验）**。最小修复：改为 `getLoginUser().getDeptId()/getWorkspaceId()` 并从 JWT/会话读取，header 仅作"选择当前工作区"且必须在用户所属集合内。

### 3.2 `DataScopeInterceptor` —— **未修复**

- 【已验证】仍只有自身定义与被测试引用：`backend/qknow-framework/qknow-mybatis/.../interceptor/DataScopeInterceptor.java:22`（类声明）+ `backend/tests/src/test/java/tech/qiantong/qknow/mybatis/DataScopeInterceptorTest.java:4,8,12,22,32,42`。`MybatisPlusConfig.java:21-31` 仍只注册 分页/乐观锁/防全表（`:26,28,30`），**无 `TenantLineInnerInterceptor`、也未注册 `DataScopeInterceptor`**。两个提交均未触及这两个文件。
- **残留风险**：所有 Mapper 查询**没有行级租户/数据权限约束**，"租户隔离"仍靠业务代码自觉。

### 3.3 会话归属校验 —— **部分修复（fail-open + 覆盖不全）**

- 【已验证】读路径加了归属校验：`KbChatMessageServiceImpl.java:19-39`。但存在 **4 个 fail-open 口子**：
  1. `conversationMapper` 为 `@Autowired(required=false)`（`:19-20`）→ 为 null 时整段跳过（`:30`）；
  2. `conv == null`（会话不存在/已删）时不校验、继续查消息（`:32`）；
  3. `conv.getUserId() == null` 时不校验（`:32`）；
  4. 整段包在 `catch (Exception ignored)` 里（`:37-39`）→ 任何异常（含无 Web 上下文）都跳过。
  且越权结果是**静默返回空列表**而非 403，攻击者可据此做存在性探测（空 vs 非空语义不同）。
- 【已验证】**写路径完全未加校验**：`saveMessage()`（`KbChatMessageServiceImpl.java:47-58`）无归属判断；`KbConversationController.java:87-88` 在 `sendMessage` 里直接向请求体给的 `conversationId` 写用户消息与"思考中"占位 → **跨用户写入 IDOR 仍在**。
- 【已验证】**删除路径完全未加校验**：`KbConversationController.java:66-71`（`deleteConversation(id)` → `deleteByConversationId(id)`），任意登录用户可删他人会话及其全部消息。
- **判定：部分修复（仅读路径、fail-open）/ 未修复（写、删）**。
- **验证方式**：静态复核上述四处 + 构造两用户集成测试（A 用户 `DELETE /kb/conversation/{B的id}` 应 403）。

### 3.4 `/internal/api/**` 鉴权 —— **未修复**

- 【已验证】`SecurityConfig.java` 的 `permitAll` 清单（`:135-156`）与 `anyRequest().authenticated()`（`:156`）在新版中**没有任何 `/internal/api/**` 规则**；`KbInternalApiController.java` 仍以注释自称"仅在 Docker 内网可访问"（`:14-17`），代码零校验。→ 任意普通 JWT 仍可 `POST /internal/api/rag/recall` 按任意 `knowledgeId` 取全文。
- **判定：未修复**。最小修复：加 `.requestMatchers("/internal/api/**").hasAuthority("ROLE_INTERNAL_SYSTEM")` 并复用 HMAC filter。

---

## 4 依赖瘦身（cb6e185c）—— **部分修复**

### 4.1 test scope 隔离 —— **已修复**

- 【已验证】`backend/qknow-framework/qknow-common/pom.xml:163-167`：`spring-boot-starter-test` 已加 `<scope>test</scope>`（原审计 P1-10 的直接病灶）。
- 【已验证】`backend/pom.xml:201-207` 新增全仓 `<dependencies>` 块，统一声明 `spring-boot-starter-test` + `<scope>test</scope>`。
  - **评价**：能防"某个子模块漏写 scope"，但这是**用全局注入掩盖单点配置错误**——聚合 POM 的 `<dependencies>` 会被所有子模块继承，等于让 48 个模块都拉一份测试框架。更干净的做法是 `maven-enforcer-plugin` 断言 + 保留子模块显式声明。
- 【已验证】`src/main` 下已无 `org.springframework.mock` / `org.springframework.test` 引用（`grep -rn ... | grep /src/main/` = 0），说明"生产代码依赖 spring-test"这个根因已被清理。

### 4.2 `ByteArrayMultipartFile` 替换 —— **已修复，副作用可控**

- 【已验证】新增 `backend/qknow-framework/qknow-common/src/main/java/tech/qiantong/qknow/common/utils/file/ByteArrayMultipartFile.java`（103 行），两处生产代码从 `MockMultipartFile` 切换：`CaCertificateIssuer.java:151,168`（import 见 `:18`）与 `CaGenerateRootCertificate.java:104,122`。
- 【已验证】副作用评估：
  1. `CaGenerateRootCertificate.convertFileToMultipartFile` 由"传流"改为 `input.readAllBytes()`（`:119-121`）→ **全量载入内存**。该方法仅处理根 CA 证书（KB 级），风险低；但若未来复用到上传文件，会成为 OOM 点位。
  2. `getBytes()` 直接返回内部数组（`ByteArrayMultipartFile.java:82-84`），无防御性拷贝 —— 与 Spring `MockMultipartFile` 行为一致，**不算回归**，但属可变状态外泄，建议后续 `Arrays.copyOf`。
  3. 新实现缺 `toString()`（Spring 版本有），日志可读性略降，无功能影响。
- **判定：已修复（含 P2 级待优化）**。

### 4.3 fat jar 是否真瘦身 —— **部分修复（3.3%）**

以 `backend/qknow-server/target/qknow-server.jar` 实测（构建时间 2026-10-20 18:59，晚于 cb6e185c 17:12，可代表修复后产物）：

| 指标 | 修复前（原审计实测） | 修复后（本次实测） | 变化 |
| --- | --- | --- | --- |
| 体积 | 453,785,919 B ≈ **432.8 MiB** | 438,693,669 B ≈ **418.4 MiB** | −14.4 MiB（**−3.3%**） |
| `BOOT-INF/lib` jar 数 | **589** | **569** | −20 |

- 【已验证】测试库确实清了：`junit*`、`mockito*`、`assertj*`、`hamcrest*`、`jsonassert`、`xmlunit`、`spring-test`、`spring-boot-test` 在 `BOOT-INF/lib` 中**均已消失** ✓。
- 【已验证】**但仍有测试/工具库残留**：
  - `BOOT-INF/lib/embedded-redis-1.4.3.jar` —— 因 `backend/qknow-server/pom.xml:119-123` **仍是 compile scope**（本轮只给 starter-test 加了 scope，漏了它）；
  - `BOOT-INF/lib/org.jacoco.agent-0.8.5-runtime.jar` —— 覆盖率 agent 进生产包，且 0.8.5（2020）不支持 Java 21 字节码。
  → 提交信息"实测达成 fat jar **零**测试库泄漏"与实测不符。
- 【已验证】**体积大头原封未动**：`hadoop*` 14 个、`tika*` 24 个、`lucene*` 13 个、`elasticsearch*` 10 个、`netty*` 37 个、`flexmark*` 49 个 —— 合计 137 个 jar 仍在。原审计 P1-11 的核心诉求（Hadoop/Tika/Lucene/ES 服务端不该在 Web 应用里）**一项未做**。
- 【已验证】`backend/qknow-server/pom.xml:194-197` 把 `excludeGroupIds` 扩成 `tech.qiantong,org.junit.jupiter,org.junit.platform`——
  - **评价**：这是"症状屏蔽"而非"病因治理"。junit 消失的实际原因是 scope 修正；`excludeGroupIds` 只覆盖 junit 两个 group，若未来 `org.mockito` / `org.assertj` 再次以 compile scope 泄漏，**该规则不拦**。同时"从 fat jar 里挖掉依赖"会让**打包 classpath 与运行 classpath 不一致**，属于高风险技巧，应回退为纯 scope 治理 + enforcer 断言。

---

## 5 CI 门禁与新增风险

### 5.1 CI 门禁 —— **未修复**

- 【已验证】两个提交均未改动 `.github/`（`git show --stat` 无该路径；`git log -6 -- .github/workflows/ci.yml` 最新为更早的 `b421c3a5`）。
- 【已验证】CI 仍只有两条 Maven 命令，且都只跑 `test`：`ci.yml:75`（`-pl qknow-hermes/qknow-hermes-core -am test`）、`ci.yml:109`（`-pl tests -am test`）。**没有** `mvn -DskipTests package` 的打包/装配 smoke、**没有** `docker compose config` 校验、**没有**依赖收敛/license/密钥扫描门禁。
- 【已验证】直接后果：本轮 §1.1（两个不同的 TOKEN_SECRET 默认值）、§1.2（compose 传给 hermes 的三个环境变量全部空转）、§2.3（掩码串回写）这三类缺陷，**现有 CI 一条都发现不了**。
- **最小建议**：加 1 个 job（约 2-4 分钟）：`mvn -B -ntp -f backend/pom.xml -DskipTests package` + `docker compose -f deploy/docker/docker-compose-qknow.yml config -q`（后者还能顺带校验 `TOKEN_SECRET:?` 之类的必填断言）。这一条 job 的收益高于本轮全部代码修复。

### 5.2 新增风险清单（本轮修复引入）

| # | 风险 | 证据 | 严重度 |
| --- | --- | --- | --- |
| N-1 | 脱敏串经 `submitBatch` 回写覆盖真实密钥 | `AiApiKeyServiceImpl.java:288-327`（`:305-314,:322-324`）+ `modelMarket/index.vue:309-335,378-392` | **P0** |
| N-2 | 公开默认 JWT/AES/HMAC 密钥（三处字面量入库） | `application.yml:125`、`docker-compose-qknow.yml:41`、`EncryptTypeHandler.java:82-84`、`SecurityConfig.java:74`、`FlyFlowHmacAuthenticationFilter.java:36` | **P0** |
| N-3 | 轮换 `token.secret` → 存量 API Key 静默失效（decrypt 吞异常返回密文） | `EncryptTypeHandler.java:50-60` | P1 |
| N-4 | JWT 签名密钥与 HMAC 请求签名密钥复用 | `SecurityConfig.java:74` | P1 |
| N-5 | `getWorkSpaceId()` 信任客户端 header，无成员校验 | `BaseController.java:222-225` | P1 |
| N-6 | Hermes gRPC 端口发布到宿主 | `docker-compose-qknow.yml:86-88` | P1 |
| N-7 | hermes 容器环境变量与自身配置键不匹配 → 必然 crash-loop | `docker-compose-qknow.yml:83-85` vs `qknow-hermes-starter/.../application.yml:15-16,20-21` | P1 |
| N-8 | 两处 TOKEN_SECRET 默认值不一致（配置漂移） | `application.yml:125` vs `docker-compose-qknow.yml:41` | P2 |
| N-9 | `excludeGroupIds` 屏蔽依赖，打包/运行 classpath 不一致 | `qknow-server/pom.xml:196` | P2 |
| N-10 | 加密后长度可能超出 `VARCHAR(128)`（存量 >96 字符时回写失败） | `deploy/sql/postgresql/01-schema.sql:363` | P2（无法静态判定实际数据） |

---

## 6 裁决汇总

| 编号 | 事项 | 判定 |
| --- | --- | --- |
| P0-01 | TOKEN_SECRET 注入 / 安全默认 | **部分修复**（启动不再失败，但改为公开默认 + 两处默认值不一致） |
| P0-02 | Hermes Dockerfile(21-jre) / compose 服务 / healthcheck / 内网 / host 解析 | **部分修复**：版本 ✓、服务声明 ✓、host 解析 ✓；healthcheck ✗、依赖顺序 ✗、env 键名 ✗、构建通道 ✗、内网 ✗（新增宿主端口发布） |
| P0-03 | Key 加密（TypeHandler/密钥来源/迁移/VO 脱敏/gRPC） | **部分修复 + 新增回归**：TypeHandler ✓、VO 脱敏 ✓、内部取键未破坏 ✓；密钥来源 ✗、迁移 ✗、gRPC 明文 ✗、**回写覆盖密钥 ✗✗** |
| P0-04 | gRPC TLS / 认证 / ServerInterceptor | **未修复**（全仓仅 `usePlaintext()`，0 处 TLS/interceptor） |
| P0-05a | `getWorkSpaceId()` 从 JWT 取 | **部分修复 + 新增回归**（改为读客户端 header，无成员校验） |
| P0-05b | `DataScopeInterceptor` 注册 | **未修复** |
| P0-05c | 会话归属校验 | **部分修复**（仅读路径，且 fail-open；写/删未覆盖） |
| P0-05d | `/internal/api` 鉴权 | **未修复** |
| P0-06 | HMAC 密钥去硬编码 | **部分修复 + 新增回归**（字面量仍在两处、回退复用 token.secret） |
| cb6e185c-s1 | test scope 隔离 | **已修复** |
| cb6e185c-s2 | `ByteArrayMultipartFile` 替换 | **已修复**（P2 级副作用：`readAllBytes` 全量载入、`getBytes` 无拷贝） |
| cb6e185c-s3 | fat jar 瘦身 | **部分修复**（−3.3%；embedded-redis 与 jacoco 仍在，Hadoop/Tika/Lucene/ES/Flexmark 未动） |
| CI | 门禁补齐 | **未修复**（无 package/compose smoke） |

**MCP 相关修复（自动装配去孤岛、假数据去伪）不在本报告范围，见 `docs/architecture-review-20261019/verify/05-mcp-fix.md`（task-11）。**

---

## 7 未验证 / 需 Lead 决策

| 项 | 说明 | 建议复验方式 |
| --- | --- | --- |
| jjwt 对含 `-` 默认密钥的实际解码长度 | 本次以字节码确认走 `DatatypeConverter.parseBase64Binary`，但未运行验证最终密钥长度与是否抛异常 | 一次 `POST /login`（需起服务，不在只读许可内） |
| Hermes 容器是否真的 crash-loop | 无容器运行证据 | `docker logs agent-hermes`；或 CI 加 compose smoke |
| 存量 `ai_api_key` 明文的真实长度分布 | 无 DB 访问权限 | `SELECT length(api_key), count(*) FROM ai_api_key GROUP BY 1;` |
| 密钥轮换后存量密文的可读性 | 需构造数据 | 单元测试：写→改 secret→读，断言不应静默返回密文（应抛错或告警） |
| 前端"密钥设置"保存是否 100% 触发 N-1 | 已确认代码路径可达，但未实机点击 | E2E：改名称保存 → 查 DB 该行是否变成掩码密文 |
| **需 Lead 决策 1** | 是否接受"公开默认密钥"这一权宜方案（我建议不接受，改为必填断言 `${TOKEN_SECRET:?}`） | — |
| **需 Lead 决策 2** | N-1（掩码回写）是否作为本轮 hotfix 立即修（我建议：是，改动 <20 行） | — |
| **需 Lead 决策 3** | 依赖瘦身是否继续（Hadoop/Tika/Lucene/ES 去留直接决定 418 MiB → 是否能降到 <120 MiB） | — |

---

### 附录 证据索引

| 主题 | 文件:行 |
| --- | --- |
| TOKEN_SECRET 应用默认 | `backend/qknow-server/src/main/resources/application.yml:125` |
| TOKEN_SECRET compose 默认 | `deploy/docker/docker-compose-qknow.yml:41` |
| Hermes gRPC host/port 注入 | `deploy/docker/docker-compose-qknow.yml:42-43` |
| Hermes Dockerfile | `backend/qknow-hermes/qknow-hermes-starter/docker/Dockerfile:1,5,7` |
| Hermes compose 服务 | `deploy/docker/docker-compose-qknow.yml:70-96`（端口 `:86-88`、依赖 `:91-95`） |
| Hermes 配置键 | `backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml:15-16,20-21` |
| Key TypeHandler | `backend/qknow-module-ai/.../dal/dataobject/modelMarket/AiApiKeyDO.java:20,42` |
| 加密实现/密钥回退 | `backend/qknow-framework/qknow-mybatis/.../type/EncryptTypeHandler.java:50-60,74-88` |
| VO 脱敏 | `backend/qknow-module-ai/.../vo/AiApiKeyRespVO.java:38-46` |
| 服务层掩码与跳过判据 | `backend/qknow-module-ai/.../impl/AiApiKeyServiceImpl.java:135-145,148-159,162-167` |
| 批量保存（回归点） | `backend/qknow-module-ai/.../impl/AiApiKeyServiceImpl.java:288-327` |
| 前端读-改-写链路 | `frontend/src/views/ai/modelMarket/index.vue:309-335,378-392`；`frontend/src/api/ai/modelMarket/key.js:44-49,69-76` |
| 控制器映射 | `backend/qknow-module-ai/.../admin/modelMarket/AiApiKeyController.java:36,46-48,54-56,70-73,110-114` |
| 列宽 | `deploy/sql/postgresql/01-schema.sql:363` |
| gRPC 明文 | `backend/qknow-module-kb/.../agent/HermesGrpcClient.java:44`；`.../impl/KbAgentConfigServiceImpl.java:407` |
| HMAC 密钥 | `backend/qknow-framework/qknow-security/.../config/SecurityConfig.java:74-78,161`；`.../filter/FlyFlowHmacAuthenticationFilter.java:36` |
| 工作区解析 | `backend/qknow-framework/qknow-common/.../controller/BaseController.java:213-235` |
| 前端未发 workspace header | `frontend/src/utils/request.js:31,39` |
| 租户拦截器缺失 | `backend/qknow-framework/qknow-mybatis/.../config/MybatisPlusConfig.java:21-31` |
| 未注册拦截器 | `backend/qknow-framework/qknow-mybatis/.../interceptor/DataScopeInterceptor.java:22` |
| 会话归属校验 | `backend/qknow-module-kb/.../impl/KbChatMessageServiceImpl.java:19-39,47-58` |
| 会话删除未校验 | `backend/qknow-module-kb/.../conversation/KbConversationController.java:66-71` |
| 内部 API | `backend/qknow-module-kb/.../admin/KbInternalApiController.java:14-17`；`SecurityConfig.java:135-156` |
| test scope 修复 | `backend/qknow-framework/qknow-common/pom.xml:163-167`；`backend/pom.xml:201-207` |
| embedded-redis 未修 | `backend/qknow-server/pom.xml:119-123` |
| excludeGroupIds | `backend/qknow-server/pom.xml:194-197` |
| ByteArrayMultipartFile | `backend/qknow-framework/qknow-common/.../utils/file/ByteArrayMultipartFile.java:82-102`；调用点 `utils/ca/CaCertificateIssuer.java:151,168`、`utils/ca/CaGenerateRootCertificate.java:104,122` |
| CI 现状 | `.github/workflows/ci.yml:75,109` |
| 产物实测 | `backend/qknow-server/target/qknow-server.jar`（438,693,669 B / 569 libs，2026-10-20 18:59） |

**报告结束。** 共复核 13 个裁决项：已修复 2、部分修复 6、未修复 3、新增回归 2（N-1 与 N-2 计为 P0 回归，P0-05a/P0-06 为方向性回归），新增风险 10 项（N-1 ~ N-10）。
