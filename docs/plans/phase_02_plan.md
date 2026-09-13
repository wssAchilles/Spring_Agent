# Phase 02: 安全 Hardening (系统架构与大模型安全)

> **前置要求**: 依据 `AGENTS.md` Research-to-Implementation Gate 门禁标准制定。
> **目标阶段**: 彻底消除系统中存在的 SQL 注入、SSRF 漏洞、API 密钥明文泄露及前端 XSS 风险，保障生产环境安全底线。

## 1. 文献支撑与技术选型依据

### 1.1 学术理论依据 (Academic Evidence)
*   **LLM SSRF 的数学与逻辑机理**: 在大模型架构中，指令与数据边界扁平化，导致**间接提示词注入 (Indirect Prompt Injection)** 易发。攻击者通过扰动模型的概率分布，将工具调用参数指向内网地址（如 `169.254.169.254`）。因此，纯依赖 LLM “意图判断”的防御在数学上存在必然的逃逸率，必须在宿主机的网络栈协议层进行硬拦截。
*   **非结构化内容防御 (AST 与白名单博弈)**: 对于防范前端 XSS，由于大模型生成的混淆变异近乎无限，基于正则和黑名单的清洗注定失效。学术界共识是利用 AST (抽象语法树) 解析器结合**严格的白名单有限集 (Allowlisting)**，进行 DOM 树修剪 (Tree Pruning)。

### 1.2 工业界落地实践 (Engineering Practices)
*   **MyBatis 拦截器 + JSqlParser**: 针对数据权限过滤导致 SQL `${}` 拼接的注入风险，大厂（如阿里）最佳实践是通过实现 MyBatis 的 `Interceptor`，在底层的 `BoundSql` 阶段拦截，并利用 JSqlParser 对 AST 语法树动态重写 `WHERE` 条件，实现完全透明且安全的自动隔离。
*   **防御 DNS 重绑定 (DNS Rebinding)**: 在 Java 端防范 SSRF，仅仅验证域名字符串是不够的。必须深入底层 HTTP 客户端（如 OkHttp），重写其 `Dns` 接口接管 DNS 解析过程，在 Socket 建立连接前拦截所有被解析为私网 IP、回环 IP 的流量，彻底封杀 DNS 重绑定攻击。
*   **Vue 3 自定义安全指令**: 在前端封装基于 DOMPurify 的自定义全局指令 `v-safe-html` 替代原生 `v-html`，实现静默的数据清洗。
*   **Jackson 数据脱敏与双密钥轮换**: 通过实现 `ContextualSerializer` 在 JSON 返回时进行敏感字段自动掩码（Masking）；并构建无感知的 Primary/Secondary API 密钥轮换机制。

---

## 2. 模块接口与数据流设计

### 2.1 MyBatis AST SQL 拦截数据流
`Mapper 执行` -> `MyBatis Interceptor 拦截` -> `解析为 Select AST` -> `动态追加数据权限 Where 节点` -> `写回 BoundSql 执行`。

### 2.2 防 SSRF 网络请求拦截数据流
`HttpRequestTool 触发` -> `OkHttp 发起 DNS 解析` -> `Dns.SYSTEM 响应真实 IP 集合` -> `检查 IP 是否属于内网/私有/回环` -> `是则抛出 UnknownHostException 阻断连接，否则放行`。

---

## 3. 细化编码待办事项 (TDD To-Dos)

#### [NEW] `backend/src/main/java/tech/qiantong/qknow/framework/security/DataScopeInterceptor.java`
*   **任务**: 实现 MyBatis `Interceptor` 接口。引入 `JSqlParser` 解析原始 SQL，剔除业务层原本使用的 `${}` 拼接，改为在 AST 层面安全地 `AND (dept_id = ?)`。

#### [NEW] `backend/src/main/java/tech/qiantong/qknow/framework/security/SafeDns.java`
*   **任务**: 实现 OkHttp 的 `Dns` 接口，调用原生系统解析获取真实 IP 列表，遍历并拦截任何匹配 `127.0.0.0/8`, `10.0.0.0/8`, `192.168.0.0/16`, `172.16.0.0/12`, `169.254.169.254` 等网段的非法尝试。并配置相应的单例 HTTP 客户端给大模型工具统一调用。

#### [NEW] `backend/src/main/java/tech/qiantong/qknow/framework/jackson/DesensitizeSerializer.java`
*   **任务**: 实现基于自定义注解（如 `@Desensitize(type = PHONE)`）的序列化过滤，确保脱敏手机号、API 密钥等数据不外泄。

#### [MODIFY] `frontend/src/directive/safe-html.js` & `main.js`
*   **任务**: 引入 `dompurify` 依赖库，编写 Vue 3 的 `v-safe-html` 自定义指令并注册到全局，替换应用中所有不安全的 `v-html` 用法。

---

## 4. 验收准则与测试规范 (Acceptance Criteria)

### 4.1 单元测试准则 (TDD Unit Tests)
*   **`DataScopeInterceptorTest.java`**: 输入不带 where 条件和带 where 条件的复杂连表查询 SQL 文本，断言经过拦截器重写后的 AST 是否正确追加了权限条件，并验证语法树合法性。
*   **`SafeDnsTest.java`**: 模拟解析恶意域名（由攻击者控制绑定到 127.0.0.1）和良性域名（如 www.baidu.com），断言针对本地解析的拦截必定触发抛出 `UnknownHostException`。

### 4.2 集成测试准则 (Integration Gate)
*   **SSRF 端到端防御**: 使用前端通过大模型请求访问 `http://169.254.169.254/latest/meta-data/` 的恶意提示词（Prompt Injection），系统必须优雅地拦截该工具调用请求，并在日志输出 “检测到非法内网 IP 访问尝试” 的告警。
*   **前端 XSS 兜底防御**: 传入包含 `<img src=x onerror=alert(1)>` 以及 `javascript:` 伪协议的攻击载荷给前端组件，确保在 DOM 渲染后上述 Payload 均被 `v-safe-html` 彻底剥离剔除，且不影响合法 `<b>`, `<i>` 标签。
