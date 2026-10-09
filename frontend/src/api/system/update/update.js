import request from '@/utils/request.js'

export function getCurrentAppVersion() {
    return request({
        url: '/updater/getCurrentAppVersion',
        method: 'get'
    })
}
