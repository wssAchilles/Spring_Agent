import DOMPurify from 'dompurify'

/**
 * v-safe-html
 * 使用 DOMPurify 对 HTML 字符串进行严格的白名单过滤，防范 XSS 攻击
 */
const config = {
  // 仅放行必要的样式和格式标签
  ALLOWED_TAGS: [
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 
    'p', 'br', 'hr', 'blockquote',
    'b', 'i', 'em', 'strong', 'u', 's',
    'a', 'ul', 'ol', 'li', 'span', 'div',
    'code', 'pre',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'img', 'figure', 'figcaption'
  ],
  // 限制允许的属性
  ALLOWED_ATTR: [
    'href', 'target', 'class', 'style', 
    'title', 'alt', 'src', 'width', 'height'
  ]
};

export default {
  mounted(el, binding) {
    el.innerHTML = DOMPurify.sanitize(binding.value || '', config)
  },
  updated(el, binding) {
    if (binding.value !== binding.oldValue) {
      el.innerHTML = DOMPurify.sanitize(binding.value || '', config)
    }
  }
}
