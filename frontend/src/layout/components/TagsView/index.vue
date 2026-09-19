<template>
  <div id="tags-view-container" class="tags-view-container">
    <scroll-pane ref="scrollPaneRef" class="tags-view-wrapper" @scroll="handleScroll">
      <router-link v-for="tag in visitedViews" :key="tag.path" :data-path="tag.path"
        :class="isActive(tag) ? 'active' : ''" :to="{ path: tag.path, query: tag.query, fullPath: tag.fullPath }"
        class="tags-view-item" :style="activeStyle(tag)" @click.middle="!isAffix(tag) ? closeSelectedTag(tag) : ''"
        @contextmenu.prevent="openMenu(tag, $event)">
        {{ tag.title }}
        <span v-if="!isAffix(tag)" @click.prevent.stop="closeSelectedTag(tag)">
          <close class="el-icon-close" style="width: 1em; height: 1em; vertical-align: middle" />
        </span>
      </router-link>
    </scroll-pane>
    <ul v-show="visible" :style="{ left: left + 'px', top: top + 'px' }" class="contextmenu">
      <li @click="refreshSelectedTag(selectedTag)">
        <refresh-right style="width: 1em; height: 1em" /> 刷新页面
      </li>
      <li v-if="!isAffix(selectedTag)" @click="closeSelectedTag(selectedTag)">
        <close style="width: 1em; height: 1em" /> 关闭当前
      </li>
      <li @click="closeOthersTags">
        <circle-close style="width: 1em; height: 1em" /> 关闭其他
      </li>
      <li v-if="!isFirstView()" @click="closeLeftTags">
        <back style="width: 1em; height: 1em" /> 关闭左侧
      </li>
      <li v-if="!isLastView()" @click="closeRightTags">
        <right style="width: 1em; height: 1em" /> 关闭右侧
      </li>
      <li @click="closeAllTags(selectedTag)">
        <circle-close style="width: 1em; height: 1em" /> 全部关闭
      </li>
    </ul>
  </div>
</template>

<script setup>
import ScrollPane from './ScrollPane';
import { getNormalPath } from '@/utils/anivia.js';
import useTagsViewStore from '@/store/system/tagsView';
import useSettingsStore from '@/store/system/settings';
import usePermissionStore from '@/store/system/permission';

const visible = ref(false);
const top = ref(0);
const left = ref(0);
const selectedTag = ref({});
const affixTags = ref([]);
const scrollPaneRef = ref(null);

const { proxy } = getCurrentInstance();
const route = useRoute();
const router = useRouter();

const visitedViews = computed(() => useTagsViewStore().visitedViews);
const routes = computed(() => usePermissionStore().routes);
const theme = computed(() => useSettingsStore().theme);

watch(route, () => {
  addTags();
  moveToCurrentTag();
});

watch(visible, (value) => {
  if (value) {
    document.body.addEventListener('click', closeMenu);
  } else {
    document.body.removeEventListener('click', closeMenu);
  }
});

onMounted(() => {
  initTags();
  addTags();
});

function isActive(r) {
  return r.path === route.path;
}

function activeStyle(tag) {
  return {};
}

function isAffix(tag) {
  return tag.meta && tag.meta.affix;
}

function isFirstView() {
  try {
    return (
      selectedTag.value.fullPath === '/index' ||
      selectedTag.value.fullPath === visitedViews.value[1].fullPath
    );
  } catch (err) {
    return false;
  }
}

function isLastView() {
  try {
    return (
      selectedTag.value.fullPath ===
      visitedViews.value[visitedViews.value.length - 1].fullPath
    );
  } catch (err) {
    return false;
  }
}

function filterAffixTags(routes, basePath = '') {
  let tags = [];
  routes.forEach((route) => {
    if (route.meta && route.meta.affix) {
      const tagPath = getNormalPath(basePath + '/' + route.path);
      tags.push({
        fullPath: tagPath,
        path: tagPath,
        name: route.name,
        meta: { ...route.meta }
      });
    }
    if (route.children) {
      const tempTags = filterAffixTags(route.children, route.path);
      if (tempTags.length >= 1) {
        tags = [...tags, ...tempTags];
      }
    }
  });
  return tags;
}

function initTags() {
  const res = filterAffixTags(routes.value);
  affixTags.value = res;
  for (const tag of res) {
    // Must have tag name
    if (tag.name) {
      useTagsViewStore().addVisitedView(tag);
    }
  }
}

function addTags() {
  const { name } = route;
  if (name) {
    useTagsViewStore().addView(route);
    if (route.meta.link) {
      useTagsViewStore().addIframeView(route);
    }
  }
  return false;
}

function moveToCurrentTag() {
  nextTick(() => {
    for (const r of visitedViews.value) {
      if (r.path === route.path) {
        scrollPaneRef.value.moveToTarget(r);
        // when query is different then update
        if (r.fullPath !== route.fullPath) {
          useTagsViewStore().updateVisitedView(route);
        }
      }
    }
  });
}

function refreshSelectedTag(view) {
  console.log(view, 'view');

  proxy.$tab.refreshPage(view);
  if (route.meta.link) {
    useTagsViewStore().delIframeView(route);
  }
}

function closeSelectedTag(view) {
  proxy.$tab.closePage(view).then(({ visitedViews }) => {
    if (isActive(view)) {
      toLastView(visitedViews, view);
    }
  });
}

function closeRightTags() {
  proxy.$tab.closeRightPage(selectedTag.value).then((visitedViews) => {
    if (!visitedViews.find((i) => i.fullPath === route.fullPath)) {
      toLastView(visitedViews);
    }
  });
}

function closeLeftTags() {
  proxy.$tab.closeLeftPage(selectedTag.value).then((visitedViews) => {
    if (!visitedViews.find((i) => i.fullPath === route.fullPath)) {
      toLastView(visitedViews);
    }
  });
}

function closeOthersTags() {
  router.push(selectedTag.value).catch(() => { });
  proxy.$tab.closeOtherPage(selectedTag.value).then(() => {
    moveToCurrentTag();
  });
}

function closeAllTags(view) {
  proxy.$tab.closeAllPage().then(({ visitedViews }) => {
    if (affixTags.value.some((tag) => tag.path === route.path)) {
      return;
    }
    toLastView(visitedViews, view);
  });
}

function toLastView(visitedViews, view) {
  const latestView = visitedViews.slice(-1)[0];
  if (latestView) {
    router.push(latestView.fullPath);
  } else {
    // now the default is to redirect to the home page if there is no tags-view,
    // you can adjust it according to your needs.
    if (view.name === 'Dashboard') {
      // to reload home page
      router.replace({ path: '/redirect' + view.fullPath });
    } else {
      router.push('/');
    }
  }
}

function openMenu(tag, e) {
  const menuMinWidth = 105;
  const offsetLeft = proxy.$el.getBoundingClientRect().left; // container margin left
  const offsetWidth = proxy.$el.offsetWidth; // container width
  const maxLeft = offsetWidth - menuMinWidth; // left boundary
  const l = e.clientX - offsetLeft + 15; // 15: margin right

  if (l > maxLeft) {
    left.value = maxLeft;
  } else {
    left.value = l;
  }

  top.value = e.clientY;
  visible.value = true;
  selectedTag.value = tag;
}

function closeMenu() {
  visible.value = false;
}

function handleScroll() {
  closeMenu();
}
</script>

<style lang="scss" scoped>
.tags-view-container {
  height: 36px;
  width: 100%;
  background: var(--ios26-bg-primary, #ffffff);
  border-bottom: 0.5px solid var(--ios26-separator-non-opaque, rgba(0, 0, 0, 0.12));
  box-shadow: none;
  display: flex;
  align-items: center;

  .tags-view-wrapper {
    display: flex;
    align-items: center;
    height: 100%;

    ::v-deep(.el-scrollbar__wrap) {
      display: flex;
      align-items: center;
    }

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      position: relative;
      cursor: pointer;
      height: 26px;
      line-height: 26px;
      border: 0.5px solid var(--ios26-separator-non-opaque, rgba(0, 0, 0, 0.12));
      border-radius: 1000px;
      color: var(--ios26-label-secondary, rgba(60, 60, 67, 0.6));
      background: var(--ios26-fill-vibrant-secondary, #e0e0e0);
      padding: 0 10px;
      font-size: 12px;
      font-weight: 510;
      margin: 0 3px;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      user-select: none;
      box-shadow: none;

      &:first-of-type {
        margin-left: 12px;
      }

      &:last-of-type {
        margin-right: 12px;
      }

      &:not(.active):hover {
        background: var(--ios26-fill-vibrant-primary, #cccccc);
        color: var(--ios26-label-primary, #000000);
        // 派生值: 派生自 --ios26-color-blue (#0088ff) 的 28% 透明度悬停微边框
        border-color: rgba(0, 136, 255, 0.28);
      }

      &.active {
        background: var(--ios26-color-blue, #0088ff) !important;
        color: #ffffff !important;
        border-color: var(--ios26-color-blue, #0088ff) !important;
        font-weight: 510 !important;
        // 派生值: 派生自 --ios26-color-blue (#0088ff) 的 25% 投影光晕
        box-shadow: 0 2px 6px rgba(0, 136, 255, 0.25) !important;
      }

      span {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        margin-left: 4px;
        width: 14px;
        height: 14px;
        border-radius: 50%;
        transition: all 0.15s ease;

        &:hover {
          background: rgba(0, 0, 0, 0.15);
          color: #ff383c;
        }

        .el-icon-close {
          width: 10px;
          height: 10px;
          cursor: pointer;
        }
      }

      &.active span:hover {
        background: rgba(255, 255, 255, 0.25);
        color: #ffffff;
      }
    }
  }

  .contextmenu {
    margin: 0;
    background: var(--ios26-bg-primary, #ffffff);
    border: 0.5px solid var(--ios26-separator-non-opaque, rgba(0, 0, 0, 0.12));
    border-radius: 12px;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08), 0 4px 10px rgba(0, 0, 0, 0.04);
    padding: 6px;
    min-width: 120px;
    z-index: 3000;
    position: absolute;
    list-style-type: none;

    li {
      margin: 1px 0;
      padding: 6px 12px;
      border-radius: 8px;
      color: var(--ios26-label-primary, #000000);
      font-size: 12px;
      font-weight: 510;
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        background: var(--ios26-fill-vibrant-secondary, #e0e0e0);
        color: var(--ios26-color-blue, #0088ff);
      }
    }
  }
}
</style>
