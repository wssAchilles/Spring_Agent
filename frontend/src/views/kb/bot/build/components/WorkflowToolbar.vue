<template>
  <div class="toolbar workflow-toolbar">
    <div class="toolbar-left">
      <span class="toolbar-title">
        <span class="title">{{ flowName || "-" }}</span>
        <el-tag v-if="workflowType !== null" class="type-tag" effect="dark">
          {{
            (workflowType == 1 && "Chatflow") ||
            (workflowType == 0 && "工作流") ||
            "Agent"
          }}
        </el-tag>
        <span v-if="flowBuiltinFlag == 1" class="internally">
          <el-icon class="desc-icon"><WarningFilled /></el-icon>
          此Bot为系统内置，不可修改！如需修改请复制新Bot。
        </span>
      </span>
    </div>

    <div class="toolbar-actions">
      <el-button
        v-ripple
        class="glass-btn custom-btn-padding"
        plain
        title="自动排版拓扑节点"
        @click="$emit('auto-layout')"
        @mousedown.prevent
      >
        <el-icon><Compass /></el-icon>
        整齐排版
      </el-button>

      <el-button
        v-ripple
        class="glass-btn custom-btn-padding"
        plain
        title="自适应缩放到适中视口"
        @click="$emit('fit-view')"
        @mousedown.prevent
      >
        <el-icon><Aim /></el-icon>
        自适应
      </el-button>

      <el-button
        v-ripple
        class="glass-btn custom-btn-padding"
        plain
        title="复制当前 Bot"
        @click="$emit('copy-bot')"
        @mousedown.prevent
      >
        <el-icon><CopyDocument /></el-icon>
        复制
      </el-button>

      <el-button
        v-ripple
        class="glass-btn custom-btn-padding"
        plain
        title="切换虚拟化因果 DAG 画布"
        @click="$emit('open-virtualized')"
        @mousedown.prevent
      >
        <el-icon><DataAnalysis /></el-icon>
        虚拟化引擎
      </el-button>

      <el-button
        v-ripple
        class="fhbtn glass-btn custom-btn-padding"
        plain
        title="返回列表"
        @click="$emit('go-back')"
      >
        <el-icon><Back /></el-icon>
        返回
      </el-button>

      <el-button
        v-ripple
        class="glass-btn debug-btn custom-btn-padding"
        title="打开调试控制台与流式执行"
        @click="$emit('open-debug')"
      >
        <el-icon><VideoPlay /></el-icon>
        调试
      </el-button>

      <el-button
        v-ripple
        type="primary"
        class="glass-btn save-btn custom-btn-padding"
        :disabled="flowBuiltinFlag == 1"
        :loading="saving"
        title="保存工作流到数据库"
        @click="$emit('save-flow')"
      >
        <el-icon><Check /></el-icon>
        保存
      </el-button>
    </div>
  </div>
</template>

<script setup>
import {
  WarningFilled,
  Compass,
  Aim,
  CopyDocument,
  DataAnalysis,
  Back,
  VideoPlay,
  Check,
} from "@element-plus/icons-vue";

defineProps({
  flowName: {
    type: String,
    default: "",
  },
  workflowType: {
    type: [Number, String],
    default: null,
  },
  flowBuiltinFlag: {
    type: [Number, String],
    default: 0,
  },
  saving: {
    type: Boolean,
    default: false,
  },
});

defineEmits([
  "auto-layout",
  "fit-view",
  "copy-bot",
  "open-virtualized",
  "go-back",
  "open-debug",
  "save-flow",
]);
</script>

<style scoped lang="scss">
.workflow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: rgba(15, 23, 42, 0.72);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
  user-select: none;
  min-height: 54px;

  .toolbar-left {
    display: flex;
    align-items: center;

    .toolbar-title {
      display: flex;
      align-items: center;
      gap: 10px;

      .title {
        font-size: 15px;
        font-weight: 700;
        color: #f8fafc;
        letter-spacing: 0.3px;
      }

      .type-tag {
        font-size: 11px;
        border-radius: 6px;
        background: rgba(99, 102, 241, 0.25);
        border: 1px solid rgba(99, 102, 241, 0.4);
        color: #c7d2fe;
      }

      .internally {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: #fbbf24;
        background: rgba(245, 158, 11, 0.12);
        padding: 2px 8px;
        border-radius: 6px;
        border: 1px solid rgba(245, 158, 11, 0.2);

        .desc-icon {
          font-size: 13px;
        }
      }
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;

    .glass-btn {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.12);
      color: #e2e8f0;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 500;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

      &:hover {
        background: rgba(255, 255, 255, 0.14);
        border-color: rgba(255, 255, 255, 0.25);
        color: #fff;
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
      }

      &:active {
        transform: translateY(0);
      }

      &.debug-btn {
        background: rgba(34, 197, 94, 0.15);
        border-color: rgba(34, 197, 94, 0.35);
        color: #86efac;

        &:hover {
          background: rgba(34, 197, 94, 0.25);
          border-color: #22c55e;
          box-shadow: 0 0 14px rgba(34, 197, 94, 0.3);
        }
      }

      &.save-btn {
        background: rgba(99, 102, 241, 0.85);
        border-color: #6366f1;
        color: #fff;
        font-weight: 600;

        &:hover {
          background: #4f46e5;
          box-shadow: 0 0 16px rgba(99, 102, 241, 0.45);
        }
      }
    }
  }
}
</style>
