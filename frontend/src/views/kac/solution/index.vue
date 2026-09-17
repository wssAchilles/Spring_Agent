<template>
  <div class="app-container solution-marketplace-page glass-card" ref="app-container">
    <!-- 顶部方案全景蓝图领域胶囊导航 -->
    <div class="solution-category-nav">
      <div class="nav-title">方案全景蓝图:</div>
      <div class="solution-chips">
        <button
          v-for="tab in typeTabs"
          :key="tab.value"
          class="solution-chip-btn"
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
        label-width="75px"
        v-show="showSearch"
        @submit.prevent
      >
        <el-form-item label="名称" prop="name">
          <el-input
            class="el-form-input-width"
            v-model="queryParams.name"
            placeholder="请输入名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select
            v-model="queryParams.type"
            placeholder="请选择类型"
            clearable
            class="el-form-input-width"
            @change="handleQuery"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
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
        <el-form-item style="float: right; margin-right: auto">
          <el-row :gutter="15" class="btn-style">
            <el-col :span="1.5">
              <el-button v-ripple class="glass-btn"
                plain
                @click="handleAdd"
                v-hasPermi="['kac:solution:solution:add']"
              >
                <i class="iconfont-mini icon-xinzeng mr5"></i>
                新建方案
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-list-panel" v-loading="loading">
      <SolutionCard
        v-if="solutionList.length"
        :data="solutionList"
        source="solution"
        variant="overview"
      />
      <el-empty v-else description="暂无解决方案" />
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

<script setup name="Solution">
import SolutionCard from "@/views/kac/mySolution/components/solutionCard.vue";
import { ref, reactive, toRefs } from "vue";
import { listSolution } from "@/api/kac/solution/solution";
import { useRouter } from "vue-router";

const router = useRouter();

const { proxy } = getCurrentInstance();

const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const currentType = ref("");

const typeTabs = [
  { label: "全部蓝图", value: "" },
  { label: "技术架构", value: "技术" },
  { label: "企业管理", value: "管理" },
  { label: "科研学术", value: "研究" },
  { label: "数据治理", value: "数据" },
  { label: "智能客服", value: "客服" },
];

const typeOptions = ["技术", "管理", "研究", "数据", "客服"];

const mockSolutions = [
  { id: 1, name: 'Flutter开发知识体系', description: '覆盖Flutter开发全流程的知识管理方案，包含框架文档、组件库、最佳实践等', coverImage: null, status: 1, appCount: 5, type: '技术', tags: '[{"name":"技术"},{"name":"Flutter"}]' },
  { id: 2, name: '企业文档管理方案', description: '一站式企业文档管理解决方案，支持文档分类、检索、协作和版本控制', coverImage: null, status: 1, appCount: 3, type: '管理', tags: '[{"name":"管理"},{"name":"文档"}]' },
  { id: 3, name: 'AI研究助手', description: '面向科研人员的AI辅助研究工具集，涵盖文献检索、数据分析、论文写作', coverImage: null, status: 1, appCount: 4, type: '研究', tags: '[{"name":"研究"},{"name":"AI"}]' },
  { id: 4, name: '数据治理平台', description: '企业级数据治理方案，包含数据质量、元数据管理、数据安全等功能', coverImage: null, status: 1, appCount: 6, type: '数据', tags: '[{"name":"数据"},{"name":"治理"}]' },
  { id: 5, name: '智能客服方案', description: '基于NLP的智能客服解决方案，支持多轮对话、知识库问答、工单管理', coverImage: null, status: 1, appCount: 3, type: '客服', tags: '[{"name":"客服"},{"name":"NLP"}]' }
];

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    type: null,
    mySolutionFlag: 0,
    orderByColumn: "id",
    isAsc: "asc",
  },
});

const { queryParams } = toRefs(data);

const solutionList = ref([]);

function selectType(val) {
  currentType.value = val;
  queryParams.value.type = val || null;
  handleQuery();
}

/** 查询解决方案列表 */
function getList() {
  loading.value = true;
  listSolution(queryParams.value)
    .then((response) => {
      const rows = response.data.rows;
      if (rows && rows.length > 0) {
        solutionList.value = rows;
        total.value = response.data.total;
      } else {
        applyMockData();
      }
    })
    .catch(() => {
      applyMockData();
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 使用模拟数据 */
function applyMockData() {
  let filtered = [...mockSolutions];
  if (queryParams.value.name) {
    filtered = filtered.filter((item) =>
      item.name.includes(queryParams.value.name)
    );
  }
  if (queryParams.value.type) {
    filtered = filtered.filter(
      (item) => item.type === queryParams.value.type
    );
  }
  total.value = filtered.length;
  const start = (queryParams.value.pageNum - 1) * queryParams.value.pageSize;
  solutionList.value = filtered.slice(start, start + queryParams.value.pageSize);
}

/** 新建方案 */
function handleAdd() {
  router.push({ path: "/kac/solution/solutionDetail" });
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
.solution-marketplace-page {
  box-sizing: border-box;
  padding-bottom: 45px;
}

.solution-category-nav {
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

  .solution-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .solution-chip-btn {
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
        border-color: #0284c7;
        background: #0284c7;
        color: #ffffff;
        font-weight: 600;
        box-shadow: 0 2px 8px rgba(2, 132, 199, 0.25);
      }
    }
  }
}

.card-list-panel {
  margin-top: 15px;
  padding: 15px;
  background: #ffffff;
  border-radius: 2px;
  min-height: 200px;
}

.pagecont-bottom {
  position: fixed;
  bottom: 0;
  width: 100%;
  left: 0;
  height: 60px;
  background: #ffffff;
  border-radius: 2px 2px 2px 2px;
  line-height: 60px;
  margin: 0;
  padding: 0 18px 0 0;
  flex: none;
  .pagination-container {
    margin-top: 0;
  }
}
.pagecont-top {
  ::v-deep .el-form-item:first-child .el-form-item__label {
    width: 41px !important;
  }
}
</style>
