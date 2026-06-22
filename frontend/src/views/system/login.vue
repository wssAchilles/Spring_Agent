<template>
    <div class="login-redesign" ref="app-container">
        <section class="login-showcase" aria-label="平台介绍">
            <div class="showcase-inner">
                <div class="product-chip">知识资产平台</div>
                <h1>把分散知识整理成可检索、可评测、可运营的资产</h1>
                <p class="showcase-summary">
                    统一处理知识接入、结构治理、智能检索与应用运营，帮助团队持续沉淀高质量知识能力。
                </p>
                <div class="showcase-actions" aria-hidden="true">
                    <span>知识库</span>
                    <span>知识图谱</span>
                    <span>Bot 运营</span>
                </div>
                <div class="pipeline" aria-hidden="true">
                    <div class="pipeline-card">
                        <span>01</span>
                        <strong>接入</strong>
                        <p>文档、数据源、业务内容</p>
                    </div>
                    <div class="pipeline-line"></div>
                    <div class="pipeline-card is-active">
                        <span>02</span>
                        <strong>治理</strong>
                        <p>切片、结构化、质量规则</p>
                    </div>
                    <div class="pipeline-line"></div>
                    <div class="pipeline-card">
                        <span>03</span>
                        <strong>应用</strong>
                        <p>检索、问答、运营分析</p>
                    </div>
                </div>
            </div>
        </section>
        <section class="login-auth" aria-label="账号登录">
            <div class="auth-card">
                <div class="auth-brand">
                    <span>K</span>
                    <strong>Knowledge Hub</strong>
                </div>
                <div class="auth-heading">
                    <p>{{ greetingsTitle }}</p>
                    <h2>登录工作台</h2>
                    <span>继续管理知识资产与智能应用。</span>
                </div>
                <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="auth-form">
                    <el-form-item prop="username">
                        <el-input
                            v-model="loginForm.username"
                            type="text"
                            auto-complete="off"
                            placeholder="请输入账号"
                            size="large"
                        >
                            <template #prefix>
                                <i class="iconfont">&#xeb44;</i>
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
                                <i class="iconfont">&#xeb6f;</i>
                            </template>
                        </el-input>
                    </el-form-item>
                    <div class="auth-options">
                        <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
                        <el-button link type="primary" @click="dialogVisible = true">忘记密码</el-button>
                    </div>
                    <el-button
                        :loading="loading"
                        type="primary"
                        class="auth-submit"
                        size="large"
                        @click.prevent="handleLogin"
                    >
                        <span v-if="!loading">登录</span>
                        <span v-else>登录中...</span>
                    </el-button>
                </el-form>
            </div>
        </section>
    </div>

    <el-dialog
        v-model="dialogVisible"
        title="忘记密码"
        class="fp-form-dialog"
        width="650px"
        :append-to="$refs['app-container']"
        draggable
        destroy-on-close
    >
        <el-form :model="fpForm" label-width="auto" style="padding: 10px 60px 0">
            <el-row :gutter="20">
                <el-col :span="24">
                    <el-form-item label="用户名">
                        <el-input v-model="fpForm.name" placeholder="请输入手机号或用户名" />
                    </el-form-item>
                </el-col>
                <el-col :span="24">
                    <el-form-item label="验证码">
                        <div class="wrapper">
                            <el-input v-model="fpForm.code" placeholder="请输入验证码" />
                            <el-button
                                type="primary"
                                :disabled="codeFlag"
                                style="margin-left: 10px"
                                @click="handleFPCodeClick"
                            >
                                {{ codeFlag ? `${codeTime}s` : '获取验证码' }}
                            </el-button>
                        </div>
                    </el-form-item>
                </el-col>
                <el-col :span="24">
                    <el-form-item label="新密码">
                        <el-input v-model="fpForm.password" placeholder="请输入新密码" />
                    </el-form-item>
                </el-col>
                <el-col :span="24">
                    <el-form-item label="确认密码">
                        <el-input v-model="fpForm.password2" placeholder="请输入确认密码" />
                    </el-form-item>
                </el-col>
            </el-row>
        </el-form>
        <template #footer>
            <div class="dialog-footer">
                <el-button type="primary" @click="dialogVisible = false"> 重置密码 </el-button>
            </div>
        </template>
    </el-dialog>
    <!-- </div> -->
</template>

<script setup>
    import { ref } from 'vue';
    import Cookies from 'js-cookie';
    import { encrypt, decrypt } from '@/utils/jsencrypt';
    import Swiper from 'swiper';
    import 'swiper/swiper-bundle.min.css';
    import useUserStore from '@/store/system/user.js';
    import { getContent } from '@/api/system/system/content';
    import defaultLogo from '@/assets/system/images/entrance/kb.png';
    // 使用 import 直接引入图片路径
    import banner1 from '@/assets/system/images/login/banner.png';
    import banner2 from '@/assets/system/images/login/banner2.png';
    import banner3 from '@/assets/system/images/login/banner-gy.jpg';
    import banner4 from '@/assets/system/images/login/banner-sl.png';
    import benefitEfficiencyIcon from '@/assets/system/images/login/hj.gif';
    import benefitReliableIcon from '@/assets/system/images/login/kf.png';
    import benefitOpenIcon from '@/assets/system/images/login/kk.png';

    const userStore = useUserStore();
    const dialogVisible = ref(false);
    const { proxy } = getCurrentInstance();
    const loading = ref(false);
    const greetingsTitle = ref('');
    const codeFlag = ref(false);
    const loginForm = ref({
        username: '',
        password: '',
        rememberMe: false
    });
    const fpForm = ref({
        username: '',
        password: '',
        password2: '',
        code: ''
    });
    // const loginimglist=ref([
    //   {
    //     id:1,
    //     imgurl:'banner.png',
    //   },
    //   // {
    //   //   id:2,
    //   //   imgurl:'banner2.png',
    //   // },
    //   // {
    //   //   id:3,
    //   //   imgurl:'banner-gy.png',
    //   // },
    //   {
    //     id:4,
    //     imgurl:'banner-sl.png',
    //   }
    // ])
    const defaltImglist = ref([
        {
            id: 1,
            image: new URL('@/assets/system/images/login/banner.png', import.meta.url).href
        }
        // {id: 4, image: new URL('@/assets/system/images/login/banner-sl.png', import.meta.url).href}
    ]);
    const loginimglist = ref([]);

    const getBackgroundStyle = (item) => {
        return {
            background: `url(${item.image}) no-repeat`,
            backgroundSize: `100% 100%`
        };
    };

    const getAssetsFile = (url) => {
        return new URL(`@/assets/system/images/login/${url}`, import.meta.url).href;
    };

    const loginRules = {
        username: [{ required: true, trigger: 'blur', message: '请输入您的账号' }],
        password: [{ required: true, trigger: 'blur', message: '请输入您的密码' }]
    };

    const logo = ref(null);
    const contentDetail = ref(null);

    onMounted(() => {
        fetchContent();
    });
    // 使用 getContent 来获取数据，而不是重新定义一个 getContent 函数
    const fetchContent = async () => {
        // logo.value = defaultLogo;
        // loginimglist.value = defaltImglist.value
        try {
            // 调用你从 API 导入的 getContent 方法
            const res = await getContent(1); // 假设请求的是 id 为 1 的数据
            if (res.code == 200) {
                const data = res.data;
                contentDetail.value = data;
                const sysLogo = data.loginLogo;
                logo.value = defaultLogo;
                const carouselImageList = data.carouselImage.split(',');
                // console.log('-----contentDetail',contentDetail.value)
                // console.log('-----login-----0----0--0--------0-',carouselImageList)
                const carouselImgList = [];
                for (let i = 0; i <= carouselImageList.length; i++) {
                    let item = carouselImageList[i];
                    if (item) {
                        carouselImgList.push({
                            id: i + 1,
                            image: item
                        });
                    }
                }
                // console.log('-----login-----1----1--1--------1-',carouselImgList)
                if (carouselImgList.length > 0) {
                    loginimglist.value = carouselImgList;
                } else {
                    loginimglist.value = defaltImglist.value;
                }
            } else {
                loginimglist.value = defaltImglist.value;
            }

            // this.$message.success('内容加载成功');
        } catch (error) {
            logo.value = defaultLogo;
            loginimglist.value = defaltImglist.value;
        }
    };

    function judgeDate() {
        var currentTime = new Date();
        var currentHour = currentTime.getHours();
        if (currentHour < 12) {
            greetingsTitle.value = '上午好';
        } else if (currentHour < 18) {
            greetingsTitle.value = '下午好';
        } else {
            greetingsTitle.value = '晚上好';
        }
    }

    judgeDate();

    function getCookie() {
        const username = Cookies.get('username');
        const password = Cookies.get('password');
        const rememberMe = Cookies.get('rememberMe');
        loginForm.value = {
            username: username === undefined ? loginForm.value.username : username,
            password: password === undefined ? loginForm.value.password : decrypt(password),
            rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
        };
    }

    getCookie();

    function handleLogin() {
        localStorage.setItem('username', loginForm.value.username);
        proxy.$refs.loginRef.validate((valid) => {
            if (valid) {
                loading.value = true;
                // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
                if (loginForm.value.rememberMe) {
                    Cookies.set('username', loginForm.value.username, { expires: 30 });
                    Cookies.set('password', encrypt(loginForm.value.password), {
                        expires: 30
                    });
                    Cookies.set('rememberMe', loginForm.value.rememberMe, { expires: 30 });
                } else {
                    // 否则移除
                    Cookies.remove('username');
                    Cookies.remove('password');
                    Cookies.remove('rememberMe');
                }
                // 调用action的登录方法
                userStore
                    .login(loginForm.value)
                    .then(() => {
                        window.location.href = '/index';
                    })
                    .catch(() => {
                        loading.value = false;
                    });
            }
        });
    }

    const timeValue = 3;
    const codeTime = ref(timeValue);

    function handleFPCodeClick() {
        if (codeFlag.value) return;
        codeFlag.value = !codeFlag.value;
        setInterval(() => {
            if (codeTime.value > 1) {
                codeTime.value--;
            } else {
                clearInterval();
                codeTime.value = timeValue;
                codeFlag.value = false;
            }
        }, 1000);
    }

</script>

<style lang="scss">
    .el-carousel__button {
        background-color: #2666fb;
    }

    .fp-form-dialog {
        .wrapper {
            width: 100%;
            display: flex;
            justify-content: center;
            align-items: center;

            .el-button {
                width: 102px;
                min-width: 102px;
            }
        }

        .el-dialog__body {
            height: 316px !important;
        }
    }
</style>

<style scoped lang="scss">
    .login-two {
        height: 100%;
        width: 100%;
        min-width: 1300px;
        overflow: auto;
        display: flex;

        ::v-deep {
            .el-carousel__container {
                height: 100%;
            }
        }

        .swiper-slide {
        }

        /*  .swiper-slide-1{
      background: url(@/assets/system/images/login/banner.png) center center / cover no-repeat;
    }
    .swiper-slide-2{
      background: url(@/assets/system/images/login/banner2.png) center center / cover no-repeat;
    }
    .swiper-slide-3{
      background: url(@/assets/system/images/login/banner-gy.jpg) center center / cover no-repeat;
    }
    .swiper-slide-4{
      background: url(@/assets/system/images/login/banner-sl.png) center center / cover no-repeat;
    }*/

        .form-actions {
            display: flex;
            align-items: center;
            justify-content: space-between;
            cursor: pointer;
        }

        ::v-deep .el-form-item__content {
            justify-content: space-between;
        }

        ::v-deep .el-input--medium .el-input__inner {
            height: 40px;
            line-height: 40px;
            padding-left: 35px;
            border-radius: 4px;
            border: 1px solid #e6e6e6;
        }

        ::v-deep .el-input__icon.input-icon.svg-icon {
            height: 45px;
            width: 14px;
            margin-left: 5px;
        }

        ::v-deep .el-form-item__error {
            color: #ff4949;
            font-size: 12px;
            line-height: 1;
            // padding-top: 9px;
            position: absolute;
            top: 100%;
            left: 0;
        }

        ::v-deep .el-checkbox {
            padding: 0 !important;
        }

        ::v-deep .el-form-item {
            margin-bottom: 24px;
        }

        ::v-deep .el-input__prefix {
            top: 1px !important;
            left: 15px !important;
        }

        .left-content {
            width: 55%;
            min-height: 750px;

            .contents {
                position: absolute;
                margin: 6% 0px 0px 13%;
                letter-spacing: 0em;
                font-family: 'sharp';

                .title {
                    font-size: 53px;
                    margin-bottom: -5px;
                    color: #000000;
                }

                .enTitle {
                    font-size: 19px;
                    margin-bottom: 70px;
                    color: #000000;
                }

                .digest {
                    font-size: 28px;
                    color: #000000;
                }

                .content {
                    width: 78%;
                    font-size: 18px;
                    line-height: 40px;
                    color: #000000;
                }
            }

            .leftSwiper {
                width: 100%;
                height: 100%;
                position: relative;
                overflow: hidden;
                background:
                    linear-gradient(135deg, rgba(236, 244, 255, 0.96), rgba(213, 229, 255, 0.88)),
                    radial-gradient(circle at 78% 26%, rgba(38, 102, 251, 0.22), transparent 32%);

                &::before {
                    content: '';
                    position: absolute;
                    inset: 0;
                    z-index: 1;
                    background:
                        linear-gradient(90deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.12)),
                        repeating-linear-gradient(
                            115deg,
                            rgba(38, 102, 251, 0.05) 0,
                            rgba(38, 102, 251, 0.05) 1px,
                            transparent 1px,
                            transparent 46px
                        );
                }

                .swiper-wrapper {
                    position: relative;
                    height: 100%;
                }

                :deep(.el-carousel) {
                    display: none;
                }

                .left-hero-copy {
                    position: absolute;
                    left: 9%;
                    top: 12%;
                    z-index: 5;
                    width: 72%;
                    color: #111827;
                }

                .left-hero-kicker {
                    color: #2666fb;
                    font-size: 16px;
                    font-weight: 700;
                    margin-bottom: 18px;
                }

                .left-hero-copy h1 {
                    margin: 0;
                    max-width: 720px;
                    font-size: 46px;
                    line-height: 1.22;
                    font-weight: 800;
                    letter-spacing: 0;
                }

                .left-hero-copy p {
                    margin: 22px 0 0;
                    max-width: 560px;
                    color: #52627a;
                    font-size: 18px;
                    line-height: 1.75;
                }

                .left-hero-tags {
                    display: flex;
                    gap: 14px;
                    margin-top: 34px;

                    span {
                        padding: 10px 18px;
                        border: 1px solid rgba(38, 102, 251, 0.22);
                        border-radius: 999px;
                        background: rgba(255, 255, 255, 0.58);
                        color: #28456f;
                        font-size: 14px;
                    }
                }

                .left-hero-visual {
                    display: grid;
                    grid-template-columns: 1fr 72px 1fr 72px 1fr;
                    align-items: center;
                    gap: 0;
                    width: min(920px, 92%);
                    margin-top: 64px;
                }

                .visual-card {
                    min-height: 148px;
                    position: relative;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    padding: 22px;
                    border: 1px solid rgba(89, 135, 205, 0.2);
                    border-radius: 12px;
                    background: rgba(255, 255, 255, 0.62);
                    box-shadow: 0 22px 54px rgba(38, 102, 251, 0.1);
                    overflow: hidden;

                    &::before {
                        content: '';
                        position: absolute;
                        right: -26px;
                        top: -24px;
                        width: 96px;
                        height: 96px;
                        border-radius: 50%;
                        background: radial-gradient(circle, rgba(38, 102, 251, 0.2), transparent 66%);
                    }

                    strong {
                        position: relative;
                        z-index: 1;
                        color: #1e3558;
                        font-size: 20px;
                        line-height: 1.2;
                    }

                    i {
                        position: relative;
                        z-index: 1;
                        width: 44px;
                        height: 44px;
                        border-radius: 12px;
                        background: linear-gradient(135deg, #2c6fff, #5fc9ff);
                        box-shadow: 0 12px 24px rgba(38, 102, 251, 0.24);
                    }
                }

                .visual-index {
                    position: relative;
                    z-index: 1;
                    width: max-content;
                    padding: 5px 10px;
                    border-radius: 999px;
                    color: #2666fb;
                    background: rgba(38, 102, 251, 0.1);
                    font-size: 13px;
                    font-weight: 800;
                }

                .visual-card-source i {
                    border-radius: 10px;
                }

                .visual-card-graph i {
                    border-radius: 50%;
                    background: radial-gradient(circle at 50% 50%, #fff 0 18%, #4aa5ff 20% 44%, #2c6fff 46% 100%);
                }

                .visual-card-app i {
                    background: linear-gradient(135deg, #27c8b9, #2c6fff);
                }

                .visual-connector {
                    height: 2px;
                    background: linear-gradient(90deg, rgba(38, 102, 251, 0.12), rgba(38, 102, 251, 0.48), rgba(38, 102, 251, 0.12));
                }

                .benefit-icons-layer,
                .login-effects-layer {
                    display: none;
                }

                .swiper-imagesize {
                    width: 100%;
                    height: 100%;
                    // object-fit: cover;
                }

                ::v-deep .swiper-pagination-bullets .swiper-pagination-bullet {
                    width: 36px;
                    height: 4px;
                    background: rgba(0, 0, 0, 0.5);
                    border-radius: 3px;
                    margin-bottom: 30px;
                    //opacity: 0.5;
                }
            }
        }

        .iconfont {
            font-size: 17px !important;
        }

        .right-content {
            width: 45%;
            min-height: 750px;
            background: #fff;
            position: relative;
            padding: 30px 0 20px 0;
            display: flex;
            flex-direction: column;
            justify-content: space-between;

            .logo {
                width: 260px;
                height: 45px;
                margin-left: 20%;

                img {
                    // width: 100%;
                    height: 100%;
                    margin-top: 25px;
                }
            }

            .greeting {
                margin-left: 20%;

                .entry_period {
                    font-size: 25px;
                    font-weight: 400;
                    margin-bottom: 6px;
                    color: rgba(57, 63, 79, 1);
                }

                .entry_greeting {
                    font-size: 12px;
                    font-weight: 400;
                    color: #888;
                }
            }

            .login-panel {
                margin-left: 20%;
                box-sizing: border-box;
                margin-top: 30px;
                width: 400px;
                height: 400px;
                box-shadow:
                    -22px -22px 44px 0px rgba(255, 255, 255, 1),
                    22px 22px 44px 0px rgba(217, 217, 217, 1);
                border-radius: 8px;
                padding: 0 38px;
                position: relative;
                overflow: hidden;

                ::v-deep .el-form-item {
                    margin-bottom: 20px;
                }

                .titles {
                    text-align: center;
                    line-height: 65px;
                    height: 65px;
                    font-size: 18px;
                    font-weight: bold;
                    margin-bottom: 30px;
                }

                .titles-bar {
                    width: 65px;
                    height: 2px;
                    background: var(--el-color-primary);
                    top: 66px;
                    position: absolute;
                    left: 50%;
                    transform: translateX(-50%);
                }

            }

            ::v-deep .el-button--medium {
                padding: 12px 20px;
                // margin-top: 10px;
            }

        }
    }
</style>
<style lang="scss" scoped>
    @media screen and (max-width: 1280px) {
    }

    @media screen and (max-width: 992px) {
    }

    @media screen and (max-width: 768px) {
    }

    @media screen and (max-width: 576px) {
        .login-two {
            min-width: 300px;

            .left-content {
                display: none;
            }

            .right-content {
                width: 100%;
                min-height: 300px;
                justify-content: flex-start;
                align-items: center;
                padding: 20px 14px 20px 14px;

                .logo {
                    margin-left: 0;
                    text-align: center;
                }

                .greeting {
                    margin-left: 0;
                    margin-top: 20px;
                    text-align: center;

                    .entry_period {
                        font-size: 16px;
                    }
                }

                .login-panel {
                    margin-left: 0;
                    margin-top: 20px;
                    width: auto;
                    height: 360px;
                    padding: 0 20px;
                    box-shadow: 5px 5px 10px 2px #eee;

                    .titles {
                        margin: 0 0 20px 0;
                    }

                    .titles-bar {
                        top: 48px;
                    }

                }

            }
        }
    }

    .app-container {
        padding: 0px;
        margin: 0px;
        background-color: #ffffff;
        min-height: 100%;
    }

    .benefit-icons-layer {
        position: absolute;
        inset: 0;
        z-index: 3;
        pointer-events: none;
        overflow: hidden;
    }

    .benefit-icon {
        position: absolute;
        width: 6.7%;
        aspect-ratio: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        transform: translate(-50%, -50%);

        img {
            display: block;
            width: 62%;
            height: 62%;
            object-fit: contain;
        }
    }

    .benefit-icon-efficiency {
        left: 16.5%;
        top: 84.6%;

        img {
            transform: translate(-4%, -2%);
        }
    }

    .benefit-icon-reliable {
        left: 44.6%;
        top: 85%;

        img {
            transform: translate(0, 3%);
        }
    }

    .benefit-icon-open {
        left: 72.2%;
        top: 84.7%;

        img {
            transform: translate(0, 6%);
        }
    }

    .benefit-icon-animated {
        animation: benefitIconFloat 3s ease-in-out infinite;
    }

    .benefit-icon-open {
        animation-delay: -1.2s;
    }

    .login-effects-layer {
        position: absolute;
        inset: 0;
        z-index: 2;
        pointer-events: none;
        overflow: hidden;
    }

    .effect-stage {
        position: absolute;
        transform: translate(-50%, -50%) translateZ(0);
        transform-origin: center;
        animation: effectBreath 4s ease-in-out infinite;
        will-change: transform, opacity;

        &::before {
            content: '';
            position: absolute;
            inset: 10% 4%;
            border-radius: 50%;
            background: radial-gradient(
                circle,
                rgba(39, 133, 255, 0.34),
                rgba(39, 133, 255, 0.08) 42%,
                transparent 70%
            );
            filter: blur(8px);
            opacity: 0.82;
            animation: effectGlow 3.2s ease-in-out infinite;
        }

        &::after {
            content: '';
            position: absolute;
            left: 6%;
            right: 6%;
            top: 0;
            height: 34%;
            border-radius: 50%;
            background: linear-gradient(
                180deg,
                rgba(255, 255, 255, 0.7),
                rgba(46, 134, 255, 0.16),
                transparent
            );
            opacity: 0.65;
            transform: translateY(-45%);
            animation: scanDown 3.8s ease-in-out infinite;
        }
    }

    .effect-dev {
        left: 12.8%;
        top: 48.8%;
        width: 12.4%;
        height: 18.8%;
    }

    .effect-workflow {
        left: 32.5%;
        top: 47.4%;
        width: 12.8%;
        height: 19%;
        animation-delay: -0.6s;
    }

    .effect-rag {
        left: 51.6%;
        top: 46.2%;
        width: 12%;
        height: 18.4%;
        animation-delay: -1.2s;
    }

    .effect-graph {
        left: 69.5%;
        top: 44.8%;
        width: 12.8%;
        height: 19.4%;
        animation-delay: -1.8s;
    }

    .effect-agent {
        left: 87.9%;
        top: 43.2%;
        width: 12.2%;
        height: 18.2%;
        animation-delay: -2.4s;
    }

    .dev-code {
        position: absolute;
        z-index: 1;
        width: 54%;
        padding: 7% 8%;
        border: 1px solid rgba(84, 160, 255, 0.42);
        border-radius: 8px;
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(104, 171, 255, 0.18));
        box-shadow: 0 0 18px rgba(47, 127, 255, 0.16);
        animation: codeFloat 3.4s ease-in-out infinite;

        span {
            display: block;
            height: 3px;
            margin-bottom: 6px;
            border-radius: 999px;
            background: linear-gradient(90deg, #2d74ff, rgba(72, 170, 255, 0.18));
            animation: codeTyping 2.6s ease-in-out infinite;
        }

        span:nth-child(2) {
            width: 76%;
            animation-delay: -0.5s;
        }

        span:nth-child(3) {
            width: 58%;
            animation-delay: -1s;
        }
    }

    .dev-code-a {
        left: 14%;
        top: 18%;
    }

    .dev-code-b {
        right: 10%;
        bottom: 20%;
        width: 48%;
        animation-delay: -1.5s;
    }

    .dev-scan {
        position: absolute;
        z-index: 2;
        left: 18%;
        top: 16%;
        width: 64%;
        height: 68%;
        border: 1px solid rgba(42, 138, 255, 0.34);
        border-radius: 12px;
        box-shadow: inset 0 0 18px rgba(42, 138, 255, 0.16);
        animation: framePulse 2.4s ease-in-out infinite;
    }

    .effect-particle {
        position: absolute;
        z-index: 3;
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: #2c7cff;
        box-shadow: 0 0 12px rgba(44, 124, 255, 0.9);
    }

    .particle-a {
        left: 20%;
        top: 24%;
        animation: particleDriftA 3.6s ease-in-out infinite;
    }

    .particle-b {
        right: 18%;
        bottom: 26%;
        animation: particleDriftB 3.2s ease-in-out infinite;
    }

    .flow-node {
        position: absolute;
        z-index: 2;
        width: 20%;
        aspect-ratio: 1;
        border-radius: 50%;
        background: linear-gradient(145deg, #ffffff, #5fb4ff 48%, #1d65ff);
        box-shadow: 0 0 20px rgba(37, 112, 255, 0.42);
        animation: nodePulse 2.2s ease-in-out infinite;
    }

    .node-a {
        left: 12%;
        top: 20%;
    }

    .node-b {
        left: 42%;
        top: 41%;
        animation-delay: -0.7s;
    }

    .node-c {
        right: 12%;
        bottom: 18%;
        animation-delay: -1.4s;
    }

    .flow-line {
        position: absolute;
        z-index: 1;
        height: 3px;
        border-radius: 999px;
        background: linear-gradient(90deg, transparent, rgba(39, 127, 255, 0.8), transparent);
        transform-origin: left center;
    }

    .line-a {
        left: 26%;
        top: 36%;
        width: 38%;
        transform: rotate(24deg);
    }

    .line-b {
        left: 55%;
        top: 58%;
        width: 34%;
        transform: rotate(24deg);
    }

    .flow-pulse {
        position: absolute;
        z-index: 3;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #ffffff;
        box-shadow: 0 0 14px #1e7dff;
    }

    .pulse-a {
        animation: flowPulseA 2.4s ease-in-out infinite;
    }

    .pulse-b {
        animation: flowPulseB 2.4s ease-in-out infinite 1.2s;
    }

    .flow-base {
        position: absolute;
        z-index: 0;
        left: 6%;
        right: 6%;
        bottom: -24%;
        height: 22%;
        border-radius: 50%;
        background: radial-gradient(
            ellipse at center,
            rgba(42, 138, 255, 0.36),
            rgba(42, 138, 255, 0.08) 52%,
            transparent 72%
        );
        filter: blur(2px);
        animation: flowBaseBreath 2.8s ease-in-out infinite;
    }

    .flow-spark {
        position: absolute;
        z-index: 3;
        bottom: 5%;
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background: #ffffff;
        box-shadow: 0 0 10px rgba(36, 119, 255, 0.95);
        animation: flowSpark 2.6s ease-in-out infinite;
    }

    .spark-a {
        left: 24%;
    }

    .spark-b {
        left: 50%;
        animation-delay: -0.8s;
    }

    .spark-c {
        right: 23%;
        animation-delay: -1.6s;
    }

    .module-platform {
        position: absolute;
        z-index: 1;
        left: 12%;
        right: 12%;
        bottom: 1%;
        height: 24%;
        border-radius: 50%;
        background: radial-gradient(
            ellipse at center,
            rgba(255, 255, 255, 0.86) 0 18%,
            rgba(86, 169, 255, 0.42) 34%,
            rgba(34, 118, 255, 0.2) 52%,
            transparent 72%
        );
        box-shadow: 0 14px 24px rgba(36, 112, 255, 0.18);
        filter: drop-shadow(0 0 12px rgba(43, 128, 255, 0.28));
        animation: platformBreath 3s ease-in-out infinite;

        &::before,
        &::after {
            content: '';
            position: absolute;
            left: 10%;
            right: 10%;
            border-radius: 50%;
            border: 1px solid rgba(63, 143, 255, 0.32);
        }

        &::before {
            top: 15%;
            height: 38%;
        }

        &::after {
            top: 32%;
            height: 46%;
            opacity: 0.62;
        }

        span {
            position: absolute;
            left: 14%;
            right: 14%;
            height: 3px;
            border-radius: 999px;
            background: linear-gradient(90deg, transparent, rgba(44, 124, 255, 0.65), transparent);
            animation: platformLine 2.4s ease-in-out infinite;
        }

        span:first-child {
            top: 34%;
        }

        span:last-child {
            top: 53%;
            animation-delay: -1.2s;
        }
    }

    .workflow-platform {
        left: -2%;
        right: -2%;
        bottom: -34%;
        height: 31%;
    }

    .graph-platform {
        left: -1%;
        right: -1%;
        bottom: -27%;
        height: 31%;
    }

    .rag-doc {
        position: absolute;
        z-index: 1;
        width: 34%;
        height: 44%;
        border-radius: 8px;
        border: 1px solid rgba(77, 149, 255, 0.36);
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(99, 169, 255, 0.22));
        box-shadow: 0 10px 24px rgba(34, 108, 255, 0.16);
        animation: docFloat 3.6s ease-in-out infinite;

        span {
            display: block;
            height: 3px;
            margin: 15% 16% 0;
            border-radius: 999px;
            background: rgba(39, 111, 255, 0.62);
        }

        span:nth-child(2) {
            width: 56%;
        }

        span:nth-child(3) {
            width: 42%;
        }
    }

    .doc-a {
        left: 12%;
        top: 16%;
    }

    .doc-b {
        right: 13%;
        bottom: 18%;
        animation-delay: -1.4s;
    }

    .rag-search {
        position: absolute;
        z-index: 3;
        left: 35%;
        top: 34%;
        width: 28%;
        aspect-ratio: 1;
        border: 4px solid rgba(38, 102, 251, 0.82);
        border-radius: 50%;
        box-shadow: 0 0 22px rgba(38, 102, 251, 0.42);
        animation: searchBeat 2.8s ease-in-out infinite;

        &::after {
            content: '';
            position: absolute;
            right: -28%;
            bottom: -10%;
            width: 42%;
            height: 4px;
            border-radius: 999px;
            background: rgba(38, 102, 251, 0.82);
            transform: rotate(45deg);
            transform-origin: left center;
        }
    }

    .rag-ray {
        position: absolute;
        z-index: 2;
        height: 2px;
        border-radius: 999px;
        background: linear-gradient(90deg, transparent, rgba(32, 121, 255, 0.84), transparent);
        animation: rayFlash 2.4s ease-in-out infinite;
    }

    .ray-a {
        left: 20%;
        top: 42%;
        width: 60%;
        transform: rotate(18deg);
    }

    .ray-b {
        left: 18%;
        top: 58%;
        width: 62%;
        transform: rotate(-22deg);
        animation-delay: -1.2s;
    }

    .graph-link {
        position: absolute;
        z-index: 1;
        height: 2px;
        border-radius: 999px;
        background: linear-gradient(
            90deg,
            rgba(49, 128, 255, 0.1),
            rgba(49, 128, 255, 0.72),
            rgba(49, 128, 255, 0.1)
        );
        transform-origin: left center;
        animation: linkGlow 2.8s ease-in-out infinite;
    }

    .link-a {
        left: 25%;
        top: 34%;
        width: 48%;
        transform: rotate(26deg);
    }

    .link-b {
        left: 28%;
        top: 58%;
        width: 48%;
        transform: rotate(-28deg);
        animation-delay: -0.8s;
    }

    .link-c {
        left: 43%;
        top: 42%;
        width: 34%;
        transform: rotate(90deg);
        animation-delay: -1.6s;
    }

    .link-d {
        left: 21%;
        top: 44%;
        width: 38%;
        transform: rotate(8deg);
        animation-delay: -0.4s;
    }

    .link-e {
        left: 47%;
        top: 38%;
        width: 36%;
        transform: rotate(42deg);
        animation-delay: -1.2s;
    }

    .link-f {
        left: 45%;
        top: 66%;
        width: 31%;
        transform: rotate(-12deg);
        animation-delay: -2s;
    }

    .graph-node {
        position: absolute;
        z-index: 2;
        width: 18%;
        aspect-ratio: 1;
        border-radius: 50%;
        border: 2px solid rgba(255, 255, 255, 0.9);
        background: radial-gradient(circle at 32% 28%, #ffffff, #55bcff 36%, #286bff 72%);
        box-shadow: 0 0 18px rgba(43, 116, 255, 0.56);
        animation: graphNodePulse 2.6s ease-in-out infinite;
    }

    .graph-a {
        left: 16%;
        top: 24%;
    }

    .graph-b {
        right: 14%;
        top: 32%;
        animation-delay: -0.5s;
    }

    .graph-c {
        left: 28%;
        bottom: 18%;
        animation-delay: -1s;
    }

    .graph-d {
        right: 22%;
        bottom: 16%;
        animation-delay: -1.5s;
    }

    .graph-e,
    .graph-f,
    .graph-g {
        width: 12%;
        border-width: 1px;
    }

    .graph-e {
        left: 40%;
        top: 14%;
        animation-delay: -0.25s;
    }

    .graph-f {
        right: 10%;
        top: 58%;
        animation-delay: -1.25s;
    }

    .graph-g {
        left: 12%;
        bottom: 34%;
        animation-delay: -2.1s;
    }

    .graph-wave {
        position: absolute;
        z-index: 0;
        left: 22%;
        top: 28%;
        width: 56%;
        aspect-ratio: 1;
        border: 1px solid rgba(49, 128, 255, 0.28);
        border-radius: 50%;
        animation: waveExpand 2.6s ease-out infinite;
    }

    .agent-core {
        position: absolute;
        z-index: 3;
        left: 34%;
        top: 30%;
        width: 32%;
        aspect-ratio: 1;
        border-radius: 50%;
        background: radial-gradient(
            circle at center,
            #ffffff 0 18%,
            #61c4ff 20% 48%,
            #236cff 50% 100%
        );
        box-shadow: 0 0 26px rgba(35, 108, 255, 0.72);
        animation: corePulse 2.2s ease-in-out infinite;
    }

    .agent-ring {
        position: absolute;
        left: 22%;
        top: 18%;
        width: 56%;
        aspect-ratio: 1;
        border-radius: 50%;
        border: 2px solid rgba(44, 124, 255, 0.46);
        animation: agentSpin 4s linear infinite;

        &::before {
            content: '';
            position: absolute;
            right: 7%;
            top: 9%;
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #ffffff;
            box-shadow: 0 0 12px #227cff;
        }
    }

    .ring-b {
        left: 13%;
        top: 9%;
        width: 74%;
        border-style: dashed;
        animation-duration: 6s;
        animation-direction: reverse;
    }

    .agent-dot {
        position: absolute;
        z-index: 2;
        width: 9px;
        height: 9px;
        border-radius: 50%;
        background: #2a78ff;
        box-shadow: 0 0 14px rgba(42, 120, 255, 0.9);
        animation: decisionDot 2.8s ease-in-out infinite;
    }

    .dot-a {
        left: 16%;
        top: 24%;
    }

    .dot-b {
        right: 14%;
        top: 28%;
        animation-delay: -0.9s;
    }

    .dot-c {
        left: 44%;
        bottom: 14%;
        animation-delay: -1.8s;
    }

    .agent-signal {
        position: absolute;
        z-index: 1;
        left: 26%;
        width: 48%;
        height: 2px;
        border-radius: 999px;
        background: linear-gradient(90deg, transparent, rgba(44, 124, 255, 0.8), transparent);
        animation: signalSweep 2.4s ease-in-out infinite;
    }

    .signal-a {
        top: 38%;
        transform: rotate(24deg);
    }

    .signal-b {
        top: 58%;
        transform: rotate(-24deg);
        animation-delay: -1.2s;
    }

    @keyframes effectBreath {
        0%,
        100% {
            opacity: 0.84;
            transform: translate(-50%, -50%) scale(0.985);
        }

        50% {
            opacity: 1;
            transform: translate(-50%, -50%) scale(1.035);
        }
    }

    @keyframes effectGlow {
        0%,
        100% {
            opacity: 0.42;
            transform: scale(0.86);
        }

        50% {
            opacity: 0.92;
            transform: scale(1.1);
        }
    }

    @keyframes scanDown {
        0% {
            opacity: 0;
            transform: translateY(-46%);
        }

        45% {
            opacity: 0.72;
        }

        100% {
            opacity: 0;
            transform: translateY(245%);
        }
    }

    @keyframes codeFloat {
        0%,
        100% {
            transform: translateY(0);
        }

        50% {
            transform: translateY(-8%);
        }
    }

    @keyframes codeTyping {
        0%,
        100% {
            width: 42%;
            opacity: 0.5;
        }

        50% {
            width: 86%;
            opacity: 1;
        }
    }

    @keyframes framePulse {
        0%,
        100% {
            opacity: 0.4;
            transform: scale(0.96);
        }

        50% {
            opacity: 1;
            transform: scale(1.04);
        }
    }

    @keyframes particleDriftA {
        0%,
        100% {
            transform: translate(0, 0);
        }

        50% {
            transform: translate(48px, 70px);
        }
    }

    @keyframes particleDriftB {
        0%,
        100% {
            transform: translate(0, 0);
        }

        50% {
            transform: translate(-52px, -58px);
        }
    }

    @keyframes nodePulse {
        0%,
        100% {
            transform: scale(0.92);
            opacity: 0.78;
        }

        50% {
            transform: scale(1.12);
            opacity: 1;
        }
    }

    @keyframes flowPulseA {
        0% {
            left: 17%;
            top: 28%;
            opacity: 0;
        }

        20%,
        80% {
            opacity: 1;
        }

        100% {
            left: 49%;
            top: 47%;
            opacity: 0;
        }
    }

    @keyframes flowPulseB {
        0% {
            left: 47%;
            top: 48%;
            opacity: 0;
        }

        20%,
        80% {
            opacity: 1;
        }

        100% {
            left: 76%;
            top: 70%;
            opacity: 0;
        }
    }

    @keyframes flowBaseBreath {
        0%,
        100% {
            opacity: 0.36;
            transform: scaleX(0.82);
        }

        50% {
            opacity: 0.92;
            transform: scaleX(1.08);
        }
    }

    @keyframes flowSpark {
        0%,
        100% {
            opacity: 0.22;
            transform: translateY(10px) scale(0.75);
        }

        45% {
            opacity: 1;
            transform: translateY(-8px) scale(1.25);
        }
    }

    @keyframes platformBreath {
        0%,
        100% {
            opacity: 0.72;
            transform: scale(0.94);
        }

        50% {
            opacity: 1;
            transform: scale(1.05);
        }
    }

    @keyframes platformLine {
        0%,
        100% {
            opacity: 0.2;
            transform: scaleX(0.62);
        }

        50% {
            opacity: 0.95;
            transform: scaleX(1);
        }
    }

    @keyframes docFloat {
        0%,
        100% {
            transform: translateY(0) rotate(-2deg);
        }

        50% {
            transform: translateY(-8%) rotate(2deg);
        }
    }

    @keyframes searchBeat {
        0%,
        100% {
            transform: scale(0.94);
        }

        50% {
            transform: scale(1.08);
        }
    }

    @keyframes rayFlash {
        0%,
        100% {
            opacity: 0.18;
        }

        50% {
            opacity: 0.95;
        }
    }

    @keyframes linkGlow {
        0%,
        100% {
            opacity: 0.32;
        }

        50% {
            opacity: 1;
        }
    }

    @keyframes graphNodePulse {
        0%,
        100% {
            transform: scale(0.92);
        }

        50% {
            transform: scale(1.12);
        }
    }

    @keyframes waveExpand {
        0% {
            opacity: 0.6;
            transform: scale(0.65);
        }

        100% {
            opacity: 0;
            transform: scale(1.35);
        }
    }

    @keyframes corePulse {
        0%,
        100% {
            transform: scale(0.95);
        }

        50% {
            transform: scale(1.08);
        }
    }

    @keyframes agentSpin {
        to {
            transform: rotate(360deg);
        }
    }

    @keyframes decisionDot {
        0%,
        100% {
            opacity: 0.45;
            transform: scale(0.8);
        }

        50% {
            opacity: 1;
            transform: scale(1.2);
        }
    }

    @keyframes signalSweep {
        0%,
        100% {
            opacity: 0.18;
        }

        50% {
            opacity: 0.9;
        }
    }

    @keyframes benefitIconFloat {
        0%,
        100% {
            filter: drop-shadow(0 6px 10px rgba(40, 118, 255, 0.18));
            transform: translate(-50%, -50%) scale(1);
        }

        50% {
            filter: drop-shadow(0 10px 14px rgba(40, 118, 255, 0.3));
            transform: translate(-50%, calc(-50% - 4px)) scale(1.04);
        }
    }

    .login-redesign {
        min-height: 100vh;
        display: grid;
        grid-template-columns: minmax(620px, 1.14fr) minmax(460px, 0.86fr);
        background: #f6f8fc;
        overflow: hidden;
        color: #101828;
    }

    .login-showcase {
        position: relative;
        min-height: 720px;
        display: flex;
        align-items: center;
        padding: 72px clamp(48px, 7vw, 118px);
        overflow: hidden;
        background:
            linear-gradient(135deg, rgba(10, 31, 68, 0.96), rgba(20, 70, 137, 0.92)),
            url('@/assets/system/images/layout/navbar_bg.jpg') center / cover no-repeat;

        &::before {
            content: '';
            position: absolute;
            inset: 0;
            background:
                radial-gradient(circle at 78% 18%, rgba(96, 199, 255, 0.34), transparent 28%),
                radial-gradient(circle at 16% 78%, rgba(48, 123, 255, 0.28), transparent 30%),
                linear-gradient(115deg, rgba(255, 255, 255, 0.06), transparent 40%);
        }

        &::after {
            content: '';
            position: absolute;
            inset: 0;
            opacity: 0.22;
            background-image:
                linear-gradient(rgba(255, 255, 255, 0.14) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255, 255, 255, 0.14) 1px, transparent 1px);
            background-size: 56px 56px;
            mask-image: linear-gradient(90deg, #000, transparent 78%);
        }
    }

    .showcase-inner {
        position: relative;
        z-index: 1;
        width: min(760px, 100%);
    }

    .product-chip {
        display: inline-flex;
        align-items: center;
        height: 34px;
        padding: 0 14px;
        border: 1px solid rgba(145, 201, 255, 0.34);
        border-radius: 999px;
        color: #b9ddff;
        background: rgba(255, 255, 255, 0.08);
        font-size: 14px;
        font-weight: 700;
    }

    .showcase-inner h1 {
        max-width: 720px;
        margin: 28px 0 0;
        color: #ffffff;
        font-size: clamp(44px, 4.7vw, 72px);
        line-height: 1.08;
        font-weight: 800;
        letter-spacing: 0;
    }

    .showcase-summary {
        max-width: 620px;
        margin: 26px 0 0;
        color: rgba(230, 241, 255, 0.78);
        font-size: 18px;
        line-height: 1.75;
    }

    .showcase-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-top: 34px;

        span {
            padding: 10px 16px;
            border: 1px solid rgba(145, 201, 255, 0.3);
            border-radius: 8px;
            color: #d9ecff;
            background: rgba(255, 255, 255, 0.08);
            font-size: 14px;
        }
    }

    .pipeline {
        display: grid;
        grid-template-columns: 1fr 56px 1fr 56px 1fr;
        align-items: center;
        gap: 0;
        margin-top: 74px;
    }

    .pipeline-card {
        min-height: 150px;
        padding: 20px;
        border: 1px solid rgba(167, 207, 255, 0.2);
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.1);
        box-shadow: 0 22px 56px rgba(0, 20, 64, 0.2);
        backdrop-filter: blur(14px);

        span {
            color: #79c2ff;
            font-size: 14px;
            font-weight: 800;
        }

        strong {
            display: block;
            margin-top: 20px;
            color: #ffffff;
            font-size: 24px;
        }

        p {
            margin: 12px 0 0;
            color: rgba(230, 241, 255, 0.66);
            font-size: 13px;
            line-height: 1.55;
        }
    }

    .pipeline-card.is-active {
        border-color: rgba(101, 201, 255, 0.55);
        background: rgba(40, 122, 255, 0.22);
    }

    .pipeline-line {
        height: 1px;
        background: linear-gradient(90deg, rgba(145, 201, 255, 0.2), rgba(145, 201, 255, 0.8), rgba(145, 201, 255, 0.2));
    }

    .login-auth {
        min-height: 720px;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 48px;
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 255, 0.98));
    }

    .auth-card {
        width: min(440px, 100%);
        padding: 42px;
        border: 1px solid #e8eef7;
        border-radius: 10px;
        background: #ffffff;
        box-shadow: 0 28px 80px rgba(16, 41, 90, 0.12);
    }

    .auth-brand {
        display: flex;
        align-items: center;
        gap: 12px;

        span {
            width: 38px;
            height: 38px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            border-radius: 10px;
            color: #ffffff;
            font-size: 21px;
            font-weight: 800;
            background: linear-gradient(135deg, #246bfe, #35c8d5);
        }

        strong {
            color: #172033;
            font-size: 19px;
            line-height: 1;
        }
    }

    .auth-heading {
        margin-top: 54px;

        p {
            margin: 0 0 10px;
            color: #2563eb;
            font-size: 15px;
            font-weight: 700;
        }

        h2 {
            margin: 0;
            color: #101828;
            font-size: 32px;
            line-height: 1.15;
            font-weight: 800;
        }

        span {
            display: block;
            margin-top: 12px;
            color: #667085;
            font-size: 14px;
        }
    }

    .auth-form {
        margin-top: 34px;

        :deep(.el-form-item) {
            margin-bottom: 18px;
        }

        :deep(.el-input__wrapper) {
            min-height: 46px;
            border-radius: 8px;
            box-shadow: 0 0 0 1px #dbe3ef inset;
            transition: box-shadow 0.2s ease, background 0.2s ease;
        }

        :deep(.el-input__wrapper:hover),
        :deep(.el-input__wrapper.is-focus) {
            box-shadow: 0 0 0 1px #2563eb inset, 0 12px 24px rgba(37, 99, 235, 0.08);
        }

        .iconfont {
            color: #98a2b3;
        }
    }

    .auth-options {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin: 4px 0 24px;
    }

    .auth-submit {
        width: 100%;
        height: 46px;
        border-radius: 8px;
        font-weight: 700;
        background: linear-gradient(90deg, #246bfe, #155eef);
        border: none;
        box-shadow: 0 16px 32px rgba(37, 99, 235, 0.22);
    }

    @media screen and (max-width: 1100px) {
        .login-redesign {
            grid-template-columns: 1fr;
            overflow: auto;
        }

        .login-showcase {
            min-height: auto;
            padding: 56px 36px;
        }

        .pipeline {
            grid-template-columns: 1fr;
            gap: 14px;
            margin-top: 40px;
        }

        .pipeline-line {
            display: none;
        }

        .login-auth {
            min-height: auto;
            padding: 36px;
        }
    }

    @media screen and (max-width: 640px) {
        .login-showcase {
            display: none;
        }

        .login-auth {
            min-height: 100vh;
            padding: 20px;
        }

        .auth-card {
            padding: 28px;
        }

        .auth-heading {
            margin-top: 40px;
        }
    }
</style>
