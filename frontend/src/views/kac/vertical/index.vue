<template>
  <div class="app-container vertical-industry-page glass-card" ref="app-container">
    <!-- 顶部行业矩阵筛选胶囊 -->
    <div class="industry-matrix-nav">
      <div class="nav-title">行业矩阵深潜:</div>
      <div class="industry-chips">
        <button
          v-for="tab in industryTabs"
          :key="tab.value"
          class="industry-chip-btn"
          :class="{ active: currentIndustry === tab.value }"
          @click="selectIndustry(tab.value)"
        >
          <span class="chip-label">{{ tab.label }}</span>
        </button>
      </div>
    </div>

    <!-- 搜索筛选栏 -->
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
        <el-form-item label="行业分类" prop="industry">
          <el-select
            v-model="queryParams.industry"
            placeholder="请选择行业分类"
            clearable
            class="el-form-input-width"
            @change="handleQuery"
          >
            <el-option
              v-for="item in industrySelectOptions"
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
            placeholder="请输入行业应用名称"
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

    <!-- 专属垂直行业卡片列表 -->
    <div class="card-list-panel" v-loading="loading">
      <VerticalCard
        v-if="applyList.length"
        :data="applyList"
      />
      <el-empty v-else description="暂无该行业相关应用" />
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

<script setup name="Vertical">
import { ref, reactive, toRefs, onMounted } from "vue";
import VerticalCard from "@/views/kac/vertical/components/VerticalCard.vue";
import { listApply } from "@/api/kac/apply/apply.js";

const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const currentIndustry = ref("");

const industryTabs = [
  { label: "全部行业", value: "" },
  { label: "金融科技", value: "金融科技" },
  { label: "智慧医疗", value: "智慧医疗" },
  { label: "智能制造", value: "智能制造" },
  { label: "智慧教育", value: "智慧教育" },
  { label: "数字政务", value: "数字政务" },
  { label: "跨境电商", value: "跨境电商" },
  { label: "智慧水利", value: "智慧水利" },
  { label: "智慧能源", value: "智慧能源" },
];

const industrySelectOptions = [
  "金融科技",
  "智慧医疗",
  "智能制造",
  "智慧教育",
  "数字政务",
  "跨境电商",
  "智慧水利",
  "智慧能源",
];

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    industry: null,
    category: 1, // 严格固定为纵向行业应用
    orderByColumn: "id",
    isAsc: "asc",
  },
});

const { queryParams } = toRefs(data);
const applyList = ref([]);

function selectIndustry(val) {
  currentIndustry.value = val;
  queryParams.value.industry = val || null;
  handleQuery();
}

/** 查询行业应用列表 */
function getList() {
  loading.value = true;
  listApply(queryParams.value)
    .then((response) => {
      applyList.value = response.data.rows || [];
      total.value = response.data.total || 0;
    })
    .catch(() => {
      applyList.value = [];
      total.value = 0;
    })
    .finally(() => {
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
  queryParams.value.name = null;
  queryParams.value.industry = null;
  currentIndustry.value = "";
  handleQuery();
}

onMounted(() => {
  getList();
});
</script>

<style scoped lang="scss">
.vertical-industry-page {
  padding: 24px;
}

.industry-matrix-nav {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(248, 250, 252, 0.85);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 10px 16px;
  margin-bottom: 20px;

  .nav-title {
    font-size: 13px;
    font-weight: 700;
    color: #334155;
    white-space: nowrap;
  }

  .industry-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .industry-chip-btn {
    border: 1px solid transparent;
    background: transparent;
    padding: 5px 12px;
    border-radius: 20px;
    font-size: 13px;
    color: #64748b;
    cursor: pointer;
    transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

    &:hover {
      color: #2563eb;
      background: rgba(37, 99, 235, 0.06);
    }

    &.active {
      background: #2563eb;
      color: #ffffff;
      font-weight: 600;
      box-shadow: 0 2px 8px rgba(37, 99, 235, 0.25);
    }
  }
}

.pagecont-top {
  margin-bottom: 20px;
}

.card-list-panel {
  min-height: 380px;
}

.pagecont-bottom {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}
</style>
