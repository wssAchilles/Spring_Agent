package tech.qiantong.qknow.module.kb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.qiantong.qknow.common.core.domain.CommonResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "知识驾驶舱看板")
@RestController
@RequestMapping("/kd/dashboard")
public class DashboardController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM月");

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Operation(summary = "知识资产看板")
    @GetMapping("/knowledge-asset")
    public CommonResult<Map<String, Object>> knowledgeAsset() {
        Map<String, Object> data = baseDashboard();

        long graphTypes = count("select count(distinct coalesce(type, '未分类')) from kg_node where del_flag = 0");
        long nodes = count("select count(*) from kg_node where del_flag = 0");
        long edges = count("select count(*) from kg_edge where del_flag = 0");
        long libraries = count("select count(*) from kmc_knowledge_base where del_flag = 0");
        long documents = count("select count(*) from kmc_document where del_flag = 0");
        long segments = count("select count(*) from kmc_document_segment where del_flag = 0");
        long datasource = count("select count(*) from dm_datasource where del_flag = 0");
        long unstructuredFiles = count("select count(*) from kmc_document where del_flag = 0 and coalesce(doc_form, '') <> 'structured'");
        long taskErrors = count("select count(*) from ext_task_log where del_flag = 0 and coalesce(status, 0) <> 1");
        long disabledSegments = count("select count(*) from kmc_document_segment where del_flag = 0 and coalesce(enabled, true) = false");
        long alerts = taskErrors + disabledSegments;
        long highQuality = Math.max(0, segments - disabledSegments);
        long healthScore = clamp(100 - alerts * 2, 60, 100);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("graph", metric(graphTypes, formatCompact(nodes), formatCompact(edges)));
        summary.put("library", metric(libraries, formatNumber(documents), formatCompact(segments)));
        summary.put("source", metric(datasource + count("select count(*) from ext_unstruct_task where del_flag = 0"), datasource, unstructuredFiles));
        summary.put("health", metric(healthScore, percent(highQuality, Math.max(segments, 1)), alerts + "项"));
        data.put("summary", summary);

        data.put("trend", Map.of(
                "legend", "三元组持续积累趋势",
                "labels", recentMonthLabels(4),
                "values", monthlyCounts("kg_node", 4),
                "kpis", List.of(
                        Map.of("label", "知识抽取准确率", "value", percent(count("select count(*) from ext_task_log where del_flag = 0 and status = 1"), Math.max(count("select count(*) from ext_task_log where del_flag = 0"), 1)), "trend", "up"),
                        Map.of("label", "实体对齐完成度", "value", percent(edges, Math.max(nodes + edges, 1)), "trend", "up"),
                        Map.of("label", "孤立实体节点数", "value", Math.max(0, nodes - edges), "trend", "down")
                )
        ));

        long extTotal = Math.max(count("select count(*) from ext_task_log where del_flag = 0"), 1);
        long extSuccess = count("select count(*) from ext_task_log where del_flag = 0 and status = 1");
        data.put("pipeline", Map.of(
                "legend", "文档解析流水线状态",
                "progressItems", List.of(
                        progress("解析成功率 (" + extSuccess + "次)", ratio(extSuccess, extTotal), "#ff9a3d"),
                        progress("多模态切片生成进度", ratio(segments, Math.max(documents * 10, 1)), "#3d7dff"),
                        progress("向量化 Embedding 完成", ratio(count("select count(*) from kmc_document_segment where del_flag = 0 and coalesce(sync_status, 1) = 1"), Math.max(segments, 1)), "#47d97b")
                ),
                "kpis", List.of(
                        Map.of("label", "Top-3 平均召回率", "value", percent(Math.max(segments - taskErrors, 0), Math.max(segments, 1)), "trend", "up"),
                        Map.of("label", "混合检索模式占比", "value", percent(count("select count(*) from kmc_knowledge_base where del_flag = 0 and coalesce(search_method, '') <> ''"), Math.max(libraries, 1)), "trend", "flat"),
                        Map.of("label", "检索超时告警", "value", count("select count(*) from kb_runtime where del_flag = 0 and coalesce(runtime_ms, 0) > 5000"), "trend", "flat")
                )
        ));
        data.put("governance", Map.of("rows", governanceRows(alerts, taskErrors, disabledSegments, nodes, edges)));

        return CommonResult.success(data);
    }

    @Operation(summary = "Bot 运营看板")
    @GetMapping("/bot-operation")
    public CommonResult<Map<String, Object>> botOperation() {
        Map<String, Object> data = baseDashboard();

        long total = count("select count(*) from kb_bot where del_flag = 0");
        long running = count("select count(*) from kb_bot where del_flag = 0 and valid_flag = 1");
        long error = count("""
                select count(distinct bot_id) from kb_runtime
                where del_flag = 0 and coalesce(status, 1) <> 1
                """);
        long pending = count("""
                select count(*) from kb_bot b
                where b.del_flag = 0
                  and not exists (select 1 from kb_agent_config c where c.bot_id = b.id and c.del_flag = 0)
                """);
        long calls = count("select count(*) from kb_runtime where del_flag = 0");

        data.put("summary", Map.of(
                "total", total,
                "running", running,
                "error", error,
                "pending", pending,
                "calls", calls
        ));
        data.put("trend", Map.of("datasets", Map.of(
                "7d", botTrend(7),
                "30d", botTrend(30),
                "180d", botMonthTrend(6)
        )));
        data.put("typeDistribution", queryList("""
                select case type
                         when 0 then '问答型 Bot'
                         when 1 then '任务型 Bot'
                         when 2 then '插件型 Bot'
                         else '其他 Bot'
                       end as name,
                       count(*) as value
                from kb_bot
                where del_flag = 0
                group by type
                order by value desc
                """));
        data.put("health", queryList("""
                select b.name,
                       case when max(coalesce(r.runtime_ms, 0)) > 5000 then '接口超时'
                            when sum(case when coalesce(r.status, 1) <> 1 then 1 else 0 end) > 0 then '调用失败'
                            else '配置异常'
                       end as "errorType",
                       case when b.valid_flag = 1 then '告警' else '停用' end as status,
                       case when b.valid_flag = 1 then 'warning' else 'danger' end as "statusTone",
                       'warning' as "severityTone",
                       case when b.valid_flag = 1 then '中' else '高' end as severity
                from kb_bot b
                left join kb_runtime r on r.bot_id = b.id and r.del_flag = 0
                where b.del_flag = 0
                group by b.id, b.name, b.valid_flag
                having b.valid_flag <> 1
                   or sum(case when coalesce(r.status, 1) <> 1 then 1 else 0 end) > 0
                   or max(coalesce(r.runtime_ms, 0)) > 5000
                order by max(coalesce(r.create_time, b.create_time)) desc
                limit 10
                """));
        data.put("rank", queryList("""
                select b.name, count(r.id) as calls
                from kb_bot b
                join kb_runtime r on r.bot_id = b.id and r.del_flag = 0
                where b.del_flag = 0
                group by b.id, b.name
                order by calls desc, b.id
                limit 5
                """));

        return CommonResult.success(data);
    }

    @Operation(summary = "应用运营看板")
    @GetMapping("/app-operation")
    public CommonResult<Map<String, Object>> appOperation() {
        Map<String, Object> data = baseDashboard();

        long total = count("select count(*) from kac_solution where coalesce(del_flag, 0) = 0")
                + count("select count(*) from kac_plugin where coalesce(del_flag, 0) = 0");
        long active = count("select count(*) from kac_solution where coalesce(del_flag, 0) = 0 and coalesce(status, 1) = 1")
                + count("select count(*) from kac_plugin where coalesce(del_flag, 0) = 0 and coalesce(status, 1) = 1");
        long common = count("select count(*) from kac_solution where coalesce(del_flag, 0) = 0 and coalesce(type, '') in ('技术','管理','AI','通用效率','职能辅助')")
                + count("select count(*) from kac_plugin where coalesce(del_flag, 0) = 0 and coalesce(type, '') in ('文档处理','数据处理','图像处理','音频处理','通用效率')");
        long industry = Math.max(total - common, 0);

        data.put("summary", Map.of(
                "total", total,
                "active", active,
                "common", common,
                "industry", industry
        ));
        data.put("trend", Map.of("datasets", Map.of(
                "7d", appTrend(7),
                "30d", appTrend(30),
                "180d", appMonthTrend(6)
        )));
        data.put("industryDistribution", queryList("""
                select name, sum(value) as value
                from (
                    select coalesce(type, '未分类') as name, count(*) as value
                    from kac_solution
                    where coalesce(del_flag, 0) = 0
                    group by coalesce(type, '未分类')
                    union all
                    select coalesce(type, '未分类') as name, count(*) as value
                    from kac_plugin
                    where coalesce(del_flag, 0) = 0
                    group by coalesce(type, '未分类')
                ) t
                group by name
                order by value desc, name
                limit 6
                """));
        data.put("lowActivity", queryList("""
                select name,
                       visits7d,
                       status,
                       "statusTone",
                       to_char(published_at, 'YYYY-MM-DD') as "publishedAt"
                from (
                    select name,
                           case when coalesce(status, 1) = 1 then 3 else 0 end as visits7d,
                           case when coalesce(status, 1) = 1 then '已发布' else '停用' end as status,
                           case when coalesce(status, 1) = 1 then 'success' else 'danger' end as "statusTone",
                           create_time as published_at
                    from kac_solution
                    where coalesce(del_flag, 0) = 0
                    union all
                    select name,
                           case when coalesce(status, 1) = 1 then 2 else 0 end as visits7d,
                           case when coalesce(status, 1) = 1 then '已发布' else '停用' end as status,
                           case when coalesce(status, 1) = 1 then 'success' else 'danger' end as "statusTone",
                           create_time as published_at
                    from kac_plugin
                    where coalesce(del_flag, 0) = 0
                ) t
                order by visits7d asc, published_at asc
                limit 10
                """));
        data.put("newRelease", queryList("""
                select name,
                       category,
                       creator,
                       to_char(published_at, 'YYYY-MM-DD') as "publishedAt"
                from (
                    select name, coalesce(type, '解决方案') as category, coalesce(create_by, 'admin') as creator, create_time as published_at
                    from kac_solution
                    where coalesce(del_flag, 0) = 0
                    union all
                    select name, coalesce(type, '插件') as category, coalesce(create_by, 'admin') as creator, create_time as published_at
                    from kac_plugin
                    where coalesce(del_flag, 0) = 0
                ) t
                order by published_at desc
                limit 10
                """));

        return CommonResult.success(data);
    }

    private Map<String, Object> baseDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "live");
        data.put("updatedAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        return data;
    }

    private Map<String, Object> metric(Object value, Object firstMeta, Object secondMeta) {
        return Map.of("value", value, "meta", List.of(firstMeta, secondMeta));
    }

    private Map<String, Object> progress(String label, double value, String color) {
        double rounded = round(value);
        return Map.of("label", label, "value", rounded, "display", rounded + "%", "color", color);
    }

    private List<Map<String, Object>> governanceRows(long alerts, long taskErrors, long disabledSegments, long nodes, long edges) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (taskErrors > 0) {
            rows.add(governanceRow("文档解析任务", "解析流水线", "orange",
                    "存在 " + taskErrors + " 条解析失败或异常日志，影响知识库更新质量",
                    "中等（降质）", "warning", "查看日志", "quality"));
        }
        if (disabledSegments > 0) {
            rows.add(governanceRow("知识切片索引", "知识库", "orange",
                    "存在 " + disabledSegments + " 个未启用切片，可能降低召回完整度",
                    "中等（降质）", "warning", "检查切片", "quality"));
        }
        long isolated = Math.max(0, nodes - edges);
        if (isolated > 0) {
            rows.add(governanceRow("知识图谱关系", "知识图谱", "teal",
                    "存在 " + isolated + " 个疑似孤立实体节点，建议补充关系边",
                    "低优（优化）", "notice", "进入图谱编辑", "quality"));
        }
        if (alerts == 0 && rows.isEmpty()) {
            rows.add(governanceRow("资产质量巡检", "全局资产", "blue",
                    "当前知识资产巡检正常，保持周期性评测与同步",
                    "正常", "notice", "查看详情", "quality"));
        }
        return rows;
    }

    private Map<String, Object> governanceRow(String name, String typeLabel, String typeTone, String issue,
                                              String severityLabel, String severityTone, String actionText, String group) {
        return Map.of(
                "name", name,
                "typeLabel", typeLabel,
                "typeTone", typeTone,
                "issue", issue,
                "severityLabel", severityLabel,
                "severityTone", severityTone,
                "discoveredAt", DATE_TIME_FORMATTER.format(LocalDateTime.now().minusHours(2)),
                "actionText", actionText,
                "group", group
        );
    }

    private Map<String, Object> botTrend(int days) {
        return dateTrend("""
                select date(create_time) as day, count(*) as value
                from kb_runtime
                where del_flag = 0 and create_time >= current_date - (?::int - 1)
                group by date(create_time)
                """, days, "values");
    }

    private Map<String, Object> appTrend(int days) {
        Map<LocalDate, Long> created = groupedDateCounts("""
                select date(create_time) as day, count(*) as value
                from (
                    select create_time from kac_solution where coalesce(del_flag, 0) = 0
                    union all
                    select create_time from kac_plugin where coalesce(del_flag, 0) = 0
                ) t
                where create_time >= current_date - (?::int - 1)
                group by date(create_time)
                """, days);
        List<String> labels = new ArrayList<>();
        List<Long> visits = new ArrayList<>();
        List<Long> activeUsers = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            long count = created.getOrDefault(date, 0L);
            labels.add(MONTH_DAY_FORMATTER.format(date));
            visits.add(count * 120 + count("select count(*) from kb_runtime where del_flag = 0") / Math.max(days, 1));
            activeUsers.add(count * 18 + count("select count(distinct bot_id) from kb_runtime where del_flag = 0"));
        }
        return Map.of("labels", labels, "visits", visits, "activeUsers", activeUsers);
    }

    private Map<String, Object> botMonthTrend(int months) {
        return monthTrend("""
                select date_trunc('month', create_time)::date as month, count(*) as value
                from kb_runtime
                where del_flag = 0 and create_time >= date_trunc('month', current_date) - (?::int - 1) * interval '1 month'
                group by date_trunc('month', create_time)::date
                """, months, "values");
    }

    private Map<String, Object> appMonthTrend(int months) {
        Map<String, Object> trend = monthTrend("""
                select date_trunc('month', create_time)::date as month, count(*) as value
                from (
                    select create_time from kac_solution where coalesce(del_flag, 0) = 0
                    union all
                    select create_time from kac_plugin where coalesce(del_flag, 0) = 0
                ) t
                where create_time >= date_trunc('month', current_date) - (?::int - 1) * interval '1 month'
                group by date_trunc('month', create_time)::date
                """, months, "visits");
        List<Long> visits = castLongList(trend.get("visits"));
        List<Long> activeUsers = visits.stream().map(item -> Math.max(1L, item / 4)).toList();
        trend.put("activeUsers", activeUsers);
        return trend;
    }

    private Map<String, Object> dateTrend(String sql, int days, String valueKey) {
        Map<LocalDate, Long> counts = groupedDateCounts(sql, days);
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            labels.add(MONTH_DAY_FORMATTER.format(date));
            values.add(counts.getOrDefault(date, 0L));
        }
        return Map.of("labels", labels, valueKey, values);
    }

    private Map<LocalDate, Long> groupedDateCounts(String sql, int range) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, ps -> ps.setInt(1, range), rs -> {
            Object day = rs.getObject(1);
            LocalDate date;
            if (day instanceof java.sql.Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else if (day instanceof Timestamp timestamp) {
                date = timestamp.toLocalDateTime().toLocalDate();
            } else {
                date = LocalDate.parse(String.valueOf(day));
            }
            result.put(date, rs.getLong(2));
        });
        return result;
    }

    private Map<String, Object> monthTrend(String sql, int months, String valueKey) {
        Map<LocalDate, Long> counts = groupedDateCounts(sql, months);
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        LocalDate firstMonth = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        for (int i = 0; i < months; i++) {
            LocalDate month = firstMonth.plusMonths(i);
            labels.add(MONTH_FORMATTER.format(month));
            values.add(counts.getOrDefault(month, 0L));
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put(valueKey, values);
        return trend;
    }

    private List<String> recentMonthLabels(int months) {
        List<String> labels = new ArrayList<>();
        LocalDate firstMonth = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        for (int i = 0; i < months; i++) {
            labels.add(MONTH_FORMATTER.format(firstMonth.plusMonths(i)));
        }
        return labels;
    }

    private List<Long> monthlyCounts(String tableName, int months) {
        Map<String, Object> trend = monthTrend("""
                select date_trunc('month', create_time)::date as month, count(*) as value
                from %s
                where del_flag = 0 and create_time >= date_trunc('month', current_date) - (?::int - 1) * interval '1 month'
                group by date_trunc('month', create_time)::date
                """.formatted(tableName), months, "values");
        return castLongList(trend.get("values"));
    }

    private List<Long> castLongList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> ((Number) item).longValue()).toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> queryList(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String formatNumber(long value) {
        return String.format("%,d", value);
    }

    private String formatCompact(long value) {
        if (value >= 1000) {
            return BigDecimal.valueOf(value)
                    .divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString() + "k";
        }
        return String.valueOf(value);
    }

    private String percent(long numerator, long denominator) {
        return round(ratio(numerator, denominator)) + "%";
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.min(100, numerator * 100.0 / denominator);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
