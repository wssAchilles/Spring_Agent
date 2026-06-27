<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>LLM 可观测性</span>
          <el-tag :type="langfuseEnabled ? 'success' : 'info'" size="small">
            {{ langfuseEnabled ? 'LangFuse 已启用' : 'LangFuse 未启用' }}
          </el-tag>
        </div>
      </template>

      <div v-if="!langfuseEnabled" class="setup-guide">
        <el-result icon="info" title="LangFuse 可观测性" sub-title="配置环境变量以启用 LLM 调用追踪和质量监控">
          <template #extra>
            <el-card shadow="never" style="max-width: 600px; margin: 0 auto;">
              <h4 style="margin-top: 0;">配置步骤</h4>
              <ol style="font-size: 14px; color: #606266; line-height: 2;">
                <li>注册 <el-link type="primary" href="https://langfuse.com" target="_blank">LangFuse</el-link> 账号</li>
                <li>创建项目，获取 Public Key 和 Secret Key</li>
                <li>在 <code>.env</code> 文件中添加：
                  <pre style="background: #f5f5f5; padding: 8px; border-radius: 4px; margin-top: 4px;">LANGFUSE_ENABLED=true
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...</pre>
                </li>
                <li>重启后端服务</li>
              </ol>
            </el-card>
          </template>
        </el-result>
      </div>

      <div v-else class="observability-dashboard">
        <el-row :gutter="16" style="margin-bottom: 20px;">
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="总对话数" :value="stats.totalTraces" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="平均延迟" :value="stats.avgLatency" suffix="ms" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="Token 消耗" :value="stats.totalTokens" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="Faithfulness" :value="stats.faithfulness" :precision="2" />
            </el-card>
          </el-col>
        </el-row>

        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          完整的链路追踪和评估数据请访问
          <el-link type="primary" :href="langfuseUrl" target="_blank">LangFuse Dashboard</el-link>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const langfuseEnabled = ref(false);
const langfuseUrl = ref('https://cloud.langfuse.com');
const stats = ref({
  totalTraces: 0,
  avgLatency: 0,
  totalTokens: 0,
  faithfulness: 0,
});

onMounted(() => {
  // 从环境变量或 API 检查 LangFuse 配置状态
  langfuseEnabled.value = import.meta.env.VITE_LANGFUSE_ENABLED === 'true';
  if (import.meta.env.VITE_LANGFUSE_BASE_URL) {
    langfuseUrl.value = import.meta.env.VITE_LANGFUSE_BASE_URL;
  }
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.setup-guide {
  padding: 20px 0;
}
.observability-dashboard {
  padding: 10px 0;
}
</style>
