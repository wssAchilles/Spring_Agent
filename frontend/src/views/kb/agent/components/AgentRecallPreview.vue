<template>
  <el-drawer v-model="visible" title="知识库召回预览" size="600px" @close="handleClose">
    <div style="padding: 0 20px; height: 100%; display: flex; flex-direction: column;">
      <el-input v-model="query" placeholder="请输入测试问题..." type="textarea" :rows="4" />
      <el-button type="primary" style="margin-top: 15px; align-self: flex-start;" @click="handleTest" :loading="loading" :disabled="!query">测试召回</el-button>
      
      <div v-if="hasTested" style="margin-top: 20px; flex: 1; overflow-y: auto;">
        <el-tabs v-model="activeTab" v-if="results.length > 0">
          <el-tab-pane v-for="res in results" :key="res.kbId" :label="res.kbName" :name="res.kbId.toString()">
            <el-card v-for="item in res.list" :key="item.id" style="margin-bottom: 15px;">
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="font-weight: 500; font-size: 14px;">{{ item.documentName }}</span>
                  <div>
                    <el-tag size="small" v-if="item.source" type="info" style="margin-right: 10px;">{{ getSourceText(item.source) }}</el-tag>
                    <el-tag size="small" type="success">得分: {{ item.score.toFixed(2) }}</el-tag>
                  </div>
                </div>
              </template>
              <div class="markdown-body" v-html="renderedMarkdown(item.content)" style="font-size: 13px;"></div>
            </el-card>
            <el-empty v-if="res.list.length === 0" description="该知识库未召回内容" />
            
            <div v-if="res.debugInfo" style="margin-top: 15px; padding-top: 15px; border-top: 1px solid #eee;">
              <h4 style="margin-top: 0; color: #606266; font-size: 13px;">调试信息</h4>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="耗时">{{ res.debugInfo.elapsedMs }}ms</el-descriptions-item>
                <el-descriptions-item label="检索模式">{{ res.debugInfo.searchMethod }}</el-descriptions-item>
                <el-descriptions-item label="向量召回">{{ res.debugInfo.vectorResultCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="关键字召回">{{ res.debugInfo.keywordResultCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="元数据召回">{{ res.debugInfo.metadataResultCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="融合后结果">{{ res.debugInfo.fusedCount || 0 }}</el-descriptions-item>
                <el-descriptions-item label="重排序结果">{{ res.debugInfo.rerankedCount || 0 }}</el-descriptions-item>
              </el-descriptions>
              <div v-if="res.contextPreview" style="margin-top: 15px;">
                <h4 style="color: #606266; font-size: 13px; margin-bottom: 10px;">最终注入上下文预览</h4>
                <el-input type="textarea" :rows="6" readonly v-model="res.contextPreview" />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else description="无知识库数据" />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue';
import MarkdownIt from "markdown-it";
import "highlight.js/styles/xcode.min.css";
import { recallDebug } from "@/api/kmc/knowledgeBase/knowledgeBase.js";

const props = defineProps({
  knowledges: {
    type: Array,
    default: () => []
  }
});

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
});

const visible = ref(false);
const query = ref('');
const loading = ref(false);
const hasTested = ref(false);
const results = ref([]);
const activeTab = ref('');

const open = () => {
  visible.value = true;
  if (props.knowledges.length > 0) {
    activeTab.value = props.knowledges[0].id.toString();
  }
};

const handleClose = () => {
  query.value = '';
  hasTested.value = false;
  results.value = [];
};

const getSourceText = (source) => {
  const map = {
    'vector': '向量检索',
    'keyword': '关键字检索',
    'metadata': '元数据检索',
    'rerank': 'Rerank',
    'agent': 'Agent召回',
    'cache': '缓存'
  };
  return map[source] || source;
};

const renderedMarkdown = (content) => {
  if (!content) return '';
  return md.render(content);
};

const handleTest = async () => {
  if (!query.value || props.knowledges.length === 0) return;
  loading.value = true;
  hasTested.value = true;
  results.value = [];
  
  try {
    const promises = props.knowledges.map(kb => {
      // 构造知识库参数
      const params = {
        id: kb.id,
        query: query.value
      };
      return recallDebug(params).then(res => {
        return {
          kbId: kb.id,
          kbName: kb.name,
          list: res.data.results || [],
          debugInfo: res.data.debugInfo || {},
          contextPreview: res.data.contextPreview || ''
        };
      }).catch(err => {
        return {
          kbId: kb.id,
          kbName: kb.name,
          list: [],
          debugInfo: null,
          contextPreview: ''
        };
      });
    });
    
    results.value = await Promise.all(promises);
    if (results.value.length > 0) {
      activeTab.value = results.value[0].kbId.toString();
    }
  } finally {
    loading.value = false;
  }
};

defineExpose({
  open
});
</script>

<style scoped>
:deep(.markdown-body p) {
  margin-top: 0;
  margin-bottom: 8px;
}
:deep(.markdown-body pre) {
  margin: 10px 0;
}
</style>
