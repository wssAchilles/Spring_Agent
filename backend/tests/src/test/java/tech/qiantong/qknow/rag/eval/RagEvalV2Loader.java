package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 01: schema-v2 loader/validator for rag-eval-v2.jsonl.
 */
public final class RagEvalV2Loader {

    private RagEvalV2Loader() {
    }

    public static final String CLASSPATH = "/rag-eval-v2.jsonl";

    public static final List<String> STRATA =
            List.of("short", "medium", "multihop", "negative", "rewrite");

    public record EvalCase(String id, String query, String lang, String stratum, String split,
                           boolean answerable, long kbId, List<String> expectedSources,
                           List<String> expectedSegments, String familyId, List<String> tags) {
    }

    public static EvalCase validate(Map<String, Object> raw, String origin) {
        String id = str(raw.get("id"));
        String query = str(raw.get("query"));
        String split = str(raw.get("split"));
        if (id.isBlank()) {
            throw new IllegalArgumentException(origin + ": missing id");
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException(origin + ": missing query");
        }
        if (split.isBlank()) {
            throw new IllegalArgumentException(origin + ": missing split");
        }
        String stratum = str(raw.get("stratum")).toLowerCase(Locale.ROOT);
        if (!STRATA.contains(stratum)) {
            throw new IllegalArgumentException(origin + ": bad stratum " + stratum);
        }
        boolean answerable = raw.get("answerable") == null || Boolean.TRUE.equals(raw.get("answerable"));
        List<String> expected = stringList(raw.get("expectedSources"));
        if (stratum.equals("negative")) {
            answerable = false;
        }
        if (answerable && expected.isEmpty()) {
            throw new IllegalArgumentException(origin + ": answerable requires expectedSources");
        }
        long kbId = 0;
        Object kb = raw.get("kbId");
        if (kb instanceof Number n) {
            kbId = n.longValue();
        }
        return new EvalCase(
                id,
                query,
                str(raw.get("lang")),
                stratum,
                split,
                answerable,
                kbId,
                expected,
                stringList(raw.get("expectedSegments")),
                str(raw.get("familyId")),
                stringList(raw.get("tags")));
    }

    public static List<EvalCase> loadFromClasspath() {
        return load(RagEvalV2Loader.class.getResourceAsStream(CLASSPATH));
    }

    public static List<EvalCase> load(InputStream in) {
        List<EvalCase> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        try (in) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int lineNo = 0;
            for (String line : text.split("\n")) {
                lineNo++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                JSONObject obj = JSON.parseObject(line);
                out.add(validate(obj, "line " + lineNo));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("failed to load eval v2", e);
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static List<String> stringList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) {
                if (x != null) {
                    out.add(String.valueOf(x));
                }
            }
            return out;
        }
        return List.of(String.valueOf(o));
    }
}
