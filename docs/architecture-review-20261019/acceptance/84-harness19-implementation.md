# Hermes Harness Stage 19 实施与验收报告

- 契约：`82-harness19-audit.md` 与双哈希冻结的 `83-harness19-taskbook.md`；`shasum -a 256 -c` 为 `OK`。
- 基线：Stage 18 `0390761e`；分支：`feat/hermes-harness-stage19`；源码提交：`aeb82368790841bdb38bbf2ad624488a4656370b`。
- 范围：静态 Profile 白名单、内存会话绑定和离线确定性契约；不包含真实模型效果或生产启用。

## 一、生命周期与身份

Profile 保持不可变，`version` 与 `status` 均进入确定性 fingerprint。默认注册表仍只有 Stage 18 的四个版本 `1`、`ACTIVE` Profile；Router 不创建或修改 Profile。

| 状态 | 新请求 | TTL 内已有绑定 | 到期或退役后 |
|---|---|---|---|
| `ACTIVE` | 可选择 | 可复用 | 重新选择当前 ACTIVE |
| `DEPRECATED` | 回退 baseline | 原 ACTIVE 身份一致时可复用 | 不能再选择，回退或选择其他 ACTIVE |
| `RETIRED` | 回退 baseline | 立即失效 | 有批准的 ACTIVE 替代则 REBOUND，否则 baseline |
| 未批准 | 回退 baseline | 立即失效 | 不执行 |

## 二、会话绑定与运行模式

绑定键为 `(sessionId, capabilityTag, domain)`；仅正数 `ChatRequest.conversationId` 转成 sessionId。无 sessionId 按请求级路由，不绑定。绑定记录只存 Profile ID、版本、fingerprint、绑定/过期时间，不存提示词正文或密钥。默认 TTL 30 分钟、容量 1024；`Clock` 可注入，`ReentrantLock` 使同 key 选择与淘汰原子化。达到容量时先清理过期记录，再淘汰最早绑定；被淘汰的会话下次请求重新选择。

| 模式 | 路由与绑定 | 实际输入与证据 |
|---|---|---|
| `OFF` | 不路由、不查绑定 | 原 voucher；不写 receipt |
| `SHADOW` | 路由但不绑定 | 原 voucher；记录候选与路由 receipt |
| `ENFORCE` | 有会话时 PINNED/REUSED/REBOUND/EXPIRED；无会话请求级路由 | 执行批准的 Profile；准备失败或 Kernel Bridge 冲突时模型调用前 fail-close |

Receipt 保留 Stage 18 的 `pipelineFingerprint` 等字段，并增加 session、capability、domain、profile status 与 binding action。route fingerprint 不含 requestId，可从域、能力、Profile fingerprint、reason 和 fallback 复算。

## 三、回退矩阵

| 条件 | 执行结果 | `routeReason` / 动作 |
|---|---|---|
| RAG/COMPOSITE 无批准路由 | baseline | `NO_APPROVED_ROUTE` / `FALLBACK` |
| 域不匹配或未知能力 | baseline | `DOMAIN_MISMATCH` / `UNKNOWN_CAPABILITY` |
| 未批准、Profile 不匹配 | baseline | `PROFILE_NOT_APPROVED` / `PROFILE_MISMATCH` |
| DEPRECATED 新会话 | baseline | `PROFILE_DEPRECATED` / `FALLBACK` |
| DEPRECATED 旧绑定且未过期 | 原 Profile | `DEPRECATED_EXISTING_ONLY` / `REUSED` |
| RETIRED 旧绑定，有 ACTIVE 替代 | 替代 Profile | `RETIRED_REBOUND` / `REBOUND` |
| RETIRED 旧绑定，无替代 | baseline | `RETIRED_FALLBACK` / `FALLBACK` |
| 绑定过期 | 重选或 baseline | `BINDING_EXPIRED` / `EXPIRED`；无可选时 `BINDING_EXPIRED_FALLBACK` |
| 路由被撤销 | baseline，清除旧绑定 | `NO_APPROVED_ROUTE` / `FALLBACK` |
| 算子准备失败 | ENFORCE fail-close，SHADOW baseline | `PIPELINE_PREPARE_FAILED:<异常类型>` |

## 四、验证记录

| 门禁 | 结果 |
|---|---|
| 83 冻结文件 | 正文 `08bcdf19fac431eeddc12100b96b390227536590cae4d1297641579c2b79126c`；全文件 `18e3fe7899dcfacb32c3d144c4f6946b95f1bec0e3c47e55fe5a2620c41c3005`；`shasum -c` OK |
| 四类定向测试 | 37 tests / 0 failures / 0 errors / 0 skipped |
| 原工作树单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` | BUILD SUCCESS；2242 / 0 / 0 / 10；日志 `/tmp/harness19-verify.WVcX2E/worktree-clean-test.log`，SHA-256 `81d7dd7712086441f4ca135af3166d23a8a502ee962d796899e5e3f25dd60ca4` |
| 全新本地 APFS 检出同 HEAD 单步 `clean test` | `/Users/achilles/.codex/worktrees/hermes19-clean-gate/Agent`，APFS `/dev/disk3s5`，HEAD `aeb82368790841bdb38bbf2ad624488a4656370b`；BUILD SUCCESS；2242 / 0 / 0 / 10；日志 `/tmp/harness19-verify.WVcX2E/clean-checkout-test.log`，SHA-256 `1a53f1c5dccc52f5ab1640de3360ba62b5deb13d42ebcbffef0ff56840153611` |
| 检出工作区 | 测试后 `git status --porcelain=v1` 为空 |
| 真实 API、生产默认 | 均未启用；默认仍为 OFF |

## 五、结论与边界

Stage 19 的离线、静态白名单生命周期与会话级路由契约通过，建议 `CLOSED`。容量淘汰发生时没有单独的逐出 receipt，后续请求表现为新的 PINNED；内存绑定不跨进程持久化。本阶段未验证真实模型效果、多域业务路由或生产运行。历史 proto 间歇故障的根因仍为 `UNPROVEN`；本次两次成功不构成根因修复证明。
