
<template>
  <div
    class="card-container"
    :class="{ 'card-container--overview': props.variant === 'overview' }"
  >
    <div
      v-for="(item, index) in data"
      :key="item.id || index"
      class="card glass-card clickable-card"
      @click="handleExperience(item)"
    >
      <div class="card-inner">
        <div class="card-title-row">
          <div class="card-title-info">
            <span class="card-title-icon">
              <img v-if="isHttpUrl(item.icon)" :src="item.icon" alt="应用图标" />
              <svg-icon v-else :icon-class="getSvgIcon(item)" class="kac-card-svg" />
            </span>
            <div class="card-title-text">
              <div class="card-title">{{ item.name || "-" }}</div>
              <div
                class="card-tags card-tags--title"
                v-if="props.variant === 'overview' && getTags(item).length > 0"
              >
                <el-tag
                  v-for="(tag, tagIndex) in getTags(item)"
                  :key="tagIndex"
                  class="card-tag"
                >
                  {{ tag.name }}
                </el-tag>
              </div>
            </div>
          </div>

          <div class="card-title-extra">
            <span
              v-if="props.source !== 'myApp'"
              class="status-pill"
              :class="getStatusClass(item.status)"
            >
              {{ getStatusLabel(item.status) }}
            </span>

            <div class="card-top" v-if="props.source === 'myApp'">
              <div class="card-title-button">
                <el-popover
                  placement="bottom"
                  trigger="click"
                  :width="96"
                  popper-class="card-more-popper"
                >
                  <template #reference>
                    <el-button v-ripple
                      link
                      @click.stop
                      class="glass-btn"
                      :class="[
                        'custom-more-button',
                        {
                          'custom-more-button--plain': props.source === 'myApp',
                        },
                      ]"
                    >
                      <el-icon class="more-icon"><More /></el-icon>
                    </el-button>
                  </template>
                  <div class="card-button-group">
                    <el-button v-ripple
                      text
                      class="card-menu-btn glass-btn"
                      @click.stop="handleUpdate(item)"
                      v-hasPermi="['kac:apply:apply:edit']"
                    >
                      <el-icon class="card-menu-icon"><EditPen /></el-icon>
                      修改
                    </el-button>
                    <el-button v-ripple
                      text
                      class="card-menu-btn glass-btn"
                      @click.stop="handleDelete(item)"
                      v-hasPermi="['kac:apply:apply:remove']"
                    >
                      <el-icon class="card-menu-icon"><Delete /></el-icon>
                      删除
                    </el-button>
                  </div>
                </el-popover>
              </div>
            </div>
          </div>
        </div>

        <el-divider class="card-divider" />

        <div class="card-body">
          <div class="card-body-main">
            <div class="card-description">
              <el-text
                :ref="(el) => setDescriptionRef(index, el)"
                class="description-text"
                :line-clamp="props.variant === 'overview' ? 2 : 3"
              >
                {{ item.description || "-" }}
              </el-text>
            </div>

            <div
              class="card-tags"
              v-if="props.variant !== 'overview' && getTags(item).length > 0"
            >
              <el-tag
                v-for="(tag, tagIndex) in getTags(item)"
                :key="tagIndex"
                class="card-tag"
              >
                {{ tag.name }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="card-actions">
          <el-button v-ripple
            class="card-action-btn glass-btn"
            @click.stop="handleExperience(item)"
          >
            <el-icon class="card-action-icon"><VideoPlay /></el-icon>
            立即体验
          </el-button>
          <el-button v-ripple
            class="card-action-btn card-action-btn--detail glass-btn"
            @click.stop="handleDetail(item)"
          >
            <el-icon class="card-action-icon"><View /></el-icon>
            查看详情
          </el-button>
        </div>
      </div>
    </div>

    <el-dialog class="glass-card" :title="title" v-model="open" width="800px">
      <el-form ref="applyRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="来源应用" prop="source">
          <el-input
            v-model="form.source"
            placeholder="请输入来源应用名称"
            disabled
          />
        </el-form-item>
        <el-form-item label="应用名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入应用名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            placeholder="请输入描述"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入备注"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-ripple class="glass-btn" size="small" @click="cancel">取消</el-button>
          <el-button v-ripple class="glass-btn" size="small" @click="submitForm">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 沉浸式毛玻璃即时运行抽屉 -->
    <AppRunnerDrawer ref="runnerDrawerRef" />
  </div>
</template>

<script setup>
import {
  getCurrentInstance,
  onBeforeUnmount,
  onMounted,
  nextTick,
  reactive,
  ref,
  toRefs,
  watch,
} from "vue";
import { ElMessage } from "element-plus";
import {
  Delete,
  EditPen,
  More,
  View,
  VideoPlay,
} from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import AppRunnerDrawer from "@/views/kac/components/runner/AppRunnerDrawer.vue";
import {
  delApply,
  getByApplyIdId,
  updateApply,
} from "@/api/kac/apply/apply.js";

const runnerDrawerRef = ref(null);
const { proxy } = getCurrentInstance();
const router = useRouter();

const { kac_horizontal_status } = proxy.useDict("kac_horizontal_status");

const props = defineProps({
  data: {
    type: Array,
    required: true,
  },
  source: {
    type: String,
    default: "horizontal",
    validator: (value) => ["horizontal", "vertical", "myApp"].includes(value),
  },
  variant: {
    type: String,
    default: "overview",
    validator: (value) => ["default", "overview"].includes(value),
  },
});

const emit = defineEmits(["refresh"]);

const rules = reactive({
  name: [
    { required: true, message: "请输入应用名称", trigger: "blur" },
    { max: 100, message: "应用名称不能超过100个字符", trigger: "blur" },
  ],
});

const open = ref(false);
const title = ref("");
const dataS = reactive({
  form: {},
});
const knowledgeBaseList = ref([]);
const graphList = ref([]);
const botList = ref([]);
const dropdownLoaded = ref(false);

const { form } = toRefs(dataS);
const descriptionRefs = ref([]);
const overflowStates = ref({});

function setDescriptionRef(index, el) {
  const target = el?.$el ?? el;
  if (target) {
    descriptionRefs.value[index] = target;
  }
}

function checkOverflow() {
  nextTick(() => {
    descriptionRefs.value.forEach((el, index) => {
      if (el) {
        overflowStates.value[index] = el.scrollHeight > el.clientHeight;
      }
    });
  });
}

const appSvgMapping = {
  // 横向通用原子技能专属 SVG 标识
  'kac-article-write': 'kac-article-write',
  'kac-batch-search': 'kac-batch-search',
  'kac-exact-search': 'kac-exact-search',
  'kac-entity-graph': 'kac-entity-graph',
  'kac-semantic-search': 'kac-semantic-search',
  'kac-qa-chat': 'kac-qa-chat',
  'kac-template-report': 'kac-template-report',
  'kac-calendar-report': 'kac-calendar-report',
  'kac-data-analysis': 'kac-data-analysis',
  'kac-smart-summary': 'kac-smart-summary',

  // 纵向行业中枢应用专属 SVG 标识
  'kac-industry-finance': 'kac-industry-finance',
  'kac-industry-medical': 'kac-industry-medical',
  'kac-industry-manufacturing': 'kac-industry-manufacturing',
  'kac-industry-education': 'kac-industry-education',
  'kac-industry-government': 'kac-industry-government',
  'kac-industry-ecommerce': 'kac-industry-ecommerce',
  'kac-industry-water': 'kac-industry-water',
  'kac-industry-energy': 'kac-industry-energy',

  // 兼容既有 Element 图标名映射
  'Edit': 'kac-article-write',
  'Search': 'kac-batch-search',
  'Aim': 'kac-semantic-search',
  'Connection': 'kac-entity-graph',
  'ChatDotRound': 'kac-qa-chat',
  'Calendar': 'kac-calendar-report',
  'DataAnalysis': 'kac-data-analysis',
  'Document': 'kac-template-report',
  'Money': 'kac-industry-finance',
  'FirstAidKit': 'kac-industry-medical',
  'Monitor': 'kac-industry-manufacturing',
  'Reading': 'kac-industry-education',
  'ShoppingCart': 'kac-industry-ecommerce',
  'Help': 'kac-industry-water',
  'Cpu': 'kac-industry-energy',
};

function isHttpUrl(val) {
  return typeof val === 'string' && (val.startsWith('http://') || val.startsWith('https://'));
}

function getSvgIcon(item) {
  if (!item) return 'kac-article-write';
  
  // 1. 显式映射
  if (item.icon && appSvgMapping[item.icon]) {
    return appSvgMapping[item.icon];
  }
  
  // 2. 根据应用名称或行业类型智能匹配
  const name = (item.name || '').trim();
  const industry = (item.industry || item.type || '').trim();

  // 行业中枢匹配
  if (name.includes('金融') || name.includes('反欺诈') || name.includes('银行') || industry.includes('金融')) return 'kac-industry-finance';
  if (name.includes('医疗') || name.includes('诊疗') || name.includes('临床') || industry.includes('医疗')) return 'kac-industry-medical';
  if (name.includes('制造') || name.includes('产线') || name.includes('工业') || industry.includes('制造')) return 'kac-industry-manufacturing';
  if (name.includes('教育') || name.includes('组卷') || name.includes('学情') || industry.includes('教育')) return 'kac-industry-education';
  if (name.includes('政务') || name.includes('公文') || name.includes('红头') || industry.includes('政务')) return 'kac-industry-government';
  if (name.includes('电商') || name.includes('选品') || name.includes('跨境') || industry.includes('电商')) return 'kac-industry-ecommerce';
  if (name.includes('水利') || name.includes('防汛') || name.includes('大坝') || industry.includes('水利')) return 'kac-industry-water';
  if (name.includes('能源') || name.includes('电网') || name.includes('绿电') || industry.includes('能源')) return 'kac-industry-energy';

  // 通用原子技能匹配
  if (name.includes('文章') || name.includes('写作') || name.includes('公文')) return 'kac-article-write';
  if (name.includes('批量') || name.includes('批量检索')) return 'kac-batch-search';
  if (name.includes('精确') || name.includes('精准') || name.includes('代码检索')) return 'kac-exact-search';
  if (name.includes('实体') || name.includes('关系') || name.includes('图谱')) return 'kac-entity-graph';
  if (name.includes('语义') || name.includes('向量')) return 'kac-semantic-search';
  if (name.includes('问答') || name.includes('对话') || name.includes('客服')) return 'kac-qa-chat';
  if (name.includes('模板') || name.includes('报告')) return 'kac-template-report';
  if (name.includes('日报') || name.includes('周报') || name.includes('日历') || name.includes('日程')) return 'kac-calendar-report';
  if (name.includes('分析') || name.includes('数据') || name.includes('统计') || name.includes('大屏')) return 'kac-data-analysis';
  if (name.includes('摘要') || name.includes('总结') || name.includes('提炼')) return 'kac-smart-summary';

  // 3. 原生本地 SVG 标识
  if (item.icon && typeof item.icon === 'string' && !isHttpUrl(item.icon)) {
    return item.icon;
  }

  // 4. 默认优雅保底
  return 'skill';
}

function getStatusOption(status) {
  const options = Array.isArray(kac_horizontal_status.value)
    ? kac_horizontal_status.value
    : [];
  return options.find((item) => String(item.value) === String(status));
}

function getStatusLabel(status) {
  return getStatusOption(status)?.label || "--";
}

function getStatusClass(status) {
  const option = getStatusOption(status);
  const label = option?.label || "";
  const tagType = option?.elTagType || "";

  if (
    ["warning", "danger", "info"].includes(tagType) ||
    label.includes("停") ||
    label.includes("禁")
  ) {
    return "status-pill--warning";
  }

  return "status-pill--primary";
}

function getTags(row) {
  if (!row.tags) {
    return [];
  }
  try {
    return JSON.parse(row.tags);
  } catch {
    return [];
  }
}

function reset() {
  form.value = {
    id: null,
    workspaceId: null,
    pluginId: null,
    name: null,
    category: null,
    description: null,
    status: null,
    source: null,
    tags: null,
    useScene: null,
    useCount: null,
    kacApplyKnowledgeList: [],
    kacApplyGraphList: [],
    kacApplyBotList: [],
    validFlag: null,
    delFlag: null,
    createBy: null,
    creatorId: null,
    createTime: null,
    updateBy: null,
    updaterId: null,
    updateTime: null,
    remark: null,
  };
  proxy.resetForm("applyRef");
}

async function ensureDropdownData() {
  if (dropdownLoaded.value) {
    return;
  }
  dropdownLoaded.value = true;
}

function handleDelete(row) {
  const _ids = row.id;
  proxy.$modal
    .confirm(`是否确认删除当前应用编号为"${_ids}"的数据项？`)
    .then(() => delApply(_ids))
    .then(() => {
      proxy.$modal.msgSuccess("删除成功");
      emit("refresh");
    })
    .catch(() => {});
}

async function handleUpdate(row) {
  reset();
  await ensureDropdownData();
  const res = await getByApplyIdId(row.id);
  form.value = res.data;
  title.value = "修改应用";
  open.value = true;
}

function handleExperience(row) {
  if (String(row.status) === '0') {
    ElMessage({ message: '该应用已停用', type: 'warning' });
    return;
  }
  if (runnerDrawerRef.value) {
    runnerDrawerRef.value.open(row);
  }
}

function handleDetail(row) {
  if (props.source === 'vertical') {
    router.push({
      path: '/kac/vertical/verticalDetail',
      query: { id: row.id },
    });
    return;
  }

  if (props.source === 'myApp') {
    router.push({
      path: '/kac/myApp/myAppDetail',
      query: { id: row.id },
    });
    return;
  }

  if (String(row.status) === '0') {
    ElMessage({ message: '该应用已停用', type: 'warning' });
    return;
  }

  router.push({
    path: '/kac/horizontal/horizontalDetail',
    query: { id: row.id },
  });
}

function submitForm() {
  proxy.$refs.applyRef.validate((valid) => {
    if (valid && form.value.id != null) {
      updateApply(form.value)
        .then(() => {
          proxy.$modal.msgSuccess("修改成功");
          emit("refresh");
          open.value = false;
        })
        .catch(() => {});
    }
  });
}

function cancel() {
  open.value = false;
  reset();
}

onMounted(() => {
  checkOverflow();
  window.addEventListener("resize", checkOverflow);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", checkOverflow);
});

watch(
  () => props.data,
  () => {
    descriptionRefs.value = [];
    overflowStates.value = {};
    checkOverflow();
  },
  { deep: true, immediate: true }
);
</script>

<style scoped lang="scss">
.card-container {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 14px;
  padding: 14px 0;
  width: 100%;
  box-sizing: border-box;
}

.card {
  min-width: 0;
  min-height: 220px;
  height: 100%;
  background: #ffffff;
  border-radius: 2px;
  cursor: default;
}

.card-inner {
  min-height: 220px;
  height: 100%;
  padding: 14px;
  display: flex;
  flex-direction: column;
}

.card-title-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: flex-start;
  gap: 12px;
}

.card-title-info {
  min-width: 0;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-title-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(241, 245, 249, 0.85) 100%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.04), inset 0 1px 0 rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

  .kac-card-svg {
    width: 26px;
    height: 26px;
    transition: transform 0.25s ease;
  }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
    border-color: rgba(147, 197, 253, 0.6);

    .kac-card-svg {
      transform: scale(1.08);
    }
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.card-title-text {
  min-width: 0;
}

.card-title {
  font-family: PingFang SC;
  font-weight: 800;
  font-size: 16px;
  line-height: 22px;
  color: #333333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-title-extra {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  min-width: max-content;
  justify-self: end;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;

  &::before {
    content: "";
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }
}

.status-pill--primary {
  background: #edf3ff;
  color: #2b70f4;
}

.status-pill--warning {
  background: #f3f5f7;
  color: #7d8797;
}

.card-top {
  position: relative;
  z-index: 2;
}

.card-title-button {
  display: flex;
  align-items: center;
}

.card-button-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  gap: 2px;
}

.card-menu-btn {
  min-height: 34px;
  font-size: 14px;
  margin-left: 0px;
}

.card-menu-icon {
  margin-right: 8px;
  width: 14px;
  height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  line-height: 1;
  flex-shrink: 0;
}

.custom-more-button {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: rgba(31, 35, 41, 0.58);
}

.custom-more-button--plain {
  color: #8b95a6;
  background: transparent;
}

.more-icon {
  font-size: 16px;
  line-height: 1;
}

:deep(.custom-more-button:hover) {
  color: #ffffff;
  background: rgba(43, 112, 244, 0.92);
}

:deep(.custom-more-button--plain:hover) {
  color: #5f6b7c;
  background: transparent;
}

:deep(.card-more-popper.el-popover) {
  min-width: 96px !important;
  padding: 6px;
}

:deep(.card-more-popper .el-button) {
  width: 100%;
  margin-left: 0 !important;
  justify-content: flex-start;
  padding: 8px 10px;
  line-height: 20px;
  display: flex;
  align-items: center;
}

:deep(.card-more-popper .el-button > span) {
  width: 100%;
  display: inline-flex;
  align-items: center;
  line-height: 20px;
}

:deep(.card-more-popper .el-button + .el-button) {
  margin-top: 0;
}

.card-divider {
  margin: 8px 0;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-body-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.card-description {
  height: 60px;
}

.description-text {
  width: 100%;
  line-height: 19px;
  font-family: PingFang SC;
  font-weight: 700;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  word-break: break-all;
}

.card-tags {
  min-height: 24px;
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 6px;
}

.card-tag {
  margin: 0;
  border: none !important;
  background: #eaf3ff !important;
  color: #2b70f4 !important;
  font-family: PingFang SC;
  font-weight: 700;
}

.card-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e8edf5;
  display: flex;
  gap: 10px;
}

.card-action-btn {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 12px;
  border-radius: 2px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: PingFang SC;
  font-weight: 400;
  font-size: 12px;
}

.card-action-icon {
  margin-right: 6px;
  font-size: 14px;
}

:deep(.card-action-btn.el-button--primary) {
  border: none;
  background: linear-gradient(90deg, #3d83ff 0%, #266cf4 100%);
}

:deep(.card-action-btn--detail.el-button--primary) {
  color: #2b70f4;
  border: 1px solid #5185fc;
  background: #ffffff;
  box-shadow: none;
}

:deep(.card-action-btn--detail.el-button--primary:hover) {
  color: #ffffff;
  border-color: #5185fc;
  background: #5185fc;
}

:deep(.card-action-btn--detail.el-button--primary:focus) {
  color: #2b70f4;
  border-color: #5185fc;
  background: #ffffff;
}

:deep(.card-actions .el-button + .el-button) {
  margin-left: 0;
}

:deep(.card-action-btn.is-disabled) {
  opacity: 0.65;
}

.card-container--overview {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 15px;
  padding: 0;

  .card {
    min-height: 196px;
    border: 1px solid #e5ebf5;
    border-radius: 4px;
    box-shadow: none;
  }

  .card-inner {
    min-height: 196px;
    padding: 20px;
  }

  .card-title-row {
    align-items: flex-start;
    gap: 12px;
  }

  .card-title-icon {
    width: 58px;
    height: 58px;
    border: 0;
    border-radius: 8px;
  }

  .card-title {
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
    color: #111827;
  }

  .status-pill {
    height: 24px;
    padding: 0 10px;
    border: 1px solid #a8e8b7;
    background: #eafbf0;
    color: #20b759;
    font-size: 14px;
    font-weight: 400;
  }

  .status-pill--warning {
    border-color: #d8dce6;
    background: #f5f7fa;
    color: #909399;
  }

  .card-tags--title {
    min-height: 24px;
    margin-top: 8px;
  }

  .card-tag {
    height: 24px;
    padding: 0 14px;
    border-radius: 2px;
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
  }

  .card-divider {
    display: none;
  }

  .card-body {
    margin-top: 12px;
  }

  .card-description {
    height: 44px;
  }

  .description-text {
    font-size: 14px;
    font-weight: 400;
    line-height: 22px;
    color: #4b5563;
    display: -webkit-box;
    overflow: hidden;
    text-overflow: ellipsis;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .card-actions {
    margin-top: 14px;
    padding-top: 0;
    border-top: 0;
    gap: 20px;
  }

  .card-action-btn {
    height: 30px;
    border-radius: 4px;
    font-size: 14px;
  }

  .card-action-icon {
    display: none;
  }
}

@media (min-width: 768px) {
  .card-container {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .card-container--overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1280px) {
  .card-container {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .card-container--overview {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .card-inner {
    min-height: 206px;
    padding: 14px;
  }

  .card-title-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .card-title-extra {
    width: 100%;
    min-width: 0;
    justify-content: space-between;
    justify-self: stretch;
  }

  .card-actions {
    flex-direction: column;
  }
}
</style>
