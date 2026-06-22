<template>
  <div
    class="sidebar-logo-container"
    :class="{ collapse: collapse }"
    
  >
    <transition name="sidebarLogoFade">
      <router-link
        v-if="collapse"
        key="collapse"
        class="sidebar-logo-link"
        to="/"
      >
        <span class="brand-mark brand-mark--compact">K</span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <span class="sidebar-logo-split" :class="{ 'logo-intro': logoIntroActive }">
          <span class="brand-mark">K</span>
          <span class="sidebar-logo-word">Knowledge Hub</span>
        </span>
      </router-link>
    </transition>
  </div>
</template>

<script setup>
const logoIntroActive = ref(false);

const props = defineProps({
  collapse: {
    type: Boolean,
    required: true,
  },
  currentRoute: {
    type: String,
    default: "/",
  },
});

onMounted(() => {
  logoIntroActive.value = true;
  window.setTimeout(() => {
    logoIntroActive.value = false;
  }, 1800);
});
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 1.5s;
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 60px;
  line-height: 50px;
  background: transparent;
  text-align: center;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,0.06);;

  & .sidebar-logo-link {
    height: 100%;
    width: 100%;

    & .sidebar-logo-split {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 100%;
      gap: 10px;
      vertical-align: middle;
    }

    & .brand-mark {
      width: 36px;
      height: 36px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 10px;
      color: #fff;
      font-size: 20px;
      font-weight: 800;
      line-height: 1;
      background: linear-gradient(135deg, #3b82ff 0%, #42c7ff 100%);
      box-shadow: 0 10px 24px rgba(44, 111, 255, 0.26);
      transform-origin: center;
    }

    & .brand-mark--compact {
      margin-top: 12px;
    }

    & .sidebar-logo-word {
      display: inline-block;
      color: #fff;
      font-size: 20px;
      font-weight: 800;
      line-height: 1;
      letter-spacing: 0;
    }

    & .logo-intro,
    &:hover .sidebar-logo-split {
      .brand-mark {
        animation: sidebarLogoKIntro 1.15s cubic-bezier(0.2, 0.85, 0.22, 1) 0.35s both;
      }

      .sidebar-logo-word {
        animation: sidebarLogoWordIntro 0.45s ease-out both;
      }
    }

    & .sidebar-title {
      display: inline-block;
      margin: 0;
      color: #fff;
      font-weight: 600;
      line-height: 50px;
      font-size: 14px;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      vertical-align: middle;
    }
  }

  &.navbar-logo{
    background-color: #fff !important;
  }

  &.collapse {
    .brand-mark {
      width: 34px;
      height: 34px;
      font-size: 19px;
    }
  }
}

@keyframes sidebarLogoKIntro {
  0% {
    opacity: 0;
    filter: drop-shadow(0 0 0 rgba(69, 145, 255, 0));
    transform: translateX(18px) scale(0.62) rotate(-10deg);
  }

  54% {
    opacity: 1;
    filter: drop-shadow(0 0 14px rgba(69, 145, 255, 0.65));
    transform: translateX(-3px) scale(1.08) rotate(3deg);
  }

  78% {
    filter: drop-shadow(0 0 10px rgba(69, 145, 255, 0.38));
    transform: translateX(1px) scale(0.98) rotate(-1deg);
  }

  100% {
    opacity: 1;
    filter: drop-shadow(0 0 0 rgba(69, 145, 255, 0));
    transform: translateX(0) scale(1) rotate(0);
  }
}

@keyframes sidebarLogoWordIntro {
  0% {
    opacity: 0;
    transform: translateX(-4px);
  }

  100% {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
