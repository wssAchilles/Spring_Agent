<template>
  <div ref="contentRef" class="markdown-view markdown-body" >
    <!-- DeepSeek 深度思考卡片 -->
    <div v-if="streamResult.thinking" class="deep-thinking-card">
      <div class="thinking-header" @click="isThinkingExpanded = !isThinkingExpanded">
        <div class="thinking-status">
          <span class="pulse-indicator" v-if="!streamResult.isThinkingComplete"></span>
          <span class="status-dot" v-else></span>
          <span class="status-text">{{ streamResult.isThinkingComplete ? '已深度思考' : '正在思考中...' }}</span>
        </div>
        <span class="toggle-action">{{ isThinkingExpanded ? '收起' : '展开' }}</span>
      </div>
      <div v-show="isThinkingExpanded || !streamResult.isThinkingComplete" class="thinking-body">
        <div class="thinking-content">{{ streamResult.thinking }}</div>
      </div>
    </div>

    <!-- 对话输出 -->
    <div v-html="streamResult.html" class="markdown-output"></div>
    <!-- 文章引用 -->
    <div class="quote" v-if="documentIdList != null && documentIdList.length > 0">
      <el-divider content-position="left">引用</el-divider>
      <el-popover placement="top" :width="400" trigger="click" popper-class="popover"
                  popper-style="padding: 0;border-radius: 4px"
                >
        <template #reference>
          <div class="content" ref="quoteRef" v-for="(item, index) in documentIdList" @click="showDetail(item)">
            <img :src="getFileType(documentNameList[index])">
            <span>{{documentNameList[index]}}</span>
          </div>
        </template>
        <div class="title">
          <img :src="getFileType(title)">
          <span>{{title}}</span>
        </div>
        <div class="content-body">
          <div class="item" v-for="(item, index) in resourcesList">
            <div class="segment"># {{item.segmentPosition}}</div>
            <div class="content">
              {{item.content}}
            </div>
            <el-divider v-if="index + 1 < resourcesList.length" />
          </div>
        </div>
      </el-popover>
    </div>
  </div>
</template>

<script setup>
import { useClipboard } from '@vueuse/core'
import MarkdownIt from 'markdown-it'
import { sanitizeHtml } from '@/utils/markdownSanitize'
import { defaultStreamingEngine } from '@/utils/streaming-markdown-engine'
import 'highlight.js/styles/xcode.min.css'
import "@/assets/app/style/dify_table.css"
import hljs from 'highlight.js'
import {renderContent} from "@/utils/app/chat/chat.js";
import {listByMessage} from "@/api/retriever/resources";
import {getFileFormat} from "../../utils/app/chat/chat.js";
import word from "@/assets/app/office/WORD.png";
import excel from "@/assets/app/office/ECEL.png";
import pdf from "@/assets/app/office/PDF.png";
import ppt from "@/assets/app/office/PPT.png";
import tet from "@/assets/app/office/TET.png";

// 定义组件属性
const props = defineProps({
  messageId: {
    type: Number,
    required: false
  },
  content: {
    type: String,
    required: true
  },
  documentIdList: {
    type: Array,
    required: false,
    default: () => {
      return []
    }
  },
  documentNameList: {
    type: Array,
    required: false,
    default: () => {
      return []
    }
  }
})
const { proxy } = getCurrentInstance();
const message = proxy.$modal // 消息弹窗

const { copy } = useClipboard() // 初始化 copy 到粘贴板
const title = ref("")
const resourcesList = ref([])

const contentRef = ref()
const popoverRef = ref()
const quoteRef = ref()
const fileImg = {
  "doc": word,
  "docx": word,
  "xlsx": excel,
  "xls": excel,
  "ppt": ppt,
  "pptx": ppt,
  "pdf": pdf,
  "txt": tet
}

const md = new MarkdownIt({
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const copyHtml = `<div id="copy" data-copy='${str}' style="position: absolute; right: 10px; top: 5px; color: #fff;cursor: pointer;">复制</div>`
        return `<pre style="position: relative;">${copyHtml}<code class="hljs">${hljs.highlight(lang, str, true).value}</code></pre>`
      } catch (__) {}
    }
    return ``
  }
})

const showDetail = (id) => {
  listByMessage({
    messageId: props.messageId,
    qtDocumentId: id
  }).then(response => {
    title.value = response.data[0].qtDocumentName
    resourcesList.value = response.data;
    unref(popoverRef).popperRef?.delayHide?.()
  })
}

const getFileType = (name) => {
  return fileImg[getFileFormat(name)]
}

const isThinkingExpanded = ref(false)

/** 流式增量防闪烁与思考流统一渲染 **/
const streamResult = computed(() => {
  return defaultStreamingEngine.renderStream(props.content || '', false)
})

/** 初始化 **/
onMounted(async () => {
  // 添加 copy 监听
  contentRef.value.addEventListener('click', (e) => {
    if (e.target.id === 'copy') {
      copy(e.target?.dataset?.copy)
      message.msgSuccess('复制成功!')
    }
  })
})
</script>

<style lang="scss" scoped>
// 引入css

.markdown-view {
  font-family: PingFang SC;
  font-size: 0.95rem;
  font-weight: 400;
  line-height: 1.6rem;
  letter-spacing: 0em;
  text-align: left;
  color: #F0F0F6;
  max-width: 100%;
  contain: layout style;
  transform: translateZ(0);

  /* DeepSeek 深度思考折叠卡片 */
  .deep-thinking-card {
    margin-bottom: 14px;
    background: var(--glass-l1-bg, rgba(255, 255, 255, 0.05));
    border: 1px solid var(--glass-l1-border, rgba(255, 255, 255, 0.08));
    border-radius: 10px;
    overflow: hidden;
    font-size: 13px;
    color: var(--monochrome-text-secondary, #8A8F98);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    transition: all 0.2s ease;

    .thinking-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 14px;
      cursor: pointer;
      user-select: none;
      background: rgba(255, 255, 255, 0.02);

      &:hover {
        background: rgba(255, 255, 255, 0.05);
      }

      .thinking-status {
        display: flex;
        align-items: center;
        gap: 8px;

        .pulse-indicator {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background-color: #8E8E93;
          box-shadow: 0 0 8px rgba(142, 142, 147, 0.7);
          animation: thinking-pulse 1.4s infinite ease-in-out;
        }

        .status-dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          background-color: #8E8E93;
          opacity: 0.7;
        }

        .status-text {
          font-weight: 500;
        }
      }

      .toggle-action {
        font-size: 12px;
        opacity: 0.7;
      }
    }

    .thinking-body {
      padding: 10px 14px;
      border-top: 1px solid rgba(255, 255, 255, 0.04);
      white-space: pre-wrap;
      line-height: 1.55;
      max-height: 320px;
      overflow-y: auto;
      color: #A1A1A6;
      font-family: inherit;
    }
  }

  @keyframes thinking-pulse {
    0%, 100% {
      opacity: 0.35;
      transform: scale(0.88);
    }
    50% {
      opacity: 1;
      transform: scale(1.15);
    }
  }

  pre {
    position: relative;
  }

  pre code.hljs {
    width: auto;
  }

  code.hljs {
    border-radius: 6px;
    padding-top: 20px;
    width: auto;
    @media screen and (min-width: 1536px) {
      width: 960px;
    }

    @media screen and (max-width: 1536px) and (min-width: 1024px) {
      width: calc(100vw - 400px - 64px - 32px * 2);
    }

    @media screen and (max-width: 1024px) and (min-width: 768px) {
      width: calc(100vw - 32px * 2);
    }

    @media screen and (max-width: 768px) {
      width: calc(100vw - 16px * 2);
    }
  }

  p,
  code.hljs {
    margin-bottom: 16px;
  }

  p {
    //margin-bottom: 1rem !important;
    margin: 0;
    margin-bottom: 3px;
  }

  /* 标题通用格式 */
  h1,
  h2,
  h3,
  h4,
  h5,
  h6 {
    color: var(--color-G900);
    margin: 24px 0 8px;
    font-weight: 600;
  }

  h1 {
    font-size: 22px;
    line-height: 32px;
  }

  h2 {
    font-size: 20px;
    line-height: 30px;
  }

  h3 {
    font-size: 18px;
    line-height: 28px;
  }

  h4 {
    font-size: 16px;
    line-height: 26px;
  }

  h5 {
    font-size: 16px;
    line-height: 24px;
  }

  h6 {
    font-size: 16px;
    line-height: 24px;
  }

  /* 列表（有序，无序） */
  ul,
  ol {
    margin: 0 0 8px 0;
    padding: 0;
    font-size: 16px;
    line-height: 24px;
    color: #3b3e55; // var(--color-CG600);
  }

  li {
    margin: 4px 0 0 20px;
    margin-bottom: 1rem;
  }

  ol > li {
    list-style-type: decimal;
    margin-bottom: 1rem;
    // 表达式,修复有序列表序号展示不全的问题
    // &:nth-child(n + 10) {
    //     margin-left: 30px;
    // }

    // &:nth-child(n + 100) {
    //     margin-left: 30px;
    // }
  }

  ul > li {
    list-style-type: disc;
    font-size: 16px;
    line-height: 24px;
    margin-right: 11px;
    margin-bottom: 1rem;
    color: #3b3e55; // var(--color-G900);
  }

  ol ul,
  ol ul > li,
  ul ul,
  ul ul li {
    // list-style: circle;
    font-size: 16px;
    list-style: none;
    margin-left: 6px;
    margin-bottom: 1rem;
  }

  ul ul ul,
  ul ul ul li,
  ol ol,
  ol ol > li,
  ol ul ul,
  ol ul ul > li,
  ul ol,
  ul ol > li {
    list-style: square;
  }

  //  引用
  .quote {
    :deep(.el-divider__text) {
      background-color: #F0F0F6;
      padding: 0 10px;
    }
    .content {
      display: inline-flex;
      align-items: center;
      background: #fff;
      width: auto;
      padding: 5px;
      border-radius: 5px;
      cursor: pointer;
      img {
        width: 20px;
        margin-right: 5px;
      }
    }
  }
}
.popover {
  .title {
    display: flex;
    align-items: center;
    background-color: rgb(249, 250, 251);
    padding: 12px 16px 8px 16px;
    border-top-left-radius: 4px;
    border-top-right-radius: 4px;
    img {
      width: 20px;
      margin-right: 5px;
    }
    span {
      --tw-text-opacity: 1;
      color: rgb(52 64 84 / var(--tw-text-opacity));
      font-weight: 500;
      text-overflow: ellipsis;
      overflow: hidden;
      white-space: nowrap;
    }
  }
  .content-body {
    padding: 8px 16px 8px 16px;
    --tw-bg-opacity: 1;
    background-color: rgb(255 255 255 / var(--tw-bg-opacity));
    border-radius: 4px;
    overflow-y: auto;
    max-height: 450px;
    .item {
      .segment {
        justify-content: space-between;
        align-items: center;
        display: inline-flex;
        border-width: 1px;
        --tw-border-opacity: 1;
        border-color: rgb(234 236 240 / var(--tw-border-opacity));
        border-radius: 6px;
        height: 20px;
        --tw-text-opacity: 1;
        color: rgb(102 112 133 / var(--tw-text-opacity));
        font-weight: 500;
        font-size: 11px;
        border-style: solid;
        padding: 5px;
        margin-bottom: 5px;
      }
      .content {
        --tw-text-opacity: 1;
        color: rgb(29 41 57 / var(--tw-text-opacity));
        font-size: 13px;
        overflow-wrap: break-word;
      }
    }
  }
}
</style>
