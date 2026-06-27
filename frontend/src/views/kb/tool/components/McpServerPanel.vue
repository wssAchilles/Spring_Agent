<template>
  <el-card class="mcp-server-panel" v-loading="loading">
    <template #header>
      <div class="panel-header">
        <el-icon><Connection /></el-icon>
        <span>MCP Server 状态</span>
        <el-button size="small" plain @click="refresh" style="margin-left: auto">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-if="servers.length === 0" class="empty-state">
      <el-empty description="暂无已注册的 MCP Server" :image-size="60" />
    </div>

    <div v-else class="server-list">
      <div v-for="server in servers" :key="server.name" class="server-item">
        <div class="server-header">
          <el-badge :type="server.connected ? 'success' : 'danger'" is-dot>
            <el-icon :size="20"><Connection /></el-icon>
          </el-badge>
          <span class="server-name">{{ server.name }}</span>
          <el-tag size="small" :type="server.connected ? 'success' : 'danger'">
            {{ server.connected ? '已连接' : '未连接' }}
          </el-tag>
          <el-tag v-if="server.url" size="small" type="info" style="margin-left: 4px">HTTP</el-tag>
          <el-tag v-else-if="server.command" size="small" type="warning" style="margin-left: 4px">Stdio</el-tag>
        </div>
        <div class="server-detail">
          <span v-if="server.url" class="server-url">{{ server.url }}</span>
          <span v-else-if="server.command" class="server-url">{{ server.command }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Connection, Refresh } from '@element-plus/icons-vue';
import request from '@/utils/request';

const loading = ref(false);
const servers = ref([]);

async function refresh() {
  loading.value = true;
  try {
    const res = await request({ url: '/kb/health', method: 'get' });
    servers.value = res.data?.mcp?.details || [];
  } catch (e) {
    console.error('Failed to fetch MCP status', e);
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
</script>

<style scoped lang="scss">
.mcp-server-panel {
  .panel-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
    font-size: 14px;
  }

  .server-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .server-item {
    padding: 10px 12px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    background: #fafafa;

    .server-header {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .server-name {
      font-weight: 500;
      font-size: 13px;
      flex: 1;
    }

    .server-detail {
      margin-top: 4px;
      padding-left: 28px;

      .server-url {
        font-size: 12px;
        color: #6b7280;
        font-family: monospace;
      }
    }
  }
}
</style>
