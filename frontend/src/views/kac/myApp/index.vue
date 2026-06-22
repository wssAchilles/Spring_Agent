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
        <el-form-item label="名称" prop="name">
          <el-input
            class="el-form-input-width"
            v-model="queryParams.name"
            placeholder="请输入名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            class="el-form-input-width"
          >
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
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
        <el-form-item style="float: right; margin-right: auto">
          <el-row :gutter="15" class="btn-style">
            <el-col :span="1.5">
              <el-button
                type="primary"
                plain
                @click="handleAdd"
                v-hasPermi="['kac:apply:apply:add']"
              >
                <i class="iconfont-mini icon-xinzeng mr5"></i>
                新建应用
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-list-panel" v-loading="loading">
      <Card
        v-if="applyList.length"
        :data="applyList"
        source="myApp"
        variant="overview"
        @refresh="getList"
      />
      <el-empty v-else description="暂无我的应用" />
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

<script setup name="MyApp">
import Card from "@/views/kac/horizontal/components/card.vue";
import { ref, reactive, toRefs } from "vue";
import { listApply } from "@/api/kac/apply/apply.js";
import { ElMessage } from "element-plus";

const { proxy } = getCurrentInstance();

const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const mockApps = [
  { id: 101, name: '我的知识问答', description: '基于企业文档的智能问答系统', icon: 'ChatDotRound', status: 1, tags: '[{"name":"问答"},{"name":"知识"}]' },
  { id: 102, name: '文档摘要工具', description: '自动提取长文档核心信息', icon: 'Document', status: 1, tags: '[{"name":"效率"},{"name":"文档"}]' },
  { id: 103, name: '代码审查助手', description: 'AI驱动的代码质量分析工具', icon: 'Monitor', status: 0, tags: '[{"name":"开发"},{"name":"AI"}]' }
];

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    status: null,
    myApplyFlag: 1,
    orderByColumn: "createTime",
    isAsc: "desc",
  },
});

const { queryParams } = toRefs(data);

const applyList = ref([]);

/** 查询我的应用列表 */
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
  if (queryParams.value.status !== null && queryParams.value.status !== '') {
    filtered = filtered.filter(
      (item) => item.status === queryParams.value.status
    );
  }
  total.value = filtered.length;
  const start = (queryParams.value.pageNum - 1) * queryParams.value.pageSize;
  applyList.value = filtered.slice(start, start + queryParams.value.pageSize);
}

/** 新建应用 */
function handleAdd() {
  ElMessage({
    message: "功能正在开发中",
    type: "warning",
  });
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
    width: 41px !important;
  }
}
</style>
