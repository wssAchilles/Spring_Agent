<template>
  <div class="app-container my-solution-page glass-card" ref="app-container">
    <!-- 顶部私有资产治理看板 -->
    <div class="asset-metrics-board">
      <div class="metric-card">
        <div class="metric-icon total-icon">
          <i class="iconfont icon-a-zu22377"></i>
        </div>
        <div class="metric-info">
          <div class="metric-title">自建方案总数</div>
          <div class="metric-num">{{ total }} <span class="metric-unit">套</span></div>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon running-icon">
          <i class="iconfont icon-yunxing"></i>
        </div>
        <div class="metric-info">
          <div class="metric-title">在线运行中</div>
          <div class="metric-num">{{ runningCount }} <span class="metric-unit">套</span></div>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon draft-icon">
          <i class="iconfont icon-caogao"></i>
        </div>
        <div class="metric-info">
          <div class="metric-title">草稿待发布</div>
          <div class="metric-num">{{ draftCount }} <span class="metric-unit">套</span></div>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon invoke-icon">
          <i class="iconfont icon-shuju"></i>
        </div>
        <div class="metric-info">
          <div class="metric-title">月度调用量</div>
          <div class="metric-num">1,280 <span class="metric-unit">次</span></div>
        </div>
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
            placeholder="请输入方案名称"
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
        <el-form-item style="float: right; margin-right: auto">
          <el-row :gutter="15" class="btn-style">
            <el-col :span="1.5">
              <el-button v-ripple class="glass-btn primary-add-btn"
                type="primary"
                @click="handleAdd"
                v-hasPermi="['kac:solution:solution:add']"
              >
                <i class="iconfont-mini icon-xinzeng mr5"></i>
                新建私有方案</el-button
              >
            </el-col>
          </el-row>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-list-panel">
      <SolutionCard
        :data="solutionList"
        source="myApp"
        variant="overview"
        @refresh="getList"
        @edit="handleEdit"
      />
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

    <!-- 添加或修改解决方案对话框 -->
    <el-dialog
      class="glass-card"
      :title="title"
      v-model="open"
      width="800px"
      append-to="body"
      draggable
    >
      <template #header>
        <span role="heading" aria-level="2" class="el-dialog__title">
          {{ title }}
        </span>
      </template>
      <el-form
        ref="solutionRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        @submit.prevent
      >
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="关联应用" prop="myAppIds">
              <div>
                <el-button v-ripple class="glass-btn"
                  :disabled="form.status && form.status != 0"
                  v-on:click="selectApp"
                  plain
                >
                  <i class="iconfont icon-upload-cloud-line mr5"></i
                  >导入关联应用
                </el-button>
              </div>
            </el-form-item>
            <div style="margin: 0px 0 10px 80px">
              <el-table
                stripe
                height="300px"
                v-loading="loading"
                :data="appTitles"
              >
                <el-table-column
                  width="450"
                  label="应用名称"
                  prop="status"
                  show-overflow-tooltip
                >
                  <template #default="scope">
                    {{ scope.row.name || "-" }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="操作"
                  align="center"
                  class-name="small-padding fixed-width"
                >
                  <template #default="scope">
                    <el-button v-ripple class="glass-btn"
                      link
                      icon="Delete"
                      :disabled="form.status && form.status != 0"
                      @click="removeSelectApp(scope.row)"
                      >删除
                    </el-button>
                  </template>
                </el-table-column>

                <template #empty>
                  <div class="emptyBg">
                    <img
                      src="@/assets/system/images/no_data/noData.png"
                      alt=""
                      style="width: 100px; height: 100px"
                    />
                    <p>暂无记录</p>
                  </div>
                </template>
              </el-table>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item
              label="封面图"
              prop="coverImage"
              class="image-form-item"
            >
              <image-upload
                v-model="form.coverImage"
                :limit="1"
                :fileSize="10"
                :isShowTip="true"
                :platForm="platForm"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="标签" prop="tags">
              <div class="flex gap-2">
                <el-tag
                  v-for="tag in form.items.row"
                  :key="tag"
                  closable
                  :disable-transitions="false"
                  @close="handleClose(tag)"
                >
                  {{ tag.name }}
                </el-tag>
                <el-input
                  v-if="inputVisible"
                  ref="InputRef"
                  v-model="inputValue"
                  class="w-20"
                  size="small"
                  @keyup.enter="handleInputConfirm"
                  @blur="handleInputConfirm"
                />
                <el-button v-ripple
                  v-else
                  class="button-new-tag glass-btn"
                  size="small"
                  @click="showInput"
                >
                  + 添加标签
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                maxlength="512"
                placeholder="请输入描述"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                maxlength="512"
                placeholder="请输入备注"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-ripple class="glass-btn" size="small" @click="cancel">取 消</el-button>
          <el-button v-ripple class="glass-btn" size="small" @click="submitForm"
            >确 定</el-button
          >
        </div>
      </template>
    </el-dialog>

    <SelectMyApp
      v-model:visible="selectAppVisible"
      :selectedIds="form.myAppIds"
      @confirm="selectConfirmData"
    ></SelectMyApp>
  </div>
</template>

<script setup name="MySolution">
import SolutionCard from "./components/solutionCard.vue";
import SelectMyApp from "./components/selectMyApp.vue";
import { getCurrentInstance, nextTick, ref, reactive, toRefs, computed } from "vue";
import {
  addSolution,
  listSolution,
  updateSolution,
  getSolution,
} from "@/api/kac/solution/solution";
import { listSolutionApply } from "@/api/kac/solution/solutionApply.js";
import { listApply } from "@/api/kac/apply/apply.js";
import useUserStore from "@/store/system/user.js";

const { proxy } = getCurrentInstance();
const userStore = useUserStore();

const open = ref(false);
const selectAppVisible = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const title = ref("");
const platForm = ref('local');
const appTitles = ref([]);
const openDetail = ref(false);
const allApplyList = ref([]); // 存储所有应用列表

const solutionList = ref([]);
const runningCount = computed(() => solutionList.value.filter((s) => s.status === 1).length);
const draftCount = computed(() => solutionList.value.filter((s) => s.status !== 1).length);

const inputValue = ref("");
const inputVisible = ref(false);
const InputRef = ref(null);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    userId: userStore.id,
    mySolutionFlag: 1,
    orderByColumn: 'createTime',
    isAsc: 'desc'
  },
  rules: {
    name: [{ required: true, message: "名称不能为空", trigger: "blur" }],
    description: [{ required: true, message: "描述不能为空", trigger: "blur" }],
    myAppIds: [
      { required: true, message: "关联应用不能为空", trigger: "blur" },
    ],
    tags: [
      {
        required: true,
        validator: (rule, value, callback) => {
          if (!form.value.items.row || form.value.items.row.length === 0) {
            callback(new Error('请至少添加一个标签'));
          } else {
            callback();
          }
        },
        trigger: 'change'
      }
    ]
  },
});

const { queryParams, form, rules } = toRefs(data);

// 处理输入确认（回车或失去焦点）
const handleInputConfirm = () => {
  const value = inputValue.value.trim();
  if (value) {
    form.value.items.row.push({ name: value });
  }
  inputVisible.value = false;
  inputValue.value = "";
};

// 显示输入框并自动聚焦
const showInput = () => {
  inputVisible.value = true;
  nextTick(() => {
    if (InputRef.value && InputRef.value.input) {
      InputRef.value.input.focus();
    }
  });
};

/** 查询解决方案列表 */
function getList() {
  loading.value = true;
  listSolution(queryParams.value).then((response) => {
    solutionList.value = response.data.rows;
    total.value = response.data.total;
    loading.value = false;
  });
  listApply({ pageNum: 1, pageSize: 999 }).then((response) => {
    allApplyList.value = response.data.rows || [];
  });
}

function selectApp() {
  selectAppVisible.value = true;
}

function selectConfirmData(data) {
  appTitles.value = data;
  form.value.myAppIds = data.map((e) => e.id).join(",");
}

function removeSelectApp(row) {
  const index = appTitles.value.findIndex((item) => item.id === row.id);
  if (index > -1) {
    appTitles.value.splice(index, 1);
    form.value.myAppIds = appTitles.value.map((e) => e.id).join(",");
  }
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

// 取消按钮
function cancel() {
  open.value = false;
  openDetail.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    workspaceId: null,
    name: null,
    description: null,
    coverImage: null,
    tags: null,
    items: {
      row: [],
    },
    status: null,
    mySolutionFlag: null,
    validFlag: null,
    delFlag: null,
    createBy: null,
    creatorId: null,
    createTime: null,
    updateBy: null,
    updaterId: null,
    updateTime: null,
    remark: null,
    myAppIds: null,
  };
  appTitles.value = [];
  proxy.resetForm("solutionRef");
}

// 删除指定的 tag
const handleClose = (tag) => {
  const index = form.value.items.row.findIndex((t) => t.name === tag.name);
  if (index !== -1) {
    form.value.items.row.splice(index, 1);
  }
};

/** 新增按钮操作 */
function handleAdd() {
  reset();
  appTitles.value = [];
  form.value.myAppIds = null;
  form.value.items = { row: [] };
  open.value = true;
  title.value = "新增我的解决方案";
}

/** 编辑按钮操作 */
function handleEdit(row) {
  reset();
  // 调用接口查询明细
  getSolution(row.id).then(response => {
    const data = response.data;
    // 复制行数据到表单
    form.value = { ...data };
    // 解析 tags
    if (data.tags) {
      try {
        const parsedTags = JSON.parse(data.tags);
        form.value.items = { row: Array.isArray(parsedTags) ? parsedTags : [] };
      } catch {
        form.value.items = { row: [] };
      }
    } else {
      form.value.items = { row: [] };
    }
    // 加载关联应用数据
    if (data.id) {
      // 调用接口查询解决方案关联应用列表
      listSolutionApply({ solutionId: data.id, pageNum: 1, pageSize: 999 }).then(response => {
        const solutionApplyList = response.data.rows || [];
        // 获取 apply_id 列表
        const applyIds = solutionApplyList.map(item => item.applyId).filter(id => id);
        if (applyIds.length > 0 && allApplyList.value.length > 0) {
          // 从缓存的应用列表中匹配名称
          const applyMap = new Map(allApplyList.value.map(item => [item.id, item]));
          appTitles.value = applyIds.map(id => {
            const apply = applyMap.get(Number(id));
            return {
              id: id,
              name: apply?.name || '-'
            };
          });
        } else {
          appTitles.value = [];
        }
      }).catch(() => {
        appTitles.value = [];
      });
    } else {
      appTitles.value = [];
    }
    open.value = true;
    title.value = "修改我的解决方案";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["solutionRef"].validate(valid => {
    if (valid) {
      if (form.value.myAppIds == null || form.value.myAppIds == "") {
        proxy.$modal.msgWarning(`关联应用不能为空`);
        return;
      }
      if (form.value.items.row) {
        form.value.tags = JSON.stringify(form.value.items.row);
      }
      if (form.value.id != null) {
        updateSolution(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        }).catch(error => {
        });
      } else {
        form.value.status = 0;
        form.value.mySolutionFlag = 1;
        addSolution(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        }).catch(error => {
        });
      }
    }
  });
}

getList();
</script>
<style lang="scss" scoped>
::v-deep .el-dialog__body {
  max-height: 700px;
  overflow-y: auto;
  padding-right: 10px;
}
.my-solution-page {
  box-sizing: border-box;
  padding-bottom: 45px;
}

.asset-metrics-board {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 14px;

  .metric-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 20px;
    background: rgba(255, 255, 255, 0.78);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid rgba(226, 232, 240, 0.9);
    border-radius: 12px;
    box-shadow: 0 2px 10px rgba(15, 23, 42, 0.03);
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
    }

    .metric-icon {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;

      &.total-icon {
        background: rgba(59, 130, 246, 0.12);
        color: #2563eb;
      }
      &.running-icon {
        background: rgba(16, 185, 129, 0.12);
        color: #059669;
      }
      &.draft-icon {
        background: rgba(245, 158, 11, 0.12);
        color: #d97706;
      }
      &.invoke-icon {
        background: rgba(99, 102, 241, 0.12);
        color: #4f46e5;
      }
    }

    .metric-info {
      .metric-title {
        font-size: 13px;
        color: #64748b;
        font-weight: 500;
        margin-bottom: 4px;
      }
      .metric-num {
        font-size: 22px;
        font-weight: 700;
        color: #0f172a;
        line-height: 1.1;

        .metric-unit {
          font-size: 12px;
          font-weight: 400;
          color: #94a3b8;
          margin-left: 2px;
        }
      }
    }
  }
}

.card-list-panel {
  margin-top: 15px;
  padding: 15px;
  background: #ffffff;
  border-radius: 2px;
}

.multiline-ellipsis {
  display: -webkit-box;
  -webkit-line-clamp: 2; /* 限制为2行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}
.gap-2 {
  gap: 0.5rem;
}
.flex {
  display: flex;
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
  :deep(.el-form-item__label) {
    white-space: nowrap !important;
    font-weight: 500;
  }
}
</style>

