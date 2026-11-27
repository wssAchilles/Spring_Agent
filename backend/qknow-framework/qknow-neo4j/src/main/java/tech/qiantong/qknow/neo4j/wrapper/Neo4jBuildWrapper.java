package tech.qiantong.qknow.neo4j.wrapper;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.qiantong.qknow.neo4j.utils.LambdaUtils;

import java.util.*;

public class Neo4jBuildWrapper<T> {
    @Getter
    private final Class<T> entityClass;

    public Neo4jBuildWrapper(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 校验 Cypher 动态标识符（标签、关系类型、属性名），阻断语法注入
     */
    private static String validateCypherIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || !identifier.matches("^[\\p{L}_][\\p{L}\\p{N}_]*$")) {
            throw new IllegalArgumentException("非法的 Cypher 标识符: " + identifier);
        }
        return identifier;
    }

    /**
     * 转义 Cypher 字符串字面量中的反斜杠与单引号，阻断字符串逃逸注入
     */
    private static String formatCypherLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        String escaped = value.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'");
        return "'" + escaped + "'";
    }

    /**
     * 根据条件修改属性
     *
     * @param paramMap
     * @param updateMap
     * @return
     */
    public String updateQuery(String label, Map<String, Object> paramMap, Map<String, Object> updateMap) {
        String safeLabel = validateCypherIdentifier(label);
        ArrayList<String> arrayList = getUpdateListByMap("n",paramMap);
        StringBuilder query = new StringBuilder("MATCH (n:" + safeLabel + ")");
        if(paramMap.get("id") != null){
            query.append(" WHERE ID(n) = ").append(((Number) paramMap.get("id")).longValue());
        }else if (paramMap.size() > 0) {
            query.append(" WHERE ").append(String.join(" AND ", arrayList));
        }
        if (updateMap.size() > 0) {
            ArrayList<String> listByMap = getUpdateListByMap("n",updateMap);
            query.append(" SET ").append(String.join(",", listByMap));
        }
        return query.toString();
    }

    /**
     * 如果节点存在, 更新此节点的属性, 如果节点不存在, 创建节点和属性
     */
    public String mergeCreateNode(String label, Map<String, Object> mergeMap, Map<String, Object> attributeMap) {
        String safeLabel = validateCypherIdentifier(label);
        String prefixName = LambdaUtils.processCompositeProperties(this.entityClass);

        StringBuilder query;
        if(!mergeMap.isEmpty()){
            ArrayList<String> arrayList = mergeAttByMap(mergeMap);
            query = new StringBuilder("MERGE (n:" + safeLabel + "{ " + String.join(",", arrayList) + "})");
        }else {
            query = new StringBuilder("MATCH (n:" + safeLabel + ")");
        }
        if (attributeMap.size() > 0) {
            query.append(" SET n += {");

            StringBuilder attributes = new StringBuilder();
            for (Map.Entry<String, Object> entry : attributeMap.entrySet()) {
                String key = validateCypherIdentifier(entry.getKey());
                String valueString = formatCypherLiteral(entry.getValue());
                if (attributes.length() > 0) {
                    attributes.append(", ");
                }
                attributes.append(prefixName).append(key).append(": ").append(valueString);
            }

            query.append(attributes);
            query.append("}");
        }
        return query.toString();
    }

    /**
     * 根据起点和结点创建关系 如果a和b不存在, 会创建节点
     */
    public String createRelationship(String label, Map<String, Object> startNodeMap, Map<String, Object> endNodeMap, String rel, Map<String, Object> relMap) {
        String safeLabel = validateCypherIdentifier(label);
        String safeRel = validateCypherIdentifier(rel);
        ArrayList<String> aList = mergeAttByMap(startNodeMap);
        ArrayList<String> bList = mergeAttByMap(endNodeMap);
        ArrayList<String> rList = getUpdateListByMap("r",relMap);
        StringBuilder aquery = new StringBuilder("MERGE (a:" + safeLabel + "{ " + String.join(",", aList) + "})\n");
        StringBuilder bquery = new StringBuilder("MERGE (b:" + safeLabel + "{ " + String.join(",", bList) + "})\n");
        StringBuilder query = aquery.append(bquery).append("MERGE (a)-[r:" + safeRel + "]->(b) \n");
        if(!relMap.isEmpty()){
            query.append("ON CREATE SET " + String.join(",", rList));
        }
        return query.toString();
    }

    /**
     * 根据起点id和结点id创建关系
     */
    public String createRelationship(String label, Long startNodeId, Long endNodeId, String rel, Map<String, Object> relMap) {
        String safeLabel = validateCypherIdentifier(label);
        String safeRel = validateCypherIdentifier(rel);
        Objects.requireNonNull(startNodeId, "startNodeId 不能为空");
        Objects.requireNonNull(endNodeId, "endNodeId 不能为空");
        ArrayList<String> rList = getUpdateListByMap("r",relMap);
        StringBuilder aquery = new StringBuilder("MATCH (a:" + safeLabel + ") WHERE id(a) = " + startNodeId + "\n");
        StringBuilder bquery = new StringBuilder("MATCH (b:" + safeLabel + ") WHERE id(b) = " + endNodeId + "\n");
        StringBuilder query = aquery.append(bquery).append("MERGE (a)-[r:" + safeRel + "]->(b) \n");
        if(!relMap.isEmpty()){
            query.append("ON CREATE SET " + String.join(",", rList));
        }
        return query.toString();
    }

    @NotNull
    private ArrayList<String> mergeAttByMap(Map<String, Object> mergeMap) {
        String prefixName = LambdaUtils.processCompositeProperties(this.entityClass);

        ArrayList<String> arrayList = new ArrayList<>();
        for (Map.Entry<String, Object> objectEntry : mergeMap.entrySet()) {
            String key = validateCypherIdentifier(objectEntry.getKey());
            String valueString = formatCypherLiteral(objectEntry.getValue());
            arrayList.add(prefixName + key + ": " + valueString);
        }
        return arrayList;
    }

    @NotNull
    private ArrayList<String> getUpdateListByMap(String as,Map<String, Object> relMap) {
        String prefixName = LambdaUtils.processCompositeProperties(this.entityClass);
        ArrayList<String> rList = new ArrayList<>();
        for (Map.Entry<String, Object> objectEntry : relMap.entrySet()) {
            String key = validateCypherIdentifier(objectEntry.getKey());
            String valueString = formatCypherLiteral(objectEntry.getValue());
            rList.add(as + "." + prefixName + key + "= " + valueString);
        }
        return rList;
    }

    public String deleteNodeAttribute(Long nodeId,String attributeKey){
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        String safeAttrKey = validateCypherIdentifier(attributeKey);
        String prefixName = LambdaUtils.processCompositeProperties(this.entityClass);

        return "MATCH (n) WHERE id(n) = " + nodeId +
                " REMOVE n." + prefixName + safeAttrKey + " " +
                " RETURN n";
    }


    //TODO 还在优化
    public static Map.Entry<String, Map<String, Object>> mergeCreateNode(
            Collection<String> labels,
            Map<String, Collection<?>> mergeFields,
            Map<String, Object> properties
    ) {
        // 参数映射
        Map<String, Object> params = new HashMap<>();
        StringBuilder query = new StringBuilder();

        // 动态构建 WITH 和 UNWIND 子句
        List<String> mergeConditions = new ArrayList<>();
        if (mergeFields != null && !mergeFields.isEmpty()) {
            for (Map.Entry<String, Collection<?>> entry : mergeFields.entrySet()) {
                String field = entry.getKey();
                Collection<?> values = entry.getValue();

                if (values != null && !values.isEmpty()) {
                    String paramName = field + "Values";
                    query.append("WITH $").append(paramName).append(" AS ").append(field).append("Values\n");
                    query.append("UNWIND ").append(field).append("Values AS ").append(field).append("\n");
                    params.put(paramName, new ArrayList<>(values));
                    mergeConditions.add(field + ": " + field);
                }
            }
        }

        // 构建 MERGE 子句
        query.append("MERGE (n:");
        if (labels != null && !labels.isEmpty()) {
            query.append(String.join(":", labels)); // 多个标签用冒号分隔
        }
        if (!mergeConditions.isEmpty()) {
            query.append(" {").append(String.join(", ", mergeConditions)).append("}");
        }
        query.append(")\n");

        // 构建 SET 子句
        if (properties != null && !properties.isEmpty()) {
            query.append("SET n += $properties\n");
            params.put("properties", properties);
        }

        return new AbstractMap.SimpleEntry<>(query.toString(), params);
    }
}
