# Apple iOS 26 弹性动效与微交互实现指南 (Spring Motion Guide)

> ⚠️ **本文档中的数值没有 Figma 出处。**
> 该设计系统的 4 个源页面（Symbols / Colors / Materials / Text Styles）是符号定义页，
> **不含原型与动效数据**，因此本文的弹簧参数、时长、缓动曲线均为设计假设，而非提取值。
> 作为工程参考可用，但**不要对外声称是"从 Figma 提取的 Apple 原生曲线"**。
> 总纲见 `00_MASTER_frontend_guide.md` §3.8。

## 一、物理动效模型与数学推导

Apple 原生动效的核心哲学是 **连续流体物理感 (Continuous Fluid Physics)**。在 iOS 26 中，动效不再依赖机械的“线性加减速”，而是严格基于 **二阶欠阻尼质量-弹簧系统 (Second-Order Underdamped Mass-Spring-Damper System)**：

$$m \ddot{x}(t) + c \dot{x}(t) + k x(t) = 0$$

其中：
- 固有频率 $\omega_n = \sqrt{k / m}$
- 阻尼比 $\zeta = \frac{c}{2 \sqrt{m k}} \approx 0.82 \sim 0.88$（略低于临界阻尼 1.0，产生极致细腻的轻微回弹而绝无晃荡感）
- 响应特征时间 $\tau = \frac{2\pi}{\omega_n} \approx 0.32s \sim 0.40s$

---

## 二、三种核心贝塞尔曲线定义

为了在纯 CSS 和 Web 动画中完美拟合上述物理系统，定义了 3 组不同能量级别的贝塞尔曲线：

```css
:root {
  /* 1. 标准弹簧 (Standard Spring) - 适用于普通视图转场、侧边栏折叠展开、卡片移动 */
  --ios26-spring-standard: cubic-bezier(0.25, 1, 0.33, 1);
  --ios26-duration-normal: 320ms;

  /* 2. 灵敏弹簧 (Snappy Spring) - 适用于按钮点击、开关 Toggle、选项卡切换 */
  --ios26-spring-snappy: cubic-bezier(0.18, 0.89, 0.32, 1.15);
  --ios26-duration-fast: 200ms;

  /* 3. 弹性浮窗 (Bouncy Spring) - 适用于底部浮出 Sheet、模态对话框、通知气泡 */
  --ios26-spring-bouncy: cubic-bezier(0.34, 1.35, 0.45, 1);
  --ios26-duration-slow: 450ms;
}
```

---

## 三、微交互 CSS 关键帧与实现代码

### 1. 模态弹窗 / 浮层上拉弹性入场 (Sheet Spring Enter)
```scss
@keyframes ios26-sheet-spring-in {
  0% {
    opacity: 0;
    transform: translateY(40px) scale(0.96);
  }
  65% {
    opacity: 1;
    transform: translateY(-4px) scale(1.006); // 柔和回弹溢出
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1.0);
  }
}

.ios26-sheet-enter-active {
  animation: ios26-sheet-spring-in var(--ios26-duration-slow) var(--ios26-spring-bouncy) forwards;
}
```

---

### 2. 按钮触感微缩放反馈 (Tactile Scale Feedback)
```scss
@mixin ios26-tactile-press {
  transform: scale(1.0);
  transition: transform var(--ios26-duration-fast) var(--ios26-spring-snappy),
              filter var(--ios26-duration-fast) ease;
  will-change: transform;

  &:active {
    transform: scale(0.96);
    filter: brightness(0.97);
  }
}

.interactive-element {
  @include ios26-tactile-press;
}
```

---

### 3. 下拉面板与侧边栏平滑流体展开
```scss
.ios26-collapse-transition {
  transition: height var(--ios26-duration-normal) var(--ios26-spring-standard),
              opacity var(--ios26-duration-normal) ease;
}
```

---

## 四、Vue 3 动画组件封装实践

为便于在前端 Vue 3 项目中复用该动效，可直接封装 `<Transition>` 组件：

```vue
<!-- IosSpringTransition.vue -->
<template>
  <Transition
    name="ios-spring"
    @enter="onEnter"
    @leave="onLeave"
  >
    <slot />
  </Transition>
</template>

<script setup>
const onEnter = (el, done) => {
  el.animate([
    { opacity: 0, transform: 'scale(0.95) translateY(16px)' },
    { opacity: 1, transform: 'scale(1) translateY(0)' }
  ], {
    duration: 320,
    easing: 'cubic-bezier(0.25, 1, 0.33, 1)',
    fill: 'forwards'
  }).onfinish = done;
};

const onLeave = (el, done) => {
  el.animate([
    { opacity: 1, transform: 'scale(1) translateY(0)' },
    { opacity: 0, transform: 'scale(0.97) translateY(8px)' }
  ], {
    duration: 200,
    easing: 'cubic-bezier(0.25, 1, 0.33, 1)',
    fill: 'forwards'
  }).onfinish = done;
};
</script>

<style scoped>
.ios-spring-enter-active,
.ios-spring-leave-active {
  will-change: transform, opacity;
}
</style>
```
