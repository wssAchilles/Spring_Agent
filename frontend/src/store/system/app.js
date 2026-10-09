import Cookies from 'js-cookie';
import bus from '@/utils/bus';

const useAppStore = defineStore('app', {
    state: () => ({
        sidebar: {
            opened: Cookies.get('sidebarStatus') ? !!+Cookies.get('sidebarStatus') : true,
            withoutAnimation: false,
            hide: false
        },
        device: 'desktop',
        size: Cookies.get('size') || 'default'
    }),
    actions: {
        toggleSideBar(withoutAnimation) {
            if (this.sidebar.hide) {
                return false;
            }
            const oldStatus = this.sidebar.opened;
            this.sidebar.opened = !this.sidebar.opened;
            this.sidebar.withoutAnimation = withoutAnimation;
            if (this.sidebar.opened) {
                Cookies.set('sidebarStatus', 1);
            } else {
                Cookies.set('sidebarStatus', 0);
            }
            if (oldStatus !== this.sidebar.opened) {
                bus.emit('getsidebarStatus', this.sidebar.opened);
            }
        },
        openSideBar({ withoutAnimation }) {
            const oldStatus = this.sidebar.opened;
            Cookies.set('sidebarStatus', 1);
            this.sidebar.opened = true;
            this.sidebar.withoutAnimation = withoutAnimation;
            if (oldStatus !== this.sidebar.opened) {
                bus.emit('getsidebarStatus', this.sidebar.opened);
            }
        },
        closeSideBar({ withoutAnimation }) {
            const oldStatus = this.sidebar.opened;
            Cookies.set('sidebarStatus', 0);
            this.sidebar.opened = false;
            this.sidebar.withoutAnimation = withoutAnimation;
            if (oldStatus !== this.sidebar.opened) {
                bus.emit('getsidebarStatus', this.sidebar.opened);
            }
        },
        toggleDevice(device) {
            this.device = device;
        },
        setSize(size) {
            this.size = size;
            Cookies.set('size', size);
        },
        toggleSideBarHide(status) {
            this.sidebar.hide = status;
        }
    }
});

export default useAppStore;
