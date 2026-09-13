package tech.qiantong.qknow.kb.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.function.Function;

@Slf4j
@Service
@Description("用于解决复杂的数学规划与运筹优化问题（如供应链、排班、资源分配等）。你需要将问题翻译为使用 gurobipy 的 Python 代码，并将其传入此工具执行。代码中必须包含 with gp.Model(env=global_env) as m，并且将优化结果写入预置的字典 output_dict 中（如 output_dict['status'] = m.status）。")
public class GurobiOptimizerTool implements Function<GurobiOptimizerTool.Request, GurobiOptimizerTool.Response> {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String OPTI_SERVER_URL = "http://127.0.0.1:8100/solve";

    @Data
    public static class Request {
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("使用 gurobipy 编写的纯 Python 建模代码，不要包含任何 Markdown 格式")
        private String code;
    }

    @Data
    public static class Response {
        private String result;
    }

    @Override
    public Response apply(Request request) {
        log.info("OptiAgent is dispatching python code to Gurobi Solver...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String jsonPayload = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            
            String responseStr = restTemplate.postForObject(OPTI_SERVER_URL, entity, String.class);
            
            Response response = new Response();
            response.setResult(responseStr);
            return response;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Gurobi request", e);
            Response err = new Response();
            err.setResult("{\"status\": \"ERROR\", \"error\": \"Serialization failed\"}");
            return err;
        } catch (Exception e) {
            log.error("Failed to connect to Python Gurobi Sidecar", e);
            Response err = new Response();
            err.setResult("{\"status\": \"ERROR\", \"error\": \"Gurobi sidecar is unreachable\"}");
            return err;
        }
    }
}
