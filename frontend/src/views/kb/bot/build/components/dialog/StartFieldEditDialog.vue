<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="560px"
    class="start-field-dialog glass-card"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="draft"
      :rules="formRules"
      :validate-on-rule-change="false"
      label-width="80px"
      status-icon
    >
      <el-form-item prop="name" label="变量名称">
        <el-input v-model="draft.name" placeholder="请输入变量英文名，如 query" />
      </el-form-item>

      <el-form-item prop="label" label="显示名称">
        <el-input v-model="draft.label" placeholder="请输入显示名称，如 用户输入" />
      </el-form-item>

      <el-form-item label="字段类型">
        <el-select
          v-model="draft.type"
          popper-class="dydome-square-popper"
          @change="handleTypeChange"
        >
          <el-option
            v-for="typeOption in startFieldTypeOptions"
            :key="typeOption.value"
            :label="typeOption.label"
            :value="typeOption.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        v-if="draftTypeMeta.supportsMaxLength"
        prop="maxLength"
        label="最大长度"
      >
        <el-input-number
          v-model="draft.maxLength"
          :min="1"
          :max="2000"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div>
        <el-button v-ripple class="glass-btn" @click="handleCancel">取消</el-button>
        <el-button v-ripple class="glass-btn" type="primary" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import {
  startFieldTypeOptions,
  getStartFieldTypeMeta,
} from "../../utils/workflowHelpers.js";

function createDefaultStartField(type = "text") {
  return {
    type,
    name: "",
    label: "",
    maxLength: 48,
    defaultValue: "",
    required: true,
  };
}

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  field: {
    type: Object,
    default: null,
  },
  existingFields: {
    type: Array,
    default: () => [],
  },
  isChatflow: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:visible", "save", "cancel"]);

const formRef = ref(null);
const draft = ref(createDefaultStartField("text"));
const currentFieldId = ref(null);

const isEditMode = computed(() => Boolean(currentFieldId.value));
const dialogTitle = computed(() => (isEditMode.value ? "修改变量" : "新增变量"));
const draftTypeMeta = computed(() => getStartFieldTypeMeta(draft.value.type));

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.field) {
        currentFieldId.value = props.field.id || null;
        draft.value = {
          ...createDefaultStartField(props.field.type || "text"),
          ...props.field,
        };
      } else {
        currentFieldId.value = null;
        draft.value = createDefaultStartField("text");
      }
    }
  }
);

function handleTypeChange(nextType) {
  const meta = getStartFieldTypeMeta(nextType);
  draft.value.maxLength = meta.supportsMaxLength ? 48 : null;
}

function validateFieldName(rule, value, callback) {
  const normalized = `${value || ""}`.trim();
  if (!normalized) {
    return callback(new Error("请输入变量名称"));
  }
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(normalized)) {
    return callback(new Error("变量名称仅支持英文字母、数字和下划线，且不能以数字开头"));
  }
  const duplicated = (props.existingFields || []).find(
    (f) =>
      f?.id !== currentFieldId.value &&
      `${f?.name || ""}`.trim().toLowerCase() === normalized.toLowerCase()
  );
  if (duplicated) {
    return callback(new Error(`变量名称“${normalized}”已存在`));
  }
  callback();
}

function validateFieldLabel(rule, value, callback) {
  const normalized = `${value || ""}`.trim();
  if (!normalized) {
    return callback(new Error("请输入显示名称"));
  }
  callback();
}

const formRules = {
  name: [{ required: true, validator: validateFieldName, trigger: "blur" }],
  label: [{ required: true, validator: validateFieldLabel, trigger: "blur" }],
};

async function handleSave() {
  if (!formRef.value) return;
  await formRef.value.validate((valid) => {
    if (valid) {
      emit("save", {
        ...draft.value,
        id: currentFieldId.value || `field_${Date.now()}`,
        name: draft.value.name.trim(),
        label: draft.value.label.trim(),
      });
      emit("update:visible", false);
    }
  });
}

function handleCancel() {
  emit("update:visible", false);
  emit("cancel");
}

function handleClosed() {
  if (formRef.value) {
    formRef.value.resetFields();
  }
}
</script>

<style scoped lang="scss">
.start-field-dialog {
  border-radius: 16px;
}
</style>
