<template>
  <div class="app-container horizontal-page glass-card" ref="app-container">
    <!-- 顶部原子生产力技能矩阵胶囊导航 -->
    <div class="skill-category-nav">
      <div class="nav-title">原子技能矩阵:</div>
      <div class="skill-chips">
        <button
          v-for="tab in skillTabs"
          :key="tab.value"
          class="skill-chip-btn"
          :class="{ active: currentType === tab.value }"
          @click="selectType(tab.value)"
        >
          <span class="chip-label">{{ tab.label }}</span>
        </button>
      </div>
    </div>

    <div class="pagecont-top" v-show="showSearch">
      <el-form
        class="btn-style"
        :model="queryParams"
        ref="queryRef"
        :inline="true"
        label-width="80px"
        v-show="showSearch"
        @submit.prevent
      >
        <el-form-item label="技能类型" prop="type">
          <el-select
            v-model="queryParams.type"
            placeholder="请选择技能类型"
            clearable
            class="el-form-input-width"
            @change="handleQuery"
          >
            <el-option
              v-for="item in typeSelectOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="应用名称" prop="name">
          <el-input
            class="el-form-input-width"
            v-model="queryParams.name"
            placeholder="请输入应用名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button v-ripple class="glass-btn"
            plain
            @click="handleQuery"
            @mousedown="(e) => e.preventDefault()"
          >
            <i class="iconfont-mini icon-a-zu22377 mr5"></i>查询
          </el-button>
          <el-button v-ripple class="glass-btn" @click="resetQuery" @mousedown="(e) => e.preventDefault()">
            <i class="iconfont-mini icon-a-zu22378 mr5"></i>重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-list-panel" v-loading="loading">
      <Card :data="applyList" source="horizontal" variant="overview" />
      <el-empty v-if="!applyList.length && !loading" description="暂无符合条件的原子技能应用" />
    </div>

    <div class="pagecont-bottom">
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>
  </div>
</template>

<script setup name="Horizontal">
import Card from "@/views/kac/horizontal/components/card.vue";
import { ref, reactive, toRefs } from "vue";
import { listApply } from "@/api/kac/apply/apply.js";

const { proxy } = getCurrentInstance();

const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const currentType = ref("");

const skillTabs = [
  { label: "全部技能", value: "" },
  { label: "文本写作", value: "写作" },
  { label: "语义检索", value: "搜索" },
  { label: "知识问答", value: "问答" },
  { label: "效率工具", value: "效率" },
  { label: "分析助手", value: "分析" },
  { label: "模板生成", value: "模板" },
];

const typeSelectOptions = ["写作", "搜索", "问答", "效率", "分析", "模板"];

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    workspaceId: null,
    pluginId: null,
    name: null,
    type: null,
    category: 0, // 严格锁定横向通用应用
    description: null,
    status: null,
    source: null,
    tags: null,
    useScene: null,
    useCount: null,
    createTime: null,
    myApplyFlag: 0,
    orderByColumn: "id",
    isAsc: "asc",
  },
});

const { queryParams } = toRefs(data);

const applyList = ref([]);

function selectType(val) {
  currentType.value = val;
  queryParams.value.type = val || null;
  handleQuery();
}

/** 查询应用列表 */
function getList() {
  loading.value = true;
  listApply(queryParams.value).then((response) => {
    applyList.value = response.data.rows;
    total.value = response.data.total;
    loading.value = false;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  currentType.value = "";
  proxy.resetForm("queryRef");
  handleQuery();
}

getList();
</script>
<style lang="scss" scoped>
.horizontal-page {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  padding: 16px 20px 0 20px;
  position: relative;
}

.skill-category-nav {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(226, 232, 240, 0.85);
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);

  .nav-title {
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    white-space: nowrap;
    letter-spacing: 0.3px;
  }

  .skill-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .skill-chip-btn {
      appearance: none;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #64748b;
      padding: 5px 14px;
      border-radius: 16px;
      font-size: 12.5px;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

      &:hover {
        border-color: #cbd5e1;
        color: #1e293b;
        background: #ffffff;
      }

      &.active {
        border-color: #2563eb;
        background: #2563eb;
        color: #ffffff;
        font-weight: 600;
        box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
      }
    }
  }
}

.pagecont-top {
  flex-shrink: 0;
  margin-bottom: 6px;

  :deep(.el-form-item__label) {
    white-space: nowrap !important;
    font-weight: 500;
  }
}

.card-list-panel {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 4px 24px 4px;
  background: transparent;
}

.multiline-ellipsis {
  display: -webkit-box;
  -webkit-line-clamp: 2; /* 限制为2行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-tag {
  margin: 2px;
}

/* 翻页选项底栏：牢固固定在右下角，微毛玻璃白底与上边框防穿透 */
.pagecont-bottom {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid rgba(226, 232, 240, 0.85);
  padding: 0 20px;
  margin: 0 -20px;
  z-index: 10;
  box-shadow: 0 -2px 10px rgba(15, 23, 42, 0.03);

  :deep(.pagination-container) {
    background: transparent !important;
    padding: 0 !important;
    margin: 0 !important;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    width: 100%;
  }

  :deep(.el-pagination) {
    justify-content: flex-end;
  }
}
</style>
