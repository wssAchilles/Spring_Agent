# Phase 40 实施计划：低延迟全双工流式多模态实时音视频交互中枢 (Low-Latency Full-Duplex Streaming Multimodal Real-Time Audio/Video Hub)

> **归档路径**：`docs/plans/phase_40_plan.md`  
> **实施纪律**：严格遵守 `AGENTS.md` Research-to-Implementation Gate；严格锁定唯一可证伪假设 H-PHASE40-001；在未获得用户明确批准前禁止修改业务代码；测试全绿后方可交付。  
> **依赖阶段**：Phase 27 (多模态文档与图像感知), Phase 28 (统一模型网关), Phase 33 (零侵入编排管道)。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面）；全系统绝无本地大模型，彻底弃用 OpenAI API；宿主环境严格使用隔离 **Java 21** (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、唯一待验证假设 (H-PHASE40-001)

在 DeepSeek API（V3 生成 / R1 思考）、阿里千问 1536 维向量空间与 Java 21 隔离环境下：
1. 通过建立全双工流式 ASR -> DeepSeek SSE -> 短语微块切分 -> 流式 TTS 的重叠流水线，能够在保证语义完整性的前提下，将端到端首音频响应时间（TTFA）从传统瀑布流的 $3000\text{ms}\sim 5000\text{ms}$ 严格压降至 $\le 800\text{ms}$（定理 1.1），延迟缩减 $\ge 70\%$；
2. 通过实施双阈值时序平滑贝叶斯 VAD 与基于代际序列号（Epoch）的原子李雅普诺夫截断机制，能够实现打断（Barge-in）响应延迟 $\le 200\text{ms}$，瞬态敲击杂音误打断率 $\le 2.0\%$，且打断后旧音频帧残留率严格为 0（定理 2.1）；
3. 通过实施基于 pHash 变化检测的视觉关键帧自适应流控算法，能够使视频/屏幕共享多模态交互在维持关键场景变动检出率 $\ge 98\%$ 的同时，将无效静态画面的图像 Token 消耗降低 $\ge 60\%$（定理 3.1）。

---

## 二、10 项严苛契约测试设计 (Phase 40 Contract Test Suite)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase40StreamingMultimodalHubContractTest.java` 中构建 10 项严苛契约测试：

1. **Contract 1: 流式微管线重叠推进与首音频延迟 TTFA 压降契约 (定理 1.1)**
   - 验证流式 ASR、DeepSeek 首 Token 与短语微块 TTS 的流水线并行时序，端到端首音频耗时 $\le 800\text{ms}$，延迟相比瀑布流压降 $\ge 70\%$。
2. **Contract 2: 标点短语双门限微切块与断句语义完备性契约**
   - 验证 `PhraseChunkingTokenizer` 在遇到逗号、句号、问号等自然停顿标点或字数累计达 6~10 字时精确切块，杜绝半词断裂与语法截断。
3. **Contract 3: 双阈值贝叶斯能量 VAD 人声起始与结束精确感知契约**
   - 验证 VAD 状态机在静音、说话起始、持续发音、说话结束四态转移顺畅，双阈值有效抑制低能量底噪。
4. **Contract 4: 瞬态噪声时序平滑过滤与抗误打断契约**
   - 验证持续时间小于 $100\text{ms}$ 的高能量瞬态脉冲（模拟键盘敲击、咳嗽）被平滑窗口有效拦截，不误触发打断。
5. **Contract 5: 毫秒级语音打断 (Barge-in) 与响应延迟契约 (定理 2.1)**
   - 验证在智能体说话播报期间，持续 $\ge 160\text{ms}$ 的有效用户人声输入触发打断动作，判定时间 $\le 200\text{ms}$。
6. **Contract 6: 代际号 (Epoch) 原子流转与旧音频帧零残留消除契约**
   - 验证触发打断后，`currentEpoch` 原子自增，旧代际音频帧与未消费 Token 被 100% 拦截丢弃，杜绝幽灵音频回放。
7. **Contract 7: 视觉关键帧 pHash 差异检测与静态帧过滤契约 (定理 3.1)**
   - 验证视觉流控器对连续相同或微小变动画面执行过滤丢弃，仅对显著变化帧（汉明距离 $\ge 8$）或保底心跳帧予以放行，Token 节约率 $\ge 60\%$。
8. **Contract 8: 全双工双向多模态 WebSocket 状态机生命周期契约**
   - 验证会话建立、音视频并发推流、控制事件（`speech.started`, `response.interrupted`）双向派发的闭环一致性。
9. **Contract 9: 极端断网重连与客户端 Jitter Buffer 自愈契约**
   - 验证 WebSocket 意外断开时，后端会话状态安全降级释放，防止线程泄漏与内存溢出。
10. **Contract 10: 端到端实时音视频多模态交互全链路协同闭环契约**
    - 验证音频输入 -> VAD 检测 -> 流式转录 -> DeepSeek 流式回答 -> 短语 TTS -> 视频关键帧注入 -> 打断取消的完整闭环。

---

## 三、最小实现组件集合清单

1. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/dto/RealtimeSessionDO.java`
2. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/dto/AudioFrameDTO.java`
3. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/dto/MultimodalVisionFrameDTO.java`
4. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/dto/RealtimeEventVO.java`
5. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/vad/BayesianEnergyVadDetector.java`
6. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/tokenizer/PhraseChunkingTokenizer.java`
7. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/vision/VisionTokenGovernor.java`
8. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/pipeline/RealtimeStreamingPipeline.java`
9. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/realtime/hub/RealtimeMultimodalHubEndpoint.java`
10. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase40StreamingMultimodalHubContractTest.java`
