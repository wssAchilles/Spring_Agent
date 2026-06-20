<template>
  <el-tooltip
    :content="text"
    placement="top"
    :disabled="!isOverflowing || !text"
  >
    <span ref="labelRef" class="debug-overflow-tooltip-label">
      {{ text }}
    </span>
  </el-tooltip>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

const props = defineProps({
  text: {
    type: String,
    default: "",
  },
});

const labelRef = ref(null);
const isOverflowing = ref(false);
let resizeObserver = null;

function updateOverflowState() {
  const labelElement = labelRef.value;

  isOverflowing.value = Boolean(
    labelElement && labelElement.scrollWidth > labelElement.clientWidth
  );
}

function observeLabelSize() {
  if (resizeObserver || typeof ResizeObserver === "undefined") {
    return;
  }

  resizeObserver = new ResizeObserver(() => {
    updateOverflowState();
  });

  if (labelRef.value) {
    resizeObserver.observe(labelRef.value);
  }
}

onMounted(() => {
  observeLabelSize();
  nextTick(() => {
    updateOverflowState();
  });
});

watch(
  () => props.text,
  () => {
    nextTick(() => {
      updateOverflowState();
    });
  },
  {
    immediate: true,
  }
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});
</script>

<style scoped lang="scss">
.debug-overflow-tooltip-label {
  display: block;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: right;
}
</style>
