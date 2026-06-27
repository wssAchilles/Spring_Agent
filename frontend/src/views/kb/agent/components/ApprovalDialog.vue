<template>
  <el-dialog v-model="visible" title="工作流审批" width="480px" :close-on-click-modal="false">
    <div v-if="currentApproval" class="approval-content">
      <div class="approval-info">
        <el-icon :size="32" color="#f59e0b"><WarningFilled /></el-icon>
        <div class="approval-text">
          <h4>需要人工审批</h4>
          <p>工作流执行到审批节点，需要您的确认才能继续。</p>
        </div>
      </div>

      <el-descriptions :column="1" border size="small" style="margin-top: 16px;">
        <el-descriptions-item label="工作流 ID">{{ currentApproval.flowId }}</el-descriptions-item>
        <el-descriptions-item label="节点 ID">{{ currentApproval.nodeId }}</el-descriptions-item>
        <el-descriptions-item label="请求 ID">{{ currentApproval.requestId }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="showRejectReason" style="margin-top: 12px;">
        <el-input v-model="rejectReason" type="textarea" :rows="2" placeholder="请输入拒绝原因（可选）" />
      </div>
    </div>

    <template #footer>
      <div class="approval-footer">
        <el-button v-if="!showRejectReason" type="danger" plain @click="showRejectReason = true">
          拒绝
        </el-button>
        <el-button v-else type="danger" @click="handleReject" :loading="loading">
          确认拒绝
        </el-button>
        <el-button type="primary" @click="handleApprove" :loading="loading">
          通过
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { WarningFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

const props = defineProps({
  modelValue: Boolean,
  approval: Object,
});
const emit = defineEmits(['update:modelValue', 'approved', 'rejected']);

const visible = ref(false);
const loading = ref(false);
const showRejectReason = ref(false);
const rejectReason = ref('');
const currentApproval = ref(null);

watch(() => props.modelValue, (val) => {
  visible.value = val;
  if (val && props.approval) {
    currentApproval.value = props.approval;
    showRejectReason.value = false;
    rejectReason.value = '';
  }
});

watch(visible, (val) => {
  emit('update:modelValue', val);
});

async function handleApprove() {
  if (!currentApproval.value) return;
  loading.value = true;
  try {
    await request({
      url: '/hermes/approval/approve',
      method: 'post',
      data: currentApproval.value,
    });
    ElMessage.success('审批通过');
    emit('approved', currentApproval.value);
    visible.value = false;
  } catch (e) {
    ElMessage.error('审批失败: ' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
}

async function handleReject() {
  if (!currentApproval.value) return;
  loading.value = true;
  try {
    await request({
      url: '/hermes/approval/reject',
      method: 'post',
      data: {
        ...currentApproval.value,
        reason: rejectReason.value || '人工拒绝',
      },
    });
    ElMessage.success('已拒绝');
    emit('rejected', currentApproval.value);
    visible.value = false;
  } catch (e) {
    ElMessage.error('拒绝失败: ' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
}

// 暴露方法供父组件调用
defineExpose({ show: (approval) => {
  currentApproval.value = approval;
  visible.value = true;
}});
</script>

<style scoped>
.approval-content {
  .approval-info {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }
  .approval-text {
    h4 { margin: 0 0 4px; font-size: 16px; color: #303133; }
    p { margin: 0; font-size: 13px; color: #606266; }
  }
}
.approval-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
