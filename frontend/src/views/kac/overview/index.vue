<template>
  <div class="overview-page">
    <div class="overview-top">
      <section
        class="overview-hero"
        :style="{ backgroundImage: `url(${bannerImage})` }"
      >
        <div class="hero-copy">
          <h1>应用中心</h1>
          <h2>汇聚行业解决方案，赋能业务智能升级</h2>
          <p>
            支持一次性上传多个查询条件并行处理，汇总输出结果。大幅提升效率，适用于多项目数据对比或大规模文献调研。
          </p>
        </div>
      </section>

      <aside class="quick-panel">
        <div class="panel-title">
          <span></span>
          快捷入口
        </div>
        <div class="quick-grid">
          <button
            v-for="item in quickEntries"
            :key="item.label"
            class="quick-entry"
            type="button"
            @click="goToPage(item.path)"
          >
            <img :src="item.icon" alt="" />
            <span>{{ item.label }}</span>
          </button>
        </div>
        <div class="stat-grid">
          <button
            v-for="item in quickStats"
            :key="item.label"
            class="stat-item"
            type="button"
            @click="goToPage(item.path)"
          >
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </aside>
    </div>

    <section class="overview-section">
      <div class="section-head">
        <div class="panel-title">
          <span></span>
          热门应用推荐
        </div>
        <button
          class="section-more"
          type="button"
          @click="goToPage('/kac/horizontal')"
        >
          查看更多
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>

      <div class="section-content" v-loading="applyLoading">
        <div v-if="applyList.length" class="apply-grid">
          <div v-for="item in applyList" :key="item.id" class="apply-card">
            <div class="apply-card-header">
              <div class="apply-icon-wrapper">
                <el-icon :size="24" color="#409eff">
                  <component :is="getIconComponent(item.icon)" />
                </el-icon>
              </div>
              <div class="apply-info">
                <div class="apply-name">{{ item.name }}</div>
                <div class="apply-tags">
                  <el-tag
                    v-for="(tag, index) in getTags(item)"
                    :key="index"
                    size="small"
                    class="apply-tag"
                  >
                    {{ tag.name }}
                  </el-tag>
                </div>
              </div>
              <el-tag
                :type="item.status === 1 ? 'success' : 'warning'"
                size="small"
                class="apply-status"
              >
                {{ item.status === 1 ? '正常' : '停用' }}
              </el-tag>
            </div>
            <div class="apply-description">{{ item.description }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无应用推荐" />
      </div>
    </section>
  </div>
</template>

<script setup name="Overview">
import { computed, onMounted, reactive, ref } from "vue";
import { ArrowRight, Edit, Search, Document, Connection, Aim, ChatDotRound, Calendar, DataAnalysis } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import { listApply } from "@/api/kac/apply/apply.js";
import useUserStore from "@/store/system/user.js";
import bannerImage from "@/assets/kac/overview/banner.png";
import quickSolutionIcon from "@/assets/kac/overview/quick-solution.png";
import quickHorizontalIcon from "@/assets/kac/overview/quick-horizontal.png";
import quickVerticalIcon from "@/assets/kac/overview/quick-vertical.png";
import quickMySolutionIcon from "@/assets/kac/overview/quick-my-solution.png";
import quickMyAppIcon from "@/assets/kac/overview/quick-my-app.png";

const router = useRouter();
const userStore = useUserStore();

const applyLoading = ref(false);
const applyList = ref([]);
const overviewStats = reactive({
  applyTotal: 0,
  myApplyTotal: 0,
});

const quickEntries = [
  {
    label: "通用应用",
    icon: quickHorizontalIcon,
    path: "/kac/horizontal",
  },
  {
    label: "行业应用",
    icon: quickVerticalIcon,
    path: "/kac/vertical",
  },
  {
    label: "我的应用",
    icon: quickMyAppIcon,
    path: "/kac/myApp",
  },
];

const quickStats = computed(() => [
  {
    label: "所有应用",
    value: overviewStats.applyTotal,
    path: "/kac/horizontal",
  },
  {
    label: "我的应用",
    value: overviewStats.myApplyTotal,
    path: "/kac/myApp",
  },
]);

const applyQueryParams = reactive({
  pageNum: 1,
  pageSize: 8,
  workspaceId: null,
  pluginId: null,
  name: null,
  category: 0,
  description: null,
  status: null,
  source: null,
  tags: null,
  useScene: null,
  useCount: null,
  createTime: null,
  myApplyFlag: 0,
  orderByColumn: "createTime",
  isAsc: "desc",
});

const iconMap = {
  Edit,
  Search,
  Document,
  Connection,
  Aim,
  ChatDotRound,
  Calendar,
  DataAnalysis,
};

function getIconComponent(iconName) {
  return iconMap[iconName] || Document;
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

async function getApplyList() {
  applyLoading.value = true;
  try {
    const response = await listApply(applyQueryParams);
    applyList.value = response.data.rows || [];
    overviewStats.applyTotal = response.data.total || 0;
  } catch {
    applyList.value = [];
  } finally {
    applyLoading.value = false;
  }
}

async function getOverviewStats() {
  try {
    const myApplyRes = await listApply({
      ...applyQueryParams,
      pageNum: 1,
      pageSize: 1,
      category: null,
      userId: userStore.id,
      myApplyFlag: 1,
    });
    overviewStats.myApplyTotal = myApplyRes.data?.total || 0;
  } catch {
    overviewStats.myApplyTotal = 0;
  }
}

function goToPage(path) {
  router.push(path);
}

onMounted(() => {
  getApplyList();
  getOverviewStats();
});
</script>

<style scoped lang="scss">
.overview-page {
  padding: 16px;
  background: #f0f2f5;
  color: #1f2937;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.overview-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 500px;
  gap: 16px;
}

.overview-hero,
.quick-panel,
.overview-section {
  background: #fff;
}

.overview-hero {
  height: 234px;
  padding: 46px 72px;
  background-color: #eaf3ff;
  background-position: center;
  background-repeat: no-repeat;
  background-size: 100% 100%;
}

.hero-copy {
  max-width: 520px;

  h1,
  h2,
  p {
    margin: 0;
  }

  h1 {
    color: #1f2937;
    font-size: 32px;
    font-weight: 600;
    line-height: 44px;
  }

  h2 {
    margin-top: 14px;
    color: #1f2937;
    font-size: 18px;
    font-weight: 400;
    line-height: 28px;
  }

  p {
    margin-top: 14px;
    color: #4b5563;
    font-size: 14px;
    line-height: 26px;
  }
}

.quick-panel {
  height: 234px;
  padding: 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  color: #1f2937;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;

  span {
    width: 6px;
    height: 16px;
    margin-right: 10px;
    border-radius: 3px;
    background: #2666fb;
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 18px;
}

.quick-entry,
.stat-item,
.section-more {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.quick-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  padding: 0;
  color: #1f2937;
  font-size: 14px;
  line-height: 20px;

  img {
    width: 52px;
    height: 52px;
    border-radius: 8px;
    object-fit: cover;
  }

  span {
    margin-top: 8px;
    white-space: nowrap;
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  height: 60px;
  margin-top: 16px;
  border: 1px solid #e5ebf5;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.02),
    rgba(38, 102, 251, 0.04)
  );
}

.stat-item {
  min-width: 0;
  height: 58px;
  padding: 7px 6px;
  border-right: 1px solid #e5ebf5;
  color: #6b7280;

  &:last-child {
    border-right: 0;
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: #1f6eea;
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
  }

  span {
    margin-top: 4px;
    font-size: 14px;
    line-height: 18px;
  }
}

.overview-section {
  margin-top: 0;
  padding: 20px;
}

.overview-top + .overview-section,
.overview-section + .overview-section {
  margin-top: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  color: #1f6eea;
  font-size: 14px;
  line-height: 22px;
}

.section-content {
  min-height: 120px;
}

.section-content :deep(.el-empty) {
  padding: 48px 0 24px;
}

.apply-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.apply-card {
  background: #fff;
  border: 1px solid #e5ebf5;
  border-radius: 8px;
  padding: 16px;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.apply-card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.apply-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: #f0f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.apply-info {
  flex: 1;
  min-width: 0;
}

.apply-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  line-height: 22px;
  margin-bottom: 8px;
}

.apply-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.apply-tag {
  margin: 0;
}

.apply-status {
  flex-shrink: 0;
}

.apply-description {
  font-size: 14px;
  color: #6b7280;
  line-height: 20px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 1440px) {
  .overview-top {
    grid-template-columns: minmax(0, 1fr) 500px;
  }

  .overview-hero {
    padding: 42px 54px;
  }

  .apply-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1200px) {
  .overview-top {
    grid-template-columns: 1fr;
  }

  .apply-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .overview-hero,
  .quick-panel,
  .overview-section {
    padding: 16px;
  }

  .overview-page {
    padding: 10px;
  }

  .overview-hero {
    min-height: 220px;
  }

  .quick-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-item:nth-child(2) {
    border-right: 0;
  }

  .apply-grid {
    grid-template-columns: 1fr;
  }
}
aside {
  margin-bottom: 0;
}
</style>
