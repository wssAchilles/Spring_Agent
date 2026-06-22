<template>
  <div class="login-page">
    <section class="brand-panel" aria-label="平台能力概览">
      <div class="brand-panel__content">
        <div class="brand-lockup">
          <span class="brand-mark">K</span>
          <span class="brand-name">Knowledge Hub</span>
        </div>
        <div class="hero-copy">
          <p class="eyebrow">智能知识资产管理</p>
          <h1>让知识接入、治理、检索和应用形成闭环</h1>
          <p class="subtitle">
            面向知识库、知识图谱、Bot 与应用运营场景，统一管理数据接入、解析评测和资产健康状态。
          </p>
        </div>
        <div class="flow-grid">
          <div v-for="item in capabilityList" :key="item.title" class="flow-card">
            <div class="flow-card__index">{{ item.index }}</div>
            <img :src="item.icon" :alt="item.title" />
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
        <div class="metric-strip">
          <div>
            <strong>统一入口</strong>
            <span>知识资产、应用、运营看板集中管理</span>
          </div>
          <div>
            <strong>可追溯</strong>
            <span>接入、解析、评测状态全链路留痕</span>
          </div>
          <div>
            <strong>可扩展</strong>
            <span>面向模型、插件和业务应用持续演进</span>
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel-wrap" aria-label="账号登录">
      <div class="top-brand">
        <span class="brand-mark brand-mark--small">K</span>
        <span>知识资产平台</span>
      </div>
      <div class="greeting">
        <span>{{ greetingsTitle }}</span>
        <p>请登录后继续管理知识资产与智能应用。</p>
      </div>

      <div class="login-panel">
        <div class="login-panel__header">
          <p>账号登录</p>
          <span>使用管理员账号进入工作台</span>
        </div>
        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              auto-complete="off"
              placeholder="请输入账号"
              size="large"
            >
              <template #prefix>
                <i class="iconfont">&#xebc0;</i>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              auto-complete="off"
              placeholder="请输入密码"
              show-password
              size="large"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <i class="iconfont">&#xeb8d;</i>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="captchaEnabled" prop="code" class="captcha-row">
            <el-input
              v-model="loginForm.code"
              auto-complete="off"
              placeholder="验证码"
              size="large"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <i class="iconfont">&#xeb9e;</i>
              </template>
            </el-input>
            <button class="login-code" type="button" @click="getCode">
              <img :src="codeUrl" class="login-code-img" alt="验证码" />
            </button>
          </el-form-item>

          <div class="form-actions">
            <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            <el-button link type="primary" @click="dialogVisible = true">忘记密码</el-button>
          </div>

          <el-button
            :loading="loading"
            type="primary"
            class="login-submit"
            size="large"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登录工作台</span>
            <span v-else>正在登录...</span>
          </el-button>
        </el-form>
      </div>

    </section>
  </div>

  <el-dialog
    v-model="dialogVisible"
    title="重置密码"
    class="fp-form-dialog"
    width="650px"
  >
    <el-form :model="fpForm" label-width="90px" class="fp-form">
      <el-form-item label="用户名">
        <el-input v-model="fpForm.username" placeholder="请输入手机号或用户名" />
      </el-form-item>

      <el-form-item label="验证码">
        <div class="wrapper">
          <el-input v-model="fpForm.code" placeholder="请输入验证码" />
          <el-button type="primary" :disabled="codeFlag" @click="handleFPCodeClick">
            {{ codeFlag ? `${codeTime}s` : '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item label="新密码">
        <el-input v-model="fpForm.password" type="password" show-password placeholder="请输入新密码" />
      </el-form-item>

      <el-form-item label="确认密码">
        <el-input v-model="fpForm.password2" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="dialogVisible = false">重置密码</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from "vue";
import { getCodeImg, ssoLoginPage } from "@/api/system/sso-login-page.js";
import Cookies from "js-cookie";
import { encrypt, decrypt } from "@/utils/jsencrypt";
import kbIcon from "@/assets/system/images/entrance/kb.png";
import graphIcon from "@/assets/system/images/entrance/graph.png";
import botIcon from "@/assets/system/images/entrance/bot.png";
import appIcon from "@/assets/system/images/entrance/app.png";

const dialogVisible = ref(false);
const { proxy } = getCurrentInstance();
const loading = ref(false);
const codeUrl = ref("");
const greetingsTitle = ref("");
const captchaEnabled = ref(false);
const codeFlag = ref(false);
const codeTimer = ref(null);
const loginForm = ref({
  username: "",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
});
const fpForm = ref({
  username: "",
  password: "",
  password2: "",
  code: ""
});

const capabilityList = [
  { index: "01", title: "知识库治理", desc: "沉淀文档、切片与质量规则", icon: kbIcon },
  { index: "02", title: "图谱构建", desc: "组织实体、关系与领域资产", icon: graphIcon },
  { index: "03", title: "Bot 运营", desc: "追踪问答、召回与转化表现", icon: botIcon },
  { index: "04", title: "应用集成", desc: "连接插件、模型与业务场景", icon: appIcon }
];

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
};

function judgeDate() {
  const currentHour = new Date().getHours();
  if (currentHour < 12) {
    greetingsTitle.value = "上午好";
  } else if (currentHour < 18) {
    greetingsTitle.value = "下午好";
  } else {
    greetingsTitle.value = "晚上好";
  }
}
judgeDate();

function getCookie() {
  const username = Cookies.get("username");
  const password = Cookies.get("password");
  const rememberMe = Cookies.get("rememberMe");
  loginForm.value = {
    ...loginForm.value,
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  };
}
getCookie();

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled;
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img;
      loginForm.value.uuid = res.uuid;
    }
  });
}
getCode();

function generateOAuth2Link() {
  const currentUrl = new URL(window.location.href);
  const { protocol, hostname, port } = window.location;
  const baseUrl = `${protocol}//${hostname}${port ? `:${port}` : ""}/oauth2/authorize`;
  return `${baseUrl}?${currentUrl.searchParams.toString()}`;
}

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true;
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 });
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 });
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 });
      } else {
        Cookies.remove("username");
        Cookies.remove("password");
        Cookies.remove("rememberMe");
      }

      const username = loginForm.value.username.trim();
      const password = loginForm.value.password;

      ssoLoginPage(username, password, loginForm.value.rememberMe)
        .then(res => {
          if (res.code === 200) {
            window.location.href = generateOAuth2Link();
          }
        })
        .finally(() => {
          loading.value = false;
        });
    }
  });
}

const timeValue = 60;
const codeTime = ref(timeValue);
function handleFPCodeClick() {
  if (codeFlag.value) return;
  codeFlag.value = true;
  window.clearInterval(codeTimer.value);
  codeTimer.value = window.setInterval(() => {
    if (codeTime.value > 1) {
      codeTime.value--;
    } else {
      window.clearInterval(codeTimer.value);
      codeTime.value = timeValue;
      codeFlag.value = false;
    }
  }, 1000);
}
</script>

<style lang="scss">
.fp-form-dialog {
  .fp-form {
    padding: 10px 48px 0;
  }

  .wrapper {
    width: 100%;
    display: flex;
    gap: 10px;
    align-items: center;

    .el-button {
      width: 112px;
      min-width: 112px;
    }
  }

  .el-dialog__body {
    height: auto !important;
    max-height: 80vh;
  }
}
</style>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(620px, 1.05fr) minmax(480px, 0.95fr);
  background: #f7faff;
  color: #111827;
  overflow: auto;
}

.brand-panel {
  position: relative;
  min-height: 760px;
  padding: 72px 64px;
  overflow: hidden;
  background:
    linear-gradient(145deg, rgba(236, 244, 255, 0.92), rgba(213, 229, 255, 0.78)),
    url("@/assets/system/images/layout/navbar_bg.jpg") center / cover no-repeat;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background:
      linear-gradient(90deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.18)),
      radial-gradient(circle at 76% 28%, rgba(38, 102, 251, 0.22), transparent 34%);
  }
}

.brand-panel__content {
  position: relative;
  z-index: 1;
  max-width: 820px;
}

.brand-lockup,
.top-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-weight: 700;
  color: #13233f;
}

.brand-mark {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #fff;
  font-size: 25px;
  font-weight: 800;
  background: linear-gradient(135deg, #135afb 0%, #42c7ff 100%);
  box-shadow: 0 14px 28px rgba(38, 102, 251, 0.26);
}

.brand-mark--small {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 19px;
}

.brand-name {
  font-size: 24px;
}

.hero-copy {
  margin-top: 86px;

  .eyebrow {
    margin: 0 0 16px;
    font-size: 15px;
    font-weight: 700;
    color: #2666fb;
  }

  h1 {
    margin: 0;
    max-width: 720px;
    font-size: 48px;
    line-height: 1.16;
    font-weight: 800;
    letter-spacing: 0;
    color: #101828;
  }

  .subtitle {
    max-width: 650px;
    margin: 24px 0 0;
    color: #52627a;
    font-size: 17px;
    line-height: 1.8;
  }
}

.flow-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 56px;
}

.flow-card {
  min-height: 132px;
  display: grid;
  grid-template-columns: 48px 54px 1fr;
  gap: 14px;
  align-items: center;
  padding: 20px;
  border: 1px solid rgba(92, 135, 204, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 18px 48px rgba(70, 112, 180, 0.12);
  backdrop-filter: blur(12px);

  img {
    width: 48px;
    height: 48px;
    object-fit: contain;
  }

  h3 {
    margin: 0 0 8px;
    color: #1d2a44;
    font-size: 17px;
  }

  p {
    margin: 0;
    color: #66758c;
    font-size: 13px;
    line-height: 1.5;
  }
}

.flow-card__index {
  color: #2666fb;
  font-size: 18px;
  font-weight: 800;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: 48px;
  padding: 22px 10px;
  border: 1px solid rgba(114, 148, 196, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.48);

  div {
    padding: 0 24px;
    border-right: 1px solid rgba(114, 148, 196, 0.24);

    &:last-child {
      border-right: none;
    }
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: #1b2a45;
    font-size: 19px;
    margin-bottom: 8px;
  }

  span {
    color: #60718a;
    font-size: 13px;
    line-height: 1.5;
  }
}

.login-panel-wrap {
  min-height: 760px;
  padding: 72px 72px 36px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 251, 255, 0.98)),
    #fff;
}

.top-brand {
  align-self: flex-start;
  font-size: 19px;
}

.greeting {
  margin: 42px 0 28px;

  span {
    display: block;
    color: #1f2937;
    font-size: 30px;
    font-weight: 700;
  }

  p {
    margin: 10px 0 0;
    color: #7a8798;
    font-size: 14px;
  }
}

.login-panel {
  width: min(100%, 460px);
  padding: 34px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 70px rgba(15, 45, 93, 0.12);
}

.login-panel__header {
  margin-bottom: 28px;

  p {
    margin: 0;
    color: #1f2937;
    font-size: 24px;
    font-weight: 800;
  }

  span {
    display: block;
    margin-top: 8px;
    color: #8a96a8;
    font-size: 13px;
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-input__wrapper) {
    border-radius: 6px;
    box-shadow: 0 0 0 1px #d9e2f2 inset;
    transition: box-shadow 0.2s ease, background 0.2s ease;

    &:hover,
    &.is-focus {
      box-shadow: 0 0 0 1px #2666fb inset, 0 8px 22px rgba(38, 102, 251, 0.1);
    }
  }

  .iconfont {
    color: #8fa2bf;
    font-size: 17px;
  }
}

.captcha-row {
  :deep(.el-form-item__content) {
    display: grid;
    grid-template-columns: 1fr 118px;
    gap: 10px;
  }
}

.login-code {
  height: 40px;
  padding: 0;
  border: 1px solid #d9e2f2;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
}

.login-code-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 22px;
}

.login-submit {
  width: 100%;
  border-radius: 6px;
  font-weight: 700;
  background: linear-gradient(90deg, #2c6fff 0%, #135afb 100%);
  box-shadow: 0 14px 28px rgba(38, 102, 251, 0.22);
}

@media screen and (max-width: 1180px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
    padding: 48px 36px;
  }

  .hero-copy {
    margin-top: 48px;

    h1 {
      font-size: 38px;
    }
  }

  .login-panel-wrap {
    min-height: auto;
    padding: 42px 36px;
  }
}

@media screen and (max-width: 720px) {
  .brand-panel {
    display: none;
  }

  .login-panel-wrap {
    min-height: 100vh;
    padding: 28px 18px;
  }

  .login-panel {
    width: 100%;
    padding: 24px;
  }

  .greeting span {
    font-size: 24px;
  }

  .captcha-row :deep(.el-form-item__content) {
    grid-template-columns: 1fr;
  }
}
</style>
