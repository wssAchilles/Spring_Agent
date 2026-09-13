<template>
  <div class="glass-empty-container" :class="customClass">
    <div class="empty-illustration">
      <!-- 单色钛金极简矢量图标插画 -->
      <svg v-if="icon === 'search'" class="empty-svg" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="28" cy="28" r="18" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="3 3"/>
        <path d="M41 41L54 54" stroke="currentColor" stroke-width="3" stroke-linecap="round"/>
        <path d="M22 28H34" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.4"/>
      </svg>
      <svg v-else-if="icon === 'folder'" class="empty-svg" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M10 20C10 17.7909 11.7909 16 14 16H25.1716C26.2324 16 27.2497 16.4214 28 17.1716L32.8284 22H50C52.2091 22 54 23.7909 54 26V46C54 48.2091 52.2091 50 50 50H14C11.7909 50 10 48.2091 10 46V20Z" stroke="currentColor" stroke-width="2.5" stroke-linejoin="round"/>
        <line x1="20" y1="34" x2="44" y2="34" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
        <line x1="20" y1="40" x2="34" y2="40" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
      </svg>
      <svg v-else class="empty-svg" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="12" y="14" width="40" height="38" rx="8" stroke="currentColor" stroke-width="2.5"/>
        <line x1="20" y1="26" x2="36" y2="26" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.6"/>
        <line x1="20" y1="34" x2="44" y2="34" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
        <line x1="20" y1="42" x2="30" y2="42" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.3"/>
        <circle cx="43" cy="23" r="3" fill="currentColor" opacity="0.2"/>
      </svg>
    </div>

    <div class="empty-content">
      <h4 class="empty-title">{{ title }}</h4>
      <p class="empty-description">{{ description }}</p>
    </div>

    <div v-if="$slots.action" class="empty-action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: '未检索到相关内容，请尝试更换筛选条件或添加新项'
  },
  icon: {
    type: String,
    default: 'default', // 'default' | 'search' | 'folder'
    validator: (v) => ['default', 'search', 'folder'].includes(v)
  },
  customClass: {
    type: String,
    default: ''
  }
})
</script>

<style lang="scss" scoped>
.glass-empty-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.4));
  border: 1px dashed var(--glass-card-border, rgba(0, 0, 0, 0.08));
  border-radius: 16px;
  margin: 20px 0;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.empty-illustration {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: rgba(0, 0, 0, 0.03);
  margin-bottom: 18px;
  color: #71717a;
  transition: transform 0.3s ease;

  .empty-svg {
    width: 42px;
    height: 42px;
  }
}

.glass-empty-container:hover .empty-illustration {
  transform: translateY(-2px) scale(1.04);
}

.empty-content {
  max-width: 420px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--glass-text-primary, #18181b);
  margin: 0 0 8px 0;
  letter-spacing: -0.01em;
}

.empty-description {
  font-size: 13px;
  color: var(--glass-text-secondary, #71717a);
  line-height: 1.6;
  margin: 0;
}

.empty-action {
  margin-top: 20px;
}

// 深色模式适配
@media (prefers-color-scheme: dark) {
  .empty-illustration {
    background: rgba(255, 255, 255, 0.04);
    color: #a1a1aa;
  }
}

:global(html.dark) {
  .empty-illustration {
    background: rgba(255, 255, 255, 0.04);
    color: #a1a1aa;
  }
}

:global(html[data-theme='dark']) {
  .empty-illustration {
    background: rgba(255, 255, 255, 0.04);
    color: #a1a1aa;
  }
}
</style>
