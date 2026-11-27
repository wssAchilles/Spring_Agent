package tech.qiantong.qknow.module.ext;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.database.DataSourceFactory;
import tech.qiantong.qknow.common.database.DbQuery;
import tech.qiantong.qknow.common.database.constants.DbQueryProperty;
import tech.qiantong.qknow.common.database.core.DbColumn;
import tech.qiantong.qknow.module.dm.api.datasource.dto.DatasourceCreaTeTableReqDTO;
import tech.qiantong.qknow.module.dm.dal.mapper.datasource.DmDatasourceMapper;
import tech.qiantong.qknow.module.dm.service.datasource.impl.DmDatasourceServiceImpl;
import tech.qiantong.qknow.module.ext.dal.dataobject.extraction.ExtExtractionDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extRelationMapping.ExtRelationMappingDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extSchema.ExtSchemaDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extSchemaMapping.ExtSchemaMappingDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extStructTask.ExtStructTaskDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extUnstructTask.ExtUnstructTaskDO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extUnstructTaskText.ExtUnstructTaskTextDO;
import tech.qiantong.qknow.module.ext.dal.mapper.extUnstructTask.ExtUnstructTaskMapper;
import tech.qiantong.qknow.module.ext.dal.mapper.extUnstructTaskText.ExtUnstructTaskTextMapper;
import tech.qiantong.qknow.module.ext.service.extSchema.IExtSchemaService;
import tech.qiantong.qknow.module.ext.service.extStructTask.impl.ExtStructTaskServiceImpl;
import tech.qiantong.qknow.module.ext.service.extUnstructTask.impl.ExtUnstructTaskServiceImpl;
import tech.qiantong.qknow.module.ext.service.neo4j.service.ExtNeo4jService;
import tech.qiantong.qknow.module.kg.controller.admin.graph.KgGraphController;
import tech.qiantong.qknow.module.kg.service.GraphCommunityService;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.DeepSeekR1JudgeEngine;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.JudgementResult;
import tech.qiantong.qknow.neo4j.domain.DynamicEntity;
import tech.qiantong.qknow.neo4j.repository.DynamicRepository;
import tech.qiantong.qknow.neo4j.wrapper.Neo4jQueryWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 战役二（2.5 ~ 2.6）：知识抽取（Ext）、数据源管理（Dm）、知识图谱（Kg）与裁判引擎回归测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeEngineeringBugFixTest {

    @Test
    @DisplayName("ExtUnstructTaskServiceImpl: cover2GraphProperties 缺少 kgDictMap 时仍必须提取实体属性")
    @SuppressWarnings("unchecked")
    void cover2GraphProperties_extractsAttributesWithoutKgDictMap() throws Exception {
        ExtUnstructTaskServiceImpl service = new ExtUnstructTaskServiceImpl();

        JSONObject entity = new JSONObject();
        entity.put("实体类型", "企业");
        JSONObject attrs = new JSONObject();
        attrs.put("成立年份", "2020");
        attrs.put("总部城市", "杭州");
        entity.put("属性", attrs);

        JSONObject attrDef1 = new JSONObject();
        attrDef1.put("id", 101L);
        attrDef1.put("name", "成立年份");
        JSONObject attrDef2 = new JSONObject();
        attrDef2.put("id", 102L);
        attrDef2.put("name", "总部城市");
        JSONArray enterpriseAttrs = new JSONArray(List.of(attrDef1, attrDef2));
        JSONObject attrListObj = new JSONObject();
        attrListObj.put("企业", enterpriseAttrs);

        Map<String, String> callWord = new HashMap<>();
        callWord.put("entityAttributeList", attrListObj.toJSONString());
        // 真实 attributeCallWord 从不设置 kgDictMap

        Method method = ExtUnstructTaskServiceImpl.class.getDeclaredMethod("cover2GraphProperties", JSONObject.class, Map.class);
        method.setAccessible(true);
        Map<String, Object> props = (Map<String, Object>) method.invoke(service, entity, callWord);

        assertFalse(props.isEmpty(), "cover2GraphProperties 不得因 kgDictMap 为空丢弃全部实体属性");
        assertEquals("2020", props.get("attribute_id_101"));
        assertEquals("杭州", props.get("attribute_id_102"));
    }

    @Test
    @DisplayName("ExtUnstructTaskServiceImpl: processingAndSave 必须写入真实段落下标而非误写入 docId")
    @SuppressWarnings("unchecked")
    void processingAndSave_setsActualParagraphIndexInsteadOfDocId() throws Exception {
        ExtUnstructTaskServiceImpl service = new ExtUnstructTaskServiceImpl();
        ExtUnstructTaskTextMapper textMapper = mock(ExtUnstructTaskTextMapper.class);
        ExtNeo4jService extNeo4jService = mock(ExtNeo4jService.class);
        ExtUnstructTaskMapper taskMapper = mock(ExtUnstructTaskMapper.class);
        IExtSchemaService schemaService = mock(IExtSchemaService.class);

        ReflectionTestUtils.setField(service, "extUnstructTaskTextMapper", textMapper);
        ReflectionTestUtils.setField(service, "extNeo4jService", extNeo4jService);
        ReflectionTestUtils.setField(service, "extUnstructTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "extSchemaService", schemaService);

        ExtUnstructTaskTextDO textDO = new ExtUnstructTaskTextDO();
        textDO.setId(5001L);
        textDO.setDocId(999L);
        textDO.setText("阿里巴巴集团总部位于杭州。");

        Map<String, List<ExtUnstructTaskTextDO>> textMap = new HashMap<>();
        textMap.put("阿里巴巴", List.of(textDO));

        DynamicEntity entity = new DynamicEntity();
        entity.setName("阿里巴巴");
        Map<String, Object> dynProps = new HashMap<>();
        dynProps.put("doc_id", 999);
        dynProps.put("workspace_id", "1");
        entity.setDynamicProperties(dynProps);

        ExtSchemaDO schemaDO = new ExtSchemaDO();
        schemaDO.setId(10L);
        schemaDO.setName("企业");

        ExtUnstructTaskDO taskDO = new ExtUnstructTaskDO();
        taskDO.setId(77L);

        Map<String, String> typeMap = Map.of("阿里巴巴", "企业");

        Method method = ExtUnstructTaskServiceImpl.class.getDeclaredMethod(
                "processingAndSave", Map.class, List.class, List.class, ExtUnstructTaskDO.class, Map.class);
        method.setAccessible(true);
        method.invoke(service, textMap, List.of(), List.of(entity), taskDO, typeMap);

        ArgumentCaptor<List<ExtExtractionDO>> captor = ArgumentCaptor.forClass(List.class);
        verify(extNeo4jService).insertExtractionList(captor.capture());
        List<ExtExtractionDO> inserted = captor.getValue();
        assertEquals(1, inserted.size());
        assertEquals(999, inserted.get(0).getDocId());
        assertEquals(1, inserted.get(0).getParagraphIndex(),
                "paragraphIndex 必须等于段落真实序号 1，不得误设为 docId 999");
    }

    @Test
    @DisplayName("ExtStructTaskServiceImpl: loadExistingEntities 必须按 task_id 条件查询且 buildRelationSQL 拦截注入标识符")
    @SuppressWarnings("unchecked")
    void extStructTask_usesScopedQueryAndRejectsSqlInjectionIdentifiers() throws Exception {
        ExtStructTaskServiceImpl service = new ExtStructTaskServiceImpl();
        DynamicRepository dynamicRepository = mock(DynamicRepository.class);
        ReflectionTestUtils.setField(service, "dynamicRepository", dynamicRepository);

        DynamicEntity existing = new DynamicEntity();
        Map<String, Object> props = new HashMap<>();
        props.put("task_id", 42L);
        props.put("table_name", "t_user");
        props.put("name", "Alice");
        existing.setDynamicProperties(props);
        lenient().when(dynamicRepository.find(any(Neo4jQueryWrapper.class))).thenReturn(List.of(existing));

        ExtStructTaskDO task = new ExtStructTaskDO();
        task.setId(42L);

        Method loadMethod = ExtStructTaskServiceImpl.class.getDeclaredMethod("loadExistingEntities", ExtStructTaskDO.class);
        loadMethod.setAccessible(true);
        Map<String, DynamicEntity> map = (Map<String, DynamicEntity>) loadMethod.invoke(service, task);

        verify(dynamicRepository, never()).findAll();
        verify(dynamicRepository, times(1)).find(any(Neo4jQueryWrapper.class));
        assertEquals(1, map.size());

        // 验证 buildRelationSQL 拦截含 SQL 注入载荷的非法表名/字段名
        DbQuery dbQuery = mock(DbQuery.class);
        DbColumn col = new DbColumn();
        col.setColName("id");
        lenient().when(dbQuery.getTableColumns(any(DbQueryProperty.class), anyString())).thenReturn(List.of(col));

        ExtSchemaMappingDO schema = new ExtSchemaMappingDO();
        schema.setTableName("users; DROP TABLE sys_user --");
        ExtRelationMappingDO rel = new ExtRelationMappingDO();
        rel.setId(1L);
        rel.setFieldName("dept_id");
        rel.setRelationTable("dept");
        rel.setRelationField("id");

        Method sqlMethod = ExtStructTaskServiceImpl.class.getDeclaredMethod(
                "buildRelationSQL", ExtSchemaMappingDO.class, ExtRelationMappingDO.class,
                DbQuery.class, DbQueryProperty.class, Map.class);
        sqlMethod.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> sqlMethod.invoke(service, schema, rel, dbQuery, mock(DbQueryProperty.class), Map.of()));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @DisplayName("DmDatasourceServiceImpl: 修复 finally 空指针、建表连接池泄漏与 getDatasourceById 空对象崩溃")
    void dmDatasourceService_fixesFinallyNpeConnectionLeakAndNullDatasource() {
        DmDatasourceServiceImpl service = new DmDatasourceServiceImpl();
        DmDatasourceMapper mapper = mock(DmDatasourceMapper.class);
        DataSourceFactory factory = mock(DataSourceFactory.class);
        DbQuery dbQuery = mock(DbQuery.class);
        ReflectionTestUtils.setField(service, "dmDatasourceMapper", mapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "dataSourceFactory", factory);

        // 1. 数据源不存在时 getDatasourceById 应安全返回 null，不抛 IllegalArgumentException
        when(mapper.selectById(404L)).thenReturn(null);
        assertNull(service.getDatasourceById(404L), "数据源不存在时 getDatasourceById 应返回 null");

        // 2. getDbTables 在数据源不存在（dbQuery == null）时应抛业务异常而非 finally NullPointerException
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getDbTables(404L));
        assertFalse(ex instanceof NullPointerException, "finally 块不得因 dbQuery 为 null 抛出 NullPointerException");

        // 3. creaDatasourceTeTable 执行完毕后必须调用 dbQuery.close() 释放连接池
        when(factory.createDbQuery(any())).thenReturn(dbQuery);
        when(dbQuery.valid()).thenReturn(true);
        when(dbQuery.generateCheckTableExistsSQL(any(), any())).thenReturn(1);

        DatasourceCreaTeTableReqDTO req = new DatasourceCreaTeTableReqDTO();
        req.setDatasourceType("MySql");
        req.setIp("127.0.0.1");
        req.setPort(3306L);
        req.setDatasourceConfig("{\"dbname\":\"test\",\"username\":\"root\",\"password\":\"pwd\"}");
        req.setTableName("t_demo");

        assertFalse(service.creaDatasourceTeTable(req));
        verify(dbQuery, times(1)).close();
    }

    @Test
    @DisplayName("KgGraphController & GraphCommunityService: 权限注解齐备且拦截恶意 workspaceId Cypher 注入")
    void kgGraphAndCommunity_enforcesAuthAndBlocksCypherInjection() throws Exception {
        Method getGraphData = KgGraphController.class.getMethod("getGraphData");
        assertNotNull(getGraphData.getAnnotation(PreAuthorize.class),
                "KgGraphController.getGraphData 必须声明 @PreAuthorize 权限门禁");

        GraphCommunityService communityService = new GraphCommunityService(mock(Driver.class), null);
        assertThrows(IllegalArgumentException.class,
                () -> communityService.detectCommunities("1' ); MATCH (n) DETACH DELETE n //"),
                "GraphCommunityService 必须拦截含引号与语句分隔的恶意 workspaceId");
    }

    @Test
    @DisplayName("DeepSeekR1JudgeEngine: 支持优先解析官方独立 reasoning_content 字段并兼容 <think> 标签")
    void deepSeekJudgeEngine_prefersOfficialReasoningContentOverLegacyThinkTag() throws Exception {
        DeepSeekR1JudgeEngine engine = new DeepSeekR1JudgeEngine();
        Method overloaded = null;
        for (Method m : DeepSeekR1JudgeEngine.class.getMethods()) {
            if ("parseJudgeResponse".equals(m.getName()) && m.getParameterCount() == 2) {
                overloaded = m;
                break;
            }
        }
        assertNotNull(overloaded, "DeepSeekR1JudgeEngine 必须提供双参 parseJudgeResponse(rawResponse, reasoningContent)");

        JudgementResult result = (JudgementResult) overloaded.invoke(
                engine,
                "{\"winner\": \"A\", \"confidence\": 0.92, \"rationale\": \"事实准确\"}",
                "逐步核对候选 A 与候选 B 的知识库溯源引用..."
        );
        assertEquals("A", result.getWinner());
        assertEquals("逐步核对候选 A 与候选 B 的知识库溯源引用...", result.getThinking());
    }
}
