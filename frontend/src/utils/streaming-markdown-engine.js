/**
 * 流式 Markdown 增量渲染与防闪烁治理引擎
 * 适用于 Vue 3 + markdown-it + Highlight.js
 * 核心特性：
 * 1. 未闭合代码块、数学公式与 HTML 标签栈的前置虚拟补全（消除 CLS 布局跳动）；
 * 2. 代码高亮 DJB2 哈希缓存（避免高频重复正则高亮消耗）；
 * 3. DeepSeek <think> 推理思考流结构化分离（支持微光状态与折叠卡片）。
 */
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

export class StreamingMarkdownEngine {
  constructor(options = {}) {
    this.codeCache = new Map()
    this.options = Object.assign({
      enableCache: true,
      maxCacheSize: 200
    }, options)

    this.md = new MarkdownIt({
      html: true,
      linkify: true,
      typographer: false,
      highlight: (str, lang) => this.renderHighlight(str, lang)
    })
  }

  /**
   * 字符串哈希算法 (DJB2)
   */
  hashString(str) {
    let hash = 5381
    for (let i = 0; i < str.length; i++) {
      hash = ((hash << 5) + hash) + str.charCodeAt(i)
      hash |= 0
    }
    return hash.toString(36)
  }

  /**
   * 代码高亮与缓存复用逻辑
   */
  renderHighlight(str, lang) {
    const cacheKey = `${lang || 'plain'}_${this.hashString(str)}`
    if (this.options.enableCache && this.codeCache.has(cacheKey)) {
      return this.codeCache.get(cacheKey)
    }

    let highlighted = ''
    if (lang && hljs.getLanguage(lang)) {
      try {
        highlighted = hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
      } catch (e) {
        highlighted = this.md.utils.escapeHtml(str)
      }
    } else {
      highlighted = this.md.utils.escapeHtml(str)
    }

    const copyBtn = `<div id="copy" data-copy="${encodeURIComponent(str)}" class="copy-code-action">复制</div>`
    const langBadge = `<span class="code-lang-badge">${lang || 'text'}</span>`
    const result = `<pre class="hljs-wrapper" style="position: relative;"><div class="code-block-header">${langBadge}${copyBtn}</div><code class="hljs language-${lang || 'text'}">${highlighted}</code></pre>`

    if (this.options.enableCache) {
      if (this.codeCache.size > this.options.maxCacheSize) {
        const firstKey = this.codeCache.keys().next().value
        this.codeCache.delete(firstKey)
      }
      this.codeCache.set(cacheKey, result)
    }
    return result
  }

  /**
   * 临时补全状态机：修复未闭合的代码块、公式与 HTML 标签栈
   */
  completeIncompleteMarkdown(rawText) {
    if (!rawText) return ''
    let text = rawText

    // 1. 代码块围栏补全 (``` 或 ~~~)
    const fenceRegex = /^([ \t]*)(`{3,}|~{3,})/gm
    let match
    let openFence = null
    while ((match = fenceRegex.exec(text)) !== null) {
      const fenceChar = match[2][0]
      const fenceLen = match[2].length
      if (!openFence) {
        openFence = { char: fenceChar, len: fenceLen }
      } else if (openFence.char === fenceChar && fenceLen >= openFence.len) {
        openFence = null // 配对闭合
      }
    }
    if (openFence) {
      text += `\n${openFence.char.repeat(openFence.len)}\n`
    }

    // 2. 数学公式块补全 ($$)
    const mathBlockCount = (text.match(/\$\$/g) || []).length
    if (mathBlockCount % 2 !== 0) {
      text += '\n$$\n'
    }

    // 3. 基础 HTML 标签栈补全
    const tagStack = []
    const htmlTagRegex = /<\/?([a-zA-Z0-9]+)(?:\s+[^>]*?)?(\/?)>/g
    const voidTags = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr'])

    let tagMatch
    while ((tagMatch = htmlTagRegex.exec(text)) !== null) {
      const [fullTag, tagName, selfClose] = tagMatch
      const lowerName = tagName.toLowerCase()
      if (voidTags.has(lowerName) || selfClose === '/') continue

      if (!fullTag.startsWith('</')) {
        tagStack.push(lowerName)
      } else {
        const lastIndex = tagStack.lastIndexOf(lowerName)
        if (lastIndex !== -1) {
          tagStack.splice(lastIndex, 1)
        }
      }
    }
    while (tagStack.length > 0) {
      const unclosed = tagStack.pop()
      text += `</${unclosed}>`
    }

    return text
  }

  /**
   * 解析 DeepSeek <think> 推理思考内容与正文的分离
   */
  parseDeepSeekThinking(rawContent) {
    if (!rawContent) {
      return { thinking: '', content: '', isThinkingComplete: true }
    }
    const startTag = '<think'
    const endTag = '</think>'
    const startIndex = rawContent.indexOf(startTag)

    if (startIndex === -1) {
      return { thinking: '', content: rawContent, isThinkingComplete: true }
    }

    // 找到闭合的 >
    const tagCloseIndex = rawContent.indexOf('>', startIndex)
    if (tagCloseIndex === -1) {
      return { thinking: '', content: '', isThinkingComplete: false }
    }

    const afterStart = rawContent.substring(tagCloseIndex + 1)
    const endIndex = afterStart.indexOf(endTag)

    if (endIndex === -1) {
      // 正在深度思考中
      return {
        thinking: afterStart.trim(),
        content: '',
        isThinkingComplete: false
      }
    } else {
      // 思考已完成，提取思考内容与后续正式回答
      return {
        thinking: afterStart.substring(0, endIndex).trim(),
        content: afterStart.substring(endIndex + endTag.length).trim(),
        isThinkingComplete: true
      }
    }
  }

  /**
   * 增量平滑渲染主入口
   */
  renderStream(rawText, isFinal = false) {
    const { thinking, content, isThinkingComplete } = this.parseDeepSeekThinking(rawText)

    const completedText = isFinal ? content : this.completeIncompleteMarkdown(content)
    const renderedHtml = completedText ? this.md.render(completedText) : ''
    const cleanHtml = DOMPurify.sanitize(renderedHtml, {
      USE_PROFILES: { html: true },
      ADD_TAGS: ['use', 'svg'],
      ADD_ATTR: ['target', 'data-copy', 'data-clipboard-text']
    })

    return {
      thinking,
      isThinkingComplete,
      html: cleanHtml
    }
  }
}

// 导出单例实例与类
export const defaultStreamingEngine = new StreamingMarkdownEngine()
export default StreamingMarkdownEngine
