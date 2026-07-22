package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class ShadowContractSupport {

    static final List<String> DATASET_RESOURCES = List.of(
            "/rag-eval/corpus.jsonl",
            "/rag-eval/queries.jsonl",
            "/rag-eval/qrels.tsv");

    private ShadowContractSupport() {
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(Objects.requireNonNull(bytes, "bytes")));
    }

    static String sha256(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot hash artifact: " + path, e);
        }
    }

    static String datasetHash() {
        MessageDigest digest = sha256Digest();
        for (String resource : DATASET_RESOURCES) {
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = ShadowContractSupport.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IllegalStateException("Missing classpath resource: " + resource);
                }
                digest.update(input.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot hash classpath resource: " + resource, e);
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String configHash(Map<String, ?> config) {
        return sha256(canonicalJson(Objects.requireNonNull(config, "config")).getBytes(StandardCharsets.UTF_8));
    }

    static String snapshotJson(RagBenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("datasetHash", report.datasetHash());
        snapshot.put("configHash", report.configHash());
        JSONObject serializedReport = JSON.parseObject(JSON.toJSONString(report));
        snapshot.put("metrics", serializedReport.getJSONObject("metrics"));
        return canonicalJson(snapshot);
    }

    private static String canonicalJson(Object value) {
        return JSON.toJSONString(canonicalize(value), JSONWriter.Feature.WriteNulls);
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("Canonical JSON map keys must be strings");
                }
                sorted.put(stringKey, canonicalize(child));
            });
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> canonical = new ArrayList<>(list.size());
            list.forEach(child -> canonical.add(canonicalize(child)));
            return canonical;
        }
        return value;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
