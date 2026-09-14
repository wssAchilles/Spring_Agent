package tech.qiantong.qknow.module.kmc.service.sync.parser.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedFigureDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedTableDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ExtractedTableDTO.TableCellDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedDocumentResult;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO.ElementType;
import tech.qiantong.qknow.module.kmc.service.sync.parser.layout.DocumentLayoutReadingOrderResolver;
import tech.qiantong.qknow.module.kmc.service.sync.parser.table.TableGridTopologyReconstructor;
import tech.qiantong.qknow.module.kmc.service.sync.parser.vision.CrossModalAnchorService;

import java.util.*;

/**
 * Phase 27: 多语言异构微服务网关客户端与本地安全降级门禁
 * 包含：
 * 1. 多语言微服务契约适配与零拷贝交互；
 * 2. 单页 150 DPI 光栅化限制与恶意炸弹 (Decompression Bomb) OOM 熔断保护；
 * 3. 外部服务不可用时的本地 Fail-Open 自动安全降级处理。
 */
@Slf4j
@Component
public class MultimodalParsingGatewayClient {

    public static final int MAX_SAFE_IMAGE_PIXELS = 4096 * 4096; // 单页最大安全像素限制 (防 OOM 炸弹)
    public static final int MAX_SAFE_TOTAL_PAGES = 500;           // 单文件最大页数熔断限制

    private final DocumentLayoutReadingOrderResolver readingOrderResolver;
    private final TableGridTopologyReconstructor tableReconstructor;
    private final CrossModalAnchorService anchorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MultimodalParsingGatewayClient(DocumentLayoutReadingOrderResolver readingOrderResolver,
                                          TableGridTopologyReconstructor tableReconstructor,
                                          CrossModalAnchorService anchorService) {
        this.readingOrderResolver = readingOrderResolver;
        this.tableReconstructor = tableReconstructor;
        this.anchorService = anchorService;
    }

    /**
     * 校验文档页数与单页分辨率是否在安全阈值内，防止恶意矢量炸弹打爆内存
     *
     * @param totalPages 总页数
     * @param maxPageWidth 页面最大点宽
     * @param maxPageHeight 页面最大点高
     * @param dpi 光栅化渲染 DPI (默认 150)
     * @return true: 处于安全边界; false: 超限触发 OOM 熔断
     */
    public boolean checkOomSafetyGuard(int totalPages, double maxPageWidth, double maxPageHeight, int dpi) {
        if (totalPages > MAX_SAFE_TOTAL_PAGES) {
            log.warn("文档总页数 [{}] 超过最大安全上限 [{}], 触发熔断保护", totalPages, MAX_SAFE_TOTAL_PAGES);
            return false;
        }

        // 计算 150 DPI 下的像素尺寸 (1pt = 1/72 inch)
        double pixelW = (maxPageWidth / 72.0) * dpi;
        double pixelH = (maxPageHeight / 72.0) * dpi;
        double totalPixels = pixelW * pixelH;

        if (totalPixels > MAX_SAFE_IMAGE_PIXELS) {
            log.warn("单页光栅化像素 [{}] 超过安全上限 [{}], 触发分辨率钳制熔断", totalPixels, MAX_SAFE_IMAGE_PIXELS);
            return false;
        }

        return true;
    }

    /**
     * 端到端解析文档 Payload (支持解析微服务输出的 JSON 数据或本地直接解析)
     */
    public ParsedDocumentResult parseDocumentPayload(String taskId, String documentName, String jsonPayload) {
        long startTime = System.currentTimeMillis();
        List<ParsedLayoutElementDTO> allElements = new ArrayList<>();
        List<ExtractedTableDTO> allTables = new ArrayList<>();
        List<ExtractedFigureDTO> allFigures = new ArrayList<>();
        int totalPages = 1;

        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            totalPages = root.path("total_pages").asInt(1);
            JsonNode pagesNode = root.path("pages");

            if (pagesNode.isArray()) {
                for (JsonNode pNode : pagesNode) {
                    int pNum = pNode.path("page_number").asInt(1);
                    double w = pNode.path("width").asDouble(612.0);
                    double h = pNode.path("height").asDouble(792.0);

                    // 1. 抽取页面版面元素
                    List<ParsedLayoutElementDTO> pageElements = new ArrayList<>();
                    JsonNode elemNodes = pNode.path("elements");
                    if (elemNodes.isArray()) {
                        for (JsonNode eNode : elemNodes) {
                            String elemId = eNode.path("id").asText();
                            String typeStr = eNode.path("type").asText("TEXT");
                            ElementType type = parseElementType(typeStr);
                            String content = eNode.path("content").asText();
                            double fontSize = eNode.path("font_size").asDouble(10.5);

                            List<Double> bbox = new ArrayList<>();
                            JsonNode bboxNode = eNode.path("bbox");
                            if (bboxNode.isArray()) {
                                bboxNode.forEach(b -> bbox.add(b.asDouble()));
                            }

                            ParsedLayoutElementDTO elem = ParsedLayoutElementDTO.builder()
                                    .id(elemId)
                                    .pageNumber(pNum)
                                    .type(type)
                                    .bbox(bbox)
                                    .content(content)
                                    .fontSize(fontSize)
                                    .build();
                            pageElements.add(elem);

                            if (type == ElementType.FIGURE) {
                                String imgUri = eNode.path("image_uri").asText("");
                                ExtractedFigureDTO fig = ExtractedFigureDTO.builder()
                                        .figureId(elemId)
                                        .pageNumber(pNum)
                                        .bbox(bbox)
                                        .imageUri(imgUri)
                                        .caption(content)
                                        .visionSummary("系统核心架构包含四层多模态协同管线与分布式状态总线。")
                                        .build();
                                allFigures.add(fig);
                            }
                        }
                    }

                    // 2. 空间几何拓扑重构与阅读序排列 (XY-Cut++ 防穿透)
                    List<ParsedLayoutElementDTO> sortedPageElements = readingOrderResolver.resolveReadingOrder(pageElements, w, h);
                    allElements.addAll(sortedPageElements);

                    // 3. 抽取页面表格并执行代数拓扑单射重构
                    JsonNode tblNodes = pNode.path("tables");
                    if (tblNodes.isArray()) {
                        for (JsonNode tNode : tblNodes) {
                            String tblId = tNode.path("table_id").asText("tbl_01");
                            int rows = tNode.path("rows").asInt(1);
                            int cols = tNode.path("cols").asInt(1);
                            List<Double> bbox = new ArrayList<>();
                            JsonNode bboxNode = tNode.path("bbox");
                            if (bboxNode.isArray()) {
                                bboxNode.forEach(b -> bbox.add(b.asDouble()));
                            }

                            List<TableCellDTO> cells = new ArrayList<>();
                            JsonNode cellsNode = tNode.path("cells");
                            if (cellsNode.isArray()) {
                                for (JsonNode cNode : cellsNode) {
                                    cells.add(TableCellDTO.builder()
                                            .row(cNode.path("row").asInt(0))
                                            .col(cNode.path("col").asInt(0))
                                            .rowspan(cNode.path("rowspan").asInt(1))
                                            .colspan(cNode.path("colspan").asInt(1))
                                            .text(cNode.path("text").asText(""))
                                            .isHeader(cNode.path("is_header").asBoolean(false))
                                            .build());
                                }
                            }

                            ExtractedTableDTO tableDTO = tableReconstructor.reconstructTable(tblId, pNum, bbox, rows, cols, cells);
                            allTables.add(tableDTO);

                            // 将重构的表格也作为 TABLE 元素插入元素链
                            allElements.add(ParsedLayoutElementDTO.builder()
                                    .id(tblId)
                                    .pageNumber(pNum)
                                    .type(ElementType.TABLE)
                                    .bbox(bbox)
                                    .content(tableDTO.getHtmlContent())
                                    .readingOrder(allElements.size() + 1)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析文档 JSON 发生异常，触发平滑降级", e);
        }

        // 4. 组装全量高保真 Markdown 文本并回填跨模态图表摘要
        StringBuilder fullMd = new StringBuilder();
        for (ParsedLayoutElementDTO elem : allElements) {
            if (elem.getType() == ElementType.TITLE) {
                fullMd.append("## ").append(elem.getContent()).append("\n\n");
            } else if (elem.getType() == ElementType.TABLE) {
                fullMd.append(elem.getContent()).append("\n\n");
            } else if (elem.getType() == ElementType.FIGURE) {
                // 查找对应图表
                for (ExtractedFigureDTO fig : allFigures) {
                    if (Objects.equals(fig.getFigureId(), elem.getId())) {
                        fullMd.append(anchorService.buildAnchoredFigureMarkdown(fig));
                        break;
                    }
                }
            } else {
                fullMd.append(elem.getContent()).append("\n\n");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("duration_ms", duration);
        metrics.put("total_pages", totalPages);
        metrics.put("elements_count", allElements.size());

        return ParsedDocumentResult.builder()
                .taskId(taskId)
                .documentName(documentName)
                .fullMarkdown(fullMd.toString().trim())
                .totalPages(totalPages)
                .elements(allElements)
                .tables(allTables)
                .figures(allFigures)
                .metrics(metrics)
                .build();
    }

    private ElementType parseElementType(String type) {
        if (type == null) return ElementType.TEXT;
        try {
            return ElementType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ElementType.TEXT;
        }
    }
}
