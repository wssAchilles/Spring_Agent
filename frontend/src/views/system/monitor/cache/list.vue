<template>
  <div class="app-container monitor-container">
    <el-row :gutter="10">
      <el-col :span="8">
        <el-card class="glass-card" style="height: calc(100vh - 125px)">
          <template #header>
            <Collection style="width: 1em; height: 1em; vertical-align: middle;" /> <span style="vertical-align: middle;">缓存列表</span>
            <el-button v-ripple class="glass-btn"
              style="float: right; padding: 3px 0"
              link
              icon="Refresh"
              @click="refreshCacheNames()"
            ></el-button>
          </template>
          <el-table
            ref="cacheNameTableRef"
            stripe
            v-loading="loading"
            :data="cacheNames"
            :height="tableHeight"
            highlight-current-row
            @row-click="getCacheKeys"
            style="width: 100%"
          >
            <el-table-column
              label="序号"
              width="60"
              type="index"
            ></el-table-column>

            <el-table-column
              label="缓存名称"
              align="center"
              prop="cacheName"
              :show-overflow-tooltip="true"
              :formatter="nameFormatter"
            ></el-table-column>

            <el-table-column
              label="备注"
              align="center"
              prop="remark"
              :show-overflow-tooltip="true"
            />
            <el-table-column
              label="操作"
              width="60"
              align="center"
              class-name="small-padding fixed-width"
            >
              <template #default="scope">
                <el-button v-ripple class="glass-btn"
                  link
                  icon="Delete"
                  @click="handleClearCacheName(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="glass-card" style="height: calc(100vh - 125px)">
          <template #header>
            <Key style="width: 1em; height: 1em; vertical-align: middle;" /> <span style="vertical-align: middle;">键名列表</span>
            <el-button v-ripple class="glass-btn"
              style="float: right; padding: 3px 0"
              link
              icon="Refresh"
              @click="refreshCacheKeys()"
            ></el-button>
          </template>
          <el-table
            ref="cacheKeyTableRef"
            stripe
            v-loading="subLoading"
            :data="cacheKeys"
            :height="tableHeight"
            highlight-current-row
            @row-click="handleCacheValue"
            style="width: 100%"
          >
            <template #empty>
              <el-empty :image-size="64" :description="nowCacheName ? '当前缓存分类暂无键名' : '请在左侧选择缓存名称以查看键名'" />
            </template>
            <el-table-column
              label="序号"
              width="60"
              type="index"
            ></el-table-column>
            <el-table-column
              label="缓存键名"
              align="center"
              :show-overflow-tooltip="true"
              :formatter="keyFormatter"
            >
            </el-table-column>
            <el-table-column
              label="操作"
              width="60"
              align="center"
              class-name="small-padding fixed-width"
            >
              <template #default="scope">
                <el-button v-ripple class="glass-btn"
                  link
                  icon="Delete"
                  @click="handleClearCacheKey(scope.row)"
                ></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="glass-card" :bordered="false" style="height: calc(100vh - 125px)">
          <template #header>
            <Document style="width: 1em; height: 1em; vertical-align: middle;" /> <span style="vertical-align: middle;">缓存内容</span>
            <div style="float: right;">
              <el-button v-if="cacheForm.cacheValue" v-ripple class="glass-btn"
                style="padding: 3px 8px; margin-right: 8px;"
                link
                icon="CopyDocument"
                @click="copyCacheValue"
              >复制内容</el-button>
              <el-button v-ripple class="glass-btn"
                style="padding: 3px 0"
                link
                @click="handleClearCacheAll()"
              >
                <i class="iconfont-mini icon-a-shuaxinxianxing mr5"></i>
                清理全部
              </el-button>
            </div>
          </template>
          <div v-if="!cacheForm.cacheKey" class="empty-state-card">
            <el-empty :image-size="70" description="请在左侧选择缓存键名以查看内容详情" />
          </div>
          <el-form v-else :model="cacheForm">
            <el-row :gutter="32">
              <el-col :offset="1" :span="22">
                <el-form-item label="缓存名称:" prop="cacheName">
                  <el-input v-model="cacheForm.cacheName" :readOnly="true" />
                </el-form-item>
              </el-col>
              <el-col :offset="1" :span="22">
                <el-form-item label="缓存键名:" prop="cacheKey">
                  <el-input v-model="cacheForm.cacheKey" :readOnly="true" />
                </el-form-item>
              </el-col>
              <el-col :offset="1" :span="22">
                <el-form-item label="缓存内容:" prop="cacheValue">
                  <el-input
                    class="cache-content-textarea"
                    :model-value="cacheForm.formattedValue || cacheForm.cacheValue"
                    type="textarea"
                    :rows="14"
                    :readOnly="true"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="CacheList">
import { listCacheName, listCacheKey, getCacheValue, clearCacheName, clearCacheKey, clearCacheAll } from "@/api/system/monitor/cache.js";

const { proxy } = getCurrentInstance();

const cacheNameTableRef = ref(null);
const cacheKeyTableRef = ref(null);
const cacheNames = ref([]);
const cacheKeys = ref([]);
const cacheForm = ref({});
const loading = ref(true);
const subLoading = ref(false);
const nowCacheName = ref("");
const tableHeight = ref(window.innerHeight - 200);

/** 查询缓存名称列表 */
function getCacheNames() {
  loading.value = true;
  listCacheName().then(response => {
    cacheNames.value = response.data || [];
    loading.value = false;
    // 默认自动选中第一项，触发键名与内容联动
    if (cacheNames.value.length > 0) {
      nextTick(() => {
        const first = cacheNames.value[0];
        cacheNameTableRef.value?.setCurrentRow(first);
        getCacheKeys(first);
      });
    }
  }).catch(() => {
    loading.value = false;
  });
}

/** 刷新缓存名称列表 */
function refreshCacheNames() {
  getCacheNames();
  proxy.$modal.msgSuccess("刷新缓存列表成功");
}

/** 清理指定名称缓存 */
function handleClearCacheName(row) {
  clearCacheName(row.cacheName).then(response => {
    proxy.$modal.msgSuccess("清理缓存名称[" + row.cacheName + "]成功");
    getCacheKeys();
  });
}

/** 查询缓存键名列表 */
function getCacheKeys(row) {
  const cacheName = row !== undefined ? (typeof row === 'object' ? row.cacheName : row) : nowCacheName.value;
  if (!cacheName) {
    return;
  }
  nowCacheName.value = cacheName;
  subLoading.value = true;
  cacheKeys.value = [];
  cacheForm.value = {};
  listCacheKey(cacheName).then(response => {
    cacheKeys.value = response.data || [];
    subLoading.value = false;
    // 默认自动选中第一个键名，展示其内容
    if (cacheKeys.value.length > 0) {
      nextTick(() => {
        const firstKey = cacheKeys.value[0];
        cacheKeyTableRef.value?.setCurrentRow(firstKey);
        handleCacheValue(firstKey);
      });
    }
  }).catch(() => {
    subLoading.value = false;
  });
}

/** 刷新缓存键名列表 */
function refreshCacheKeys() {
  getCacheKeys();
  proxy.$modal.msgSuccess("刷新键名列表成功");
}

/** 清理指定键名缓存 */
function handleClearCacheKey(cacheKey) {
  const rawKey = typeof cacheKey === 'object' && cacheKey !== null 
    ? (cacheKey.cacheKey || cacheKey[Object.keys(cacheKey)[0]] || '') 
    : String(cacheKey || '');
  clearCacheKey(rawKey).then(response => {
    proxy.$modal.msgSuccess("清理缓存键名[" + rawKey + "]成功");
    getCacheKeys();
  });
}

/** 列表前缀去除 */
function nameFormatter(row) {
  return (row && row.cacheName ? row.cacheName : '').replace(":", "");
}

/** 键名前缀去除 */
function keyFormatter(cacheKey) {
  const raw = typeof cacheKey === 'object' && cacheKey !== null 
    ? (cacheKey.cacheKey || '') 
    : String(cacheKey || '');
  return raw.replace(nowCacheName.value, "");
}

/** 缓存内容格式化（兼容标准 JSON 与 FastJSON 序列化字符串） */
function formatCacheValue(val) {
  if (!val) return '';
  try {
    return JSON.stringify(JSON.parse(val), null, 2);
  } catch (e) {
    try {
      let formatted = '';
      let indent = 0;
      let inQuote = false;
      for (let i = 0; i < val.length; i++) {
        const char = val[i];
        if (char === '"' && val[i - 1] !== '\\') {
          inQuote = !inQuote;
          formatted += char;
        } else if (!inQuote) {
          if (char === '{' || char === '[') {
            indent += 2;
            formatted += char + '\n' + ' '.repeat(indent);
          } else if (char === '}' || char === ']') {
            indent = Math.max(0, indent - 2);
            formatted += '\n' + ' '.repeat(indent) + char;
          } else if (char === ',') {
            formatted += char + '\n' + ' '.repeat(indent);
          } else {
            formatted += char;
          }
        } else {
          formatted += char;
        }
      }
      return formatted;
    } catch (err) {
      return val;
    }
  }
}

/** 查询缓存内容详细 */
function handleCacheValue(cacheKey) {
  const rawKey = typeof cacheKey === 'object' && cacheKey !== null 
    ? (cacheKey.cacheKey || cacheKey[Object.keys(cacheKey)[0]] || '') 
    : String(cacheKey || '');
  if (!rawKey) return;
  getCacheValue(nowCacheName.value, rawKey).then(response => {
    const data = response.data || {};
    data.formattedValue = formatCacheValue(data.cacheValue);
    cacheForm.value = data;
  });
}

/** 清理全部缓存 */
function handleClearCacheAll() {
  clearCacheAll().then(response => {
    proxy.$modal.msgSuccess("清理全部缓存成功");
    cacheKeys.value = [];
    cacheForm.value = {};
  });
}

/** 复制缓存内容 */
function copyCacheValue() {
  const val = cacheForm.value.formattedValue || cacheForm.value.cacheValue;
  if (!val) return;
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(val).then(() => {
      proxy.$modal.msgSuccess("缓存内容已成功复制到剪贴板");
    }).catch(() => {
      fallbackCopy(val);
    });
  } else {
    fallbackCopy(val);
  }
}

function fallbackCopy(val) {
  const ta = document.createElement("textarea");
  ta.value = val;
  document.body.appendChild(ta);
  ta.select();
  document.execCommand("copy");
  document.body.removeChild(ta);
  proxy.$modal.msgSuccess("缓存内容已成功复制到剪贴板");
}

getCacheNames();
</script>

<style lang="scss" scoped>
/* 缓存列表页面画布容器规范：消除外层双重卡片白色底板，确保多卡片在系统画布上平滑流动 */
.monitor-container {
  background: transparent !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  border: none !important;
  padding: 16px 20px 28px 20px;

  :deep(.el-card) {
    border: 0.5px solid var(--ios26-separator-non-opaque, rgba(0, 0, 0, 0.12));
    box-shadow: var(--ios26-shadow-sm, 0 4px 16px rgba(0, 0, 0, 0.04));
    transition: all 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);

    &:hover {
      box-shadow: var(--ios26-shadow-md, 0 8px 24px rgba(0, 0, 0, 0.08));
    }
  }

  :deep(.el-table .current-row > td.el-table__cell) {
    background-color: rgba(0, 136, 255, 0.09) !important;
    color: var(--ios26-color-blue, #0088ff) !important;
    font-weight: 550;
  }

  :deep(.cache-content-textarea textarea) {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
    font-size: 12.5px;
    line-height: 1.5;
    border-radius: 12px;
  }

  .empty-state-card {
    display: flex;
    align-items: center;
    justify-content: center;
    height: calc(100vh - 230px);
  }
}
</style>
