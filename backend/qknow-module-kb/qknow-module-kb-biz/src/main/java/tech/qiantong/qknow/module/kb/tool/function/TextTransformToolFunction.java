package tech.qiantong.qknow.module.kb.tool.function;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Slf4j
@Component("text_transform")
public class TextTransformToolFunction
        implements Function<TextTransformToolFunction.Request, TextTransformToolFunction.Response> {

    @Data
    @JsonClassDescription("文本转换工具：支持大小写转换、去除空白、截取等操作")
    public static class Request {

        @JsonProperty(required = true, value = "text")
        @JsonPropertyDescription("要处理的文本内容")
        private String text;

        @JsonProperty(required = true, value = "operation")
        @JsonPropertyDescription("操作类型: uppercase(转大写), lowercase(转小写), trim(去首尾空白), substring(截取前N字符), length(获取长度)")
        private String operation;

        @JsonProperty(value = "param")
        @JsonPropertyDescription("额外参数，如 substring 操作时指定截取长度")
        private Integer param;
    }

    @Data
    public static class Response {
        private String result;
        private String error;

        public static Response success(String result) {
            Response r = new Response();
            r.result = result;
            return r;
        }

        public static Response error(String error) {
            Response r = new Response();
            r.error = error;
            return r;
        }
    }

    @Override
    public Response apply(Request request) {
        try {
            String text = request.getText();
            String operation = request.getOperation();

            return switch (operation) {
                case "uppercase" -> Response.success(text.toUpperCase());
                case "lowercase" -> Response.success(text.toLowerCase());
                case "trim" -> Response.success(text.trim());
                case "substring" -> {
                    int len = request.getParam() != null ? request.getParam() : 100;
                    yield Response.success(text.substring(0, Math.min(text.length(), len)));
                }
                case "length" -> Response.success(String.valueOf(text.length()));
                default -> Response.error("不支持的操作: " + operation);
            };
        } catch (Exception e) {
            return Response.error("处理失败: " + e.getMessage());
        }
    }
}
