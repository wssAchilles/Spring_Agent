<template>
  <div class="dynamic-param-form">
    <el-form :model="formData" label-position="top" class="param-form">
      <div v-if="!schemaList || schemaList.length === 0" class="empty-schema-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>该应用未配置特定输入参数模式，请输入默认提示词或指令：</span>
        <el-input
          v-model="formData.query"
          type="textarea"
          :rows="4"
          placeholder="请输入您希望该应用处理的指令、问题或内容..."
          class="glass-input mt-2"
        />
      </div>

      <template v-else>
        <el-form-item
          v-for="item in schemaList"
          :key="item.field"
          :label="item.label"
          :required="item.required"
          class="param-form-item"
        >
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
            :rows="item.rows || 3"
            :placeholder="item.placeholder || `请输入${item.label}`"
            show-word-limit
            maxlength="2000"
            class="glass-input"
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
          />

          <!-- 兜底文本输入 -->
          <el-input
            v-else
            v-model="formData[item.field]"
            :placeholder="item.placeholder || `请输入${item.label}`"
            class="glass-input"
          />
        </el-form-item>
      </template>
    </el-form>
  </div>
</template>

<script setup>
import { computed, watch } from "vue";
import { InfoFilled } from "@element-plus/icons-vue";

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
  if (Array.isArray(props.schema)) return props.schema;
  try {
    const parsed = JSON.parse(props.schema);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
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

  .empty-schema-tip {
    font-size: 13px;
    color: #64748b;
    display: flex;
    flex-direction: column;
    gap: 6px;
    background: rgba(241, 245, 249, 0.6);
    padding: 12px;
    border-radius: 8px;
    border: 1px dashed rgba(203, 213, 225, 0.8);

    .mt-2 {
      margin-top: 8px;
    }
  }

  .param-form-item {
    margin-bottom: 16px;

    :deep(.el-form-item__label) {
      font-size: 13px;
      font-weight: 500;
      color: #1e293b;
      line-height: 20px;
      padding-bottom: 4px;
    }
  }

  .glass-input,
  .glass-select {
    :deep(.el-input__wrapper),
    :deep(.el-textarea__inner) {
      background: rgba(255, 255, 255, 0.8);
      backdrop-filter: blur(10px);
      border: 1px solid rgba(226, 232, 240, 0.9);
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
      border-radius: 8px;
      transition: all 0.2s ease;

      &:hover {
        border-color: rgba(0, 82, 255, 0.4);
      }

      &.is-focus,
      &:focus {
        border-color: #0052ff;
        box-shadow: 0 0 0 2px rgba(0, 82, 255, 0.15);
      }
    }
  }
}
</style>
