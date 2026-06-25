const baseUrl = import.meta.env.VITE_APP_FILE_VIEW + "/onlinePreview?url="

// 获取屏幕尺寸
const screenWidth = window.screen.width;
const screenHeight = window.screen.height;

// 设置窗口尺寸为屏幕尺寸的一部分，例如60%
const width = screenWidth * 0.7;
const height = screenHeight * 0.7;

// 计算窗口居中时的左上角位置
const left = (screenWidth - width) / 2;
const top = (screenHeight - height) / 2;


export const filePreview = (fileUrl) => {
    // 打开新窗口并居中
    let targetUrl = getBaseURL() + fileUrl;
    if (fileUrl.toLowerCase().endsWith('.pdf') || !import.meta.env.VITE_APP_FILE_VIEW || import.meta.env.VITE_APP_FILE_VIEW === 'http://127.0.0.1:8012') {
         // 直接使用浏览器原生预览
    } else {
         targetUrl = baseUrl + base64Encode(getBaseURL() + fileUrl);
    }
    const newWindow = window.open(targetUrl, '', `scrollbars=yes, width=${width}, height=${height}, top=${top}, left=${left}`);
    if (window.focus) {
        newWindow.focus();
    }
}

export const filePreviewUrl = (fileUrl) => {
    return baseUrl + base64Encode(getBaseURL() + fileUrl);
}

function base64Encode(str) {
    // 首先将字符串转换为Uint8Array
    let uint8Array = new TextEncoder().encode(str);
    // 然后使用btoa进行Base64编码，注意这里使用了Uint8Array的reduce结合atob进行转换
    return btoa(String.fromCharCode.apply(null, uint8Array));
}

function getBaseURL(){
    const { protocol, hostname, port } = window.location;
    return `${protocol}//${hostname}${port ? ':' + port : ''}`;
    // return "http://192.168.0.115:80"
}
