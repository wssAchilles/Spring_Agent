<template>
  <div class="stagingIndex">
    <div class="dashboard-layout">
      <main class="dashboard-main">
        <section class="welcome-panel">
          <div class="welcome-copy">
            <el-avatar :size="58" class="avatar">
              {{ userStore.nickName?.slice(0, 1) || "用" }}
            </el-avatar>
            <div>
              <p class="eyebrow">{{ todayLabel }}</p>
              <h1>上午好，{{ userStore.nickName }}</h1>
              <p class="welcome-desc">
                <span>系统管理员</span>
                <i></i>
                {{ xljtcont }}
              </p>
            </div>
          </div>
          <div class="welcome-actions">
            <el-button type="primary" @click="goprofile">个人中心</el-button>
            <el-button @click="logout">退出登录</el-button>
          </div>
        </section>

        <section class="metrics-grid" aria-label="知识资产核心指标">
          <article v-for="item in module1" :key="item.name" class="metric-item">
            <div class="metric-heading">
              <div>
                <p>{{ item.name }}</p>
                <strong>{{ item.value }}</strong>
              </div>
              <img :src="item.img" :alt="item.name" />
            </div>
            <div class="metric-trend">
              <span>同比</span>
              <b :class="item.up ? 'trend-up' : 'trend-down'">
                {{ item.up ? '↑' : '↓' }} {{ item.speed }}%
              </b>
            </div>
          </article>
        </section>

        <div class="analysis-grid">
          <section class="dashboard-panel chart-panel">
            <header class="panel-header">
              <div>
                <h2>文件类型统计</h2>
                <p>知识资产内容结构与占比</p>
              </div>
            </header>
            <div ref="module4ChartRef" class="chart-container"></div>
          </section>

          <section class="dashboard-panel chart-panel">
            <header class="panel-header">
              <div>
                <h2>近 7 日抽取数量</h2>
                <p>每日实体抽取任务完成情况</p>
              </div>
            </header>
            <div ref="module5ChartRef" class="chart-container"></div>
          </section>
        </div>

        <section class="dashboard-panel asset-overview">
          <header class="panel-header">
            <div>
              <h2>知识资产概览</h2>
              <p>按领域查看当前资产构成</p>
            </div>
          </header>
          <el-table :data="categoryStats" class="asset-table">
            <el-table-column prop="category" label="类别" min-width="150">
              <template #default="scope">
                <el-link type="primary" :underline="false">{{ scope.row.category }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="entities" label="实体数" align="right" />
            <el-table-column prop="relations" label="关系数" align="right" />
            <el-table-column prop="triples" label="三元组数" align="right" />
            <el-table-column prop="files" label="文件数" align="right" />
            <el-table-column prop="tasks" label="抽取任务" align="right" />
            <el-table-column prop="updatedAt" label="更新时间" align="right" min-width="130" />
          </el-table>
        </section>

        <section class="dashboard-panel trend-panel">
          <header class="panel-header">
            <div>
              <h2>近半年实体新增趋势</h2>
              <p>知识资产持续沉淀情况</p>
            </div>
          </header>
          <div ref="module8ChartRef" class="chart-container"></div>
        </section>
      </main>

      <aside class="dashboard-aside">
        <section class="dashboard-panel weather-panel">
          <Weather />
        </section>

        <section class="dashboard-panel aside-panel">
          <header class="panel-header compact">
            <h2>公告</h2>
            <el-link type="primary" :underline="false" @click="goxinwen">查看更多</el-link>
          </header>
          <div class="notice-list">
            <button
              v-for="(item, index) in module6"
              :key="index"
              type="button"
              class="notice-row"
              @click="goxinwen"
            >
              <span class="notice-title" :title="item.noticeTitle">{{ item.noticeTitle }}</span>
              <time>{{ parseTime(item.createTime, "{y}-{m}-{d}") }}</time>
            </button>
            <el-empty v-if="!module6.length" description="暂无公告" :image-size="56" />
          </div>
        </section>

        <section class="dashboard-panel aside-panel">
          <header class="panel-header compact">
            <h2>最近任务</h2>
            <span class="panel-count">{{ module9.length }} 项</span>
          </header>
          <div class="task-list">
            <div v-for="item in module9.slice(0, 4)" :key="item.id" class="task-row">
              <div class="task-copy">
                <strong :title="item.name">{{ item.name }}</strong>
                <span>{{ item.createTime }}</span>
              </div>
              <dict-tag :options="ext_task_status" :value="item.status" />
            </div>
          </div>
        </section>

        <section class="dashboard-panel aside-panel quick-panel">
          <header class="panel-header compact">
            <h2>快捷功能</h2>
          </header>
          <div class="quick-grid">
            <button
              v-for="item in entranceList"
              :key="item.name"
              v-hasPermi="item.perm"
              type="button"
              class="quick-item"
              @click="routeTo(item.path, item.query)"
            >
              <img :src="item.iconPath" alt="" />
              <span>{{ item.name }}</span>
            </button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup name="Index">
import useUserStore from "@/store/system/user";
import { listNotice } from "@/api/system/system/notice.js";
import Weather from "@/components/Weather/index.vue";
import * as echarts from "echarts";
// eslint-disable-next-line no-unused-vars
import {
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  getCurrentInstance,
} from "vue";

let { proxy } = getCurrentInstance();
const { ext_task_status,publish_status } = proxy.useDict(
  "ext_task_status",
  "publish_status"
);
const router = useRouter();
const { sys_notice_type } = proxy.useDict("sys_notice_type");
const getAssetsFile = (url) => {
  return new URL(`../../assets/system/images/index/${url}`, import.meta.url)
    .href;
};
const getEntranceIcon = (url) => {
  return new URL(`../../assets/system/images/entrance/${url}`, import.meta.url)
    .href;
};

const entranceList = [
  {
    name: "Bot管理",
    path: "/kb/bot/workflow?botType=0",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("bot.png"),
    color: "color-primary",
  },
  {
    name: "图谱探索",
    path: "/kg/app/graphExploration",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("graph.png"),
    color: "color-pale-blue",
  },
  {
    name: "概念配置",
    path: "/kg/ext/schema",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("concept.png"),
    color: "color-orange",
  },
  {
    name: "非结构化抽取",
    path: "/kg/ext/unstructTask",
    query: {},
    perm: ["ext:extUnstructTask:unstructtask:list"],
    iconPath: getEntranceIcon("unstruct.png"),
    color: "color-pink",
  },
  {
    name: "结构化抽取",
    path: "/kg/ext/extStructTask",
    query: {},
    perm: ["ext:extStructTask:struct:list"],
    iconPath: getEntranceIcon("struct.png"),
    color: "color-green",
  },
  {
    name: "应用中心",
    path: "/kac/overview",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("app.png"),
    color: "color-purple",
  },
  {
    name: "知识库",
    path: "/kmc/knowledgeBase",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("kb.png"),
    color: "color-yellow",
  },
  {
    name: "插件中心",
    path: "/plugin/plugin",
    query: {},
    perm: [""],
    iconPath: getEntranceIcon("plugin.png"),
    color: "color-red",
  },
];

const chartIntances = [];
// eslint-disable-next-line no-unused-vars
const userStore = useUserStore();
const module1 = ref([
  {
    name: "实体总数",
    value: 126,
    up: true,
    speed: 12,
    img: getAssetsFile("1.png"),
  },
  {
    name: "关系总数",
    value: 72,
    up: true,
    speed: 2,
    img: getAssetsFile("2.png"),
  },
  {
    name: "三元组总数",
    value: 164,
    up: true,
    speed: 9,
    img: getAssetsFile("3.png"),
  },
  {
    name: "文件总数",
    value: 76,
    up: true,
    speed: 10,
    img: getAssetsFile("4.png"),
  },
  {
    name: "抽取任务总数",
    value: 18,
    up: false,
    speed: 10,
    img: getAssetsFile("5.png"),
  },
]);

const todayLabel = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(new Date());

const categoryStats = [
  { category: "基础理论", entities: 32, relations: 18, triples: 46, files: 20, tasks: 5, updatedAt: "2026-06-21" },
  { category: "智能技术", entities: 38, relations: 22, triples: 62, files: 19, tasks: 5, updatedAt: "2026-06-21" },
  { category: "行业应用", entities: 18, relations: 12, triples: 20, files: 10, tasks: 3, updatedAt: "2026-06-20" },
  { category: "未来趋势", entities: 24, relations: 14, triples: 24, files: 15, tasks: 4, updatedAt: "2026-06-20" },
  { category: "其他", entities: 14, relations: 6, triples: 12, files: 12, tasks: 1, updatedAt: "2026-06-19" },
];

//新闻跳转
function goxinwen() {
  proxy.$router.push("/system/notice"); // 内部页面路径
}

function goprofile() {
  proxy.$router.push("/user/profile"); // 内部页面路径
}

// 认证模式
const authType = import.meta.env.VITE_APP_AUTH_TYPE;
function logout() {
  ElMessageBox.confirm("确定注销并退出系统吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      userStore.logOut().then(() => {
        if (authType === "sso") {
          // 退出统一认证中心的登录状态
          loginOut(userStore.userId).then(() => {
            location.href = "/index";
          });
        } else {
          location.href = "/index";
        }
      });
    })
    .catch(() => {});
}

async function routeTo(link, query = {}) {
  if (link && link.indexOf("http") !== -1) {
    window.location.href = link;
    return;
  }

  if (link) {
    if (link === router.currentRoute.value.path) {
      window.location.reload();
    } else {
      try {
        await router.push({ path: link, query });
        // 跳转成功后再刷新
        // window.location.reload();
      } catch (err) {
        console.error("路由跳转失败:", err);
      }
    }
  }
}
//获取心灵鸡汤内容
const xljtcont = ref("");
function getxljtcont() {
  let xljtlist = [
    { value: "从知识图谱到智能应用，构建企业AI大脑。" },
    { value: "重塑企业知识力，构建 AI 落地新范式。" },
    // { value: "数据是新时代的金矿，用心挖掘就能发现无尽的价值。" },
    // { value: "AI赋能，让知识管理更智能，让工作效率倍增。" },
    // { value: "构建知识图谱，连接万物智慧，让信息不再孤岛。" },
    // { value: "文档管理井然有序，工作生活从容不迫。" },
    // { value: "每一次对话都是思想的碰撞，每一次搜索都是知识的探索。" },
    // { value: "系统稳定运行，服务永不止步，我们一直在路上。" },
    // { value: "知识积累如滴水成河，终能汇聚成智慧的海洋。" },
    // { value: "用科技赋能知识，让智慧触手可及。" },
  ];
  let num = Math.floor(Math.random() * xljtlist.length);
  xljtcont.value = xljtlist[num].value;
}

const module4ChartRef = ref(null);
function initModule4() {
  const intance = echarts.init(module4ChartRef.value, "macarons");
  let m2R2Data = [
    // {
    //     value: 335,
    //     legendname: '种类06',
    //     name: '种类06  335'
    //     // itemStyle: { color: '#fca4bb' }
    // },
    // {
    //     value: 335,
    //     legendname: '种类07',
    //     name: '种类07  335'
    //     // itemStyle: { color: '#f59a8f' }
    // },
    {
      value: 130,
      legendname: "基础理论",
      name: "基础理论",
      // itemStyle: { color: '#fdb301' }
    },
    {
      value: 150,
      legendname: "智能技术",
      name: "智能技术",
      // itemStyle: { color: '#57e7ec' }
    },
    {
      value: 100,
      legendname: "行业应用",
      name: "行业应用",
      // itemStyle: { color: '#cf9ef1' }
    },
    {
      value: 190,
      legendname: "未来趋势",
      name: "未来趋势",
      // itemStyle: { color: '#57e7ec' }
    },
    {
      value: 200,
      legendname: "其他",
      name: "其他",
      // itemStyle: { color: '#cf9ef1' }
    },
  ];

  let option = {
    title: [
      // {
      //     text: '标题',
      //     textStyle: {
      //         fontSize: 16,
      //         color: 'black'
      //     },
      //     left: '2%'
      // },
      {
        text: "100%",
        // subtext: 12312 + '个',
        textStyle: {
          fontSize: 26,
          color: "#172033",
          fontWeight: 600,
        },
        // subtextStyle: {
        //     fontSize: 20,
        //     color: 'black'
        // },
        textAlign: "center",
        x: "34%",
        y: "42%",
      },
    ],
    tooltip: {
      trigger: "item",
      formatter: function (parms) {
        let str =
          parms.seriesName +
          "</br>" +
          parms.marker +
          "" +
          parms.data.legendname +
          "</br>" +
          "数量：" +
          parms.data.value +
          "</br>" +
          "占比：" +
          parms.percent +
          "%";
        return str;
      },
    },
    legend: {
      type: "scroll",
      orient: "vertical",
      left: "75%",
      align: "left",
      top: "10%",
      // top: 'middle',
      itemWidth: 8, // 设置图例项的宽度
      itemHeight: 8, // 设置图例项的高度
      icon: "circle", // 设置图例图标为圆形
      // textStyle: {
      //     color: '#8C8C8C',
      //     fontSize: 14,
      //     lineHeight: 20 // 设置行高，以确保文本垂直居中
      // },

      formatter: function (name) {
        // 找到对应的项并计算百分比
        console.log(name, "============name");
        let total = 770;
        let item = m2R2Data.find((item) => item.name === name);
        // eslint-disable-next-line no-unused-vars
        let percent = item
          ? ((item.value / total) * 100).toFixed(2) + "%"
          : "0%";
        // return `${name} |  ${percent}  ${item.value}`; // 自定义图例显示
        // 使用 HTML 语法自定义颜色
        let arr = [
          "{a|" + name + "}",
          // '{b|' + '|   ' + percent + '}',
          // '{c|' + item.value + '}'
        ];
        return arr.join("  ");
        // return `${name}: <span style="color: red;">${percent}</span> (<span style="color: blue;">${percent}</span>)`;
      },
      textStyle: {
        lineHeight: 25, // 设置行高，以确保文本垂直居中

        // 添加
        padding: [0, 0, 0, 0],
        rich: {
          a: {
            fontSize: 14,
            width: 46,
          },
          b: {
            fontSize: 14,
            width: 70,
            color: "#888888",
          },
          c: {
            fontSize: 14,
            color: "rgba(0,0,0,0.65)",
          },
        },
      },
      // height:250
    },
    series: [
      {
        name: "标题",
        type: "pie",
        center: ["35%", "50%"],
        radius: ["35%", "55%"],
        clockwise: false, //饼图的扇区是否是顺时针排布
        avoidLabelOverlap: false,
        // label: {
        //     normal: {
        //         // show: true,
        //         // position: 'outter',
        //         formatter: function (parms) {
        //             return parms.data.value;
        //         }
        //     }
        // },
        label: {
          formatter: "{d}%", // 显示每个部分的名称和占比
          position: "outside", // 标签位置
        },
        itemStyle: {
          color: (params) => ["#3478f6", "#28b7ad", "#f2b43c", "#8c6bd8", "#ef5f72"][params.dataIndex],
          borderColor: "#ffffff",
          borderWidth: 2,
        },
        data: m2R2Data,
      },
    ],
  };
  intance.setOption(option);
  chartIntances.push(intance);
  //  // 窗口大小变化时，自动更新图表大小
  // window.addEventListener('resize', function() {
  //     intance.resize();
  // });
}

const module5ChartRef = ref(null);
function initModule5() {
  const intance = echarts.init(module5ChartRef.value, "macarons");
  intance.setOption({
    grid: {
      top: "15%",
      bottom: "10%",
      right: "5%",
    },
    legend: {
      show: true,
      itemGap: 50,
      data: ["抽取实体数量"],
      icon: "circle",
      itemWidth: 8, // 设置图例项的宽度
      itemHeight: 8, // 设置图例项的高度
      right: "5%",
      top: "1%",
      textStyle: {
        color: "rgba(0,0,0,0.65)",
        fontSize: 15,
        lineHeight: 30, // 设置行高，以确保文本垂直居中
      },
    },
    xAxis: {
      type: "category",
      data: ["05-01", "05-02", "05-03", "05-04", "05-05", "05-06", "05-07"],
    },
    yAxis: {
      type: "value",
      max: 1000, // 设置 Y 轴最大值为 1000
    },
    series: [
      {
        type: "bar",
        name: "抽取实体数量",
        barWidth: 30,
        itemStyle: {
          color: "#3478f6",
          borderRadius: [3, 3, 0, 0],
        },
        data: [800, 550, 740, 450, 800, 730, 600],
      },
    ],
  });
  chartIntances.push(intance);
}

const module6 = ref([
  {
    date: "2016-05-05",
    title: "置顶",
    value: "XXXXXxxx科技有限公司",
    type: "danger",
  },
  {
    date: "2016-05-03",
    title: "新闻",
    value: "XXXXXxxx科技有限公司",
    type: "success",
  },
  {
    date: "2016-05-02",
    title: "其他",
    value: "XXXXXxxx科技有限公司",
    type: "primary",
  },
  {
    date: "2016-05-04",
    title: "其他",
    value: "XXXXXxxx科技有限公司",
    type: "info",
  },
  {
    date: "2016-05-06",
    title: "公告",
    value: "XXXXXxxx科技有限公司",
    type: "warning",
  },
]);

function initModule6() {
  let query = {
    pageNum: 1,
    pageSize: 5,
  };
  listNotice(query).then((response) => {
    module6.value = response.rows || [];
  }).catch(() => {
    module6.value = [];
  });
}

const module8ChartRef = ref(null);
function initModule8() {
  const intance = echarts.init(module8ChartRef.value, "macarons");
  intance.setOption({
    // title: [
    //     {
    //         text: '告警趋势',
    //         textStyle: {
    //             fontSize: 14,
    //             color: 'rgba(0,0,0,0.65)'
    //         },
    //         right: '2%'
    //     }
    // ],
    legend: {
      show: true,
      itemGap: 50,
      data: ["实体新增趋势"],
      icon: "circle",
      itemWidth: 6, // 设置图例项的宽度
      itemHeight: 6, // 设置图例项的高度
      right: "5%",
      top: "1%",
      textStyle: {
        color: "rgba(0,0,0,0.65)",
        fontSize: 15,
        lineHeight: 30, // 设置行高，以确保文本垂直居中
      },
    },
    grid: {
      top: "18%",
      bottom: "10%",
      right: "5%",
    },
    xAxis: {
      type: "category",
      data: ["25年1月", "25年2月", "25年3月", "25年4月", "25年5月", "25年6月"],
    },
    yAxis: {
      type: "value",
      max: 1000, // 设置 Y 轴最大值为 1000
    },
    series: [
      {
        name: "告警趋势",
        lineStyle: {
          normal: {
            color: "#5285fd",
          },
        },
        // itemStyle: {
        //     color: '#3571FC',
        //     borderColor: '#3571FC'
        // },

        // symbol: 'circle', // 数据点形状
        symbolSize: 8, // 数据点大小
        itemStyle: {
          color: "#427afd",
          borderColor: "#427afd", // 边框颜色
          borderWidth: 1, // 边框宽度
        },

        areaStyle: {
          color: "rgba(52, 120, 246, 0.08)",
        },
        data: [250, 100, 780, 60, 760, 200],
        type: "line",
      },
    ],
  });
  chartIntances.push(intance);
}

const module9 = ref([
  {
    id: "1",
    name: "《2030年知识经济预测模型》",
    status: "1",
    publishStatus: "1",
    publishBy: "管理员",
    createTime: "2025-05-01 11:23",
  },
  {
    id: "2",
    name: "《金融知识图谱构建与风险预警白皮书》",
    status: "2",
    publishStatus: "1",
    publishBy: "管理员",
    createTime: "2025-05-01 11:23",
  },
  {
    id: "3",
    name: "《多模态知识融合算法白皮书》",
    status: "1",
    publishStatus: "1",
    publishBy: "管理员",
    createTime: "2025-05-01 11:23",
  },
  {
    id: "4",
    name: "《智慧城市知识管理解决方案》",
    status: "2",
    publishStatus: "0",
    publishBy: "管理员",
    createTime: "2025-05-01 11:23",
  },
  {
    id: "5",
    name: "《智能教育平台知识点分类体系》",
    status: "1",
    publishStatus: "1",
    publishBy: "管理员",
    createTime: "2025-05-01 11:23",
  },
]);

function chartIntancesResize() {
  console.log(chartIntances);
  chartIntances.forEach((intance) => {
    intance.resize();
  });
}

window.addEventListener("resize", chartIntancesResize);

onBeforeUnmount(() => {
  window.removeEventListener("resize", chartIntancesResize);
});

// 获取当前实例
const instance = getCurrentInstance();

const callback = () => {
  window.addEventListener("resize", chartIntancesResize);
};

// 在组件销毁时移除事件监听
onBeforeUnmount(() => {
  instance.appContext.config.globalProperties.$bus.off(
    "getsidebarStatus",
    callback
  );
});

onMounted(() => {
  initModule4();
  initModule5();
  initModule6();
  initModule8();
  getxljtcont();
  instance.appContext.config.globalProperties.$bus.on(
    "getsidebarStatus",
    () => {
      window.addEventListener("resize", chartIntancesResize);
    }
  );
});
</script>

<style scoped lang="scss">
.home-gutter {
  position: relative;
}

.work-time-div {
  width: 85%;
  height: 99%;
  position: absolute;
  /* top: 20px; */
  right: 20px;
  background-color: #ffffff;
  /* color: white; */
  /* font-size: 18px; */
  /* font-weight: bold; */
  /* padding: 30px 30px; */
  border-radius: 2px;
  text-align: center;
  /* cursor: pointer; */
  /* box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2); */
  /* transition: transform 0.3s, box-shadow 0.3s;
        }

        .work-time-div:hover {
            /*transform: scale(1.05);*/
  /*box-shadow: 0 6px 12px rgba(0, 0, 0, 0.3);*/
}

.work-time-div:active {
  transform: scale(1);
}

.stagingIndex {
  min-width: 1290px;
  max-width: 100%;
  background: #f0f2f5;

  ::v-deep .el-carousel {
    height: 100%;
    .el-carousel__container {
      height: 100%;
    }
    .el-carousel__arrow--left {
      display: none;
    }
    .el-carousel__arrow--right {
      display: none;
    }
  }
}

.module-4,
.module-5,
.module-8,
.module-9 {
  height: 350px !important;
  //   box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.2);
}

.module-6,
.module-7 {
  height: 250px !important;
  //   box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.2);
}
.module-report {
  margin-bottom: 15px;
  height: 60px !important;
}
.module-report .report-button {
  width: 100%;
  height: 60px;
  font-size: 22px;
  border-radius: 2px;
}

.home-gutter {
  margin-bottom: 15px;
}

.userInfo {
  height: 150px;
  padding: 35px 60px 0 32px;
  border-radius: 2px;
  justify-content: space-between;
  align-items: center;
  background: url(@/assets/system/images/index/content.png) no-repeat center;
  background-size: 100% 100%;
  //   box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.2);
}

.userInfo .menu {
  margin-bottom: 16px;
}

.userInfo .menu .el-breadcrumb__inner.is-link {
  font-family: PingFang SC;
  font-size: 14px;
  color: #888888;
  line-height: 22px;
  font-weight: 400;
}

.userInfo .menu .el-breadcrumb__inner {
  font-family: PingFang SC;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  text-align: left;
  line-height: 22px;
  font-weight: 400;
}

.userInfo .menu .el-breadcrumb__separator {
  font-size: 14px;
  color: #888888;
}

.userInfo .info-main {
  min-width: 100%;
  display: flex;
  position: relative;
  flex-shrink: 0;
  align-items: center;
}

.userInfo .info-main .avatar {
  width: 72px;
  height: 72px;
  margin-right: 24px;
  border-radius: 50%;
}

.userInfo .info-main .info-con {
  height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.userInfo .info-main .info-con .info-con-name {
  font-family: PingFang SC;
  font-size: 20px;
  color: rgba(0, 0, 0, 0.85);
  line-height: 28px;
  font-weight: 600;
}

.userInfo .info-main .info-con .info-con-desc {
  font-family: PingFang SC;
  font-size: 14px;
  color: #888888;
  line-height: 22px;
  font-weight: 400;
}

.info-btns {
    position: absolute;
    right: 0px;
    font-family: PingFang SC;
    font-size: 14px;
    font-weight: 500;

    img {
    margin-right: 5px;
    }

    .info-btn {
    cursor: pointer;
    color: #fff;
    width: 100px;
    height: 40px;
    display: inline-flex;
    justify-content: center;
    align-items: center;
    border-radius: 4px;
    background: var(--el-color-primary);

    .icon {
        width: 15px;
        height: 15px;
        background: url("@/assets/system/images/index/user-icon.png") no-repeat;
        margin-right: 5px;
    }
    }

    .btn-dft {
    color: var(--el-color-primary);
    border: 1px solid var(--el-color-primary);
    background: transparent;
    margin-left: 20px;

    .icon {
        width: 16px;
        height: 13px;
        background: url("@/assets/system/images/index/logout-icon.png") no-repeat;
    }
    }
}


.module-2 {
  height: 150px;
  // background-color: #fff;
  border-radius: 2px;
  text-align: center;
  //   box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.2);

  img {
    height: 100%;
  }
}

.module-2 #mobile12 .wtleft {
  width: 30%;
}

.module-3 {
  display: flex;

  justify-content: space-between;
  height: 150px;
  margin-bottom: 15px;
}

.module-3 .module-item {
  // width: 18.3%;
  // min-width: 185px;
  flex: 1;
  height: 100%;
  border-radius: 2px;
  padding: 20px;
  background: #fff;
  margin-right: 15px;
  //   box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.2);
}
.module-3 .module-item:last-of-type {
  margin-right: 0px;
}

.module-3 .module-item .module-item-t {
  display: flex;

  align-items: center;

  justify-content: space-between;
}

.module-3 .module-item .module-item-t .module-item-t-l {
  font-family: PingFang SC;
  font-size: 14px;
  color: #888888;
  line-height: 22px;
  font-weight: 400;
}

.module-3 .module-item .module-item-t .module-item-t-l .name {
  margin-bottom: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.module-3 .module-item .module-item-t .module-item-t-l span {
  font-family: Sharp;
  // font-size: 34px;
  font-size: 30px;
  color: #000;
}

.module-3 .module-item .module-item-t .module-item-t-r {
  width: 48px;
  height: 48px;
}

.module-3 .module-item .module-item-border {
  height: 1px;
  background: #e2e2e2;
  margin: 20px 0 12px;
}

.module-3 .module-item .module-item-data {
  display: flex;

  align-items: center;
  // color: #666;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.module-3 .module-item .module-item-data .data-up {
  font-size: 14px;
  margin: 0 10px;
  // color: #000;
  color: rgba(0, 0, 0, 0.85);
  position: relative;
}

.module-3 .module-item .module-item-data .data-up:after {
  content: "";
  position: absolute;
  top: -2px;
  right: -16px;
  width: 0;
  height: 12px;
  border: 6px solid #f5222d;
  border-color: transparent transparent #f5222d transparent;
}

.module-3 .module-item .module-item-data .data-down {
  font-size: 14px;
  margin: 0 10px;
  color: #000;
  position: relative;
}

.module-3 .module-item .module-item-data .data-down:after {
  content: "";
  position: absolute;
  top: 4px;
  right: -16px;
  width: 0;
  height: 12px;
  border: 6px solid #f5222d;
  border-color: #60f522 transparent transparent transparent;
}

.module-3 .module-item .module-item-data .data-equal {
  font-size: 14px;
  margin: 0 10px;
  color: #000;
  position: relative;
}

.border-item {
  width: 100%;
  height: 100%;
  background: #fff;
  border-radius: 2px;
}

.border-item .border-item-head {
  height: 50px;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e8e8e8;
}

.border-item .border-item-head .head-title {
    font-size: 16px;
    color: #000000d9;
    display: flex;
    align-items: center;
    font-family: PingFang SC;
    font-weight: 500;
}
.border-item .border-item-head .head-title-seach {
  cursor: pointer;
  font-size: 14px;
  color: #0f62ff;
  line-height: 0px;
  font-style: normal;
}

.border-item .border-item-head .head-title:before {
    display: inline-block;
    content: "";
    width: 6px;
    height: 16px;
    border-radius: 3px;
    background: var(--el-color-primary);
    margin-right: 8px;
}

.border-item .border-item-body {
  height: calc(100% - 50px);
  position: relative;
}

.border-item .border-item-body .chart-container {
  height: 100%;
}

.border-item .border-item-body .border-item-order {
  display: flex;
  justify-content: space-between;
  padding: 12px 20px 0 20px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
  line-height: 22px;
  font-weight: 400;
}

.border-item .border-item-body .border-item-order .name-span {
  width: 225px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.border-item .border-item-body .border-item-order .name-span:hover {
  color: #409eff;
}

.border-item .border-item-body .border-item-order .time-span {
  margin-left: 20px;
  width: 60px;
  min-width: 60px;
  height: 22px;
  font-size: 14px;
  font-family: HelveticaNeue;
  color: #888888;
  line-height: 22px;
}

.border-item .border-item-body .border-item-order .top {
  width: 50px;
  height: 22px;
  background: rgba(255, 0, 25, 0.06);
  border-radius: 2px;
  color: #ff0019;
}

.border-item .border-item-body .border-item-order .hui-span {
  background: #f0f2f5 !important;
  color: rgba(0, 0, 0, 0.65) !important;
}

.module3-item {
  background-color: #f7f7f7;
}

.module-6 {
  .border-item-body {
    overflow-y: auto;
    overflow-x: hidden;
    padding: 12px 0px;
  }
  .module-item {
    display: flex;
    align-items: center;
    padding: 6px 20px;
    cursor: pointer;
  }
  // .value,
  // .title {
  //     margin: 0 10px;
  // }

  .value {
    margin: 0 10px;
    font-size: 14px;
    white-space: nowrap;
    text-overflow: ellipsis;
    overflow: hidden;
    flex: 1;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.65);
  }

  .time {
    width: 90px;
    text-align: right;
    font-size: 14px;
    color: #888888;
  }
}

.module-7 {
  .news {
    height: 245px;

    .border-item-body {
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .toAll {
      font-family: PingFangSC-Regular;
      font-size: 14px;
      color: #262626;
      font-weight: 400;

      &:hover {
        color: #0f62ff;
      }
    }

    .all-entrance {
        width: 100%;
      display: grid;
      grid-template-columns: repeat(4, 1fr);

      .entrance-item {
        padding: 14px 4px;
        text-align: center;
        font-size: 14px;
        .name {
          margin-top: 5px;
          color: #5a5e66;
        }

        .image {
          height: 40px;
          display: flex;
          justify-content: center;

          // 图标圆角方形背景
          .icon-background {
            width: 40px;
            height: 40px;
            border-radius: 6px;
            position: relative;
            display: flex;
            justify-content: center;
            align-items: center;
          }

          // 图标
          .icon {
            display: flex;
            justify-content: center;
            align-items: center;
            font-size: 24px;
            color: #fff;
            line-height: 40px;
          }

          // 背景色：主色
          .color-primary {
            background-image: linear-gradient(
              to bottom right,
              #2162fc 30%,
              #4f84fd 80%
            );
          }

          // 背景色：橙色
          .color-orange {
            background-image: linear-gradient(
              to bottom right,
              #f59040 30%,
              #f9bd77 90%
            );
          }

          // 背景色：淡蓝色
          .color-pale-blue {
            background-image: linear-gradient(
              to bottom right,
              #348bf2 10%,
              #63abff 60%
            );
          }

          // 背景色：粉红色
          .color-pink {
            background-image: linear-gradient(
              to bottom right,
              #fb6594 20%,
              #fc92bb 80%
            );
          }
        }
      }
    }
  }
}

.module-9 {
  // ::v-deep .el-table {
  //     thead .el-table__cell.is-leaf {
  //         background-color: #fff !important;
  //     }
  // }
}
.module-9 {
  .border-item-body {
    padding: 15px 34px 15px 34px;
  }
}
</style>

<style scoped lang="scss">
.stagingIndex {
  min-width: 0;
  width: 100%;
  max-width: none;
  min-height: 100%;
  padding: 18px;
  background: #f5f7fa;
  color: #172033;
  font-family: "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

.dashboard-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  max-width: 1680px;
  margin: 0 auto;
  align-items: start;
}

.dashboard-main,
.dashboard-aside {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.dashboard-panel,
.welcome-panel,
.metrics-grid {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
}

.welcome-panel {
  min-height: 116px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.welcome-copy {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.welcome-copy .avatar {
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  border-radius: 50%;
  object-fit: cover;
  background: #eef4ff;
  border: 1px solid #d9e5f8;
  color: #2468e5;
  font-weight: 600;
}

.welcome-copy h1 {
  margin: 3px 0 7px;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  color: #172033;
  letter-spacing: 0;
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: #8a95a6;
}

.welcome-desc {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: #6f7b8d;
  font-size: 13px;
  line-height: 20px;
}

.welcome-desc span {
  color: #3478f6;
  white-space: nowrap;
}

.welcome-desc i {
  width: 1px;
  height: 14px;
  flex: 0 0 1px;
  background: #dfe4eb;
}

.welcome-actions {
  display: flex;
  flex: 0 0 auto;
}

.welcome-actions :deep(.el-button) {
  min-width: 88px;
  height: 36px;
  border-radius: 4px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  overflow: hidden;
}

.metric-item {
  padding: 18px;
  min-width: 0;
  border-right: 1px solid #e7ebf1;
}

.metric-item:last-child {
  border-right: 0;
}

.metric-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.metric-heading p {
  margin: 0 0 7px;
  font-size: 13px;
  line-height: 20px;
  color: #7d8898;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-heading strong {
  display: block;
  font-size: 28px;
  line-height: 34px;
  font-weight: 600;
  color: #111827;
}

.metric-heading img {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.metric-trend {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #edf0f4;
  font-size: 12px;
  color: #8a95a6;
}

.metric-trend b {
  font-weight: 500;
}

.trend-up {
  color: #e5484d;
}

.trend-down {
  color: #1a9b62;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
}

.panel-header {
  min-height: 64px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e9edf2;
}

.panel-header h2 {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  color: #172033;
  letter-spacing: 0;
}

.panel-header p {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: #929cab;
}

.panel-header.compact {
  min-height: 50px;
  padding: 12px 14px;
}

.panel-count {
  font-size: 12px;
  color: #8a95a6;
}

.chart-panel .chart-container {
  height: 318px;
}

.asset-overview {
  overflow: hidden;
}

.asset-table {
  width: 100%;
  padding: 0 10px 10px;
}

.asset-table :deep(.el-table__header th.el-table__cell) {
  height: 40px;
  background: #f8fafc;
  color: #677386;
  font-size: 12px;
  font-weight: 500;
}

.asset-table :deep(.el-table__row td.el-table__cell) {
  height: 40px;
  color: #4d596a;
  font-size: 13px;
}

.asset-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.trend-panel .chart-container {
  height: 260px;
}

.dashboard-aside {
  position: sticky;
  top: 78px;
}

.weather-panel {
  min-height: 172px;
  overflow: hidden;
}

.weather-panel :deep(> *) {
  min-height: 172px;
}

.aside-panel {
  overflow: hidden;
}

.notice-list,
.task-list {
  padding: 6px 14px 10px;
}

.notice-row {
  width: 100%;
  min-height: 42px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: transparent;
  color: #4d596a;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.notice-row:last-child {
  border-bottom: 0;
}

.notice-row:hover .notice-title {
  color: #3478f6;
}

.notice-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
}

.notice-row time {
  font-size: 11px;
  color: #9aa4b2;
}

.task-row {
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid #edf0f4;
}

.task-row:last-child {
  border-bottom: 0;
}

.task-copy {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.task-copy strong {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #344054;
  font-size: 13px;
  font-weight: 500;
}

.task-copy span {
  color: #98a2b3;
  font-size: 11px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
}

.quick-item {
  min-width: 0;
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 9px;
  border: 1px solid #e6eaf0;
  border-radius: 4px;
  background: #fff;
  color: #4d596a;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.quick-item:hover,
.quick-item:focus-visible {
  border-color: #a9c5fa;
  background: #f6f9ff;
  color: #1d5fd1;
  outline: none;
}

.quick-item img {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  object-fit: contain;
}

.quick-item span {
  min-width: 0;
  font-size: 12px;
  line-height: 17px;
  text-align: left;
}

@media (max-width: 1360px) {
  .dashboard-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
  }

  .metric-item {
    padding: 15px 13px;
  }

  .metric-heading img {
    width: 32px;
    height: 32px;
  }
}

@media (max-width: 1120px) {
  .dashboard-layout {
    grid-template-columns: 1fr;
  }

  .dashboard-aside {
    position: static;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .weather-panel,
  .quick-panel {
    grid-row: span 2;
  }
}

@media (max-width: 820px) {
  .stagingIndex {
    padding: 12px;
  }

  .welcome-panel {
    align-items: flex-start;
    flex-direction: column;
  }

  .welcome-actions {
    width: 100%;
  }

  .welcome-actions :deep(.el-button) {
    flex: 1;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(2n) {
    border-right: 0;
  }

  .metric-item {
    border-bottom: 1px solid #e7ebf1;
  }

  .analysis-grid,
  .dashboard-aside {
    grid-template-columns: 1fr;
  }

  .weather-panel,
  .quick-panel {
    grid-row: auto;
  }

  .asset-overview {
    overflow-x: auto;
  }

  .asset-table {
    min-width: 720px;
  }
}

@media (max-width: 520px) {
  .welcome-copy {
    align-items: flex-start;
  }

  .welcome-desc {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .metric-item,
  .metric-item:nth-child(2n) {
    border-right: 0;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
<style lang="scss" scoped>
@media screen and (max-width: 1280px) {
}
@media screen and (max-width: 992px) {
  .stagingIndex {
    min-width: 100%;
    padding: 12px;
    .userInfo {
      height: 100px;
      padding: 15px 15px;
      .info-main {
        align-items: center;
        .avatar {
          width: 48px;
          height: 48px;
          margin-right: 12px;
        }
        .info-con {
          .info-con-name {
            font-size: 14px;
            line-height: 20px;
          }
          .info-con-desc {
            font-size: 10px;
            line-height: 20px;
          }
        }
      }
      .info-btns {
        display: none;
      }
    }
    .module-3 {
      flex-direction: column;
      height: auto;
    }
    .module-3 .module-item {
      width: 100%;
      margin-bottom: 15px;
    }
  }
}
@media screen and (max-width: 768px) {
  .stagingIndex {
    min-width: 100%;
    padding: 12px;
    .userInfo {
      height: 100px;
      padding: 15px 15px;
      .info-main {
        align-items: center;
        .avatar {
          width: 48px;
          height: 48px;
          margin-right: 12px;
        }
        .info-con {
          .info-con-name {
            font-size: 14px;
            line-height: 20px;
          }
          .info-con-desc {
            font-size: 10px;
            line-height: 20px;
          }
        }
      }
      .info-btns {
        display: none;
      }
    }
    .module-3 {
      flex-direction: column;
      height: auto;
    }
    .module-3 .module-item {
      width: 100%;
      margin-bottom: 15px;
    }
  }
}
@media screen and (max-width: 576px) {
  .stagingIndex {
    min-width: 100%;
    padding: 12px;
    .userInfo {
      height: 100px;
      padding: 15px 15px;
      .info-main {
        align-items: center;
        .avatar {
          width: 48px;
          height: 48px;
          margin-right: 12px;
        }
        .info-con {
          .info-con-name {
            font-size: 14px;
            line-height: 20px;
          }
          .info-con-desc {
            font-size: 10px;
            line-height: 20px;
          }
        }
      }
      .info-btns {
        display: none;
      }
    }
    .module-3 {
      flex-direction: column;
      height: auto;
    }
    .module-3 .module-item {
      width: 100%;
      margin-bottom: 15px;
    }
  }
}
</style>
