<template>
  <div ref="messageContainer" class="h-100% overflow-y-auto relative">
    <el-empty description="在下方输入问题，看看 Bot 会如何回答" style="height: 100%;" v-if="list.length <= 0" />
    <div class="chat-list" v-for="(item, index) in list" :key="index">
      <!-- 靠左 message：system、assistant 类型 -->
      <div class="left-message message-item" v-if="item.type === 1">
        <div class="avatar">
          <el-avatar
              :src="roleAvatar"
              :size="46"
              style="background-color: transparent"
          />
        </div>
        <div class="message">
          <div>
            <el-text class="time">{{ parseTime(item.createTime) }}</el-text>
          </div>
          <div class="left-text-container">
            <MarkdownView
                class="left-text"
                ref="markdownViewRef"
                :messageId="item.id"
                :documentNameList="item.documentNameList"
                :content="item.content"
                :documentIdList="item.documentIdList"
            />
          </div>
<!--          <div class="left-btns">-->
<!--            <el-button class="btn-cus" link @click="copyContent(index)">-->
<!--              <img class="btn-image" src="@/assets/app/copy.png" />-->
<!--            </el-button>-->
<!--            <el-divider direction="vertical" class="btn-divider" />-->
<!--            <el-button-->
<!--                v-if="item.id > 0"-->
<!--                class="btn-cus"-->
<!--                link-->
<!--                @click="onDelete(item.id)"-->
<!--            >-->
<!--              <img class="btn-image h-17px" src="@/assets/app/delete.png" />-->
<!--            </el-button>-->
<!--          </div>-->
        </div>
      </div>
      <!-- 靠右 message：user 类型 -->
      <div class="right-message message-item" v-if="item.type === 0">
        <div class="avatar">
          <el-avatar :src="userAvatar" :size="46" />
        </div>
        <div class="message">
          <div>
            <el-text class="time">{{ parseTime(item.createTime) }}</el-text>
          </div>
          <div class="right-text-container">
            <div class="right-text">{{ item.content }}</div>
          </div>
        </div>
      </div>
      <!-- 工具调用消息 -->
      <div class="left-message message-item" v-if="item.type === 'tool_call'">
        <div class="avatar">
          <el-avatar :size="36" style="background-color: #e8f5e9">
            <el-icon><Connection /></el-icon>
          </el-avatar>
        </div>
        <div class="message">
          <div class="tool-call-container">
            <div class="tool-call-header" @click="item._expanded = !item._expanded">
              <el-icon><Tools /></el-icon>
              <span class="tool-name">{{ item.toolName || '工具调用' }}</span>
              <el-tag size="small" :type="item.status === 'success' ? 'success' : item.status === 'error' ? 'danger' : 'warning'">
                {{ item.status || '执行中' }}
              </el-tag>
              <el-icon class="expand-icon" :class="{ expanded: item._expanded }"><ArrowDown /></el-icon>
            </div>
            <div v-if="item._expanded" class="tool-call-detail">
              <div v-if="item.toolInput" class="tool-section">
                <div class="tool-section-label">输入</div>
                <pre class="tool-code">{{ formatJson(item.toolInput) }}</pre>
              </div>
              <div v-if="item.toolOutput" class="tool-section">
                <div class="tool-section-label">输出</div>
                <pre class="tool-code">{{ formatJson(item.toolOutput) }}</pre>
              </div>
              <div v-if="item.durationMs" class="tool-meta">
                耗时: {{ item.durationMs }}ms
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 记忆召回消息 -->
      <div class="left-message message-item" v-if="item.type === 'memory_recall'">
        <div class="avatar">
          <el-avatar :size="36" style="background-color: #fff3e0">
            <el-icon><Memo /></el-icon>
          </el-avatar>
        </div>
        <div class="message">
          <div class="memory-recall-container">
            <div class="memory-header">
              <el-icon><Memo /></el-icon>
              <span>召回 {{ item.memoryCount ?? item.memories?.length ?? 0 }} 条记忆</span>
            </div>
            <div v-if="item.content && !item.memories?.length" class="memory-summary">
              {{ item.content }}
            </div>
            <div v-for="(mem, mi) in item.memories" :key="mi" class="memory-item">
              <span class="memory-text">{{ mem.content }}</span>
              <span class="memory-score">
                sim={{ mem.similarity?.toFixed(2) }}
                decay={{ mem.decay?.toFixed(2) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <!-- 回到底部 -->
<!--  <div v-if="isScrolling" class="to-bottom" @click="handleGoBottom">-->
<!--    <el-button icon="ArrowDownBold" circle />-->
<!--  </div>-->
</template>
<script setup>
import MarkdownView from "@/components/MarkdownView/index.vue";
import { ChatMessageApi } from "@/api/app/chat/message";
import useUserStore from "@/store/system/user";
import userAvatarDefaultImg from "@/assets/system/images/index/icon (1).png";
import roleAvatarDefaultImg from "@/assets/app/gpt-new.svg";
import { useClipboard } from "@vueuse/core";
import { Connection, Tools, ArrowDown, Memo } from "@element-plus/icons-vue";

const { proxy } = getCurrentInstance();
const message = proxy.$modal; // 消息弹窗
const userStore = useUserStore();
const { copy } = useClipboard(); // 初始化 copy 到粘贴板

// 判断“消息列表”滚动的位置(用于判断是否需要滚动到消息最下方)
const messageContainer = ref(null);
const isScrolling = ref(false); //用于判断用户是否在滚动
const markdownViewRef = ref(null);

const userAvatar = computed(() => userStore.avatar || userAvatarDefaultImg);
const roleAvatar = computed(
  () => roleAvatarDefaultImg
);

// 定义 props
const props = defineProps({
  list: {
    type: Array,
    required: true,
  },
  suggestedList: {
    type: Array,
    default: () => [],
  },
});

const { list } = toRefs(props); // 消息列表

const emits = defineEmits([
  "onDeleteSuccess",
  "onRefresh",
  "onEdit",
  "onPrompt",
]);

// 格式化 JSON 用于工具调用展示
function formatJson(obj) {
  if (!obj) return '';
  if (typeof obj === 'string') {
    try { return JSON.stringify(JSON.parse(obj), null, 2); } catch { return obj; }
  }
  return JSON.stringify(obj, null, 2);
} // 定义 emits

// ============ 处理对话滚动 ==============

/** 滚动到底部 */
const scrollToBottom = async (isIgnore) => {
  // 注意要使用 nextTick 以免获取不到 dom
  await nextTick();
  if (isIgnore || !isScrolling.value) {
    messageContainer.value.scrollTop =
        messageContainer.value.scrollHeight - messageContainer.value.offsetHeight;
  }
};

function handleScroll() {
  const scrollContainer = messageContainer.value;
  const scrollTop = scrollContainer.scrollTop;
  const scrollHeight = scrollContainer.scrollHeight;
  const offsetHeight = scrollContainer.offsetHeight;
  if (scrollTop + offsetHeight < scrollHeight - 100) {
    // 用户开始滚动并在最底部之上，取消保持在最底部的效果
    isScrolling.value = true;
  } else {
    // 用户停止滚动并滚动到最底部，开启保持到最底部的效果
    isScrolling.value = false;
  }
}

/** 回到底部 */
const handleGoBottom = () => {
  const scrollContainer = messageContainer.value;
  scrollContainer.scrollTop = scrollContainer.scrollHeight;
};

/** 回到顶部 */
const handlerGoTop = () => {
  const scrollContainer = messageContainer.value;
  scrollContainer.scrollTop = 0;
};

defineExpose({ scrollToBottom, handlerGoTop, handleGoBottom }); // 提供方法给 parent 调用

// ============ 处理消息操作 ==============

function isStringRobust(value) {
  if (value == null) return false;
  return typeof value === "string" || value instanceof String;
}

/** 复制 */
const copyContent = (index) => {
  if (isStringRobust(index)) {
    copy(index).then(() => {
      message.msgSuccess("复制成功！");
    });
    return;
  }
  let count = -1;
  for (let i = 0; i <= index; i++) {
    if (list.value[i].type === 1) {
      count++;
    }
  }
  markdownViewRef.value[count].copyContent();
};

/** 删除 */
const onDelete = async (id) => {
  // 删除 message
  await ChatMessageApi.deleteChatMessage(id);
  message.msgSuccess("删除成功！");
  // 回调
  emits("onDeleteSuccess");
};

/** 刷新 */
const onRefresh = (message) => {
  emits("onRefresh", message);
};

/** 编辑 */
const onEdit = (message) => {
  emits("onEdit", message);
};

/** 尝试问问 */
const handlerSuggested = (item) => {
  emits("onPrompt", item);
};

/** 初始化 */
onMounted(() => {
  messageContainer.value.addEventListener("scroll", handleScroll);
});
</script>

<style scoped lang="scss">
.message-container {
  position: relative;
  overflow-y: scroll;
}
.h-100\% {
  height: 100%;
}

.overflow-y-auto {
  overflow-y: auto;
}
.relative {
  position: relative;
  padding-bottom: 20px;
  scrollbar-width: none;
}

// 中间
.chat-list {
  display: flex;
  flex-direction: column;
  overflow-y: hidden;
  padding: 0 20px;
  .message-item {
    margin-top: 50px;
  }

  .left-message {
    display: flex;
    flex-direction: row;
  }

  .right-message {
    display: flex;
    flex-direction: row-reverse;
    justify-content: flex-start;
  }

  .message {
    display: flex;
    flex-direction: column;
    text-align: right;
    margin: 0 15px;

    .time {
      text-align: left;
      line-height: 22px;
      font-weight: normal;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.65);
      font-style: normal;
      text-transform: none;
    }

    .left-text-container {
      position: relative;
      display: flex;
      flex-direction: column;
      overflow-wrap: break-word;
      background-color: #f0f0f6;
      padding: 10px 10px 5px 10px;
      border-radius: 4px 4px 4px 4px;
      margin-top: 8px;
      .left-text {
        color: #707070;
        font-size: 14px;
      }
    }

    .right-text-container {
      display: flex;
      flex-direction: row-reverse;
      margin-top: 8px;

      .right-text {
        display: inline;
        background: #257fff;
        border-radius: 4px 4px 4px 4px;
        padding: 10px;
        width: auto;
        overflow-wrap: break-word;
        white-space: pre-wrap;
        font-weight: 400;
        font-size: 14px;
        color: #ffffff;
        text-align: left;
        font-style: normal;
        text-transform: none;
      }
    }

    .left-btns {
      display: flex;
      flex-direction: row;
      margin-top: 8px;
      align-items: center;
    }

    .right-btns {
      display: flex;
      flex-direction: row-reverse;
      margin-top: 8px;
      align-items: center;
    }
  }

  .btn-divider {
    --el-border-color: #d9d8e8;
    margin: 11px;
  }

  // 复制、删除按钮
  .btn-cus {
    display: flex;
    background-color: transparent;
    align-items: center;

    .btn-image {
      height: 14px;
    }
  }

  .btn-cus:hover {
    cursor: pointer;
    background-color: #f6f6f6;
  }
}

.suggested-list {
  display: flex;
  justify-content: center;
  gap: 20px;
}

// 回到底部
.to-bottom {
  position: absolute;
  z-index: 1000;
  bottom: 0;
  right: 50%;
}

// 工具调用样式
.tool-call-container {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  padding: 8px 12px;
  max-width: 500px;

  .tool-call-header {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    font-size: 13px;
    color: #495057;

    .tool-name {
      font-weight: 500;
      flex: 1;
    }

    .expand-icon {
      transition: transform 0.2s;
      &.expanded { transform: rotate(180deg); }
    }
  }

  .tool-call-detail {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid #e9ecef;

    .tool-section {
      margin-bottom: 6px;
      .tool-section-label {
        font-size: 11px;
        color: #868e96;
        text-transform: uppercase;
        margin-bottom: 2px;
      }
      .tool-code {
        background: #212529;
        color: #adb5bd;
        padding: 6px 8px;
        border-radius: 4px;
        font-size: 12px;
        font-family: 'JetBrains Mono', monospace;
        overflow-x: auto;
        max-height: 200px;
        white-space: pre-wrap;
        word-break: break-all;
      }
    }
    .tool-meta {
      font-size: 11px;
      color: #868e96;
    }
  }
}

// 记忆召回样式
.memory-recall-container {
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  padding: 8px 12px;
  max-width: 400px;

  .memory-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #f57f17;
    font-weight: 500;
    margin-bottom: 6px;
  }

  .memory-summary {
    font-size: 12px;
    color: #6d4c41;
  }

  .memory-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 4px 0;
    border-bottom: 1px solid #fff3e0;
    font-size: 12px;

    .memory-text {
      flex: 1;
      color: #424242;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .memory-score {
      font-size: 11px;
      color: #868e96;
      margin-left: 8px;
      white-space: nowrap;
    }
  }
}
</style>
