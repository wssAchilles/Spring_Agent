<template>
  <div class="glass-skeleton-wrapper" :class="[`type-${type}`, customClass]">
    <!-- 卡片网格骨架屏 (1:1 几何孪生) -->
    <div v-if="type === 'card'" class="skeleton-card-grid" :style="gridStyle">
      <div v-for="n in count" :key="n" class="skeleton-card-item">
        <div class="skeleton-card-header">
          <div class="skeleton-avatar shimmer"></div>
          <div class="skeleton-header-meta">
            <div class="skeleton-line title shimmer"></div>
            <div class="skeleton-line subtitle shimmer"></div>
          </div>
        </div>
        <div class="skeleton-card-body">
          <div class="skeleton-line text shimmer" style="width: 90%;"></div>
          <div class="skeleton-line text shimmer" style="width: 75%;"></div>
          <div class="skeleton-line text shimmer" style="width: 60%;"></div>
        </div>
        <div class="skeleton-card-footer">
          <div class="skeleton-badge shimmer"></div>
          <div class="skeleton-line timestamp shimmer"></div>
        </div>
      </div>
    </div>

    <!-- 表格骨架屏 -->
    <div v-else-if="type === 'table'" class="skeleton-table-container">
      <div class="skeleton-table-header">
        <div v-for="col in columns" :key="col" class="skeleton-th shimmer"></div>
      </div>
      <div class="skeleton-table-body">
        <div v-for="row in count" :key="row" class="skeleton-tr">
          <div v-for="col in columns" :key="col" class="skeleton-td shimmer"></div>
        </div>
      </div>
    </div>

    <!-- 列表/自定义骨架屏 -->
    <div v-else class="skeleton-list-container">
      <div v-for="n in count" :key="n" class="skeleton-list-item">
        <div class="skeleton-avatar shimmer" v-if="hasAvatar"></div>
        <div class="skeleton-list-content">
          <div class="skeleton-line title shimmer" style="width: 40%;"></div>
          <div class="skeleton-line text shimmer" style="width: 80%;"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'card', // 'card' | 'table' | 'list'
    validator: (v) => ['card', 'table', 'list'].includes(v)
  },
  count: {
    type: Number,
    default: 6
  },
  columns: {
    type: Number,
    default: 6
  },
  hasAvatar: {
    type: Boolean,
    default: true
  },
  minItemWidth: {
    type: String,
    default: '280px'
  },
  customClass: {
    type: String,
    default: ''
  }
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(auto-fill, minmax(${props.minItemWidth}, 1fr))`
}))
</script>

<style lang="scss" scoped>
.glass-skeleton-wrapper {
  width: 100%;
  animation: fadeInSkeleton 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes fadeInSkeleton {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 纯 CSS 硬件加速 Shimmer 流光动效
.shimmer {
  position: relative;
  overflow: hidden;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.5));
  contain: paint;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    transform: translateX(-100%);
    background: linear-gradient(
      90deg,
      transparent 0%,
      rgba(255, 255, 255, 0.4) 50%,
      transparent 100%
    );
    animation: skeleton-shimmer 1.8s infinite ease-in-out;
    will-change: transform;
  }
}

@keyframes skeleton-shimmer {
  100% {
    transform: translateX(100%);
  }
}

// 骨架几何形态
.skeleton-card-grid {
  display: grid;
  gap: 20px;
  width: 100%;
}

.skeleton-card-item {
  min-height: 220px;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.6));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.skeleton-card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.skeleton-avatar {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.skeleton-header-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 14px;
  border-radius: 6px;

  &.title {
    height: 16px;
    width: 65%;
  }

  &.subtitle {
    height: 12px;
    width: 40%;
  }

  &.text {
    height: 12px;
    margin-bottom: 8px;
  }

  &.timestamp {
    height: 10px;
    width: 30%;
  }
}

.skeleton-card-body {
  margin-bottom: 16px;
}

.skeleton-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.04));
}

.skeleton-badge {
  width: 50px;
  height: 20px;
  border-radius: 4px;
}

// 表格骨架屏形态
.skeleton-table-container {
  width: 100%;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.6));
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  overflow: hidden;
}

.skeleton-table-header {
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(0, 0, 0, 0.02);
  border-bottom: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
}

.skeleton-th {
  flex: 1;
  height: 16px;
  border-radius: 4px;
}

.skeleton-table-body {
  display: flex;
  flex-direction: column;
}

.skeleton-tr {
  display: flex;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.04));

  &:last-child {
    border-bottom: none;
  }
}

.skeleton-td {
  flex: 1;
  height: 14px;
  border-radius: 4px;
}

// 列表骨架屏
.skeleton-list-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.5));
  border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.06));
  border-radius: 8px;
}

.skeleton-list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

// 深色模式适配
@media (prefers-color-scheme: dark) {
  .shimmer::after {
    background: linear-gradient(
      90deg,
      transparent 0%,
      rgba(255, 255, 255, 0.08) 50%,
      transparent 100%
    );
  }
}

:global(html.dark) .shimmer::after,
:global(html[data-theme='dark']) .shimmer::after {
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.08) 50%,
    transparent 100%
  );
}
</style>
