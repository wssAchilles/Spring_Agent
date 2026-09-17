<template>
  <div v-if="!item.hidden">
    <template
      v-if="
        hasOneShowingChild(item.children, item) &&
        (!onlyOneChild.children || onlyOneChild.noShowingChildren) &&
        !item.alwaysShow
      "
    >
      <app-link
        v-if="onlyOneChild.meta"
        :to="resolvePath(onlyOneChild.path, onlyOneChild.query)"
        @click="handleMenuClick(onlyOneChild)"
      >
        <el-menu-item
          :index="resolvePath(onlyOneChild.path)"
          :class="{ 'submenu-title-noDropdown': !isNest, 'nest-menu': props.isNest }"
        >
          <svg-icon
            :style="getTitleStyle(onlyOneChild.meta.title)"
            :icon-class="getMenuIcon(onlyOneChild, item)"
          />
          <template #title
            ><span
              class="menu-title"
              :title="hasTitle(onlyOneChild.meta.title)"
              >{{ onlyOneChild.meta.title }}</span
            ></template
          >
        </el-menu-item>
      </app-link>
    </template>

    <el-sub-menu
      v-else
      ref="subMenu"
      :index="resolvePath(item.path)"
      teleported
    >
      <template v-if="item.meta" #title>
        <svg-icon
          :style="getTitleStyle(item.meta.title)"
          :icon-class="getMenuIcon(item)"
        />
        <span class="menu-title" :title="hasTitle(item.meta.title)">{{
          item.meta.title
        }}</span>
      </template>

      <sidebar-item
        v-for="(child, index) in item.children"
        :key="child.path + index"
        :is-nest="true"
        :item="child"
        :base-path="resolvePath(child.path)"
        class="nest-menu"
      />
    </el-sub-menu>
  </div>
</template>

<script setup>
import { isExternal } from "@/utils/validate";
import AppLink from "./Link";
import { getNormalPath } from "@/utils/anivia.js";
import useUserStore from "@/store/system/user.js";
const route = useRoute();
const router = useRouter();

const props = defineProps({
  // route object
  item: {
    type: Object,
    required: true,
  },
  isNest: {
    type: Boolean,
    default: false,
  },
  basePath: {
    type: String,
    default: "",
  },
});

const onlyOneChild = ref({});

// 菜单语义图标保底字典 (当数据库未配置或配为'#'时自动激活)
const FALLBACK_ICON_MAP = {
  '知识中心': 'book-open-line',
  '知识抽取': 'file-ai-line',
  '知识应用': 'apps-ai-fill',
  '数据管理': 'database-2-line',
  'Bot 管理': 'bot管理',
  'Bot管理': 'bot管理',
  '知识文件': 'file-text-line',
  '知识分类': 'folder-5-fill',
  '图谱探索': 'kac-entity-graph',
  '工作流': 'flow-chart',
  'Chatflow': 'message-ai-3-fill',
  'chatflow': 'message-ai-3-fill',
  'Agent': 'brain-ai-3-line',
  'agent': 'brain-ai-3-line',
  '工具管理': 'tools-line',
  '概念配置': 'atom-fill',
  '关系配置': 'link',
  '非结构化抽取': 'file-ai-line',
  '结构化抽取': 'table',
  '抽取日志': 'log',
  '数据源': 'database-2-line',
  '模型市场': 'apps-ai-fill',
  '我的模型': 'ai-generate-3d-fill',
  '知识库': 'book-open-fill',
  '基础设置': 'tools-line',
  '权限设置': 'lock',
  '检索设置': 'search',
  '删除设置': 'alert-triangle-fill',
  '概览': '概览',
  '解决方案': 'solution',
  '横向通用应用': '横向',
  '纵向行业应用': '纵向',
  '我的解决方案': 'my-solution',
  '我的应用': '我的应用',
  '系统管理': 'system'
};

const getMenuIcon = (child, parent) => {
  const childIcon = child?.meta?.icon;
  if (childIcon && childIcon !== '#' && childIcon !== '') {
    return childIcon;
  }
  const parentIcon = parent?.meta?.icon;
  if (parentIcon && parentIcon !== '#' && parentIcon !== '') {
    return parentIcon;
  }
  const title = child?.meta?.title || parent?.meta?.title || '';
  if (FALLBACK_ICON_MAP[title]) {
    return FALLBACK_ICON_MAP[title];
  }
  // 语义关键词模糊保底匹配
  if (title.includes('Bot') || title.includes('bot')) return 'bot管理';
  if (title.includes('抽取')) return 'file-ai-line';
  if (title.includes('文件') || title.includes('文档')) return 'file-text-line';
  if (title.includes('分类') || title.includes('目录')) return 'folder-5-fill';
  if (title.includes('图谱') || title.includes('探索')) return 'kac-entity-graph';
  if (title.includes('流') || title.includes('flow') || title.includes('Flow')) return 'flow-chart';
  if (title.includes('智能体') || title.includes('Agent') || title.includes('agent')) return 'brain-ai-3-line';
  if (title.includes('模型')) return 'apps-ai-fill';
  if (title.includes('数据') || title.includes('库')) return 'database-2-line';
  if (title.includes('应用')) return 'apps-ai-fill';
  if (title.includes('中心') || title.includes('知识')) return 'book-open-line';
  if (title.includes('配置') || title.includes('设置') || title.includes('管理')) return 'tools-line';
  if (title.includes('日志') || title.includes('记录')) return 'log';
  if (title.includes('权限') || title.includes('安全')) return 'lock';
  return 'article-fill';
};

const getTitleStyle = (title) => {
  return {
    fontSize: "18px !important",
    width: "18px !important",
    height: "18px !important",
  };
};

function hasOneShowingChild(children = [], parent) {
  if (!children) {
    children = [];
  }
  const showingChildren = children.filter((item) => {
    if (item.hidden) {
      return false;
    } else {
      // Temp set(will be used if only has one showing child)
      onlyOneChild.value = item;
      return true;
    }
  });

  // When there is only one child router, the child router is displayed by default
  if (showingChildren.length === 1) {
    return true;
  }

  // Show parent if there are no child router to display
  if (showingChildren.length === 0) {
    onlyOneChild.value = { ...parent, path: "", noShowingChildren: true };
    return true;
  }

  return false;
}

function resolvePath(routePath, routeQuery) {
  if (isExternal(routePath)) {
    return routePath;
  }
  if (isExternal(props.basePath)) {
    return props.basePath;
  }
  if (routeQuery) {
    let query = JSON.parse(routeQuery);
    return {
      path: getNormalPath(props.basePath + "/" + routePath),
      query: query,
    };
  }
  return getNormalPath(props.basePath + "/" + routePath);
}

function hasTitle(title) {
  if (title.length > 5) {
    return title;
  } else {
    return "";
  }
}

function handleMenuClick(onlyOneChild) {
  const menuPath = resolvePath(onlyOneChild.path, onlyOneChild.query);
  if (!menuPath) return;

  // 从当前路由获取所有 params
  const { params } = route;

  // 替换路径中所有 :param 形式的参数
  const targetPath = menuPath.replace(/:(\w+)/g, (match, key) => {
    if (params && params.hasOwnProperty(key)) {
      return params[key];
    }
    return match; // 没有对应参数就保留原样
  });

  router.push(targetPath);
}
</script>

<style></style>
