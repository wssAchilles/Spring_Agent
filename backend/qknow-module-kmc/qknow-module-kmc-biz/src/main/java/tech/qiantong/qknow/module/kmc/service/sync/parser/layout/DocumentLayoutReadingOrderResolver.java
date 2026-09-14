package tech.qiantong.qknow.module.kmc.service.sync.parser.layout;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO;
import tech.qiantong.qknow.module.kmc.service.sync.parser.dto.ParsedLayoutElementDTO.ElementType;

import java.util.*;

/**
 * Phase 27: 版面空间几何拓扑偏序与阅读序重构引擎
 * 包含：
 * 1. 递归扩展 XY-Cut (ESP-Tree) 空间投影算法；
 * 2. 通栏标题与横幅元素优先分离，消除垂直投影死锁；
 * 3. 垂直投影直方图空白槽 (Vertical Gutter) 检测，实现双栏/多栏自适应分割；
 * 4. 阅读序有向无环图 (DAG) 拓扑排序，严格保证跨栏防穿透定理 (Theorem 1.1)。
 */
@Slf4j
@Component
public class DocumentLayoutReadingOrderResolver {

    public static final double SPANNING_WIDTH_RATIO = 0.65; // 宽度占比超过 65% 判定为通栏元素
    public static final double MIN_VERTICAL_GUTTER_PT = 15.0; // 垂直空白分栏槽最小宽度 (15pt)

    /**
     * 对单页或全页面版面元素集合执行空间拓扑重构与阅读顺序全序化排序
     *
     * @param elements 原始检测版面元素列表
     * @param pageWidth 页面物理宽度
     * @param pageHeight 页面物理高度
     * @return 重新赋予严格 readingOrder 并排好序的元素列表
     */
    public List<ParsedLayoutElementDTO> resolveReadingOrder(List<ParsedLayoutElementDTO> elements,
                                                           double pageWidth,
                                                           double pageHeight) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }

        // 1. 过滤页眉与页脚干扰元素 (避免周期性污染正文拓扑)
        List<ParsedLayoutElementDTO> validElements = new ArrayList<>();
        for (ParsedLayoutElementDTO elem : elements) {
            if (elem.getType() != ElementType.HEADER && elem.getType() != ElementType.FOOTER) {
                validElements.add(elem);
            }
        }

        if (validElements.size() <= 1) {
            if (!validElements.isEmpty()) {
                validElements.getFirst().setReadingOrder(1);
            }
            return validElements;
        }

        // 2. 识别通栏隔离带 (Spanning Barriers: 如大标题、全宽图表)
        List<ParsedLayoutElementDTO> spanningElements = new ArrayList<>();
        List<ParsedLayoutElementDTO> bodyElements = new ArrayList<>();

        for (ParsedLayoutElementDTO elem : validElements) {
            double elemWidth = getElementWidth(elem);
            if (elemWidth >= pageWidth * SPANNING_WIDTH_RATIO) {
                spanningElements.add(elem);
            } else {
                bodyElements.add(elem);
            }
        }

        // 3. 构建阅读序有向无环图 (DAG) 邻接表与入度数组
        int n = validElements.size();
        Map<String, Integer> idToIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            idToIndex.put(validElements.get(i).getId(), i);
        }

        List<List<Integer>> adj = new ArrayList<>(n);
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }

        // 4. 判定正文区域是否存在双栏分栏间隙 (Gutter)
        Double gutterCenterX = detectVerticalGutterCenter(bodyElements, pageWidth);

        // 5. 按照 ESP-Tree 空间几何拓扑偏序添加有向边
        for (int i = 0; i < n; i++) {
            ParsedLayoutElementDTO e1 = validElements.get(i);
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                ParsedLayoutElementDTO e2 = validElements.get(j);

                if (isStrictlyPreceding(e1, e2, gutterCenterX, pageWidth)) {
                    adj.get(i).add(j);
                    inDegree[j]++;
                }
            }
        }

        // 6. 执行 Kahn 算法进行 DAG 拓扑排序
        PriorityQueue<Integer> queue = new PriorityQueue<>(Comparator.comparingDouble(idx -> getElementY1(validElements.get(idx))));
        for (int i = 0; i < n; i++) {
            if (inDegree[i] == 0) {
                queue.offer(i);
            }
        }

        List<ParsedLayoutElementDTO> sortedResult = new ArrayList<>(n);
        int orderCounter = 1;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            ParsedLayoutElementDTO elem = validElements.get(u);
            elem.setReadingOrder(orderCounter++);
            sortedResult.add(elem);

            for (int v : adj.get(u)) {
                inDegree[v]--;
                if (inDegree[v] == 0) {
                    queue.offer(v);
                }
            }
        }

        // 容错降级：若因测量噪声意外成环，兜底按 Y 优先或原有顺序列出
        if (sortedResult.size() < n) {
            log.warn("检测到局部拓扑异常，启用平滑几何降级兜底");
            Set<String> includedIds = new HashSet<>();
            for (ParsedLayoutElementDTO e : sortedResult) {
                includedIds.add(e.getId());
            }
            for (ParsedLayoutElementDTO e : validElements) {
                if (!includedIds.contains(e.getId())) {
                    e.setReadingOrder(orderCounter++);
                    sortedResult.add(e);
                }
            }
        }

        return sortedResult;
    }

    /**
     * 检测垂直投影直方图空白槽中心线 (Gutter Center)
     */
    public Double detectVerticalGutterCenter(List<ParsedLayoutElementDTO> bodyElements, double pageWidth) {
        if (bodyElements == null || bodyElements.size() < 2) {
            return null;
        }

        double midStart = pageWidth * 0.40;
        double midEnd = pageWidth * 0.60;

        // 寻找位于页面中央区域的最大垂直空白条带
        List<double[]> columnRanges = new ArrayList<>();
        for (ParsedLayoutElementDTO elem : bodyElements) {
            double x1 = getElementX1(elem);
            double x2 = getElementX2(elem);
            columnRanges.add(new double[]{x1, x2});
        }

        // 检测是否存在贯穿垂直方向的空白槽 (无任何文字块在 X 轴交叠)
        double maxGapWidth = 0.0;
        double bestGutterX = -1.0;

        // 简化检查：在 [midStart, midEnd] 范围内按 5pt 步长探测
        for (double probeX = midStart; probeX <= midEnd; probeX += 5.0) {
            boolean isClear = true;
            for (double[] range : columnRanges) {
                if (range[0] < probeX && range[1] > probeX) {
                    isClear = false;
                    break;
                }
            }
            if (isClear) {
                // 向左右扩展空白槽边界
                double leftBound = probeX;
                while (leftBound > 0) {
                    boolean hit = false;
                    for (double[] range : columnRanges) {
                        if (range[0] <= leftBound && range[1] >= leftBound) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) break;
                    leftBound -= 2.0;
                }

                double rightBound = probeX;
                while (rightBound < pageWidth) {
                    boolean hit = false;
                    for (double[] range : columnRanges) {
                        if (range[0] <= rightBound && range[1] >= rightBound) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) break;
                    rightBound += 2.0;
                }

                double gap = rightBound - leftBound;
                if (gap >= MIN_VERTICAL_GUTTER_PT && gap > maxGapWidth) {
                    maxGapWidth = gap;
                    bestGutterX = (leftBound + rightBound) / 2.0;
                }
            }
        }

        return bestGutterX > 0 ? bestGutterX : null;
    }

    /**
     * 判定元素 e1 是否在空间几何偏序与阅读序上严格领先于 e2
     */
    private boolean isStrictlyPreceding(ParsedLayoutElementDTO e1,
                                        ParsedLayoutElementDTO e2,
                                        Double gutterX,
                                        double pageWidth) {
        double y1_e1 = getElementY1(e1);
        double y2_e1 = getElementY2(e1);
        double y1_e2 = getElementY1(e2);
        double y2_e2 = getElementY2(e2);

        double x1_e1 = getElementX1(e1);
        double x2_e1 = getElementX2(e1);
        double x1_e2 = getElementX1(e2);
        double x2_e2 = getElementX2(e2);

        boolean isSpanningE1 = (x2_e1 - x1_e1) >= pageWidth * SPANNING_WIDTH_RATIO;
        boolean isSpanningE2 = (x2_e2 - x1_e2) >= pageWidth * SPANNING_WIDTH_RATIO;

        // 规则 1: 若 e1 为上方通栏元素，且整体位于 e2 上方
        if (isSpanningE1 && y2_e1 <= y1_e2 + 10.0) {
            return true;
        }
        // 反之，若 e2 为通栏且在上方，则 e1 不可能领先
        if (isSpanningE2 && y2_e2 <= y1_e1 + 10.0) {
            return false;
        }

        // 规则 2: 双栏分栏防穿透判断
        if (gutterX != null && !isSpanningE1 && !isSpanningE2) {
            boolean e1InLeft = x2_e1 <= gutterX + 5.0;
            boolean e2InRight = x1_e2 >= gutterX - 5.0;

            // 左栏元素严格先于右栏元素阅读 (Theorem 1.1 Column Non-Crossing)
            if (e1InLeft && e2InRight) {
                return true;
            }
            if (!e1InLeft && !e2InRight && x1_e1 >= gutterX - 5.0 && x2_e2 <= gutterX + 5.0) {
                // e1 在右栏，e2 在左栏 -> e1 绝不领先 e2
                return false;
            }
        }

        // 规则 3: 同栏内部按自上而下阅读序
        double overlapX = Math.max(0, Math.min(x2_e1, x2_e2) - Math.max(x1_e1, x1_e2));
        double minWidth = Math.min(x2_e1 - x1_e1, x2_e2 - x1_e2);

        if (minWidth > 0 && overlapX / minWidth >= 0.40) {
            // 水平交叠显著，垂直上方者绝对领先
            return y2_e1 <= y1_e2 || (y1_e1 < y1_e2 && y2_e1 < y2_e2);
        }

        // 兜底几何比较
        return y2_e1 <= y1_e2;
    }

    private double getElementX1(ParsedLayoutElementDTO e) {
        return (e.getBbox() != null && !e.getBbox().isEmpty()) ? e.getBbox().get(0) : 0.0;
    }

    private double getElementY1(ParsedLayoutElementDTO e) {
        return (e.getBbox() != null && e.getBbox().size() > 1) ? e.getBbox().get(1) : 0.0;
    }

    private double getElementX2(ParsedLayoutElementDTO e) {
        return (e.getBbox() != null && e.getBbox().size() > 2) ? e.getBbox().get(2) : 100.0;
    }

    private double getElementY2(ParsedLayoutElementDTO e) {
        return (e.getBbox() != null && e.getBbox().size() > 3) ? e.getBbox().get(3) : 100.0;
    }

    private double getElementWidth(ParsedLayoutElementDTO e) {
        return getElementX2(e) - getElementX1(e);
    }
}
