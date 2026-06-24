<template>
  <div class="app-container">
    <div class="page-wrapper">
      <!-- 页面标题区域 -->
      <div class="page-header">
        <div class="header-icon-wrapper">
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
        </div>
        <div class="header-text">
          <h2 class="header-title">{{ title }}</h2>
          <p class="header-subtitle">上传知识文件并配置切分方式，以便进行向量化检索</p>
        </div>
      </div>

      <!-- 基本信息卡片 -->
      <div class="form-card">
        <div class="card-section-header">
          <div class="section-dot"></div>
          <span>基本信息</span>
        </div>
        <el-form
          ref="documentRef"
          :model="form"
          :rules="rules"
          label-width="96px"
          @submit.prevent
        >
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="所属知识库" prop="knowledgeBaseId">
                <el-select
                  disabled
                  v-model="form.knowledgeBaseId"
                  placeholder="请选择知识库"
                  class="form-full-width"
                >
                  <el-option
                    v-for="item in knowledgeBaseList"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  >
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="所属分类" prop="categoryId">
                <el-tree-select
                  class="form-full-width"
                  v-model="form.categoryId"
                  :data="KcOptions"
                  :props="{ value: 'id', label: 'label', children: 'children' }"
                  value-key="id"
                  placeholder="请选择所属分类"
                  check-strictly
                  @change="handleTypeChange"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="文件描述" prop="description">
                <el-input
                  v-model="form.description"
                  placeholder="请输入文件描述"
                  class="form-full-width"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="文件路径" prop="path">
                <div class="upload-zone">
                  <FileUpload
                    v-model="form.path"
                    :fileName="form.name"
                    :fileSize="15"
                    :platForm="platForm"
                    :fileType="[
                      'txt',
                      'pdf',
                      'html',
                      'xlsx',
                      'xls',
                      'docx',
                      'csv',
                      'md',
                      'mdx',
                      'htm',
                      'markdown',
                    ]"
                    @update:fileName="updateFormFileName"
                    @delete:index="handleDeleteFile"
                  ></FileUpload>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 切分模式选择卡片 -->
      <div
        v-if="settingBase == null"
        class="form-card"
      >
        <div class="card-section-header">
          <div class="section-dot"></div>
          <span>切分模式</span>
        </div>
        <div class="mode-options">
          <div
            :class="['mode-card', form.mode === 'recursive' ? 'active' : '']"
            @click="handleMode('recursive')"
          >
            <div class="mode-card-inner">
              <div class="mode-card-icon recursive-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
              </div>
              <div>
                <div class="mode-card-title">递归切分（推荐）</div>
                <div class="mode-card-desc">按段落→换行→标点递归切分，语义完整性最佳</div>
              </div>
            </div>
            <div v-if="form.mode === 'recursive'" class="mode-badge">推荐</div>
          </div>
          <div
            :class="['mode-card', form.mode === 'custom' ? 'active' : '']"
            @click="handleMode('custom')"
          >
            <div class="mode-card-inner">
              <div class="mode-card-icon custom-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
              </div>
              <div>
                <div class="mode-card-title">通用切分</div>
                <div class="mode-card-desc">按指定分隔符切分，适合简单文本</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 递归切分配置 -->
      <div
        v-if="form.mode === 'recursive'"
        class="form-card config-card"
      >
        <div class="config-header">
          <div class="config-header-left">
            <div class="mode-card-icon recursive-icon small">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>
            </div>
            <div>
              <div class="config-title">递归切分配置</div>
              <div class="config-desc">按段落→换行→句号→逗号等标点递归切分，中文文档推荐使用</div>
            </div>
          </div>
        </div>
        <div class="config-body">
          <div class="config-grid">
            <div class="config-item">
              <div class="config-label">
                <span>分段最大长度</span>
                <el-tooltip content="每个切片的最大字符数。中文推荐 512（约 256 tokens）">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="help-icon"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </el-tooltip>
              </div>
              <el-input-number
                v-model="form.maxTokens"
                controls-position="right"
                :min="100"
                :max="4000"
                class="config-input"
              ></el-input-number>
            </div>
            <div class="config-item">
              <div class="config-label">
                <span>分段重叠长度</span>
                <el-tooltip content="相邻切片的重叠字符数，建议为最大长度的 10%-25%">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="help-icon"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </el-tooltip>
              </div>
              <el-input-number
                v-model="form.chunkOverlap"
                controls-position="right"
                :min="0"
                :max="500"
                class="config-input"
              ></el-input-number>
            </div>
          </div>
          <div class="preprocess-section">
            <div class="preprocess-title">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
              <span>文本预处理规则</span>
            </div>
            <el-checkbox v-model="form.removeExtraSpaces">替换掉连续的空格、换行符和制表符</el-checkbox>
            <el-checkbox v-model="form.removeUrlsEmails">删除所有 URL 和电子邮件地址</el-checkbox>
          </div>
        </div>
      </div>

      <!-- 通用切分配置 -->
      <div
        v-if="settingBase == null || settingBase.mode === 'custom'"
        :class="['form-card', 'config-card', form.mode !== 'custom' ? 'config-inactive' : '']"
      >
        <div class="config-header">
          <div class="config-header-left">
            <div :class="['mode-card-icon', 'custom-icon', 'small', form.mode === 'custom' ? '' : 'inactive']">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
            </div>
            <div>
              <div :class="['config-title', form.mode !== 'custom' ? 'inactive' : '']">通用</div>
              <div class="config-desc">通用文本分块模式，检索和召回的块相同的</div>
            </div>
          </div>
        </div>
        <div class="config-body" v-if="form.mode == 'custom'">
          <div class="config-grid config-grid-3">
            <div class="config-item">
              <div class="config-label">
                <span>分段标识符</span>
                <el-tooltip
                  content="分隔符是用于分隔文本的字符。\n\n和 \n 是常用于分隔段落和行的分隔符。用逗号连接分隔符(\n\n,\n)，当段落超过最大块长度时，会按行进行分割。你也可以使用自定义的特殊分隔符(例如 ***)。"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="help-icon"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </el-tooltip>
              </div>
              <el-input
                v-model="form.separator"
                placeholder="请输入内容"
                class="config-input"
              ></el-input>
            </div>
            <div class="config-item">
              <div class="config-label">
                <span>分段最大长度</span>
              </div>
              <el-input-number
                v-model="form.maxTokens"
                controls-position="right"
                class="config-input"
              ></el-input-number>
            </div>
            <div class="config-item">
              <div class="config-label">
                <span>分段重叠长度</span>
                <el-tooltip
                  content="设置分段之间的重叠长度可以保留分段之间的语义关系，提升召回效果。建议设置为最大分段长度的10%-25%"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="help-icon"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </el-tooltip>
              </div>
              <el-input-number
                v-model="form.chunkOverlap"
                controls-position="right"
                @change="handleChange"
                class="config-input"
              ></el-input-number>
            </div>
          </div>
          <div class="preprocess-section">
            <div class="preprocess-title">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
              <span>文本预处理规则</span>
            </div>
            <el-checkbox v-model="form.removeExtraSpaces"
              >替换掉连续的空格、换行符和制表符</el-checkbox
            >
            <el-checkbox v-model="form.removeUrlsEmails"
              >删除所有 URL 和电子邮件地址</el-checkbox
            >
            <div class="qa-section">
              <el-checkbox
                v-model="checkedyy"
                @change="eckboxChange"
                :disabled="settingBase"
              >
                <span>使用Q&A分段,语言</span>
                <el-select
                  v-model="form.docLanguage"
                  placeholder="请选择"
                  style="width: 160px; border: none"
                  :disabled="!checkedyy"
                >
                  <el-option
                    v-for="dict in kmc_language_type"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
                <span style="margin-left: 8px">模型</span>
                <el-select
                  v-model="form.chatModel"
                  placeholder="请选择"
                  style="width: 160px; border: none"
                  :disabled="!checkedyy"
                >
                  <el-option-group
                    v-for="group in chatModel"
                    :key="group.label.zh_Hans"
                    :label="group.label.zh_Hans"
                  >
                    <el-option
                      v-for="item in group.models"
                      :key="item.model"
                      :label="item.model"
                      :value="item.model"
                    />
                  </el-option-group>
                </el-select>
              </el-checkbox>
              <el-tooltip content="开启后将会消耗额外的 token">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="help-icon"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              </el-tooltip>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button class="btn-cancel" @click="cancel">取消</el-button>
        <el-button type="primary" class="btn-submit" @click="submitForm">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 6px"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
          确定
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import FileUpload from "@/components/FileUpload2/index.vue";
import { kmcCategoryTree } from "@/api/kmc/kmcCategory/kmcCategory.js";
import { getKmcKnowledgeBaseList } from "@/api/kmc/knowledgeBase/knowledgeBase.js";
import {
  addDocument,
  updateDocument,
  getDocument,
  getOne,
} from "@/api/kmc/kmcDocument/kmcDocument.js";
import { getChatModelDict } from "@/api/ai/myModel/myModel.js";
import moment from "moment/moment.js";
const platForm = ref("aliyun-oss-qt");
const { proxy } = getCurrentInstance();
const title = ref("新增知识文件");
const { kmc_language_type } = proxy.useDict("kmc_language_type");
const data = reactive({
  form: {},
  rules: {
    knowledgeBaseId: [
      { required: true, message: "所属知识库不能为空", trigger: "blur" },
    ],
    categoryId: [
      { required: true, message: "所属分类不能为空", trigger: "blur" },
    ],
    // name: [{ required: true, message: '文件名称不能为空', trigger: 'blur' }],
    path: [{ required: true, message: "文件路径不能为空", trigger: "blur" }],
  },
});
const checkedyy = ref(false);
const knowledgeBaseList = ref([]);
const KcOptions = ref(undefined);
const checkList = ref(["a", "b"]);
const settingBase = ref(null);
const route = useRoute();
const { form, rules } = toRefs(data);
const chatModel = ref([]);

function handleMode(model) {
  form.value.mode = model;
  form.value.docForm = "text_model";
  if (model === "hierarchical") {
    form.value.docForm = "hierarchical_model";
  }
  if (model === "recursive") {
    form.value.separator = "\\n\\n";
    form.value.maxTokens = 512;
    form.value.chunkOverlap = 64;
  } else if (model === "custom") {
    form.value.separator = "\\n\\n";
    form.value.maxTokens = 1024;
    form.value.chunkOverlap = 50;
  }
}

function getKmcCategoryTree() {
  kmcCategoryTree({ knowledgeBaseId: form.value.knowledgeBaseId }).then(
    (response) => {
      KcOptions.value = response.data;
      if (!proxy.$route.query.id) {
        handleTypeChange(proxy.$route.query.categoryId);
      }
    }
  );
}
/** 处理选择变化的方法 */
const handleTypeChange = (value) => {
  const selectedNode = findNodeById(KcOptions.value, value);
  if (selectedNode) {
    form.value.categoryName = selectedNode.label;
  }
};
const eckboxChange = () => {
  if (checkedyy) {
    form.value.docForm = "qa_model";
  } else {
    form.value.docForm = "text_model";
  }
};
/** 递归查找选中的节点 */
const findNodeById = (nodes, id) => {
  for (let node of nodes) {
    if (node.id === id) {
      return node;
    }
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
};
const cancel = () => {
  const obj = {
    path: `/kmc/${route.params.kbId}/kmcDocument`,
  };
  proxy.$tab.closeOpenPage(obj);
};

function submitForm() {
  form.value.docForm = checkedyy.value ? "qa_model" : "text_model";
  proxy.$refs["documentRef"].validate((valid) => {
    if (valid) {
      proxy.$modal.loading("正在保存，请稍候...");
      if (form.value.createTime != null) {
        form.value.createTime = moment(form.value.createTime).format(
          "YYYY-MM-DD HH:mm:ss"
        );
      }
      if (form.value.updateTime != null) {
        form.value.updateTime = moment(form.value.updateTime).format(
          "YYYY-MM-DD HH:mm:ss"
        );
      }
      form.value.separator = form.value.separator.replace(/\\n/g, "\n");
      form.value.subchunkSeparator = form.value.subchunkSeparator.replace(
        /\\n/g,
        "\n"
      );
      if (form.value.id != null) {
        updateDocument(form.value)
          .then((response) => {
            proxy.$modal.closeLoading();
            proxy.$modal.msgSuccess("修改成功");
            cancel();
          })
          .catch((error) => {
            proxy.$modal.closeLoading();
          });
      } else {
        //将form.value.name由数组转为字符串用逗号分割
        if (Array.isArray(form.value.name)) {
          form.value.name = form.value.name.join(",");
        }
        addDocument(form.value)
          .then((response) => {
            proxy.$modal.closeLoading();
            proxy.$modal.msgSuccess("新增成功");
            cancel();
          })
          .catch((error) => {
            proxy.$modal.closeLoading();
          });
      }
    }
  });
}

function getKnowledge() {
  getKmcKnowledgeBaseList().then((res) => {
    knowledgeBaseList.value = res.data;
    getKmcCategoryTree();
  });
}
function reset() {
  form.value = {
    id: null,
    workspaceId: null,
    categoryId: proxy.$route.query.categoryId
      ? Number(proxy.$route.query.categoryId)
      : null,
    categoryName: null,
    name: [],
    path: null,
    description: "",
    validFlag: null,
    delFlag: null,
    createBy: null,
    creatorId: null,
    createTime: null,
    updateBy: null,
    updaterId: null,
    updateTime: null,
    remark: null,
    previewCount: null,
    downloadCount: null,

    knowledgeBaseId: Number(proxy.$route.params.kbId),
    knowledgeBaseName: null,
    mode: "recursive",
    parentMode: "paragraph",
    removeExtraSpaces: true,
    removeUrlsEmails: false,
    chunkOverlap: 64,
    maxTokens: 512,
    separator: "\n\n",
    docForm: checkedyy.value ? "qa_model" : "text_model",
    docLanguage: "Chinese",
    subchunkMaxTokens: 512,
    subchunkSeparator: "\n",
    chatModel: null,
    chatModelProvider: null,
  };
  form.value.separator = form.value.separator.replace(/\n/g, "\\n");
  form.value.subchunkSeparator = form.value.subchunkSeparator.replace(
    /\n/g,
    "\\n"
  );

  if (KcOptions.value) {
    handleTypeChange(form.value.categoryId);
  }

  proxy.resetForm("documentRef");
}
function handleUpdate(id) {
  getDocument(id).then((response) => {
    form.value = response.data;
    if (form.value.separator) {
      form.value.separator = form.value.separator.replace(/\n/g, "\\n");
    }
    getFirstConfig(response.data.knowledgeBaseId);
    if (response.data.knowledgeBaseId !== Number(proxy.$route.params.kbId)) {
      proxy.$modal.msgSuccess("文档与知识库不匹配，当前页面进行关闭。");
      cancel();
    }
  });
}
const updateFormFileName = (newValue) => {
  if (Array.isArray(form.value.name)) {
    form.value.name.push(newValue);
  }
};

// 处理文件删除
const handleDeleteFile = (index) => {
  // 从form.name数组中删除对应索引的文件名
  if (Array.isArray(form.value.name)) {
    form.value.name.splice(index, 1);
  }
};

// 获取文件下的第一个配置
function getFirstConfig(id) {
  getOne(id).then((res) => {
    settingBase.value = res.data;
    checkedyy.value = res.data.docForm === "qa_model";
    form.value.mode = res.data.mode;
    form.value.docForm = res.data.docForm;
  });
}
onMounted(async () => {
  getKnowledge();
});

watch(
  () => proxy.$route.query.id,
  (newId) => {
    title.value = "新增知识文件";
    if (newId) {
      title.value = "修改知识文件";
      handleUpdate(newId);
    }
  },
  { deep: true, immediate: true }
);

watch(
  () => [proxy.$route.params.kbId, proxy.$route.query.categoryId],
  () => {
    if (!proxy.$route.query.id) {
      reset();
    }
    if (proxy.$route.params.kbId) {
      form.value.knowledgeBaseId = Number(proxy.$route.params.kbId);
      getKmcCategoryTree();
      getFirstConfig(proxy.$route.params.kbId);
    }
  },
  { deep: true, immediate: true }
);
// 模型联动逻辑
watchEffect(() => {
  const selectedModel = form.value.chatModel;
  if (!selectedModel) return;
  for (const group of chatModel.value) {
    const found = group.models.find((item) => item.model === selectedModel);
    if (found) {
      form.value.chatModelProvider = group.provider;
      return;
    }
  }
  form.value.chatModelProvider = "";
});
// 初始化模型数据
function init() {
  getChatModelDict().then((response) => {
    chatModel.value = response.data;
  });
}
// 初始化
init();
</script>

<style scoped lang="scss">
/* ===================================================================
   知识文件上传页 - 现代极简主义 + 微玻璃态设计系统
   基于 UI/UX Pro Max 规范 + Dify/Coze/FastGPT 行业调研
   =================================================================== */

/* --- 设计令牌 (Design Tokens) --- */
$primary: #4B7EED;
$primary-light: #6366F1;
$primary-bg: rgba(75, 126, 237, 0.06);
$primary-border: rgba(75, 126, 237, 0.2);
$bg-page: #F0F4FA;
$bg-card: #FFFFFF;
$text-primary: #1E293B;
$text-secondary: #64748B;
$text-muted: #94A3B8;
$border-light: #E8ECF4;
$border-hover: #CBD5E1;
$radius-card: 14px;
$radius-inner: 10px;
$radius-input: 8px;
$shadow-card: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
$shadow-hover: 0 4px 20px rgba(75, 126, 237, 0.1), 0 2px 8px rgba(0, 0, 0, 0.04);
$transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

/* --- 页面容器 --- */
.page-wrapper {
  min-height: calc(100vh - 125px);
  max-width: 920px;
  margin: 0 auto;
  padding: 0 16px 100px;
  position: relative;
}

/* --- 页面标题 --- */
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 0 24px;

  .header-icon-wrapper {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    background: linear-gradient(135deg, $primary, $primary-light);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
    box-shadow: 0 4px 12px rgba(75, 126, 237, 0.3);
  }

  .header-text {
    .header-title {
      font-size: 20px;
      font-weight: 600;
      color: $text-primary;
      margin: 0;
      line-height: 1.4;
      letter-spacing: -0.02em;
    }
    .header-subtitle {
      font-size: 13px;
      color: $text-muted;
      margin: 2px 0 0;
      line-height: 1.5;
    }
  }
}

/* --- 通用卡片 --- */
.form-card {
  background: $bg-card;
  border: 1px solid $border-light;
  border-radius: $radius-card;
  padding: 24px;
  margin-bottom: 16px;
  box-shadow: $shadow-card;
  transition: $transition;

  &:hover {
    box-shadow: $shadow-hover;
    border-color: $border-hover;
  }
}

.card-section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;

  .section-dot {
    width: 4px;
    height: 18px;
    border-radius: 2px;
    background: linear-gradient(180deg, $primary, $primary-light);
    flex-shrink: 0;
  }
}

/* --- 表单样式 --- */
.form-full-width {
  width: 100% !important;
}

:deep(.el-form-item__label) {
  font-size: 14px;
  color: $text-secondary;
  font-weight: 500;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-textarea__inner) {
  border-radius: $radius-input !important;
  transition: $transition;
  &:hover {
    border-color: $border-hover !important;
  }
  &.is-focus, &:focus {
    border-color: $primary !important;
    box-shadow: 0 0 0 3px rgba(75, 126, 237, 0.08) !important;
  }
}

/* --- 文件上传区域 --- */
.upload-zone {
  width: 100%;
}

:deep(.el-upload-dragger) {
  height: 140px;
  border-radius: $radius-inner;
  border: 2px dashed $border-light;
  background: $primary-bg;
  transition: $transition;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  &:hover {
    border-color: $primary;
    background: rgba(75, 126, 237, 0.08);
    transform: translateY(-1px);
  }
}

:deep(.el-icon--upload) {
  font-size: 36px;
  margin-bottom: 4px;
  color: $primary;
}

:deep(.el-upload__text) {
  font-size: 14px;
  color: $text-secondary;
  em {
    color: $primary;
    font-style: normal;
    font-weight: 500;
  }
}

:deep(.el-upload__tip) {
  font-size: 12px;
  color: $text-muted;
  margin-top: 8px;
}

/* --- 切分模式选择卡片 --- */
.mode-options {
  display: flex;
  gap: 14px;
}

.mode-card {
  flex: 1;
  position: relative;
  padding: 18px 20px;
  border: 1.5px solid $border-light;
  border-radius: $radius-inner;
  cursor: pointer;
  transition: $transition;
  overflow: hidden;
  background: $bg-card;

  &:hover {
    border-color: rgba(75, 126, 237, 0.4);
    background: $primary-bg;
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(75, 126, 237, 0.1);
  }

  &.active {
    border-color: $primary;
    background: linear-gradient(135deg, rgba(75, 126, 237, 0.04), rgba(99, 102, 241, 0.06));
    box-shadow: 0 0 0 3px rgba(75, 126, 237, 0.08);

    .mode-card-title {
      color: $primary;
    }

    .mode-card-icon {
      background: linear-gradient(135deg, $primary, $primary-light);
      color: #fff;
    }
  }

  .mode-card-inner {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .mode-card-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: $transition;

    &.recursive-icon {
      background: rgba(75, 126, 237, 0.1);
      color: $primary;
    }
    &.custom-icon {
      background: rgba(99, 102, 241, 0.1);
      color: $primary-light;
    }
    &.small {
      width: 32px;
      height: 32px;
      border-radius: 8px;
    }
    &.inactive {
      background: #f1f5f9;
      color: $text-muted;
    }
  }

  .mode-card-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    transition: $transition;
  }

  .mode-card-desc {
    font-size: 12px;
    color: $text-muted;
    margin-top: 3px;
    line-height: 1.5;
  }
}

.mode-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  background: linear-gradient(135deg, $primary, $primary-light);
  color: #fff;
  letter-spacing: 0.02em;
}

/* --- 配置卡片 --- */
.config-card {
  &.config-inactive {
    opacity: 0.5;
    pointer-events: none;
    &:hover {
      box-shadow: $shadow-card;
      border-color: $border-light;
    }
  }
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid $border-light;
  margin-bottom: 20px;

  .config-header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .config-title {
    font-size: 15px;
    font-weight: 600;
    color: $primary;
    &.inactive {
      color: $text-muted;
    }
  }

  .config-desc {
    font-size: 12px;
    color: $text-muted;
    margin-top: 2px;
  }
}

.config-body {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;

  &.config-grid-3 {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

.config-item {
  .config-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: $text-secondary;
    font-weight: 500;
    margin-bottom: 8px;
  }
}

.config-input {
  width: 100% !important;
}

.help-icon {
  color: $text-muted;
  cursor: help;
  transition: $transition;
  &:hover {
    color: $primary;
  }
}

/* --- 预处理规则 --- */
.preprocess-section {
  padding: 16px 18px;
  background: #F8FAFC;
  border-radius: $radius-input;
  border: 1px solid $border-light;

  .preprocess-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    color: $primary;
    margin-bottom: 12px;

    svg {
      color: $primary;
    }
  }

  :deep(.el-checkbox) {
    display: flex;
    margin-bottom: 6px;
  }

  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: $text-secondary;
  }
}

.qa-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid $border-light;
  display: flex;
  align-items: center;

  :deep(.el-select__wrapper) {
    min-height: 28px;
    margin-left: 8px;
    border-radius: 6px;
  }
}

/* --- 操作按钮栏 --- */
.action-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  margin: 0 -16px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid $border-light;
  z-index: 10;

  .btn-cancel {
    padding: 10px 28px;
    border-radius: $radius-input;
    font-size: 14px;
    font-weight: 500;
    border: 1.5px solid $border-light;
    color: $text-secondary;
    background: $bg-card;
    transition: $transition;
    cursor: pointer;

    &:hover {
      border-color: $border-hover;
      color: $text-primary;
      background: #F8FAFC;
    }
  }

  .btn-submit {
    padding: 10px 32px;
    border-radius: $radius-input;
    font-size: 14px;
    font-weight: 600;
    background: linear-gradient(135deg, $primary, $primary-light);
    border: none;
    color: #fff;
    transition: $transition;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    box-shadow: 0 2px 8px rgba(75, 126, 237, 0.3);

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 16px rgba(75, 126, 237, 0.4);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

/* --- Element Plus 组件覆盖 --- */
:deep(.el-radio__label) {
  display: none;
}
:deep(.checkbox__label) {
  width: 375px;
}
:deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
  color: $text-primary;
}

/* --- 响应式适配 --- */
@media (max-width: 768px) {
  .page-wrapper {
    padding: 0 12px 100px;
  }
  .mode-options {
    flex-direction: column;
  }
  .config-grid {
    grid-template-columns: 1fr;
    &.config-grid-3 {
      grid-template-columns: 1fr;
    }
  }
  .action-bar {
    padding: 12px 16px;
  }
}

/* --- 平滑滚动 --- */
@media (prefers-reduced-motion: no-preference) {
  .form-card, .mode-card, .btn-submit, .btn-cancel {
    transition: $transition;
  }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
