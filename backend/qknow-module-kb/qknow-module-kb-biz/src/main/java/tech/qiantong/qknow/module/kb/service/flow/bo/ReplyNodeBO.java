package tech.qiantong.qknow.module.kb.service.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.BotExecuteModeEnum;
import tech.qiantong.qknow.module.kb.dal.enums.BotTypeEnums;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplyNodeBO extends BaseNodeBO {
    public ReplyNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        super(nodeDefinition, edgeList);
    }

    /**
     * 执行具体的节点逻辑（由子类实现）
     *
     * @param inputData 输入数据
     * @param context   工作流上下文
     * @return 执行结果
     */
    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = super.getNodeDefinition();
        JSONObject contextVariables = context.getVariables();
        JSONArray outputDefArray = StrUtil.isNotBlank(nodeDefinition.getOutput())
                ? JSONArray.parseArray(nodeDefinition.getOutput())
                : null;
        if (outputDefArray == null) {
            outputDefArray = new JSONArray();
        }

        Map<String, Object> outputData = new java.util.HashMap<>(outputDefArray.size());

        if (Objects.equals(context.getBotType(), BotTypeEnums.CHAT_FLOW)) {
            this.setChatFlowFlux(nodeDefinition, context);// chatFlow 结果
        } else if (!Objects.equals(context.getExecuteMode(), BotExecuteModeEnum.BLOCK)) {
            this.setWorkFlowFlux(outputDefArray, context);// 工作流的流式结果
        } else {
            for (int i = 0; i < outputDefArray.size(); i++) {
                JSONObject outputDef = outputDefArray.getJSONObject(i);
                if (outputDef == null) {
                    continue;
                }
                String sourceNodeId = outputDef.getString("sourceNodeId");
                String key = sourceNodeId + "." + outputDef.getString("variableKey");
                String value = contextVariables != null ? contextVariables.getString(key) : null;
                outputData.put(outputDef.getString("name"), value);
            }
            if (context.getRuntimeDO() != null) {
                context.getRuntimeDO().setOutput(JSONObject.toJSONString(outputData));
            }
        }
        // 从配置中获取提示词
        return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);
    }

    /**
     * 设置获取流式输出结果
     *
     * @param outputDefArray 工作流的输出定义
     * @param context        运行上下文
     */
    private void setWorkFlowFlux(JSONArray outputDefArray, RuntimeContextBO context) {
        JSONObject contextVariables = context.getVariables();
        Map<String, Flux<String>> fluxMap = context.getFluxMap();
        Flux<CommonResult<String>> resultFlux = context.getResultFlux();
        if (Objects.isNull(resultFlux)) {
            resultFlux = Flux.just(CommonResult.success("{}"));
        }

        if (outputDefArray == null || outputDefArray.isEmpty()) {
            // 未显式配置输出映射时，自动回退消费上游大模型流或上下文文本
            resultFlux = appendFallbackOutputFlux(resultFlux, super.getNodeDefinition(), context, "output");
            context.setResultFlux(resultFlux);
            return;
        }

        for (int i = 0; i < outputDefArray.size(); i++) {
            JSONObject outputDef = outputDefArray.getJSONObject(i);
            if (outputDef == null) {
                continue;
            }
            String sourceNodeId = outputDef.getString("sourceNodeId");
            String sourceNodeType = outputDef.getString("sourceNodeType");
            String key = sourceNodeId + "." + outputDef.getString("variableKey");
            String name = outputDef.getString("name");

            if (Objects.equals(sourceNodeType, FlowNodeTypeEnums.LLM.getName()) && fluxMap != null) {
                Flux<String> flux = fluxMap.get(sourceNodeId);
                if (!Objects.isNull(flux)) {
                    Flux<CommonResult<String>> map = flux.map(result -> {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("text", result);
                        jsonObject.put("name", name);
                        return CommonResult.success(jsonObject.toString());
                    });
                    resultFlux = Flux.concat(resultFlux,
                            map,
                            this.gapFlux(name));
                    continue;
                }
            }

            String value = contextVariables != null ? contextVariables.getString(key) : null;
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("text", value);
            jsonObject.put("name", name);
            resultFlux = Flux.concat(resultFlux,
                    Flux.just(CommonResult.success(jsonObject.toString())),
                    this.gapFlux(name));
        }
        context.setResultFlux(resultFlux);
    }

    /**
     * 设置对话流的输出
     */
    private void setChatFlowFlux(KbFlowNodeDO nodeDefinition, RuntimeContextBO context) {
        JSONObject contextVariables = context.getVariables();
        JSONObject configJson = StrUtil.isNotBlank(nodeDefinition.getConfig())
                ? JSONObject.parseObject(nodeDefinition.getConfig())
                : null;
        if (configJson == null) {
            configJson = new JSONObject();
        }
        String resultFormat = configJson.getString("content");
        if (StrUtil.isBlank(resultFormat)) {
            resultFormat = configJson.getString("template");
        }
        if (StrUtil.isBlank(resultFormat)) {
            resultFormat = configJson.getString("text");
        }
        if (StrUtil.isBlank(resultFormat)) {
            this.buildFallbackChatFlowFlux(nodeDefinition, context);
            return;
        }

        Map<String, KbFlowNodeDO> nodeMap = context.getNodeMap();
        Map<String, Flux<String>> fluxMap = context.getFluxMap();
        List<String> resultList = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{\\{\\s*(.+?)\\s*}}|[^\\s{}]+");
        Matcher matcher = pattern.matcher(resultFormat);
        while (matcher.find()) {
            resultList.add(matcher.group());
        }
        Flux<CommonResult<String>> resultFlux = Flux.just(CommonResult.success("{}"));
        for (String result : resultList) {
            if (result.startsWith("{{") && result.endsWith("}}")) {
                String key = result.substring(2, result.length() - 2).trim();
                String sourceNodeId = key.contains(".") ? key.split("\\.")[0] : key;
                KbFlowNodeDO sourceNode = nodeMap != null ? nodeMap.get(sourceNodeId) : null;
                if (sourceNode != null && Objects.equals(sourceNode.getType(), FlowNodeTypeEnums.LLM.getCode())) {
                    Flux<String> flux = fluxMap != null ? fluxMap.get(sourceNodeId) : null;
                    if (!Objects.isNull(flux)) {
                        Flux<CommonResult<String>> map = flux.map(segment -> {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("text", segment);
                            return CommonResult.success(jsonObject.toString());
                        });
                        resultFlux = resultFlux.concatWith(map);
                    } else {
                        String string = contextVariables != null ? contextVariables.getString(key) : null;
                        if (string != null) {
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("text", string);
                            resultFlux = resultFlux.concatWith(Flux.just(CommonResult.success(jsonObject.toString())));
                        }
                    }
                } else {
                    String string = contextVariables != null ? contextVariables.getString(key) : null;
                    if (string != null) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("text", string);
                        resultFlux = resultFlux.concatWith(Flux.just(CommonResult.success(jsonObject.toString())));
                    }
                }
            } else {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("text", result);
                resultFlux = resultFlux.concatWith(Flux.just(CommonResult.success(jsonObject.toString())));
            }
        }

        context.setResultFlux(resultFlux);
    }

    /**
     * 当对话流回复节点未配置模板内容时，自动回退消费上游流或文本输出
     */
    private void buildFallbackChatFlowFlux(KbFlowNodeDO nodeDefinition, RuntimeContextBO context) {
        Flux<CommonResult<String>> resultFlux = Flux.just(CommonResult.success("{}"));
        resultFlux = appendFallbackOutputFlux(resultFlux, nodeDefinition, context, null);
        context.setResultFlux(resultFlux);
    }

    /**
     * 抽取上游连线节点（保序去重）并按需附着流式或变量兜底输出
     */
    private Flux<CommonResult<String>> appendFallbackOutputFlux(Flux<CommonResult<String>> baseFlux,
                                                              KbFlowNodeDO nodeDefinition,
                                                              RuntimeContextBO context,
                                                              String outputFieldName) {
        Flux<CommonResult<String>> resultFlux = baseFlux;
        List<String> upstreamNodeUuids = new ArrayList<>();
        List<KbFlowEdgeDO> edges = super.getEdgeList();
        if (edges != null && nodeDefinition != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (KbFlowEdgeDO edge : edges) {
                if (edge != null
                        && Objects.equals(edge.getTargetNodeUuid(), nodeDefinition.getUuid())
                        && StrUtil.isNotBlank(edge.getSourceNodeUuid())) {
                    seen.add(edge.getSourceNodeUuid());
                }
            }
            upstreamNodeUuids.addAll(seen);
        }

        boolean matchedFlux = false;
        Map<String, Flux<String>> fluxMap = context != null ? context.getFluxMap() : null;
        if (fluxMap != null && !fluxMap.isEmpty()) {
            for (String upstreamUuid : upstreamNodeUuids) {
                Flux<String> upstreamFlux = fluxMap.get(upstreamUuid);
                if (upstreamFlux != null) {
                    resultFlux = concatWrappedFlux(resultFlux, upstreamFlux, outputFieldName);
                    matchedFlux = true;
                }
            }
            if (!matchedFlux) {
                // 若入边前置节点未直接产生流，则兜底输出活跃流映射中的所有流
                for (Flux<String> activeFlux : fluxMap.values()) {
                    if (activeFlux != null) {
                        resultFlux = concatWrappedFlux(resultFlux, activeFlux, outputFieldName);
                        matchedFlux = true;
                    }
                }
            }
        }

        if (!matchedFlux && context != null && context.getVariables() != null) {
            JSONObject contextVariables = context.getVariables();
            String fallbackText = null;
            for (String upstreamUuid : upstreamNodeUuids) {
                String candidate = contextVariables.getString(upstreamUuid + ".text");
                if (StrUtil.isNotBlank(candidate)) {
                    fallbackText = candidate;
                    break;
                }
            }
            if (StrUtil.isBlank(fallbackText)) {
                fallbackText = contextVariables.getString("text");
            }
            if (StrUtil.isNotBlank(fallbackText)) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("text", fallbackText);
                if (StrUtil.isNotBlank(outputFieldName)) {
                    jsonObject.put("name", outputFieldName);
                    resultFlux = Flux.concat(resultFlux,
                            Flux.just(CommonResult.success(jsonObject.toString())),
                            this.gapFlux(outputFieldName));
                } else {
                    resultFlux = resultFlux.concatWith(Flux.just(CommonResult.success(jsonObject.toString())));
                }
            }
        }
        return resultFlux;
    }

    /**
     * 将上游文本流包装为统一 SSE 数据块结构并拼接到输出流
     */
    private Flux<CommonResult<String>> concatWrappedFlux(Flux<CommonResult<String>> baseFlux,
                                                         Flux<String> upstreamFlux,
                                                         String outputFieldName) {
        Flux<CommonResult<String>> wrapped = upstreamFlux.map(segment -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("text", segment);
            if (StrUtil.isNotBlank(outputFieldName)) {
                jsonObject.put("name", outputFieldName);
            }
            return CommonResult.success(jsonObject.toString());
        });
        if (StrUtil.isNotBlank(outputFieldName)) {
            return Flux.concat(baseFlux, wrapped, this.gapFlux(outputFieldName));
        }
        return baseFlux.concatWith(wrapped);
    }

    /**
     * 添加间隔符
     *
     * @param name 名称
     * @return Flux
     */
    private Flux<CommonResult<String>> gapFlux(String name) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("text", "\n");
        jsonObject.put("name", name);
        return Flux.just(CommonResult.success(jsonObject.toString()));
    }

    /**
     * 合并输出结果
     *
     * @param list 输出数据列表
     * @return 合并后的结果
     */
    private String margeOutPut(List<String> list) {
        Map<String, StringBuilder> sbMap = new HashMap<>();
        for (String str : list) {
            JSONObject jsonObject = JSONObject.parseObject(str);
            String key = jsonObject.getString("name");
            String text = jsonObject.getString("text");
            if (StrUtil.hasBlank(key, text)) {
                continue;
            }
            StringBuilder sb = sbMap.get(key);
            if (Objects.isNull(sb)) {
                sb = new StringBuilder();
            }
            sb.append(text);
            sbMap.put(key, sb);
        }
        return JSONObject.toJSONString(sbMap);
    }

    private String margeOutPutChatFlow(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            JSONObject jsonObject = JSONObject.parseObject(str);
            String text = jsonObject.getString("text");
            if (StrUtil.hasBlank(text)) {
                continue;
            }
            sb.append(text);
        }
        return sb.toString();
    }
}
