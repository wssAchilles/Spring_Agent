<template>
  <div class="card-container">
    <div
      v-for="(item, index) in data"
      :key="index"
      class="card"
      @click="handleDetail(item)"
    >
      <!-- 标题栏：图标 + 名称 + 启用标签 -->
      <div class="card-top">
        <div class="card-title">
          <div class="card-title-text">
            <span class="icon">
              <img src="@/assets/icons/svg/kmc/kmc.svg" alt="知识库图标" />
            </span>
            <span class="name">{{ item.name }}</span>
          </div>
          <div class="card-title-status">
            <el-tag
              :type="item.validFlag == 1 ? 'primary' : 'warning'"
              size="small"
            >
              {{ item.validFlag == 1 ? "启用" : "禁用" }}
            </el-tag>
          </div>
        </div>

        <el-divider style="margin: 7px 0px; width: 100%" />

        <!-- 统计项 + 标签 + 封面图 -->
        <div class="card-bottom">
          <div class="card-bottom-right">
            <div class="card-description">{{ item.description }}</div>
            <div class="card-bottom-text-group">
              <div class="card-bottom-text">
                <el-tooltip
                  class="item"
                  effect="dark"
                  content="文件总数"
                  placement="top"
                >
                  <img
                    src="@/assets/icons/svg/kmc/FileNum.svg"
                    alt="文件数"
                    class="stat-icon"
                  />
                </el-tooltip>
                <span class="card-bottom-text-name"
                  >{{ item.fileCount }} 个</span
                >
              </div>
              <div class="card-bottom-text">
                <el-tooltip
                  class="item"
                  effect="dark"
                  content="索引方式"
                  placement="top"
                >
                  <img
                    src="@/assets/icons/svg/kmc/IndexWay.svg"
                    alt="索引方式"
                    class="stat-icon"
                  />
                </el-tooltip>
                <span class="card-bottom-text-name">
                  {{ getDictLabel(item.indexingTechnique) }}
                </span>
              </div>
              <div class="card-bottom-text">
                <el-tooltip
                  class="item"
                  effect="dark"
                  content="创建时间"
                  placement="top"
                >
                  <img
                    src="@/assets/icons/svg/kmc/CreateTime.svg"
                    alt="时间"
                    class="stat-icon"
                  />
                </el-tooltip>
                <span class="card-bottom-text-name">{{
                  parseTime(item.createTime, "{y}-{m}-{d}")
                }}</span>
              </div>
              <div class="card-bottom-text">
                <el-tooltip
                  class="item"
                  effect="dark"
                  content="创建人"
                  placement="top"
                >
                  <img
                    src="@/assets/icons/svg/kmc/CreateBy.svg"
                    alt="创建人"
                    class="stat-icon"
                  />
                </el-tooltip>
                <span class="card-bottom-text-name">{{ item.createBy }}</span>
              </div>
            </div>
            <div class="card-title-tags">
              <el-tag
                v-for="(tag, index) in parseTags(item.tags)"
                :key="index"
                size="medium"
                class="card-tag"
              >
                {{ tag.name }}
              </el-tag>
            </div>
          </div>
          <div class="card-bottom-left">
            <img :src="getImage(item)" alt="知识库封面" class="cover" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import KnowledgeCover from "@/assets/kmc/knowledgeCover.png";

const { proxy } = getCurrentInstance();
const router = useRouter();

defineEmits(["handleDataScope"]);
const { kmc_know_index } = proxy.useDict("kmc_know_index");

const getDictLabel = (dictValue) => {
  // 安全地获取字典数组
  const dictArray = kmc_know_index.value;

  if (Array.isArray(dictArray)) {
    const found = dictArray.find((item) => item.value === dictValue);
    if (found) {
      return found.label;
    }
  }

  // 如果找不到匹配项，返回原值或默认显示
  return dictValue || "--";
};
defineProps({
  data: {
    type: Array,
    required: true,
  },
});

// 解析标签
const parseTags = (tagsStr) => {
  try {
    return JSON.parse(tagsStr);
  } catch {
    return [];
  }
};

function getImage(row) {
  if (!row.coverImage) {
    return KnowledgeCover;
  }
  return import.meta.env.VITE_APP_BASE_API + "/profile" + row.coverImage;
}

// 查看详情
function handleDetail(row) {
  router.push({
    path: "/kmc/" + row.id + "/kmcDocument",
  });
}
</script>

<style scoped lang="scss">
.card-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(555px, 1fr));
  gap: 20px;
  padding: 16px 0px;
  box-sizing: border-box;
  width: 100%;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);

  @media (min-width: 555px) {
    grid-template-columns: repeat(1, minmax(555px, 1fr));
  }
  @media (min-width: 1160px) {
    grid-template-columns: repeat(2, minmax(555px, 1fr));
  }
  @media (min-width: 1735px) {
    grid-template-columns: repeat(3, minmax(555px, 1fr));
  }

  .card {
    background: var(--glass-card-bg, rgba(255, 255, 255, 0.72));
    backdrop-filter: blur(16px) saturate(140%);
    -webkit-backdrop-filter: blur(16px) saturate(140%);
    border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
    width: 100%;
    min-width: 555px;
    min-height: 240px;
    display: flex;
    flex-direction: column;
    border-radius: 12px;
    cursor: pointer;
    box-shadow: 0 4px 16px -2px rgba(0, 0, 0, 0.04);
    transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1),
                box-shadow 0.25s cubic-bezier(0.16, 1, 0.3, 1),
                border-color 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    contain: layout style;

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 16px 36px -6px rgba(0, 0, 0, 0.12), 0 0 0 1px rgba(0, 0, 0, 0.06);
      border-color: rgba(0, 0, 0, 0.18);
    }

    .card-top {
      display: flex;
      flex-direction: column;
      padding: 21px;

      .card-title {
        height: 28px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .card-title-text {
          height: 28px;
          font-weight: 700;
          font-size: 18px;
          color: var(--glass-text-primary, #18181b);
          line-height: 28px;
          text-align: left;
          display: flex;
          align-items: center;
          min-width: 0;

          .icon {
            width: 28px;
            height: 28px;
            background: rgba(0, 0, 0, 0.04);
            border-radius: 6px;
            margin-right: 10px;
            display: flex;
            align-items: center;
            justify-content: center;
            border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.06));

            img {
              width: 18px;
              height: 18px;
            }
          }
        }

        .card-title-status {
          ::v-deep .el-tag {
            border-radius: 12px !important;
            font-weight: 600;
          }
        }
      }

      .card-bottom {
        display: flex;
        justify-content: space-between;
        padding: 6px 0px;
        flex: 1;

        .card-bottom-left {
          flex-shrink: 0;
          img {
            width: 100%;
            max-width: 205px;
            height: 149px;
            background: rgba(0, 0, 0, 0.02);
            border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
            border-radius: 8px;
            object-fit: cover;
            transition: transform 0.3s ease;

            @media (max-width: 767px) {
              max-width: 160px;
              height: 120px;
            }
          }
        }

        &:hover .card-bottom-left img {
          transform: scale(1.02);
        }

        .card-bottom-right {
          display: flex;
          flex-direction: column;
          flex: 1;
          margin-right: 16px;
          min-width: 0;

          @media (max-width: 767px) {
            margin-right: 12px;
          }

          .card-description {
            width: 100%;
            flex: 1;
            min-height: 42px;
            font-weight: 500;
            font-size: 13px;
            color: var(--glass-text-secondary, #52525b);
            line-height: 20px;
            text-align: left;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            text-overflow: ellipsis;
            word-break: break-all;

            @media (min-width: 768px) {
              min-height: 60px;
              -webkit-line-clamp: 3;
            }
          }

          .card-bottom-text-group {
            min-height: 52px;
            width: 100%;
            margin-top: 3px;
            display: grid;
            gap: 8px;
            align-items: center;
            grid-template-columns: repeat(2, 1fr);

            .card-bottom-text {
              flex: 1;
              min-width: 140px;
              height: 26px;
              background: rgba(0, 0, 0, 0.03);
              border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.04));
              padding: 2px 10px;
              display: flex;
              align-items: center;
              border-radius: 6px;

              img {
                width: 15px;
                height: 15px;
                flex-shrink: 0;
                opacity: 0.75;
              }

              .card-bottom-text-name {
                flex: 1;
                padding: 0 6px;
                font-weight: 500;
                font-size: 13px;
                color: var(--glass-text-primary, #27272a);
                line-height: 24px;
                text-align: left;
                display: -webkit-box;
                -webkit-line-clamp: 1;
                -webkit-box-orient: vertical;
                overflow: hidden;
                text-overflow: ellipsis;
                word-break: break-all;

                @media (max-width: 767px) {
                  font-size: 12px;
                }
              }
            }
          }

          .card-title-tags {
            width: 100%;
            margin-top: 7px;
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
            overflow: hidden;
            min-height: 24px;

            .card-tag {
              flex-shrink: 0;
              background: rgba(0, 0, 0, 0.03) !important;
              border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.06)) !important;
              font-size: 12px;
              color: var(--glass-text-secondary, #52525b) !important;
              border-radius: 6px;
              max-width: 120px;

              @media (max-width: 767px) {
                font-size: 11px;
                max-width: 100px;
              }
            }
          }
        }
      }
    }
  }
}

// 深色模式 Hover Glow 与高对比度边框
@media (prefers-color-scheme: dark) {
  .card:hover {
    box-shadow: 0 16px 40px -8px rgba(0, 0, 0, 0.5), 0 0 16px rgba(255, 255, 255, 0.08) !important;
    border-color: rgba(255, 255, 255, 0.24) !important;
  }
}

:global(html.dark) .card:hover,
:global(html[data-theme='dark']) .card:hover {
  box-shadow: 0 16px 40px -8px rgba(0, 0, 0, 0.5), 0 0 16px rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(255, 255, 255, 0.24) !important;
}
</style>
