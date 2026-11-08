package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TauRetailEnvironment {
    private JSONObject database;
    private final List<CallRecord> calls = new ArrayList<>();
    private final List<ToolError> errors = new ArrayList<>();

    public TauRetailEnvironment(JSONObject database) {
        this.database = copy(database);
    }

    public static TauRetailEnvironment fromResource() throws IOException {
        try (InputStream stream = TauRetailEnvironment.class.getResourceAsStream("/tau2/retail/db.json")) {
            if (stream == null) {
                throw new IllegalArgumentException("missing tau retail database");
            }
            return new TauRetailEnvironment(JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    public JSONObject snapshot() {
        return copy(database);
    }

    JSONObject table(String name) {
        JSONObject table = database.getJSONObject(name);
        if (table == null) {
            throw new TauRetailToolException("MISSING_TABLE", "database table is unavailable");
        }
        return table;
    }

    void record(TauRetailTaskLoader.ToolCall call, TauRetailTools.ToolResult result) {
        calls.add(new CallRecord(call, result.success(), result.errorCode(), result.output()));
        if (!result.success()) {
            errors.add(new ToolError(call.name(), result.errorCode()));
        }
    }

    public List<CallRecord> calls() {
        return List.copyOf(calls);
    }

    public List<ToolError> errors() {
        return List.copyOf(errors);
    }

    static JSONObject copy(JSONObject object) {
        return JSON.parseObject(object.toJSONString());
    }

    public record CallRecord(TauRetailTaskLoader.ToolCall call, boolean success, String errorCode,
                             JSONObject output) {
        public CallRecord {
            output = output == null ? new JSONObject() : copy(output);
        }
    }
    public record ToolError(String toolName, String errorCode) { }

    static final class TauRetailToolException extends RuntimeException {
        private final String code;

        TauRetailToolException(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
