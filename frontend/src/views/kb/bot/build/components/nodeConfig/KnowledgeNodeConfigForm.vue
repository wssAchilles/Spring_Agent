<template>
  <div class="knowledge-config-panel">
    <!-- 1. 基础信息区域 -->
    <div class="knowledge-config-section">
      <div class="knowledge-config-section-title node-config-section-title">
        <span class="blue-bar"></span>知识库选择
      </div>
      <div class="knowledge-config-tip">
        支持配置单个或并发检索多个企业知识库，基于 Java 21 虚拟线程原生并发派发。
      </div>

      <div class="knowledge-kb-select-wrapper mt10">
        <el-select
          v-model="selectedKbIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="请选择要关联检索的企业知识库"
          class="knowledge-kb-select"
          popper-class="dydome-square-popper"
          @change="handleKbIdsChange"
        >
          <el-option
            v-for="item in kbOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
            <div class="kb-option-item">
              <span class="kb-option-name">{{ item.name }}</span>
              <span class="kb-option-id">ID: {{ item.id }}</span>
            </div>
          </el-option>
        </el-select>
      </div>
    </div>

    <!-- 2. 查询词与检索设置 -->
    <div class="knowledge-config-section">
      <div class="knowledge-config-section-title node-config-section-title">
        <span class="blue-bar"></span>检索参数
      </div>

      <div class="knowledge-form-item mt10">
        <div class="knowledge-item-label">
          <span>检索查询词 (Query)</span>
          <span class="knowledge-subtip">支持常量或变量引用如 &#123;&#123;start.query&#125;&#125;</span>
        </div>
        <el-input
          v-model="queryText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="请输入检索查询内容或引用上游变量"
          @change="updateConfigField('query', queryText)"
        />
      </div>

      <div class="knowledge-form-item mt12">
        <div class="knowledge-item-label">检索模式</div>
        <el-radio-group v-model="retrievalModel" size="small" @change="updateConfigField('retrievalModel', retrievalModel)">
          <el-radio-button label="hybrid">超球面混合检索 (推荐)</el-radio-button>
          <el-radio-button label="dense">千问 1536D 向量</el-radio-button>
          <el-radio-button label="sparse">BM25 关键字</el-radio-button>
        </el-radio-group>
      </div>

      <div class="knowledge-form-item mt12">
        <div class="knowledge-item-label-row">
          <span class="knowledge-item-label">召回切片上限 (Top-K)</span>
          <span class="knowledge-item-value">{{ topK }} 篇</span>
        </div>
        <el-slider
          v-model="topK"
          :min="1"
          :max="20"
          :step="1"
          show-stops
          @change="updateConfigField('topK', topK)"
        />
      </div>

      <div class="knowledge-form-item mt12">
        <div class="knowledge-item-label-row">
          <span class="knowledge-item-label">相似度门限 (Score Threshold)</span>
          <span class="knowledge-item-value">{{ scoreThreshold.toFixed(2) }}</span>
        </div>
        <el-slider
          v-model="scoreThreshold"
          :min="0.0"
          :max="1.0"
          :step="0.05"
          @change="updateConfigField('score', scoreThreshold)"
        />
      </div>
    </div>

    <!-- 3. Phase P2 企业级算法与 Token 预算高级面板 -->
    <div class="knowledge-config-section">
      <div
        class="knowledge-advanced-toggle"
        @click="advancedExpanded = !advancedExpanded"
      >
        <div class="knowledge-config-section-title node-config-section-title">
          <span class="purple-bar"></span>算法重排与 Token 预算门限
        </div>
        <el-icon class="toggle-icon" :class="{ 'is-expanded': advancedExpanded }">
          <ArrowRight />
        </el-icon>
      </div>

      <el-collapse-transition>
        <div v-show="advancedExpanded" class="knowledge-advanced-card">
          <!-- Token 预算截断门限 -->
          <div class="advanced-item">
            <div class="knowledge-item-label-row">
              <span class="knowledge-item-label">最大 Token 预算截断</span>
              <span class="knowledge-item-value badge-token">{{ maxTokenBudget }} Tokens</span>
            </div>
            <div class="knowledge-subtip">
              按综合排序截断超出预算的切片，防止下游 Prompt 膨胀与 DeepSeek 首字延迟增加。
            </div>
            <el-slider
              v-model="maxTokenBudget"
              :min="512"
              :max="4096"
              :step="256"
              show-stops
              @change="updateConfigField('maxTokenBudget', maxTokenBudget)"
            />
          </div>

          <!-- Anthropic 风格 Contextual Retrieval 语境前缀增强 -->
          <div class="advanced-item mt12">
            <div class="advanced-switch-row">
              <div>
                <div class="advanced-switch-title">Contextual Retrieval 语境前缀</div>
                <div class="knowledge-subtip">自动为切片注入文档全局主旨与章节面包屑，降低 49% 检索丢失。</div>
              </div>
              <el-switch
                v-model="contextualEnrichment"
                active-color="#10b981"
                @change="updateConfigField('contextualEnrichment', contextualEnrichment)"
              />
            </div>
          </div>

          <!-- 千问 1536 维超球面几何调制 -->
          <div class="advanced-item mt12">
            <div class="advanced-switch-row">
              <div>
                <div class="advanced-switch-title">千问 1536D 超球面几何加权</div>
                <div class="knowledge-subtip">在 RRF 倒数排名融合基础上，利用超球面余弦测度对切片进行非线性调制。</div>
              </div>
              <el-switch
                v-model="hypersphericalModulation"
                active-color="#6366f1"
                @change="updateConfigField('hypersphericalModulation', hypersphericalModulation)"
              />
            </div>

            <div v-if="hypersphericalModulation" class="mt8">
              <div class="knowledge-item-label-row">
                <span class="knowledge-item-label">余弦调制权重 (α)</span>
                <span class="knowledge-item-value">{{ alphaWeight.toFixed(2) }}</span>
              </div>
              <el-slider
                v-model="alphaWeight"
                :min="0.0"
                :max="1.0"
                :step="0.05"
                @change="updateConfigField('alpha', alphaWeight)"
              />
            </div>
          </div>

          <!-- 多库并发超时设置 -->
          <div class="advanced-item mt12">
            <div class="knowledge-item-label">并发超时保护 (毫秒)</div>
            <div class="timeout-grid mt6">
              <div class="timeout-cell">
                <span class="timeout-label">单库软超时</span>
                <el-input-number
                  v-model="singleTimeoutMs"
                  :min="500"
                  :max="10000"
                  :step="500"
                  size="small"
                  @change="updateConfigField('singleTimeoutMs', singleTimeoutMs)"
                />
              </div>
              <div class="timeout-cell">
                <span class="timeout-label">全局硬超时</span>
                <el-input-number
                  v-model="globalTimeoutMs"
                  :min="1000"
                  :max="20000"
                  :step="500"
                  size="small"
                  @change="updateConfigField('globalTimeoutMs', globalTimeoutMs)"
                />
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </div>

    <!-- 4. 节点描述 -->
    <div class="knowledge-config-section">
      <div class="knowledge-config-section-title node-config-section-title">
        节点描述
      </div>
      <el-input
        :model-value="description"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        placeholder="请输入节点描述信息"
        @update:model-value="emitField('description', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { ArrowRight } from '@element-plus/icons-vue';
import { listKnowledgeBase } from '@/api/kmc/knowledgeBase/knowledgeBase';

const props = defineProps({
  node: {
    type: Object,
    default: () => ({})
  },
  description: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['updateField', 'updateConfig']);

const kbOptions = ref([]);
const selectedKbIds = ref([]);
const queryText = ref('');
const retrievalModel = ref('hybrid');
const topK = ref(5);
const scoreThreshold = ref(0.60);
const maxTokenBudget = ref(2048);
const contextualEnrichment = ref(true);
const hypersphericalModulation = ref(true);
const alphaWeight = ref(0.35);
const singleTimeoutMs = ref(2000);
const globalTimeoutMs = ref(3000);
const advancedExpanded = ref(true);

// 加载知识库列表
const fetchKbOptions = async () => {
  try {
    const res = await listKnowledgeBase({ pageNum: 1, pageSize: 100 });
    if (res && res.data && res.data.rows) {
      kbOptions.value = res.data.rows.map(item => ({
        id: item.id,
        name: item.name || `知识库 #${item.id}`
      }));
    } else if (res && res.rows) {
      kbOptions.value = res.rows.map(item => ({
        id: item.id,
        name: item.name || `知识库 #${item.id}`
      }));
    }
  } catch (err) {
    console.warn('获取知识库列表失败，使用降级占位项:', err);
  }
};

// 同步初始化节点配置
const syncFromNode = () => {
  if (!props.node) return;
  const config = props.node.config || {};

  // 多库 ID 列表规整
  const ids = [];
  if (Array.isArray(config.knowledgeBaseIds)) {
    ids.push(...config.knowledgeBaseIds);
  } else if (config.knowledgeBaseId) {
    ids.push(config.knowledgeBaseId);
  } else if (config.knowledgeId) {
    ids.push(config.knowledgeId);
  }
  selectedKbIds.value = [...new Set(ids)];

  queryText.value = config.query || '';
  retrievalModel.value = config.retrievalModel || 'hybrid';
  topK.value = typeof config.topK === 'number' ? config.topK : 5;
  scoreThreshold.value = typeof config.score === 'number' ? config.score : 0.60;
  maxTokenBudget.value = typeof config.maxTokenBudget === 'number' ? config.maxTokenBudget : 2048;
  contextualEnrichment.value = config.contextualEnrichment !== false;
  hypersphericalModulation.value = config.hypersphericalModulation !== false;
  alphaWeight.value = typeof config.alpha === 'number' ? config.alpha : 0.35;
  singleTimeoutMs.value = typeof config.singleTimeoutMs === 'number' ? config.singleTimeoutMs : 2000;
  globalTimeoutMs.value = typeof config.globalTimeoutMs === 'number' ? config.globalTimeoutMs : 3000;
};

watch(() => props.node, syncFromNode, { immediate: true, deep: true });

onMounted(() => {
  fetchKbOptions();
});

const handleKbIdsChange = (val) => {
  updateConfigField('knowledgeBaseIds', val);
  // 同时同步单库兼容字段 (取第一个)
  if (val && val.length > 0) {
    updateConfigField('knowledgeBaseId', val[0]);
  } else {
    updateConfigField('knowledgeBaseId', null);
  }
};

const updateConfigField = (key, val) => {
  emit('updateConfig', { [key]: val });
};

const emitField = (field, value) => {
  emit('updateField', { field, value });
};
</script>

<style scoped>
.knowledge-config-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
}

.knowledge-config-section {
  display: flex;
  flex-direction: column;
}

.node-config-section-title {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 590;
  color: #1e293b;
  margin-bottom: 4px;
}

.blue-bar {
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #3b82f6;
  border-radius: 2px;
  margin-right: 8px;
}

.purple-bar {
  display: inline-block;
  width: 3px;
  height: 14px;
  background: #8b5cf6;
  border-radius: 2px;
  margin-right: 8px;
}

.knowledge-config-tip {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.knowledge-kb-select {
  width: 100%;
}

.kb-option-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.kb-option-name {
  font-weight: 500;
}

.kb-option-id {
  font-size: 11px;
  color: #94a3b8;
}

.knowledge-form-item {
  display: flex;
  flex-direction: column;
}

.knowledge-item-label {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 4px;
}

.knowledge-item-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.knowledge-item-value {
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
}

.badge-token {
  background: rgba(59, 130, 246, 0.1);
  padding: 2px 8px;
  border-radius: 12px;
  color: #2563eb;
}

.knowledge-subtip {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
  margin-bottom: 6px;
}

.knowledge-advanced-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  padding: 6px 0;
  user-select: none;
}

.toggle-icon {
  font-size: 12px;
  color: #94a3b8;
  transition: transform 0.25s ease;
}

.toggle-icon.is-expanded {
  transform: rotate(90deg);
}

.knowledge-advanced-card {
  background: rgba(248, 250, 252, 0.7);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 14px;
  margin-top: 6px;
}

.advanced-item {
  display: flex;
  flex-direction: column;
}

.advanced-switch-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.advanced-switch-title {
  font-size: 13px;
  font-weight: 590;
  color: #1e293b;
}

.timeout-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.timeout-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.timeout-label {
  font-size: 11px;
  color: #64748b;
}

.mt6 { margin-top: 6px; }
.mt8 { margin-top: 8px; }
.mt10 { margin-top: 10px; }
.mt12 { margin-top: 12px; }
</style>
