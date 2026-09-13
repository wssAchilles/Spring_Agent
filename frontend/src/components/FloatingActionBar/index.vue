<template>
  <transition name="floating-bar-spring">
    <div v-if="visible && count > 0" class="floating-action-bar-container">
      <div class="floating-action-bar">
        <div class="selection-info">
          <div class="selection-badge">
            <span class="count-num">{{ count }}</span>
          </div>
          <span class="selection-label">已选择 <strong class="highlight-count">{{ count }}</strong> 项内容</span>
          <el-button
            link
            class="btn-clear-selection"
            @click="handleClear"
          >
            清空选择
          </el-button>
        </div>

        <div class="actions-group">
          <slot name="actions">
            <!-- 默认操作插槽 -->
          </slot>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
const props = defineProps({
  count: {
    type: Number,
    default: 0
  },
  visible: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['clear'])

function handleClear() {
  emit('clear')
}
</script>

<style lang="scss" scoped>
.floating-action-bar-container {
  position: fixed;
  bottom: 32px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
  pointer-events: none; // 仅内部 bar 接收点击
}

.floating-action-bar {
  pointer-events: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-width: 480px;
  max-width: 860px;
  padding: 12px 24px;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.82));
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.12));
  border-radius: 40px;
  box-shadow: 0 16px 36px -8px rgba(0, 0, 0, 0.16), 0 4px 12px -2px rgba(0, 0, 0, 0.08);
  contain: layout style;
}

.selection-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selection-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: 12px;
  background: #18181b;
  color: #fafafa;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.selection-label {
  font-size: 13px;
  color: var(--glass-text-secondary, #52525b);

  .highlight-count {
    color: var(--glass-text-primary, #09090b);
    font-weight: 600;
  }
}

.btn-clear-selection {
  font-size: 12px;
  color: #71717a;
  padding: 0 4px;
  margin-left: 4px;

  &:hover {
    color: #18181b;
  }
}

.actions-group {
  display: flex;
  align-items: center;
  gap: 10px;

  :deep(.el-button) {
    border-radius: 20px;
    font-size: 13px;
    font-weight: 500;
    transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

    &:hover {
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(1px);
    }
  }
}

// 弹性阻尼物理动画 (Spring Dynamics)
.floating-bar-spring-enter-active {
  animation: floatingSpringIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.floating-bar-spring-leave-active {
  animation: floatingSpringOut 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes floatingSpringIn {
  from {
    opacity: 0;
    transform: translateY(40px) scale(0.92);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes floatingSpringOut {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
}

// 深色模式适配
@media (prefers-color-scheme: dark) {
  .selection-badge {
    background: #f4f4f5;
    color: #18181b;
  }
  .btn-clear-selection:hover {
    color: #f4f4f5;
  }
}

:global(html.dark) {
  .selection-badge {
    background: #f4f4f5;
    color: #18181b;
  }
  .btn-clear-selection:hover {
    color: #f4f4f5;
  }
}

:global(html[data-theme='dark']) {
  .selection-badge {
    background: #f4f4f5;
    color: #18181b;
  }
  .btn-clear-selection:hover {
    color: #f4f4f5;
  }
}
</style>
