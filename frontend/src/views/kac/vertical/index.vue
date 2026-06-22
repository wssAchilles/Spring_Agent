<template>
  <div class="app-container" ref="app-container">
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
        <el-form-item label="行业分类" prop="type">
          <el-select
            v-model="queryParams.type"
            placeholder="请选择行业分类"
            clearable
            class="el-form-input-width"
          >
            <el-option
              v-for="item in industryOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input
            class="el-form-input-width"
            v-model="queryParams.name"
            placeholder="请输入名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            plain
            type="primary"
            @click="handleQuery"
            @mousedown="(e) => e.preventDefault()"
          >
            <i class="iconfont-mini icon-a-zu22377 mr5"></i>查询
          </el-button>
          <el-button @click="resetQuery" @mousedown="(e) => e.preventDefault()">
            <i class="iconfont-mini icon-a-zu22378 mr5"></i>重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-list-panel" v-loading="loading">
      <Card
        v-if="applyList.length"
        :data="applyList"
        source="vertical"
        variant="overview"
      />
      <el-empty v-else description="暂无行业应用" />
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
import Card from "@/views/kac/horizontal/components/card.vue";
import { ref, reactive, toRefs } from "vue";
import { listApply } from "@/api/kac/apply/apply.js";

const { proxy } = getCurrentInstance();

const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const industryOptions = ["金融", "医疗", "教育", "制造", "零售"];

const mockApps = [
  { id: 1, name: '金融风控助手', description: '基于AI的金融风险评估工具', icon: 'Money', status: 1, tags: '[{"name":"金融"},{"name":"AI"}]', type: '金融' },
  { id: 2, name: '医疗知识库', description: '医学文献智能检索与问答系统', icon: 'FirstAidKit', status: 1, tags: '[{"name":"医疗"},{"name":"知识库"}]', type: '医疗' },
  { id: 3, name: '智能教学助手', description: '个性化学习路径推荐引擎', icon: 'Reading', status: 1, tags: '[{"name":"教育"},{"name":"AI"}]', type: '教育' },
  { id: 4, name: '产线质检系统', description: '基于视觉AI的产品质量检测', icon: 'Monitor', status: 1, tags: '[{"name":"制造"},{"name":"视觉"}]', type: '制造' },
  { id: 5, name: '智能推荐引擎', description: '用户行为分析与商品推荐', icon: 'ShoppingCart', status: 1, tags: '[{"name":"零售"},{"name":"AI"}]', type: '零售' },
  { id: 6, name: '信贷审批助手', description: '自动化信贷申请审核流程', icon: 'CreditCard', status: 1, tags: '[{"name":"金融"},{"name":"自动化"}]', type: '金融' },
  { id: 7, name: '电子病历分析', description: '病历信息结构化与智能分析', icon: 'Document', status: 1, tags: '[{"name":"医疗"},{"name":"NLP"}]', type: '医疗' },
  { id: 8, name: '在线考试系统', description: '智能组卷与自动评分平台', icon: 'EditPen', status: 1, tags: '[{"name":"教育"},{"name":"考试"}]', type: '教育' }
];

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    type: null,
    category: 1,
    myApplyFlag: 0,
    orderByColumn: "createTime",
    isAsc: "desc",
  },
});

const { queryParams } = toRefs(data);

const applyList = ref([]);

/** 查询行业应用列表 */
function getList() {
  loading.value = true;
  listApply(queryParams.value)
    .then((response) => {
      const rows = response.data.rows;
      if (rows && rows.length > 0) {
        applyList.value = rows;
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
  let filtered = [...mockApps];
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
  applyList.value = filtered.slice(start, start + queryParams.value.pageSize);
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

getList();
</script>
<style lang="scss" scoped>
.app-container {
  box-sizing: border-box;
  padding-bottom: 45px;
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
    width: 65px !important;
  }
}
</style>
