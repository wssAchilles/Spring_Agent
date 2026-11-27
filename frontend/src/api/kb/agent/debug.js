import { fetchEventSource } from '@microsoft/fetch-event-source';
import { getToken } from '@/utils/auth';
import { API_BASE_URL } from '@/utils/request';

// 调试 Agent（流式输出）
export function debugAgent(data, onMessage, onError, onClose) {
    const token = getToken();
    const ctrl = new AbortController();
    
    return fetchEventSource(`${API_BASE_URL}/kb/agent/testChatMessages`, {
        method: 'post',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
        },
        openWhenHidden: true,
        body: JSON.stringify(data),
        onmessage: onMessage,
        onerror: onError,
        onclose: onClose,
        signal: ctrl.signal
    });
}
