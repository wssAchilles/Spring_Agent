package tech.qiantong.qknow.ai.dataagent.model;

import java.io.Serializable;
import java.util.*;

/**
 * 数据库表/图模式元数据卡片
 * 封装表名、表中文注释、字段列表、主外键依赖关系、高基数列真实样本值以及千问 1536 维超球面向量
 */
public class SchemaCard implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据源标识 */
    private String datasourceId;
    /** 表名 (Table Name / Vertex Label) */
    private String tableName;
    /** 表业务注释 */
    private String tableComment;
    /** 字段元数据字典: 列名 -> 类型及注释 */
    private Map<String, String> columns = new LinkedHashMap<>();
    /** 主键字段集合 */
    private Set<String> primaryKeys = new LinkedHashSet<>();
    /** 外键映射: 本表列 -> 引用目标表.列 */
    private Map<String, String> foreignKeys = new LinkedHashMap<>();
    /** 高基数或枚举字段代表性样本值: 列名 -> 样本取值列表 */
    private Map<String, List<String>> sampleValues = new LinkedHashMap<>();
    /** 阿里千问 1536 维超球面归一化嵌入向量 */
    private float[] embedding;

    public SchemaCard() {}

    public SchemaCard(String tableName, String tableComment) {
        this.tableName = tableName;
        this.tableComment = tableComment;
    }

    public SchemaCard(String tableName, String tableComment, Map<String, String> columns,
                      Map<String, String> foreignKeys, List<String> sampleValues, float[] embedding) {
        this.tableName = tableName;
        this.tableComment = tableComment;
        if (columns != null) this.columns.putAll(columns);
        if (foreignKeys != null) this.foreignKeys.putAll(foreignKeys);
        if (sampleValues != null && !sampleValues.isEmpty()) {
            this.sampleValues.put("default_samples", sampleValues);
        }
        this.embedding = embedding;
    }

    public void addColumn(String columnName, String columnTypeAndComment) {
        this.columns.put(columnName, columnTypeAndComment);
    }

    public void addPrimaryKey(String columnName) {
        this.primaryKeys.add(columnName);
    }

    public void addForeignKey(String localColumn, String targetTableAndColumn) {
        this.foreignKeys.put(localColumn, targetTableAndColumn);
    }

    public void addSampleValues(String columnName, List<String> samples) {
        this.sampleValues.put(columnName, samples);
    }

    /**
     * 生成供向量化与 Prompt 组装的紧凑自然语言文本描述
     */
    public String toCompactDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(tableName);
        if (tableComment != null && !tableComment.isBlank()) {
            sb.append(" (").append(tableComment).append(")");
        }
        sb.append("\nColumns:\n");
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            sb.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue());
            if (primaryKeys.contains(entry.getKey())) {
                sb.append(" [PRIMARY KEY]");
            }
            if (foreignKeys.containsKey(entry.getKey())) {
                sb.append(" [FK -> ").append(foreignKeys.get(entry.getKey())).append("]");
            }
            if (sampleValues.containsKey(entry.getKey())) {
                sb.append(" [Samples: ").append(String.join(", ", sampleValues.get(entry.getKey()))).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- Getter & Setter ---
    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String datasourceId) { this.datasourceId = datasourceId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public Map<String, String> getColumns() { return columns; }
    public void setColumns(Map<String, String> columns) { this.columns = columns; }
    public Set<String> getPrimaryKeys() { return primaryKeys; }
    public void setPrimaryKeys(Set<String> primaryKeys) { this.primaryKeys = primaryKeys; }
    public Map<String, String> getForeignKeys() { return foreignKeys; }
    public void setForeignKeys(Map<String, String> foreignKeys) { this.foreignKeys = foreignKeys; }
    public Map<String, List<String>> getSampleValues() { return sampleValues; }
    public void setSampleValues(Map<String, List<String>> sampleValues) { this.sampleValues = sampleValues; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}
