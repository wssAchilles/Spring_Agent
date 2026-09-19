import defaultSettings from '@/settings'
import { useDynamicTitle } from '@/utils/dynamicTitle'

const { sideTheme, showSettings, topNav, tagsView, fixedHeader, sidebarLogo, dynamicTitle } = defaultSettings

const storageSetting = JSON.parse(localStorage.getItem('layout-setting')) || ''
// 迁移旧缓存中的旧主题色与旧暗色侧边栏
const defaultTheme = '#0088ff'
const defaultSideTheme = 'theme-light'
const initialTheme = (storageSetting.theme === '#2666fb' || storageSetting.theme === '#409EFF' || storageSetting.theme === '#1D1D1F') ? defaultTheme : (storageSetting.theme || defaultTheme)
const initialSideTheme = (storageSetting.sideTheme === 'theme-dark') ? defaultSideTheme : (storageSetting.sideTheme || sideTheme || defaultSideTheme)

const useSettingsStore = defineStore(
  'settings',
  {
    state: () => ({
      title: '',
      theme: initialTheme,
      sideTheme: initialSideTheme,
      showSettings: showSettings,
      topNav: true,
      // topNav: storageSetting.topNav === undefined ? topNav : storageSetting.topNav,
      tagsView: storageSetting.tagsView === undefined ? tagsView : storageSetting.tagsView,
      fixedHeader: storageSetting.fixedHeader === undefined ? fixedHeader : storageSetting.fixedHeader,
      sidebarLogo: storageSetting.sidebarLogo === undefined ? sidebarLogo : storageSetting.sidebarLogo,
      dynamicTitle: storageSetting.dynamicTitle === undefined ? dynamicTitle : storageSetting.dynamicTitle
    }),
    actions: {
      // 修改布局设置
      changeSetting(data) {
        const { key, value } = data
        if (this.hasOwnProperty(key)) {
          this[key] = value
        }
      },
      // 设置网页标题
      setTitle(title) {
        this.title = title
        useDynamicTitle();
      }
    }
  })

export default useSettingsStore
