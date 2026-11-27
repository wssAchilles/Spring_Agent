package tech.qiantong.qknow.hermes.benchmark.dataset;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkTask;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 真实四大公开学术数据集标准加载器。
 * <p>
 * 原生解析本地 {@code data/} 目录下已校验的四大基准原始格式：
 * 1. {@code data/hotpot_dev_distractor_v1.json}
 * 2. {@code data/musique/musique_ans_v1.0_dev.jsonl}
 * 3. {@code data/longmem/longmemeval_oracle.json}
 * 4. {@code data/tablebench/TableBench.jsonl}
 * 支持按固定随机种子进行分层可复现抽样，并同时适配 {@code backend/tests} 或项目根目录工作区相对路径。
 * </p>
 */
public class RealPublicBenchmarkLoader {

    private final Path dataRoot;

    public RealPublicBenchmarkLoader() {
        this(resolveDefaultDataRoot());
    }

    public RealPublicBenchmarkLoader(Path dataRoot) {
        this.dataRoot = dataRoot != null ? dataRoot : resolveDefaultDataRoot();
    }

    public static Path resolveDefaultDataRoot() {
        List<Path> candidates = List.of(
                Path.of("data"),
                Path.of("../data"),
                Path.of("../../data"),
                Path.of("/Users/achilles/.gemini/antigravity/worktrees/Agent/backend_codebase_indexing_agents/data"),
                Path.of("/Users/achilles/Documents/许子祺/Agent/data")
        );
        for (Path p : candidates) {
            if (Files.isDirectory(p) && Files.exists(p.resolve("hotpot_dev_distractor_v1.json"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        return Path.of("data").toAbsolutePath().normalize();
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    /**
     * 从四个真实公开数据集中各抽取 {@code samplesPerDataset} 条样本，形成统一标准化评测队列。
     */
    public List<RealBenchmarkSample> loadStratifiedSuite(int samplesPerDataset, long sampleSeed) {
        int limit = Math.max(1, samplesPerDataset);
        List<RealBenchmarkSample> all = new ArrayList<>(limit * 4);
        all.addAll(loadHotpotQa(limit, sampleSeed));
        all.addAll(loadMuSiQue(limit, sampleSeed + 1L));
        all.addAll(loadLongMemEval(limit, sampleSeed + 2L));
        all.addAll(loadTableBench(limit, sampleSeed + 3L));
        return Collections.unmodifiableList(all);
    }

    public List<RealBenchmarkSample> loadHotpotQa(int limit, long seed) {
        Path file = dataRoot.resolve("hotpot_dev_distractor_v1.json");
        if (!Files.exists(file)) {
            throw new IllegalStateException("未找到 HotpotQA 数据集文件: " + file);
        }
        try {
            JSONArray array = JSON.parseArray(Files.readString(file, StandardCharsets.UTF_8));
            List<Integer> indices = sampleIndices(array.size(), limit, seed);
            List<RealBenchmarkSample> list = new ArrayList<>(indices.size());
            for (int idx : indices) {
                JSONObject obj = array.getJSONObject(idx);
                String id = obj.getString("_id");
                String question = obj.getString("question");
                String answer = obj.getString("answer");
                String qType = obj.getString("type");

                Set<String> supportSet = new LinkedHashSet<>();
                JSONArray supArr = obj.getJSONArray("supporting_facts");
                if (supArr != null) {
                    for (int i = 0; i < supArr.size(); i++) {
                        JSONArray pair = supArr.getJSONArray(i);
                        if (pair != null && !pair.isEmpty()) {
                            supportSet.add(pair.getString(0));
                        }
                    }
                }

                List<RealBenchmarkSample.ContextChunk> chunks = new ArrayList<>();
                JSONArray ctxArr = obj.getJSONArray("context");
                if (ctxArr != null) {
                    for (int i = 0; i < ctxArr.size(); i++) {
                        JSONArray entry = ctxArr.getJSONArray(i);
                        if (entry != null && entry.size() >= 2) {
                            String title = entry.getString(0);
                            JSONArray sents = entry.getJSONArray(1);
                            StringBuilder sb = new StringBuilder();
                            if (sents != null) {
                                for (int j = 0; j < sents.size(); j++) {
                                    sb.append(sents.getString(j)).append(' ');
                                }
                            }
                            chunks.add(new RealBenchmarkSample.ContextChunk(title, sb.toString().trim(), ""));
                        }
                    }
                }
                list.add(new RealBenchmarkSample(
                        "HOTPOT-" + id,
                        AcademicBenchmarkTask.BenchmarkDataset.HOTPOT_QA,
                        question,
                        List.of(answer),
                        chunks,
                        new ArrayList<>(supportSet),
                        qType != null ? qType : "hotpot",
                        "",
                        false
                ));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("解析 HotpotQA 数据集失败: " + file, e);
        }
    }

    public List<RealBenchmarkSample> loadMuSiQue(int limit, long seed) {
        Path file = dataRoot.resolve("musique/musique_ans_v1.0_dev.jsonl");
        if (!Files.exists(file)) {
            throw new IllegalStateException("未找到 MuSiQue 数据集文件: " + file);
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Integer> indices = sampleIndices(lines.size(), limit, seed);
            List<RealBenchmarkSample> list = new ArrayList<>(indices.size());
            for (int idx : indices) {
                JSONObject obj = JSON.parseObject(lines.get(idx));
                String id = obj.getString("id");
                String question = obj.getString("question");
                String answer = obj.getString("answer");
                List<String> golds = new ArrayList<>();
                if (answer != null && !answer.isBlank()) {
                    golds.add(answer);
                }
                JSONArray aliases = obj.getJSONArray("answer_aliases");
                if (aliases != null) {
                    for (int i = 0; i < aliases.size(); i++) {
                        String al = aliases.getString(i);
                        if (al != null && !al.isBlank() && !golds.contains(al)) {
                            golds.add(al);
                        }
                    }
                }
                List<RealBenchmarkSample.ContextChunk> chunks = new ArrayList<>();
                List<String> supports = new ArrayList<>();
                JSONArray paras = obj.getJSONArray("paragraphs");
                if (paras != null) {
                    for (int i = 0; i < paras.size(); i++) {
                        JSONObject p = paras.getJSONObject(i);
                        String title = p.getString("title");
                        String text = p.getString("paragraph_text");
                        String uniqueTitle = (title != null ? title : "para") + "#" + i;
                        chunks.add(new RealBenchmarkSample.ContextChunk(uniqueTitle, text != null ? text : "", ""));
                        if (Boolean.TRUE.equals(p.getBoolean("is_supporting"))) {
                            supports.add(uniqueTitle);
                        }
                    }
                }
                list.add(new RealBenchmarkSample(
                        "MUSIQUE-" + id,
                        AcademicBenchmarkTask.BenchmarkDataset.MUSIQUE,
                        question,
                        golds,
                        chunks,
                        supports,
                        "multihop",
                        "",
                        false
                ));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("解析 MuSiQue 数据集失败: " + file, e);
        }
    }

    public List<RealBenchmarkSample> loadLongMemEval(int limit, long seed) {
        Path file = dataRoot.resolve("longmem/longmemeval_oracle.json");
        if (!Files.exists(file)) {
            throw new IllegalStateException("未找到 LongMemEval 数据集文件: " + file);
        }
        try {
            JSONArray array = JSON.parseArray(Files.readString(file, StandardCharsets.UTF_8));
            List<Integer> indices = sampleIndices(array.size(), limit, seed);
            List<RealBenchmarkSample> list = new ArrayList<>(indices.size());
            for (int idx : indices) {
                JSONObject obj = array.getJSONObject(idx);
                String qid = obj.getString("question_id");
                String qType = obj.getString("question_type");
                String question = obj.getString("question");
                String qDate = obj.getString("question_date");
                String answer = String.valueOf(obj.get("answer"));

                JSONArray dates = obj.getJSONArray("haystack_dates");
                JSONArray sessionIds = obj.getJSONArray("haystack_session_ids");
                JSONArray sessions = obj.getJSONArray("haystack_sessions");
                JSONArray ansIds = obj.getJSONArray("answer_session_ids");
                List<String> supIds = new ArrayList<>();
                if (ansIds != null) {
                    for (int i = 0; i < ansIds.size(); i++) {
                        supIds.add(ansIds.getString(i));
                    }
                }

                List<RealBenchmarkSample.ContextChunk> chunks = new ArrayList<>();
                if (sessions != null) {
                    for (int s = 0; s < sessions.size(); s++) {
                        String sid = (sessionIds != null && s < sessionIds.size()) ? sessionIds.getString(s) : ("session_" + s);
                        String sdate = (dates != null && s < dates.size()) ? dates.getString(s) : "";
                        JSONArray turns = sessions.getJSONArray(s);
                        StringBuilder sb = new StringBuilder();
                        if (!sdate.isBlank()) {
                            sb.append("[SessionDate: ").append(sdate).append("]\n");
                        }
                        if (turns != null) {
                            for (int t = 0; t < turns.size(); t++) {
                                JSONObject turn = turns.getJSONObject(t);
                                sb.append(turn.getString("role")).append(": ")
                                        .append(turn.getString("content")).append('\n');
                            }
                        }
                        chunks.add(new RealBenchmarkSample.ContextChunk(sid, sb.toString().trim(), sdate));
                    }
                }
                String fullQuestion = (qDate != null && !qDate.isBlank())
                        ? question + " (Current Reference Date: " + qDate + ")"
                        : question;
                list.add(new RealBenchmarkSample(
                        "LONGMEM-" + qid,
                        AcademicBenchmarkTask.BenchmarkDataset.LONG_MEM_EVAL,
                        fullQuestion,
                        List.of(answer),
                        chunks,
                        supIds,
                        qType != null ? qType : "longmem",
                        "",
                        false
                ));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("解析 LongMemEval 数据集失败: " + file, e);
        }
    }

    public List<RealBenchmarkSample> loadTableBench(int limit, long seed) {
        Path file = dataRoot.resolve("tablebench/TableBench.jsonl");
        if (!Files.exists(file)) {
            throw new IllegalStateException("未找到 TableBench 数据集文件: " + file);
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Integer> indices = sampleIndices(lines.size(), limit, seed);
            List<RealBenchmarkSample> list = new ArrayList<>(indices.size());
            int counter = 0;
            for (int idx : indices) {
                JSONObject obj = JSON.parseObject(lines.get(idx));
                String id = obj.getString("id");
                String qType = obj.getString("qtype");
                String question = obj.getString("question");
                String answer = String.valueOf(obj.get("answer"));
                JSONObject tableObj = obj.getJSONObject("table");
                String tableJson = tableObj != null ? tableObj.toJSONString() : "{}";
                String tableReadable = formatTableReadable(tableObj);

                boolean isHighRisk = (counter++ % 5 == 0);
                list.add(new RealBenchmarkSample(
                        "TABLEBENCH-" + id,
                        AcademicBenchmarkTask.BenchmarkDataset.TABLE_BENCH,
                        question,
                        List.of(answer),
                        List.of(new RealBenchmarkSample.ContextChunk("table_data", tableReadable, "")),
                        List.of("table_data"),
                        qType != null ? qType : "table",
                        tableJson,
                        isHighRisk
                ));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("解析 TableBench 数据集失败: " + file, e);
        }
    }

    private static String formatTableReadable(JSONObject tableObj) {
        if (tableObj == null) {
            return "";
        }
        JSONArray cols = tableObj.getJSONArray("columns");
        JSONArray rows = tableObj.getJSONArray("data");
        StringBuilder sb = new StringBuilder();
        if (cols != null) {
            sb.append("| ");
            for (int i = 0; i < cols.size(); i++) {
                sb.append(cols.getString(i)).append(" | ");
            }
            sb.append('\n');
        }
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                JSONArray row = rows.getJSONArray(r);
                sb.append("| ");
                for (int c = 0; c < row.size(); c++) {
                    sb.append(row.getString(c)).append(" | ");
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static List<Integer> sampleIndices(int totalSize, int limit, long seed) {
        List<Integer> all = new ArrayList<>(totalSize);
        for (int i = 0; i < totalSize; i++) {
            all.add(i);
        }
        Collections.shuffle(all, new Random(seed));
        int count = Math.min(limit, totalSize);
        List<Integer> picked = new ArrayList<>(all.subList(0, count));
        Collections.sort(picked);
        return picked;
    }
}
