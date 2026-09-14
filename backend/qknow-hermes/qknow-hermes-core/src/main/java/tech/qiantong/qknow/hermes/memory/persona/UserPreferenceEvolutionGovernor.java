package tech.qiantong.qknow.hermes.memory.persona;

import tech.qiantong.qknow.hermes.memory.model.EvolutionDecision;
import tech.qiantong.qknow.hermes.memory.model.PreferenceRecord;

import java.util.Map;

/**
 * 用户个性化偏好演化治理器 (User Preference Evolution Governor) 契约接口
 * 覆盖：
 * 1. 意态与反事实过滤器 (Modality & Counterfactual Filter: 拦截率 >= 98%)
 * 2. 偏好演化决策状态机 (ADD / UPDATE / SUPERSEDE / REJECT / NOOP)
 * 3. 行级 CAS 乐观锁版本化防脑裂与属性级三向合并 (0 脑裂 0 脏覆写)
 */
public interface UserPreferenceEvolutionGovernor {

    /**
     * 意态检测：判定是否包含虚拟假设、反事实或代他人咨询语气
     */
    boolean isHypotheticalOrCounterfactual(String userUtterance);

    /**
     * 偏好仲裁：根据当前已存画像评估输入演化决策
     */
    EvolutionDecision arbitratePreference(String userId, String preferenceKey, String newPreferenceValue, String userUtterance);

    /**
     * 基于行级 CAS 乐观锁并发安全更新用户偏好属性 (带指数抖动退避重试与三向属性合并)
     * @param userId 用户 ID
     * @param newAttributes 本次待更新的属性键值对
     * @param expectedVersion 调用方期望的版本号
     * @return 更新后的最新版本号
     */
    int updateProfileWithCAS(String userId, Map<String, Object> newAttributes, int expectedVersion);

    /**
     * 获取指定用户的完整偏好画像快照
     */
    Map<String, PreferenceRecord> getUserPreferences(String userId);

    /**
     * 获取指定用户当前画像全局版本号
     */
    int getUserProfileVersion(String userId);
}
