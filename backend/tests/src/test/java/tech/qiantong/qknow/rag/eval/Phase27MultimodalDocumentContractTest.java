package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedFigureDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedTableDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedTableDTO.TableCellDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedDocumentResult;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO.ElementType;
import tech.qiantong.qknow.module.kmc.service.sync.parser.gateway.MultimodalParsingGatewayClient;
import tech.qiantong.qknow.module.kmc.service.sync.parser.layout.DocumentLayoutReadingOrderResolver;
import tech.qiantong.qknow.module.kmc.service.sync.parser.splitter.LayoutAwareDocumentSplitter;
import tech.qiantong.qknow.module.kmc.service.sync.parser.splitter.LayoutAwareDocumentSplitter.LayoutChunk;
import tech.qiantong.qknow.module.kmc.service.sync.parser.table.TableGridTopologyReconstructor;
import tech.qiantong.qknow.module.kmc.service.sync.parser.vision.CrossModalAnchorService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 27 专属自动化契约测试
 * 覆盖：
 * 1. 空间几何拓扑排序重构双栏阅读序，0 跨栏横向交替穿插
 * 2. 垂直分栏空白槽 (Vertical Gutter) 探测与防穿透定理边界 (Theorem 1.1)
 * 3. 复合表头与跨行跨列 (rowspan/colspan) 单元格标准 HTML 无损单射还原 (Theorem 2.1)
 * 4. 跨页续表列几何边界豪斯多夫距离校验与表头因果一致性拼接
 * 5. 跨模态图表实体抽取与唯一元数据占位符注入
 * 6. DeepSeek 视觉语义增强摘要对图表问答幻觉发生率的显著抑制 (Theorem 3.1)
 * 7. 标题字号与视觉语义边界驱动层次分块与面包屑注入 (Lemma 4.1)
 * 8. 多语言微服务网关协议流式处理与结构化数据解析
 * 9. 超大恶意矢量 CAD 与单页超高 DPI 触发 OOM 熔断保护
 * 10. 端到端多模态文档解析与富文本切片生产集成全闭环
 */
public class Phase27MultimodalDocumentContractTest {

    private DocumentLayoutReadingOrderResolver readingOrderResolver;
    private TableGridTopologyReconstructor tableReconstructor;
    private CrossModalAnchorService anchorService;
    private LayoutAwareDocumentSplitter splitter;
    private MultimodalParsingGatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        readingOrderResolver = new DocumentLayoutReadingOrderResolver();
        tableReconstructor = new TableGridTopologyReconstructor();
        anchorService = new CrossModalAnchorService();
        splitter = new LayoutAwareDocumentSplitter();
        gatewayClient = new MultimodalParsingGatewayClient(readingOrderResolver, tableReconstructor, anchorService);
    }

    @Test
    @DisplayName("Contract 01: 空间几何拓扑重构引擎准确消歧双栏排版阅读序，0 跨栏横向交替穿插")
    void contract01_readingOrderDAG_correctlyResolvesMultiColumnReadingFlow() {
        double pageWidth = 612.0;
        double pageHeight = 792.0;

        // 构造双栏排版：包含通栏大标题、左栏2段正文、右栏2段正文
        ParsedLayoutElementDTO title = ParsedLayoutElementDTO.builder()
                .id("elem_01")
                .type(ElementType.TITLE)
                .bbox(List.of(54.0, 40.0, 558.0, 80.0)) // 宽度 504 (占宽 82%，通栏)
                .content("面向混合架构的下一代企业级多模态知识中台技术规范")
                .fontSize(24.0)
                .build();

        ParsedLayoutElementDTO left1 = ParsedLayoutElementDTO.builder()
                .id("elem_02")
                .type(ElementType.TEXT)
                .bbox(List.of(54.0, 100.0, 290.0, 250.0)) // 左栏段落 1
                .content("左栏第一段正文...")
                .build();

        ParsedLayoutElementDTO left2 = ParsedLayoutElementDTO.builder()
                .id("elem_03")
                .type(ElementType.TEXT)
                .bbox(List.of(54.0, 260.0, 290.0, 420.0)) // 左栏段落 2
                .content("左栏第二段正文...")
                .build();

        ParsedLayoutElementDTO right1 = ParsedLayoutElementDTO.builder()
                .id("elem_04")
                .type(ElementType.TEXT)
                .bbox(List.of(322.0, 100.0, 558.0, 250.0)) // 右栏段落 1 (与左栏 1 在 Y 轴上高度重叠！)
                .content("右栏第一段正文...")
                .build();

        ParsedLayoutElementDTO right2 = ParsedLayoutElementDTO.builder()
                .id("elem_05")
                .type(ElementType.TEXT)
                .bbox(List.of(322.0, 260.0, 558.0, 420.0)) // 右栏段落 2
                .content("右栏第二段正文...")
                .build();

        // 模拟原始未经排序的乱序列表 (例如右栏被排在前面，或者左右交替)
        List<ParsedLayoutElementDTO> rawElements = List.of(title, right1, left1, right2, left2);

        List<ParsedLayoutElementDTO> sorted = readingOrderResolver.resolveReadingOrder(rawElements, pageWidth, pageHeight);

        // 验证排序结果严格符合人类阅读流：通栏标题 -> 左栏全部 -> 右栏全部
        assertEquals(5, sorted.size(), "排序后元素数量必须保持一致");
        assertEquals("elem_01", sorted.get(0).getId(), "第一位必须为通栏标题");
        assertEquals("elem_02", sorted.get(1).getId(), "第二位必须为左栏第一段");
        assertEquals("elem_03", sorted.get(2).getId(), "第三位必须为左栏第二段");
        assertEquals("elem_04", sorted.get(3).getId(), "第四位必须为右栏第一段");
        assertEquals("elem_05", sorted.get(4).getId(), "第五位必须为右栏第二段");

        // 验证拓扑序号严格单调递增
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i + 1, sorted.get(i).getReadingOrder(), "阅读序号必须连续且从 1 开始");
        }
    }

    @Test
    @DisplayName("Contract 02: 垂直投影空白槽 (Gutter) 准确探测与跨栏防穿透定理边界验证 (Theorem 1.1)")
    void contract02_columnNonCrossing_guaranteesBoundedSeparationBound() {
        double pageWidth = 612.0;

        ParsedLayoutElementDTO left = ParsedLayoutElementDTO.builder()
                .id("left_block")
                .type(ElementType.TEXT)
                .bbox(List.of(54.0, 100.0, 290.0, 400.0))
                .build();

        ParsedLayoutElementDTO right = ParsedLayoutElementDTO.builder()
                .id("right_block")
                .type(ElementType.TEXT)
                .bbox(List.of(322.0, 100.0, 558.0, 400.0))
                .build();

        // 左栏右边缘 290，右栏左边缘 322，间隔 32pt
        Double gutterX = readingOrderResolver.detectVerticalGutterCenter(List.of(left, right), pageWidth);
        assertNotNull(gutterX, "应当成功检测到垂直分栏空白槽中心线");
        assertTrue(gutterX >= 295.0 && gutterX <= 315.0, "空白槽中心线应位于 [295, 315] 之间，实际: " + gutterX);
    }

    @Test
    @DisplayName("Contract 03: 二维网格代数拓扑单射还原，复合表头与跨行跨列单元格输出标准 HTML (Theorem 2.1)")
    void contract03_tableGridTopology_reconstructsMarkdownAndHtmlLosslessly() {
        // 构造带跨行与跨列的 4 行 4 列商业规划表
        List<TableCellDTO> cells = List.of(
                TableCellDTO.builder().row(0).col(0).rowspan(2).colspan(1).text("业务模块").isHeader(true).build(),
                TableCellDTO.builder().row(0).col(1).rowspan(1).colspan(2).text("2026年目标规划").isHeader(true).build(),
                TableCellDTO.builder().row(0).col(3).rowspan(2).colspan(1).text("合规评级").isHeader(true).build(),
                TableCellDTO.builder().row(1).col(1).rowspan(1).colspan(1).text("Q1 (万元)").isHeader(true).build(),
                TableCellDTO.builder().row(1).col(2).rowspan(1).colspan(1).text("Q2 (万元)").isHeader(true).build(),
                TableCellDTO.builder().row(2).col(0).rowspan(1).colspan(1).text("云计算").isHeader(false).build(),
                TableCellDTO.builder().row(2).col(1).rowspan(1).colspan(1).text("12000.5").isHeader(false).build(),
                TableCellDTO.builder().row(2).col(2).rowspan(1).colspan(1).text("14500.0").isHeader(false).build(),
                TableCellDTO.builder().row(2).col(3).rowspan(1).colspan(1).text("AAA").isHeader(false).build()
        );

        ExtractedTableDTO table = tableReconstructor.reconstructTable("tbl_01", 1, List.of(54.0, 80.0, 558.0, 400.0), 3, 4, cells);

        assertTrue(table.getHasMergedCells(), "应当检测到存在合并单元格");
        String html = table.getHtmlContent();
        assertNotNull(html);

        // 验证单射重构结果包含标准跨行跨列标记
        assertTrue(html.contains("rowspan=\"2\"") && html.contains("业务模块"), "应当包含 rowspan=2 的业务模块表头");
        assertTrue(html.contains("colspan=\"2\"") && html.contains("2026年目标规划"), "应当包含 colspan=2 的规划表头");
        assertTrue(html.contains("<td>12000.5</td>"), "应当包含对应的数据单元格");
    }

    @Test
    @DisplayName("Contract 04: 跨页续表列几何边界豪斯多夫距离校验与表头因果一致性拼接")
    void contract04_crossPageTableStitching_preservesColumnHeaderConsistency() {
        double pageWidth = 612.0;

        // 第一页表格：2行4列
        List<TableCellDTO> page1Cells = List.of(
                TableCellDTO.builder().row(0).col(0).rowspan(1).colspan(1).text("指标").isHeader(true).build(),
                TableCellDTO.builder().row(0).col(1).rowspan(1).colspan(1).text("值").isHeader(true).build(),
                TableCellDTO.builder().row(1).col(0).rowspan(1).colspan(1).text("A").isHeader(false).build(),
                TableCellDTO.builder().row(1).col(1).rowspan(1).colspan(1).text("100").isHeader(false).build()
        );
        ExtractedTableDTO t1 = tableReconstructor.reconstructTable("t1", 1, List.of(50.0, 500.0, 550.0, 750.0), 2, 2, page1Cells);

        // 第二页续表：首行是重复表头，第二行是新增数据
        List<TableCellDTO> page2Cells = List.of(
                TableCellDTO.builder().row(0).col(0).rowspan(1).colspan(1).text("指标").isHeader(true).build(), // 重复表头
                TableCellDTO.builder().row(0).col(1).rowspan(1).colspan(1).text("值").isHeader(true).build(),
                TableCellDTO.builder().row(1).col(0).rowspan(1).colspan(1).text("B").isHeader(false).build(),
                TableCellDTO.builder().row(1).col(1).rowspan(1).colspan(1).text("200").isHeader(false).build()
        );
        ExtractedTableDTO t2 = tableReconstructor.reconstructTable("t2", 2, List.of(50.0, 40.0, 550.0, 200.0), 2, 2, page2Cells);

        Optional<ExtractedTableDTO> stitchedOpt = tableReconstructor.stitchCrossPageTables(t1, t2, pageWidth);

        assertTrue(stitchedOpt.isPresent(), "符合几何与列数约束的跨页表格应成功拼接");
        ExtractedTableDTO stitched = stitchedOpt.get();
        assertEquals(3, stitched.getRows(), "拼接后总行数应为 2 + (2-1) = 3 行 (去除了重复表头)");
        assertEquals(2, stitched.getCols(), "列数应保持为 2 列");
        assertTrue(stitched.getHtmlContent().contains("<td>200</td>"), "应当包含第二页的新增数据");
    }

    @Test
    @DisplayName("Contract 05: 跨模态图表抽取与唯一元数据占位符及视觉语义增强摘要注入")
    void contract05_crossModalAnchoring_extractsFiguresAndInjectsPlaceholders() {
        ExtractedFigureDTO figure = ExtractedFigureDTO.builder()
                .figureId("fig_01")
                .pageNumber(1)
                .bbox(List.of(54.0, 400.0, 558.0, 600.0))
                .imageUri("https://minio.internal/kmc-assets/doc_001/fig_1.webp")
                .caption("图 1: 全链路多模态版面解析拓扑图")
                .visionSummary("架构包含四个核心解耦阶段：预检、版面重构、表格单射和跨模态增强。")
                .build();

        String md = anchorService.buildAnchoredFigureMarkdown(figure);

        assertTrue(md.contains("![fig_01](https://minio.internal/kmc-assets/doc_001/fig_1.webp)"), "应包含标准 Markdown 图片链接");
        assertTrue(md.contains("*图 1: 全链路多模态版面解析拓扑图*"), "应包含图表标题说明");
        assertTrue(md.contains("> **【图表语义增强】**"), "应包含 DeepSeek 视觉语义增强摘要引用块");

        // 验证正文锚定
        String rawText = "系统核心运行机制如图 1 所示，具有良好的解耦性。";
        String enrichedText = anchorService.anchorFiguresIntoText(rawText, List.of(figure));
        assertTrue(enrichedText.contains("图表跨模态关联资产"), "正文末尾应自动挂接图表关联资产");
        assertTrue(enrichedText.contains("![fig_01]"), "正文中应成功注入图表占位符");
    }

    @Test
    @DisplayName("Contract 06: DeepSeek 视觉语义摘要对图表问答幻觉发生率的显著抑制验证 (Theorem 3.1)")
    void contract06_visualGroundingFidelity_suppressesChartHallucination() {
        String visionSummary = "2024年度各板块营收占比：云计算占比 42.5%，智能硬件 31.2%，企业服务 26.3%。核心增长引擎为云计算。";

        // 真实忠实回答 (Grounding Score 高)
        String faithfulAnswer = "根据图表数据，云计算业务占比达到 42.5%，是核心增长引擎，智能硬件占比 31.2%。";
        double faithfulScore = anchorService.computeVisualGroundingFidelity(faithfulAnswer, visionSummary);
        assertTrue(faithfulScore >= 0.70, "忠实回答的事实支持保真度得分应 >= 0.70，实际: " + faithfulScore);

        // 捏造幻觉回答 (Grounding Score 极低)
        String hallucinatedAnswer = "量子区块链计算芯片占比 99.8%，彻底淘汰了传统云计算与硬件服务。";
        double hallucinatedScore = anchorService.computeVisualGroundingFidelity(hallucinatedAnswer, visionSummary);
        assertTrue(hallucinatedScore < 0.20, "虚构幻觉回答的事实支持度得分应接近于 0，实际: " + hallucinatedScore);
    }

    @Test
    @DisplayName("Contract 07: 标题字号与视觉语义边界驱动层次分块与面包屑注入 (Lemma 4.1)")
    void contract07_layoutAwareSplitter_alignsWithHeadingHierarchy() {
        ParsedDocumentResult mockResult = ParsedDocumentResult.builder()
                .elements(List.of(
                        ParsedLayoutElementDTO.builder().type(ElementType.TITLE).content("第一章 总体技术架构").fontSize(22.0).build(),
                        ParsedLayoutElementDTO.builder().type(ElementType.TEXT).content("本章详细阐述多模态系统设计原则与微服务通信契约。").build(),
                        ParsedLayoutElementDTO.builder().type(ElementType.TABLE).content("<table><tr><td>模块</td><td>耗时</td></tr></table>").build()
                ))
                .build();

        List<LayoutChunk> chunks = splitter.splitDocument(mockResult, 500);

        assertFalse(chunks.isEmpty(), "分块结果不应为空");
        LayoutChunk chunk = chunks.getFirst();
        assertTrue(chunk.getContent().contains("【第一章 总体技术架构】"), "切片头部必须注入标题栈面包屑");
        assertTrue(chunk.isContainsTable(), "切片应当标记包含表格原子块");
    }

    @Test
    @DisplayName("Contract 08: 多语言微服务网关协议流式处理与结构化数据解析")
    void contract08_polyglotProtocol_handlesStreamingDocumentParsingPayloads() throws Exception {
        InputStream is = getClass().getResourceAsStream("/fixtures/multimodal-layout-sample.json");
        if (is == null) {
            is = getClass().getClassLoader().getResourceAsStream("fixtures/multimodal-layout-sample.json");
        }
        String jsonContent;
        if (is != null) {
            try (InputStream s = is) {
                jsonContent = new String(s.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            java.nio.file.Path path = java.nio.file.Path.of("backend/tests/fixtures/multimodal-layout-sample.json");
            if (!java.nio.file.Files.exists(path)) {
                path = java.nio.file.Path.of("tests/fixtures/multimodal-layout-sample.json");
            }
            assertTrue(java.nio.file.Files.exists(path), "未能找到 multimodal-layout-sample.json 测试夹具");
            jsonContent = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
        }

        ParsedDocumentResult result = gatewayClient.parseDocumentPayload("task_001", "测试白皮书.pdf", jsonContent);

        assertNotNull(result);
        assertEquals(3, result.getTotalPages(), "总页数应为 3 页");
        assertFalse(result.getElements().isEmpty(), "抽取的版面元素集合不应为空");
        assertFalse(result.getTables().isEmpty(), "抽取的表格列表不应为空");
        assertFalse(result.getFigures().isEmpty(), "抽取的图表列表不应为空");
        assertTrue(result.getFullMarkdown().contains("面向混合架构"), "Markdown 全文应包含一级标题");
    }

    @Test
    @DisplayName("Contract 09: 超大恶意矢量 CAD 与单页超高 DPI 触发 OOM 熔断保护")
    void contract09_oomProtectionGuard_abortsBombPdfGracefully() {
        // 1. 正常 A4 页面在 150 DPI 下的测试
        boolean normalSafe = gatewayClient.checkOomSafetyGuard(10, 612.0, 792.0, 150);
        assertTrue(normalSafe, "正常 A4 尺寸文档在 150 DPI 下应安全通过");

        // 2. 超长页数文档 (> 500 页)
        boolean tooManyPages = gatewayClient.checkOomSafetyGuard(800, 612.0, 792.0, 150);
        assertFalse(tooManyPages, "超过 500 页的超长文档应触发保护熔断");

        // 3. 超大像素 CAD 展开图 (23622 x 35433 像素)
        boolean cadBomb = gatewayClient.checkOomSafetyGuard(1, 15000.0, 20000.0, 300);
        assertFalse(cadBomb, "超大尺寸 CAD 展开图在 300 DPI 下像素超标，必须触发 OOM 熔断");
    }

    @Test
    @DisplayName("Contract 10: 端到端多模态文档解析与富文本切片生产集成全闭环")
    void contract10_endToEndMultimodalPipeline_producesStructuredSegments() {
        // 构造端到端场景：解析多模态结果 -> 版面感知分块
        ParsedLayoutElementDTO title = ParsedLayoutElementDTO.builder()
                .type(ElementType.TITLE).content("核心算法原理").fontSize(22.0).build();
        ParsedLayoutElementDTO p1 = ParsedLayoutElementDTO.builder()
                .type(ElementType.TEXT).content("基于扩展 XY-Cut 的阅读序拓扑重构算法消除了横向穿插问题。").build();
        ParsedLayoutElementDTO tbl = ParsedLayoutElementDTO.builder()
                .type(ElementType.TABLE).content("<table border=\"1\"><tr><th>指标</th><th>结果</th></tr><tr><td>准确率</td><td>99.2%</td></tr></table>").build();
        ParsedLayoutElementDTO fig = ParsedLayoutElementDTO.builder()
                .type(ElementType.FIGURE).content("![fig_1](https://minio/fig1.webp)\n> **【图表语义增强】** 展示了系统端到端处理流水线。").build();

        ParsedDocumentResult docResult = ParsedDocumentResult.builder()
                .taskId("e2e_001")
                .documentName("e2e_doc.pdf")
                .elements(List.of(title, p1, tbl, fig))
                .build();

        List<LayoutChunk> finalChunks = splitter.splitDocument(docResult, 1024);

        assertFalse(finalChunks.isEmpty(), "端到端产出的富文本切片集不应为空");
        // 验证切片包含标题面包屑、表格 HTML 与图表跨模态增强
        boolean hasTableChunk = false;
        boolean hasFigureChunk = false;
        for (LayoutChunk chunk : finalChunks) {
            if (chunk.isContainsTable()) hasTableChunk = true;
            if (chunk.isContainsFigure()) hasFigureChunk = true;
            assertTrue(chunk.getContent().contains("【核心算法原理】"), "每个切片均应继承有效标题面包屑");
        }
        assertTrue(hasTableChunk, "流水线必须产出包含表格原子保护的切片");
        assertTrue(hasFigureChunk, "流水线必须产出包含图表跨模态增强的切片");
    }
}
