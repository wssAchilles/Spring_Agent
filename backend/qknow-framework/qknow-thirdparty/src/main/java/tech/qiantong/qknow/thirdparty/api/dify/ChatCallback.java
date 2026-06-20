package tech.qiantong.qknow.thirdparty.api.dify;

import okhttp3.Response;

import java.io.IOException;

/**
 * 大模型回调封装
 * @author wang
 * @date 2025/03/18 15:41
 **/
public interface ChatCallback {
    /**
     * 成功回调
     * @param response
     */
    void onSuccess(Response response) throws IOException;

    /**
     * 失败回调
     * @param e
     */
    void onFailure(IOException e);
}
