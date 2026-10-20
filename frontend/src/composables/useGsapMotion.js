import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue';
import gsap from 'gsap';

/**
 * 顶级 Apple iOS 26 Liquid Glass 动效管理基座 (GSAP 3 Composable)
 *
 * 核心设计原则：
 * 1. 严格遵守无层叠上下文铁律：禁止在液态玻璃容器上遗留 transform、opacity 或 will-change，
 *    所有入场动效在 onComplete 强制调用 clearProps。
 * 2. 严格遵循 SPA 零内存泄漏规范：所有 Tween 与 Timeline 必须在 gsap.context() 托管沙盒中运行，
 *    在组件 unmount 时无损 revert() 释放 DOM 引用。
 * 3. 60fps 性能预算：杜绝在 rAF 循环中触发 Vue 响应式依赖收集，数值插值采用纯 JS 扁平对象。
 */

/**
 * 创建 GSAP 沙盒生命周期托管
 * @param {import('vue').Ref<HTMLElement | null>} scopeRef 作用域容器引用
 * @param {(context: gsap.Context) => void} setupFn 动画注册回调
 * @returns {{ ctx: import('vue').Ref<gsap.Context | null> }}
 */
export function useGsapContext(scopeRef, setupFn) {
  const ctx = ref(null);

  onMounted(() => {
    nextTick(() => {
      const scope = scopeRef?.value || undefined;
      ctx.value = gsap.context((self) => {
        if (typeof setupFn === 'function') {
          setupFn(self);
        }
      }, scope);
    });
  });

  onUnmounted(() => {
    if (ctx.value) {
      ctx.value.revert();
      ctx.value = null;
    }
  });

  return { ctx };
}

/**
 * 苹果液态玻璃单元素入场动画（带无层叠上下文 clearProps 保证）
 * @param {HTMLElement | string} target 目标 DOM 或选择器
 * @param {object} [options] 自定义参数
 * @returns {gsap.core.Tween}
 */
export function animateEnter(target, options = {}) {
  const {
    fromY = 12,
    fromScale = 0.96,
    duration = 0.36,
    ease = 'power2.out',
    delay = 0,
    onComplete: userOnComplete,
  } = options;

  return gsap.fromTo(
    target,
    {
      opacity: 0,
      y: fromY,
      scale: fromScale,
    },
    {
      opacity: 1,
      y: 0,
      scale: 1,
      duration,
      delay,
      ease,
      onComplete() {
        // 关键铁律：动画结束后彻底清除内联属性，避免成为 Isolated Group 污染 color-dodge
        gsap.set(target, { clearProps: 'transform,opacity,scale,y,willChange' });
        if (typeof userOnComplete === 'function') {
          userOnComplete();
        }
      },
    }
  );
}

/**
 * 列表/网格级联交错入场动画 (Stagger Enter)
 * @param {HTMLElement[] | string} targets 目标集合
 * @param {object} [options] 自定义参数
 * @returns {gsap.core.Tween}
 */
export function staggerEnter(targets, options = {}) {
  const {
    fromY = 16,
    fromScale = 0.95,
    duration = 0.38,
    stagger = 0.05,
    ease = 'power2.out',
    delay = 0,
    onComplete: userOnComplete,
  } = options;

  return gsap.fromTo(
    targets,
    {
      opacity: 0,
      y: fromY,
      scale: fromScale,
    },
    {
      opacity: 1,
      y: 0,
      scale: 1,
      duration,
      stagger,
      delay,
      ease,
      onComplete() {
        gsap.set(targets, { clearProps: 'transform,opacity,scale,y,willChange' });
        if (typeof userOnComplete === 'function') {
          userOnComplete();
        }
      },
    }
  );
}

/**
 * 纯 JS 对象驱动的高性能数字平滑滚动插值 (支持千分位与小数格式化)
 * @param {import('vue').Ref<number>} sourceValueRef 源响应式数字
 * @param {object} [options] 配置项
 * @returns {import('vue').Ref<string>} 格式化后的展示文本
 */
export function useNumberScroll(sourceValueRef, options = {}) {
  const {
    duration = 1.0,
    ease = 'power2.out',
    decimals = 0,
    thousandSeparator = true,
    prefix = '',
    suffix = '',
  } = options;

  const displayValue = ref(`${prefix}0${suffix}`);
  const tweenState = { value: 0 };
  let activeTween = null;

  function formatNumber(num) {
    const fixed = Number(num).toFixed(decimals);
    if (!thousandSeparator) return `${prefix}${fixed}${suffix}`;
    const parts = fixed.split('.');
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return `${prefix}${parts.join('.')}${suffix}`;
  }

  function startTween(toVal) {
    if (activeTween) {
      activeTween.kill();
    }
    const target = Number(toVal) || 0;
    activeTween = gsap.to(tweenState, {
      value: target,
      duration,
      ease,
      onUpdate() {
        displayValue.value = formatNumber(tweenState.value);
      },
      onComplete() {
        displayValue.value = formatNumber(target);
      },
    });
  }

  watch(
    sourceValueRef,
    (newVal) => {
      startTween(newVal);
    },
    { immediate: true }
  );

  onUnmounted(() => {
    if (activeTween) {
      activeTween.kill();
      activeTween = null;
    }
  });

  return displayValue;
}

/**
 * 苹果液态玻璃卡片安全呼吸光晕动效绑定
 * 遵守禁忌：不通过 transform 移动卡片本体，仅通过内层图标微动与外层光晕扩散
 * @param {import('vue').Ref<HTMLElement | null>} cardRef 卡片根节点引用
 * @param {import('vue').Ref<HTMLElement | null>} iconRef 内层图标引用
 */
export function bindCardGlowHover(cardRef, iconRef) {
  function handleEnter() {
    if (iconRef?.value) {
      gsap.to(iconRef.value, {
        scale: 1.12,
        duration: 0.25,
        ease: 'back.out(2)',
      });
    }
  }

  function handleLeave() {
    if (iconRef?.value) {
      gsap.to(iconRef.value, {
        scale: 1,
        duration: 0.22,
        ease: 'power2.out',
        onComplete() {
          gsap.set(iconRef.value, { clearProps: 'transform,scale' });
        },
      });
    }
  }

  return {
    handleEnter,
    handleLeave,
  };
}

export default {
  useGsapContext,
  animateEnter,
  staggerEnter,
  useNumberScroll,
  bindCardGlowHover,
};
