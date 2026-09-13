package tech.qiantong.qknow.ai.transformer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StructureAwareMarkdownSplitter 结构感知 Markdown 分块器测试")
class StructureAwareMarkdownSplitterTest {

    @Test
    @DisplayName("多级标题面包屑路径自动注入")
    void testBreadcrumbInjection() {
        String md = """
# 基础架构
## 存储引擎
### 向量索引
本节详细介绍高维向量索引在分布式场景下的构建与维护策略。
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(300, 20, true);
        List<Document> docs = splitter.apply(List.of(new Document(md)));

        assertFalse(docs.isEmpty(), "分块结果不应为空");
        Document chunk = docs.get(0);
        assertTrue(chunk.getText().contains("【基础架构 > 存储引擎 > 向量索引】"), "切片首部应包含标题面包屑路径");
        assertTrue(chunk.getText().contains("本节详细介绍高维向量索引"), "切片应包含正文内容");

        @SuppressWarnings("unchecked")
        List<String> breadcrumb = (List<String>) chunk.getMetadata().get("breadcrumb");
        assertNotNull(breadcrumb, "元数据中应包含 breadcrumb");
        assertEquals(List.of("基础架构", "存储引擎", "向量索引"), breadcrumb);
    }

    @Test
    @DisplayName("小表格整块原子保护")
    void testTablePreservationSmallTable() {
        String md = """
# 数据报表
| 指标名称 | 当前值 | 阈值 | 状态 |
| :--- | :--- | :--- | :--- |
| CPU使用率 | 45% | 80% | 正常 |
| 内存占用 | 62% | 85% | 正常 |
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(500, 0, false);
        List<Document> docs = splitter.apply(List.of(new Document(md)));

        assertEquals(1, docs.size(), "小型表格应作为原子块不被切断");
        assertTrue(docs.get(0).getText().contains("| 指标名称 | 当前值 | 阈值 | 状态 |"));
        assertTrue(docs.get(0).getText().contains("| 内存占用 | 62% | 85% | 正常 |"));
    }

    @Test
    @DisplayName("超长表格行级切片并自动复制表头 (Header Propagation)")
    void testTablePropagationLargeTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 资产清单\n");
        sb.append("| 资产ID | 资产名称 | 规格型号 | 所属部门 | 采购年份 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format("| ASSET-%03d | 生产服务器集群节点%d | 2U-64C-256G-NVMe | 基础设施运维部 | 202%d |\n", i, i, i % 5));
        }

        // maxChunkSize 设置为较小值 (250 字符)，强制表格按行拆分
        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(250, 0, false);
        List<Document> docs = splitter.apply(List.of(new Document(sb.toString())));

        assertTrue(docs.size() > 1, "超长表格应拆分为多个切片");
        String expectedHeader = "| 资产ID | 资产名称 | 规格型号 | 所属部门 | 采购年份 |";
        String expectedDivider = "| :--- | :--- | :--- | :--- | :--- |";

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            assertTrue(doc.getText().contains(expectedHeader), "切片 " + i + " 必须包含完整表头");
            assertTrue(doc.getText().contains(expectedDivider), "切片 " + i + " 必须包含分隔线");
            assertTrue(doc.getText().contains("| ASSET-"), "切片 " + i + " 必须包含数据行");
        }
    }

    @Test
    @DisplayName("三反引号代码块围栏完整性保护")
    void testCodeBlockPreservation() {
        String md = """
# 算法实现
```python
def quick_sort(arr):
    if len(arr) <= 1:
        return arr
    pivot = arr[len(arr) // 2]
    left = [x for x in arr if x < pivot]
    middle = [x for x in arr if x == pivot]
    right = [x for x in arr if x > pivot]
    return quick_sort(left) + middle + quick_sort(right)
```
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(400, 0, false);
        List<Document> docs = splitter.apply(List.of(new Document(md)));

        assertEquals(1, docs.size(), "代码块未超长时应整块保留");
        assertTrue(docs.get(0).getText().startsWith("```python"), "代码块必须以围栏开头");
        assertTrue(docs.get(0).getText().endsWith("```"), "代码块必须以围栏闭合");
    }

    @Test
    @DisplayName("版本号与小数防误切保护 (不被点号截断)")
    void testVersionNumberProtection() {
        String md = """
当前核心系统采用 Spring Boot 3.5.8 框架开发，底层依赖 PostgreSQL 16.2 版本。
内存开销占比控制在 12.5% 以内，网络丢包率低于 0.01%。
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(80, 0, false);
        List<Document> docs = splitter.apply(List.of(new Document(md)));

        // 验证切片中 3.5.8、16.2、12.5%、0.01% 未被断裂
        String allText = docs.stream().map(Document::getText).collect(Collectors.joining(" | "));
        assertTrue(allText.contains("3.5.8"), "版本号 3.5.8 不应被点号截断");
        assertTrue(allText.contains("16.2"), "版本号 16.2 不应被点号截断");
        assertTrue(allText.contains("12.5%"), "百分数 12.5% 不应被截断");
        assertTrue(allText.contains("0.01%"), "小数 0.01% 不应被截断");
    }

    @Test
    @DisplayName("生成 Parent-Child 层次结构切片")
    void testSplitParentChild() {
        String md = """
# 第一章 系统架构
## 1.1 模块职责
Hermes 是智能体任务编排引擎，负责调度 LLM、工具和状态机；
KMC 是知识管理中心，负责知识库文档切分、向量嵌入和混合检索；
Security 模块负责传输层安全加固、SSRF 拦截和接口鉴权认证。
""";

        StructureAwareMarkdownSplitter splitter = new StructureAwareMarkdownSplitter(100, 10, true);
        List<Document> result = splitter.splitParentChild(
                List.of(new Document(md)),
                300, 80, 20, 10
        );

        assertFalse(result.isEmpty());
        List<Document> parents = result.stream()
                .filter(d -> "parent".equals(d.getMetadata().get("chunk_level")))
                .collect(Collectors.toList());
        List<Document> children = result.stream()
                .filter(d -> "child".equals(d.getMetadata().get("chunk_level")))
                .collect(Collectors.toList());

        assertFalse(parents.isEmpty(), "应生成 parent 节点");
        assertFalse(children.isEmpty(), "应生成 child 节点");

        for (Document child : children) {
            String parentId = (String) child.getMetadata().get("parent_segment_id");
            assertNotNull(parentId, "child 必须包含 parent_segment_id");
            assertFalse(parentId.isEmpty(), "parent_segment_id 不能为空");
            boolean parentExists = parents.stream().anyMatch(p -> p.getId().equals(parentId));
            assertTrue(parentExists, "child 引用的 parentId 必须在 parents 集合中存在");
        }
    }
}
