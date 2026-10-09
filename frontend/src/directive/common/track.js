/**
 * v-track 用户行为轨迹与可观测性埋点指令
 * 支持参数形如: v-track="{ type: 'preview', documentId: id }"
 */

export default {
  mounted(el, binding) {
    const trackData = binding.value || {};
    const handler = (e) => {
      try {
        // 记录可观测性追踪事件
        const eventDetail = {
          type: trackData.type || 'click',
          documentId: trackData.documentId,
          timestamp: Date.now(),
          target: el.tagName,
          ...trackData
        };
        // 可以在需要时派发自定义事件或记录 trace
        el.dispatchEvent(new CustomEvent('analytics:track', { detail: eventDetail, bubbles: true }));
      } catch (err) {
        // 静默保护，严禁埋点影响主业务逻辑
      }
    };
    el._trackHandler = handler;
    el.addEventListener('click', handler);
  },
  unmounted(el) {
    if (el._trackHandler) {
      el.removeEventListener('click', el._trackHandler);
      delete el._trackHandler;
    }
  }
};
