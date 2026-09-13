<template>
  <div class="app-container glass-card" ref="app-container">
    <div class="pagecont-top" v-show="showSearch" style="padding-bottom:15px">
      <div class="infotop">
        <div class="infotop-title mb15">
          <el-tag class="id-tag">
            {{ bot.id }}
          </el-tag>
          <span style="margin-left: 8px">
            {{ bot.name || "-" }}
          </span>
          <el-row :gutter="15" class="btn-style" style="margin-left: auto">
            <el-col :span="1.5">
              <el-button v-ripple
                  size="small"
                  class="fhbtn glass-btn"
                  plain
                  @click="handleReturn"
                  @mousedown="(e) => e.preventDefault()"
              >
                <svg-icon style="width: 1em;height: 1em; margin-right: 3px;" :iconClass="'fhs'" /> 返回
              </el-button>
            </el-col>
          </el-row>
        </div>
        <el-row :gutter="3" style="margin-bottom: 3px;">
          <el-col :span="8">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">Bot 名称</div>
              <div class="infotop-row-value">
                {{ bot.name || '-' }}
              </div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">类型</div>
              <div class="infotop-row-value">
                <dict-tag :options="kg_bot_type" :value="bot.type"/>
              </div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">是否内置</div>
              <div class="infotop-row-value">
                <dict-tag :options="sys_is_or_not" :value="bot.builtinFlag"/>
              </div>
            </div>
          </el-col>
        </el-row>
          <el-row :gutter="3" style="margin-bottom: 3px;">
          <el-col :span="8">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">创建人</div>
              <div class="infotop-row-value">
                {{ bot.createBy || "-" }}
              </div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">创建时间</div>
              <div class="infotop-row-value">
                <span>{{ parseTime(bot.createTime, "{y}-{m}-{d} {h}:{i}") }}</span>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="3">
          <el-col :span="24">
            <div class="infotop-row border-top">
              <div class="infotop-row-lable">描述</div>
              <div class="infotop-row-value">
                {{ bot.description || '-' }}
              </div>
            </div>
          </el-col>
        </el-row>

      </div>
    </div>

    <div class="pagecont-bottom">
      <el-tabs v-model="activeName" class="demo-tabs">
        <el-tab-pane label="访问密钥" name="apiKey">
          <BotApiKeyTable :botId="bot.id" v-if="activeName === 'apiKey'" ref="botApuKeyTable"></BotApiKeyTable>
        </el-tab-pane>
        <el-tab-pane label="执行日志" name="log">
          <BotLogTable :botId="bot.id" v-if="activeName === 'log'"></BotLogTable>
        </el-tab-pane>
      </el-tabs>
    </div>

  </div>
</template>

<script setup name="botDetail">
import {useRoute, useRouter} from 'vue-router';
import {getBot,} from "@/api/kb/bot/bot";
import BotLogTable from "@/views/kb/bot/detail/botLogTable.vue";
import BotApiKeyTable from "@/views/kb/bot/detail/botApiKeyTable.vue";

const {proxy} = getCurrentInstance();
const {kg_bot_type, sys_is_or_not} = proxy.useDict("kg_bot_type", "sys_is_or_not");
const router = useRouter();

const {
  ai_apikey_status,
  ai_model_platform,
  ai_deploy_type
} = proxy.useDict('ai_apikey_status', 'ai_model_platform', 'ai_deploy_type');

const activeName = ref('apiKey')

const showSearch = ref(true);
const botApuKeyTable = ref(null);
const route = useRoute();
let botId = route.query.botId;
let bot = ref({});
// 监听 id 变化
watch(
    () => route.query.botId,
    (newBotId) => {
      botId = newBotId;
      queryBot();
    },
    {immediate: true}
);
const data = reactive({
  modelDetail: {},
  form: {},
});

const {modelDetail, rules} = toRefs(data);

/**
 * 查询 bot 信息
 */
function queryBot() {
  if (!botId) {
    return;
  }
  getBot(botId).then((response) => {
    bot.value = response.data;
  });
}
const handleReturn = () => {
  let path = "";
  let botType = 0;
  if (route.path.includes("workflow")){
    path = '/kb/bot/workflow';
    botType = 0
  }else if (route.path.includes("chatflow")){
    path = '/kb/bot/chatflow';
    botType = 1
  }else if (route.path.includes("agent")){
    path = '/kb/bot/agent'
    botType = 2
  }
  router.push({
    path: path,
    query: {
      botType: botType
    },
  });
};

</script>

<style scoped lang="scss">
.id-tag {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  padding: 2px 6px;
  background-color: var(--glass-text-primary, #18181b);
  color: #fafafa;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  min-width: 22px;
  height: 20px;
}
.fhbtn {
  .svg-icon {
    font-size: 12px;
    margin-right: 3px;
    vertical-align: middle;
    margin-top: -3px;
  }
}

.infotop {
  background: var(--glass-card-bg, rgba(255, 255, 255, 0.72));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--glass-card-border, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  padding: 20px;
}

.infotop-row {
  padding: 8px 12px;
  display: flex;
  align-items: center;

  .infotop-row-lable {
    color: var(--glass-text-secondary, #71717a);
    font-size: 13px;
    width: 80px;
    flex-shrink: 0;
  }

  .infotop-row-value {
    color: var(--glass-text-primary, #18181b);
    font-size: 13px;
    font-weight: 500;
  }
}
</style>