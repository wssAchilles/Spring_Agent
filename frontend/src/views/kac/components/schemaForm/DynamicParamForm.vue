<template>
  <div class="dynamic-param-form">
    <el-form :model="formData" label-position="top" class="param-form">
      <!-- 模式一：通用自然语言指令输入模式 (无特定 Schema) -->
      <div v-if="!schemaList || schemaList.length === 0" class="prompt-input-wrapper">
        <div class="prompt-header">
          <div class="prompt-label-group">
            <span class="prompt-main-label">
              <el-icon class="label-icon"><EditPen /></el-icon>
              业务需求与执行指令
            </span>
            <span class="prompt-sub-label">支持自然语言描述、复杂业务场景或专业领域问答</span>
          </div>
          <div class="prompt-actions" v-if="formData.query">
            <el-button
              link
              size="small"
              class="clear-link-btn"
              @click="formData.query = ''"
            >
              <el-icon class="mr-1"><Delete /></el-icon>
              清空输入
            </el-button>
          </div>
        </div>

        <div class="textarea-container">
          <el-input
            v-model="formData.query"
            type="textarea"
            :rows="7"
            :autosize="{ minRows: 6, maxRows: 12 }"
            placeholder="请在此输入您希望该应用处理的核心指令、业务背景或待分析内容...&#10;例如：「请结合平台已沉淀的高保真知识库与风险研判模型，对当前业务场景进行合规穿透核验，并输出包含关键指标与处置建议的执行白皮书。」"
            class="glass-textarea"
            resize="vertical"
          />
          <div class="textarea-footer">
            <span class="shortcut-tip">
              提示：输入完成后可点击下方「立即运行」或按 <kbd>Ctrl</kbd> + <kbd>Enter</kbd>
            </span>
            <span class="char-count">
              {{ (formData.query || '').length }} 字符
            </span>
          </div>
        </div>
      </div>

      <!-- 模式二：结构化动态 Schema 参数输入模式 -->
      <div v-else class="schema-form-grid">
        <el-form-item
          v-for="item in schemaList"
          :key="item.field"
          :required="item.required"
          class="param-form-item"
          :class="{ 'full-width-item': item.type === 'TEXTAREA' || !item.type }"
        >
          <template #label>
            <div class="form-item-label-row">
              <span class="field-title">{{ item.label }}</span>
              <span class="field-key" v-if="item.field">({{ item.field }})</span>
            </div>
          </template>

          <!-- 纯文本输入 -->
          <el-input
            v-if="item.type === 'STRING'"
            v-model="formData[item.field]"
            :placeholder="item.placeholder || `请输入${item.label}`"
            clearable
            class="glass-input"
          />

          <!-- 多行文本域 -->
          <el-input
            v-else-if="item.type === 'TEXTAREA'"
            v-model="formData[item.field]"
            type="textarea"
            :rows="item.rows || 5"
            :autosize="{ minRows: item.rows || 4, maxRows: 10 }"
            :placeholder="item.placeholder || `请输入${item.label}`"
            show-word-limit
            maxlength="4000"
            class="glass-textarea"
          />

          <!-- 下拉单选 -->
          <el-select
            v-else-if="item.type === 'SELECT'"
            v-model="formData[item.field]"
            :placeholder="item.placeholder || `请选择${item.label}`"
            class="glass-select"
            style="width: 100%"
          >
            <el-option
              v-for="opt in item.options || []"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <!-- 数字输入 -->
          <el-input-number
            v-else-if="item.type === 'NUMBER'"
            v-model="formData[item.field]"
            :min="item.min !== undefined ? item.min : 0"
            :max="item.max !== undefined ? item.max : 99999"
            class="glass-number"
            controls-position="right"
          />

          <!-- 兜底文本输入 -->
          <el-input
            v-else
            v-model="formData[item.field]"
            :placeholder="item.placeholder || `请输入${item.label}`"
            clearable
            class="glass-input"
          />
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, watch } from "vue";
import { EditPen, Delete } from "@element-plus/icons-vue";

const props = defineProps({
  schema: {
    type: [Array, String],
    default: () => [],
  },
  modelValue: {
    type: Object,
    default: () => ({}),
  },
});

const emit = defineEmits(["update:modelValue"]);

const schemaList = computed(() => {
  if (!props.schema) return [];
  let list = [];
  if (Array.isArray(props.schema)) {
    list = props.schema;
  } else {
    try {
      const parsed = JSON.parse(props.schema);
      list = Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  // 核心归一化：支持 field / name / key 映射，大写类型对齐，标准化 options 选项
  return list.map((item, index) => {
    const field = item.field || item.name || item.key || `param_${index + 1}`;
    const rawType = (item.type || "STRING").toUpperCase();
    const label = item.label || item.title || field;

    let normalizedOptions = [];
    if (Array.isArray(item.options)) {
      normalizedOptions = item.options.map((opt) => {
        if (typeof opt === "object" && opt !== null) {
          return {
            label: opt.label !== undefined ? String(opt.label) : String(opt.value),
            value: opt.value !== undefined ? opt.value : opt.label,
          };
        }
        return { label: String(opt), value: opt };
      });
    }

    return {
      ...item,
      field,
      label,
      type: rawType,
      options: normalizedOptions,
    };
  });
});

const formData = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

// 初始化默认值
watch(
  schemaList,
  (list) => {
    if (list && list.length) {
      const updated = { ...props.modelValue };
      let changed = false;
      list.forEach((item) => {
        if (updated[item.field] === undefined && item.defaultValue !== undefined) {
          updated[item.field] = item.defaultValue;
          changed = true;
        }
      });
      if (changed) {
        emit("update:modelValue", updated);
      }
    }
  },
  { immediate: true }
);
</script>

<style scoped lang="scss">
.dynamic-param-form {
  width: 100%;

  .prompt-input-wrapper {
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.9) 100%);
    border: 1px solid rgba(226, 232, 240, 0.9);
    border-radius: 12px;
    padding: 16px;
    box-shadow: 0 2px 10px -2px rgba(15, 23, 42, 0.04);
    transition: all 0.25s ease;

    &:hover {
      border-color: rgba(0, 82, 255, 0.3);
      box-shadow: 0 4px 16px -2px rgba(0, 82, 255, 0.06);
    }

    .prompt-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .prompt-label-group {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .prompt-main-label {
          font-size: 13.5px;
          font-weight: 600;
          color: #0f172a;
          display: inline-flex;
          align-items: center;
          gap: 6px;

          .label-icon {
            color: #0052ff;
            font-size: 15px;
          }
        }

        .prompt-sub-label {
          font-size: 11.5px;
          color: #64748b;
        }
      }

      .clear-link-btn {
        font-size: 12px;
        color: #94a3b8;
        padding: 0;

        &:hover {
          color: #ef4444;
        }
      }
    }

    .textarea-container {
      position: relative;

      .textarea-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 8px;
        padding: 0 4px;
        font-size: 11.5px;
        color: #94a3b8;

        .shortcut-tip {
          display: inline-flex;
          align-items: center;
          gap: 4px;

          kbd {
            display: inline-block;
            padding: 1px 5px;
            font-size: 10.5px;
            font-family: inherit;
            color: #475569;
            background: #f1f5f9;
            border: 1px solid #cbd5e1;
            border-radius: 4px;
            box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
          }
        }

        .char-count {
          font-variant-numeric: tabular-nums;
          font-weight: 500;
        }
      }
    }
  }

  .schema-form-grid {
    display: flex;
    flex-direction: column;
    gap: 14px;

    .param-form-item {
      margin-bottom: 0;

      .form-item-label-row {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        line-height: 20px;

        .field-title {
          font-weight: 600;
          color: #1e293b;
        }

        .field-key {
          font-size: 11px;
          color: #94a3b8;
          font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        }
      }
    }
  }

  .glass-input,
  .glass-select,
  .glass-number {
    width: 100%;

    :deep(.el-input__wrapper) {
      background: rgba(255, 255, 255, 0.95);
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 6px 12px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
      transition: all 0.2s ease;

      &:hover {
        border-color: #94a3b8;
      }

      &.is-focus {
        border-color: #0052ff;
        box-shadow: 0 0 0 3px rgba(0, 82, 255, 0.12);
      }
    }
  }

  .glass-textarea {
    width: 100%;

    :deep(.el-textarea__inner) {
      background: rgba(255, 255, 255, 0.95);
      border: 1px solid #cbd5e1;
      border-radius: 10px;
      padding: 12px 14px;
      font-size: 13.5px;
      line-height: 1.65;
      color: #1e293b;
      box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.03);
      transition: all 0.2s ease;

      &::placeholder {
        color: #94a3b8;
        font-size: 12.5px;
        line-height: 1.6;
      }

      &:hover {
        border-color: #94a3b8;
      }

      &:focus {
        border-color: #0052ff;
        background: #ffffff;
        box-shadow: 0 0 0 3px rgba(0, 82, 255, 0.12), inset 0 1px 2px rgba(0, 0, 0, 0.02);
      }
    }
  }
}
</style>
