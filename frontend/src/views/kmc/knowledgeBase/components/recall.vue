<template>
  <div class="app-container recall-glass-app">
    <GuideTip tip-id="kmc/recall" />
    <div class="container">
      <div class="left-container">
        <div class="border-item module-1">
          <div class="border-item-head">
            <span class="head-title">召回测试 </span>
          </div>
          <div class="border-item-body">
            <div class="title">
              <span>源文本</span>
              <el-button @click="drawer = true" style="margin-left: auto">
                {{ getSearchName() }}
                <el-icon class="el-icon--right">
                  <Operation />
                </el-icon>
              </el-button>
            </div>
            <div class="content">
              <el-input
                class="input-text"
                v-model="knowledgeBase.query"
                placeholder="请输入你的问题"
                type="textarea"
                rows="8"
                :resize="'none'"
              ></el-input>
              <el-button
                class="input-btn"
                @click="getRecall"
                :disabled="knowledgeBase.query === ''"
                type="primary"
                :loading="loading"
                >测试
              </el-button>
            </div>
          </div>
        </div>
        <div class="border-item module-2">
          <div class="border-item-head">
            <span class="head-title">记录 </span>
            <el-link type="primary" :underline="false" @click="goRecallLog">
              查看更多
            </el-link>
          </div>
          <div class="border-item-body">
            <el-table
              stripe
              :data="recallLogList"
              max-height="100%"
              :default-sort="defaultSort"
              @sort-change="handleSortChange"
            >
              <el-table-column
                label="编号"
                align="center"
                prop="id"
                sortable="custom"
                width="80"
              />
              <el-table-column
                label="文本"
                align="left"
                prop="query"
                width="300"
                :show-overflow-tooltip="{ effect: 'light' }"
              />
              <el-table-column label="创建人" align="center" prop="createBy" />
              <el-table-column
                label="创建时间"
                align="center"
                prop="createTime"
                width="180"
                sortable="custom"
                :sort-orders="['descending', 'ascending']"
              >
                <template #default="scope">
                  <span>{{
                    parseTime(scope.row.createTime, "{y}-{m}-{d} {h}:{i}")
                  }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
      <div class="right-container">
        <div class="border-item">
          <div class="border-item-head" style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
            <span class="head-title">召回段落 </span>
            <div style="display: flex; align-items: center; gap: 15px;">
              <el-switch v-model="debugMode" active-text="显示调试信息" inactive-text="普通模式" />
              <el-button type="danger" size="small" plain @click="handleClearCache" :loading="clearingCache">清除 RAG 缓存</el-button>
            </div>
          </div>
          <div class="border-item-body" style="overflow-y: auto">
            <el-card v-for="item in recallList" :key="item.id || item.segmentId || item.content" style="margin-bottom: 20px">
              <template #header>
                <div class="title">
                  <img :src="getFileType(item.documentName)" />
                  <span>{{ item.documentName || '未知文档' }}</span>
                  <el-tag size="small" style="margin-left: 10px" v-if="debugMode && item.source" type="info">{{ getSourceText(item.source) }}</el-tag>
                  <el-progress
                    :text-inside="true"
                    :stroke-width="16"
                    :percentage="getScorePercent(item.score)"
                    style="width: 60px; margin-left: auto"
                  >
                    {{ getScoreText(item.score) }}
                  </el-progress>
                </div>
              </template>
              <div class="markdown-body">
                <div v-html="renderedMarkdown(item.content)"></div>
                <span></span>
              </div>
            </el-card>
            <el-empty v-if="recallList.length <= 0"></el-empty>

            <div v-if="debugMode && debugInfo" style="margin-top: 20px; border-top: 1px solid #e8e8e8; padding-top: 15px;">
              <h4 style="margin-top: 0; margin-bottom: 15px; color: #303133;">调试面板</h4>

              <!-- 查询增强信息 -->
              <div v-if="debugInfo.queryEnhance" style="margin-bottom: 12px; padding: 10px; background: #f0f9ff; border-radius: 6px; border-left: 3px solid #3b82f6;">
                <div style="font-weight: 500; font-size: 13px; color: #1e40af; margin-bottom: 6px;">
                  查询增强: {{ debugInfo.queryEnhance.strategy || '无' }}
                </div>
                <div v-if="debugInfo.queryEnhance.originalQuery" style="font-size: 12px; color: #64748b;">
                  原始: {{ debugInfo.queryEnhance.originalQuery }}
                </div>
                <div v-if="debugInfo.queryEnhance.variants?.length" style="font-size: 12px; color: #64748b; margin-top: 4px;">
                  变体: {{ debugInfo.queryEnhance.variants.join(' / ') }}
                </div>
              </div>

              <!-- 弱路径排除警告 -->
              <div v-if="debugInfo.excludedPaths?.length" style="margin-bottom: 12px; padding: 10px; background: #fffbeb; border-radius: 6px; border-left: 3px solid #f59e0b;">
                <div style="font-weight: 500; font-size: 13px; color: #92400e; margin-bottom: 4px;">
                  弱路径排除
                </div>
                <div v-for="p in debugInfo.excludedPaths" :key="p.path" style="font-size: 12px; color: #92400e;">
                  {{ p.path }} (top score {{ p.topScore?.toFixed(3) }} < {{ p.threshold }})
                </div>
              </div>

              <!-- 检索统计 -->
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="耗时(ms)">{{ debugInfo.elapsedMs }}</el-descriptions-item>
                <el-descriptions-item label="检索模式">{{ debugInfo.searchMethod }}</el-descriptions-item>
                <el-descriptions-item label="向量召回">
                  <span :style="{ color: getDebugCount('vectorResultCount', 'firstVectorResultCount') > 0 ? '#10b981' : '#94a3b8' }">{{ getDebugCount('vectorResultCount', 'firstVectorResultCount') }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="关键字召回">
                  <span :style="{ color: getDebugCount('keywordResultCount', 'firstKeywordResultCount') > 0 ? '#10b981' : '#94a3b8' }">{{ getDebugCount('keywordResultCount', 'firstKeywordResultCount') }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="元数据召回">
                  <span :style="{ color: getDebugCount('metadataResultCount', 'firstMetadataResultCount') > 0 ? '#10b981' : '#94a3b8' }">{{ getDebugCount('metadataResultCount', 'firstMetadataResultCount') }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="图谱召回">
                  <span :style="{ color: getDebugCount('graphResultCount', 'firstGraphResultCount') > 0 ? '#10b981' : '#94a3b8' }">{{ getDebugCount('graphResultCount', 'firstGraphResultCount') }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="融合后结果">{{ getDebugCount('fusedCount', 'firstFusedCount') }}</el-descriptions-item>
                <el-descriptions-item label="重排序结果">{{ getDebugCount('rerankedCount', 'firstRerankedCount') }}</el-descriptions-item>
                <el-descriptions-item label="Reranker">{{ debugInfo.rerankerProvider || 'deterministic' }}</el-descriptions-item>
              </el-descriptions>

              <!-- 上下文预算条 -->
              <div v-if="debugInfo.contextBytes" style="margin-top: 12px;">
                <div style="display: flex; justify-content: space-between; font-size: 12px; color: #64748b; margin-bottom: 4px;">
                  <span>上下文预算</span>
                  <span>{{ (debugInfo.contextBytes / 1024).toFixed(1) }}KB / {{ (debugInfo.maxContextBytes / 1024).toFixed(0) }}KB</span>
                </div>
                <el-progress
                  :percentage="Math.min(100, (debugInfo.contextBytes / debugInfo.maxContextBytes) * 100)"
                  :stroke-width="10"
                  :color="debugInfo.contextBytes / debugInfo.maxContextBytes > 0.8 ? '#ef4444' : debugInfo.contextBytes / debugInfo.maxContextBytes > 0.6 ? '#f59e0b' : '#10b981'"
                />
              </div>

              <div v-if="contextPreview" style="margin-top: 15px;">
                <h4 style="color: #303133; margin-bottom: 10px;">最终注入上下文预览</h4>
                <el-input type="textarea" :rows="10" readonly v-model="contextPreview" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 检索设置弹窗：完全对齐原页面样式 -->
      <el-dialog
        v-model="drawer"
        width="55%"
        :before-close="handleClose"
        class="search-setting-dialog"
        draggable
      >
        <template #header="{ close, titleId, titleClass }">
          <span role="heading" aria-level="2" class="el-dialog__title">
            检索设置
          </span>
        </template>
        <!-- <div class="app-container">
          <div class="pagecont-top" v-if="!loading"> -->
        <el-form
          ref="knowledgeBaseRef"
          :model="knowledgeBase"
          :rules="rules"
          label-width="auto"
          @submit.prevent
          v-if="!loading"
        >
          <!-- 1. 检索方式区块：对齐原页面样式 -->
          <div class="section-block">
            <div class="header-text">
              <div class="header-left">
                <div class="blue-bar"></div>
                检索方式
              </div>
            </div>
            <div class="search-method-group">
              <!-- 向量检索 -->
              <div class="search-method-row">
                <div
                  class="search-method-item"
                  @click="knowledgeBase.searchMethod = 'semantic_search'"
                  v-show="knowledgeBase.indexingTechnique !== 'economy'"
                >
                  <el-radio
                    v-model="knowledgeBase.searchMethod"
                    label="semantic_search"
                    class="radio-label-hidden"
                  />
                  <div class="search-title">
                    <span>向量检索</span>
                  </div>
                </div>
                <div
                  v-show="knowledgeBase.indexingTechnique !== 'economy'"
                  class="search-desc"
                >
                  <el-icon class="desc-icon">
                    <WarningFilled />
                  </el-icon>
                  通过生成查询嵌入并查询与其向量表示最相似的文本分段。
                </div>
              </div>
              <!-- 全文检索 -->
              <div class="search-method-row">
                <div
                  class="search-method-item"
                  @click="knowledgeBase.searchMethod = 'full_text_search'"
                >
                  <el-radio
                    v-model="knowledgeBase.searchMethod"
                    label="full_text_search"
                    class="radio-label-hidden"
                  />
                  <div class="search-title">
                    <span>全文检索</span>
                  </div>
                </div>
                <div class="search-desc">
                  <el-icon class="desc-icon">
                    <WarningFilled />
                  </el-icon>
                  索引文档中的所有词汇，从而允许用户查询任意词汇，并返回包含这些词汇的文本片段。
                </div>
              </div>

              <!-- 混合检索 -->
              <div class="search-method-row">
                <div
                  class="search-method-item"
                  @click="knowledgeBase.searchMethod = 'hybrid_search'"
                  v-show="knowledgeBase.indexingTechnique !== 'economy'"
                >
                  <el-radio
                    v-model="knowledgeBase.searchMethod"
                    label="hybrid_search"
                    class="radio-label-hidden"
                  />
                  <div class="search-title">
                    <div class="search-recommend">
                      <span>混合检索</span>
                      <!--                          <el-tag size="small" type="primary" class="recommend-tag">推荐</el-tag>-->
                    </div>
                  </div>
                </div>
                <div
                  v-show="knowledgeBase.indexingTechnique !== 'economy'"
                  class="search-desc"
                >
                  <el-icon class="desc-icon">
                    <WarningFilled />
                  </el-icon>
                  同时执行全文检案和向量检索，并应用重排序步骤，从查询结果中选择匹配用户问题的最佳结果，用户可以选择设置权重或配置重新排序模型。
                </div>
              </div>
            </div>
          </div>
          <el-divider></el-divider>
          <!-- 2. 检索规则区块：非经济模式 -->
          <div class="section-block section-block-rule">
            <div class="header-text">
              <div class="header-left">
                <div class="blue-bar"></div>
                检索规则
              </div>
            </div>
            <div
              class="search-rule-card"
              :class="{
                'no-border': knowledgeBase.searchMethod === 'hybrid_search',
              }"
            >
              <!-- 向量检索规则 -->
              <div
                v-show="knowledgeBase.searchMethod === 'semantic_search'"
                class="query-rule"
              >
                <el-form-item class="form-item-inline">
                  <div class="option-label">Rerank模型启用状态:</div>
                  <el-switch v-model="vectorData.rerankingEnable" />
                </el-form-item>
                <el-form-item class="form-item-inline">
                  <div class="option-label">Rerank模型:</div>
                  <el-select
                    v-model="vectorData.rerankingModelName"
                    placeholder="请选择Rerank模型"
                    :disabled="!vectorData.rerankingEnable"
                    class="select-short"
                  >
                    <el-option-group
                      v-for="group in rerankingModel"
                      :key="group.label.zh_Hans"
                      :label="group.label.zh_Hans"
                    >
                      <el-option
                        v-for="item in group.models"
                        :key="item.model"
                        :label="item.model"
                        :value="item.model"
                      />
                    </el-option-group>
                  </el-select>
                </el-form-item>

                <el-form-item class="form-item-inline" prop="scoreThreshold">
                  <div class="label-input-icon">
                    <div class="label-input">
                      <div class="option-label">Score阈值:</div>
                      <el-input
                        v-model="vectorData.scoreThreshold"
                        :min="0"
                        :max="1"
                        :step="0.1"
                        type="number"
                        class="select-short"
                        placeholder="请输入0-1的小数"
                        :disabled="!vectorData.scoreThresholdEnabled"
                      />
                    </div>
                    <div class="icon-text icon-margin">
                      <el-icon class="desc-icon">
                        <WarningFilled />
                      </el-icon>
                      设置相似度分数的最小阈值
                    </div>
                  </div>
                </el-form-item>

                <el-form-item
                  class="form-item-inline topk-rule-posi-"
                  prop="topK"
                >
                  <div class="label-input-icon" style="margin-left: 37px">
                    <div class="label-input">
                      <div class="option-label">Top K:</div>
                      <el-input
                        v-model="vectorData.topK"
                        class="select-short"
                        type="number"
                        :min="1"
                        :max="10"
                        placeholder="请输入1-10的数字"
                      />
                    </div>
                    <div class="icon-text">
                      <el-icon class="desc-icon">
                        <WarningFilled />
                      </el-icon>
                      选择返回的相似文本数量
                    </div>
                  </div>
                </el-form-item>

                <el-form-item class="form-item-inline">
                  <div class="option-label">Score阈值启用状态:</div>
                  <el-switch
                    v-model="vectorData.scoreThresholdEnabled"
                    class="switch-margin"
                  />
                </el-form-item>
              </div>

              <!-- 全文检索规则 -->
              <div
                v-show="knowledgeBase.searchMethod === 'full_text_search'"
                class="query-rule"
              >
                <el-form-item class="form-item-inline">
                  <div class="option-label">Rerank模型启用状态:</div>
                  <el-switch v-model="fullTextData.rerankingEnable" />
                </el-form-item>
                <el-form-item class="form-item-inline">
                  <div class="option-label">Rerank模型:</div>
                  <el-select
                    v-model="fullTextData.rerankingModelName"
                    placeholder="请选择Rerank模型"
                    :disabled="!fullTextData.rerankingEnable"
                    class="select-short"
                  >
                    <el-option-group
                      v-for="group in rerankingModel"
                      :key="group.label.zh_Hans"
                      :label="group.label.zh_Hans"
                    >
                      <el-option
                        v-for="item in group.models"
                        :key="item.model"
                        :label="item.model"
                        :value="item.model"
                      />
                    </el-option-group>
                  </el-select>
                </el-form-item>

                <el-form-item class="form-item-inline" prop="scoreThreshold">
                  <div class="label-input-icon">
                    <div class="label-input">
                      <div class="option-label">Score阈值:</div>
                      <el-input
                        v-model="fullTextData.scoreThreshold"
                        :min="0"
                        :max="1"
                        :step="0.1"
                        class="select-short"
                        placeholder="请输入0-1的小数"
                        type="number"
                        :disabled="!fullTextData.scoreThresholdEnabled"
                      />
                    </div>
                    <div class="icon-text icon-margin">
                      <el-icon class="desc-icon">
                        <WarningFilled />
                      </el-icon>
                      设置相似度分数的最小阈值
                    </div>
                  </div>
                </el-form-item>
                <el-form-item
                  class="form-item-inline topk-rule-posi-"
                  prop="topK"
                >
                  <div class="label-input-icon" style="margin-left: 37px">
                    <div class="label-input">
                      <div class="option-label">Top K:</div>
                      <el-input
                        v-model="fullTextData.topK"
                        :min="1"
                        :max="10"
                        class="select-short"
                        placeholder="请输入1-10的数字"
                      />
                    </div>
                    <div class="icon-text">
                      <el-icon class="desc-icon">
                        <WarningFilled />
                      </el-icon>
                      选择返回的相似文本数量
                    </div>
                  </div>
                </el-form-item>

                <el-form-item class="form-item-inline">
                  <div class="option-label">Score阈值启用状态:</div>
                  <el-switch
                    v-model="fullTextData.scoreThresholdEnabled"
                    class="switch-margin"
                  />
                </el-form-item>
              </div>

              <!-- 混合检索规则 -->
              <div v-show="knowledgeBase.searchMethod === 'hybrid_search'">
                <el-form-item class="form-item-inline">
                  <div class="quan-rerank">
                    <div
                      @click="mixData.rerankingMode = 'weighted_score'"
                      class="label-input-query"
                    >
                      <div class="search-method-row-mix">
                        <div class="label-input">
                          <el-radio
                            v-model="mixData.rerankingMode"
                            label="weighted_score"
                            class="radio-label-hidden"
                          ></el-radio>
                          <div class="mode-title">权重设置</div>
                        </div>

                        <div class="icon-text">
                          <el-icon class="desc-icon">
                            <WarningFilled />
                          </el-icon>
                          通过调整分配的权重，重新排序策略确定是优先进行语义匹配还是关键字匹配。
                        </div>
                      </div>

                      <div class="query-rule-mix">
                        <el-form-item
                          class="form-item-inline"
                          :prop="
                            mixData.rerankingMode === 'weighted_score'
                              ? 'weightAllocation'
                              : ''
                          "
                        >
                          <div class="option-label">权重分配:</div>
                          <el-input
                            v-model="mixData.keywordWeight"
                            :min="0"
                            :max="1.0"
                            :step="0.1"
                            class="select-short"
                            placeholder="请输入0-1的小数"
                            type="number"
                            :disabled="
                              mixData.rerankingMode === 'reranking_model'
                            "
                          ></el-input>
                        </el-form-item>

                        <el-form-item class="form-item-inline">
                          <div class="option-label">Score阈值启用状态:</div>
                          <el-switch
                            v-model="mixData.weightedScoreThresholdEnabled"
                            class="switch-margin"
                            :disabled="
                              mixData.rerankingMode === 'reranking_model'
                            "
                          />
                        </el-form-item>

                        <el-form-item
                          class="form-item-inline topk-margin-r"
                          :prop="
                            mixData.rerankingMode === 'weighted_score'
                              ? 'topK'
                              : ''
                          "
                        >
                          <div class="option-label">Top K:</div>
                          <el-input
                            v-model="mixData.weightedTopK"
                            :min="1"
                            :max="10"
                            controls-position="right"
                            class="select-short"
                            :disabled="
                              mixData.rerankingMode === 'reranking_model'
                            "
                          />
                          <div class="icon-text" style="margin-left: 52px">
                            <el-icon class="desc-icon">
                              <WarningFilled />
                            </el-icon>
                            选择返回的相似文本数量
                          </div>
                        </el-form-item>

                        <el-form-item
                          class="form-item-inline score-rule-posi-mix"
                          style="margin-left: -28px"
                          :prop="
                            mixData.rerankingMode === 'weighted_score'
                              ? 'scoreThreshold'
                              : ''
                          "
                        >
                          <div class="option-label score-margin">
                            Score阈值:
                          </div>
                          <el-input
                            v-model="mixData.weightedScoreThreshold"
                            :min="0"
                            :max="1"
                            :step="0.1"
                            controls-position="right"
                            class="select-short"
                            placeholder="请输入0-1的小数"
                            type="number"
                            :disabled="
                              !mixData.weightedScoreThresholdEnabled ||
                              mixData.rerankingMode === 'reranking_model'
                            "
                          />
                          <div class="icon-text" style="margin-left: 108px">
                            <el-icon class="desc-icon">
                              <WarningFilled />
                            </el-icon>
                            设置相似度分数的最小阈值
                          </div>
                        </el-form-item>
                      </div>
                    </div>

                    <div
                      @click="mixData.rerankingMode = 'reranking_model'"
                      class="label-input-query"
                    >
                      <div class="search-method-row-mix">
                        <div class="label-input">
                          <el-radio
                            v-model="mixData.rerankingMode"
                            label="reranking_model"
                            class="radio-label-hidden"
                          ></el-radio>
                          <div class="mode-title mode-titlelon">Rerank模型</div>
                        </div>

                        <div class="icon-text">
                          <el-icon class="desc-icon">
                            <WarningFilled />
                          </el-icon>
                          重排序模型将根据候选文档列表与用户问题语义匹配度进行重新排序。
                        </div>
                      </div>

                      <div class="query-rule-mix">
                        <el-form-item class="form-item-inline">
                          <div class="option-label">Rerank模型:</div>
                          <el-select
                            v-model="mixData.rerankingModelName"
                            placeholder="请选择模型"
                            :disabled="
                              mixData.rerankingMode === 'weighted_score' ||
                              !mixData.rerankingEnable
                            "
                            class="select-short"
                          >
                            <el-option-group
                              v-for="group in rerankingModel"
                              :key="group.label.zh_Hans"
                              :label="group.label.zh_Hans"
                            >
                              <el-option
                                v-for="item in group.models"
                                :key="item.model"
                                :label="item.model"
                                :value="item.model"
                              />
                            </el-option-group>
                          </el-select>
                        </el-form-item>

                        <el-form-item class="form-item-inline">
                          <div class="option-label">Score阈值启用状态:</div>
                          <el-switch
                            v-model="mixData.rerankScoreThresholdEnabled"
                            class="switch-margin"
                            :disabled="
                              mixData.rerankingMode === 'weighted_score'
                            "
                          />
                        </el-form-item>
                        <el-form-item
                          class="form-item-inline topk-margin"
                          :prop="
                            mixData.rerankingMode === 'reranking_model'
                              ? 'topK'
                              : ''
                          "
                        >
                          <div class="option-label">Top K:</div>
                          <el-input
                            v-model="mixData.rerankTopK"
                            :min="1"
                            :max="10"
                            controls-position="right"
                            class="select-short"
                            :disabled="
                              mixData.rerankingMode === 'weighted_score'
                            "
                          />
                          <div class="icon-text" style="margin-left: 52px">
                            <el-icon class="desc-icon">
                              <WarningFilled />
                            </el-icon>
                            选择返回的相似文本数量
                          </div>
                        </el-form-item>

                        <el-form-item
                          class="form-item-inline score-rule-posi-mix"
                          style="margin-left: -28px"
                          :prop="
                            mixData.rerankingMode === 'reranking_model'
                              ? 'scoreThreshold'
                              : ''
                          "
                        >
                          <div class="option-label score-margin">
                            Score阈值:
                          </div>
                          <el-input
                            v-model="mixData.rerankScoreThreshold"
                            :min="0"
                            :max="1"
                            :step="0.1"
                            controls-position="right"
                            class="select-short"
                            placeholder="请输入0-1的小数"
                            type="number"
                            :disabled="
                              !mixData.rerankScoreThresholdEnabled ||
                              mixData.rerankingMode === 'weighted_score'
                            "
                          />
                          <div class="icon-text" style="margin-left: 108px">
                            <el-icon class="desc-icon">
                              <WarningFilled />
                            </el-icon>
                            设置相似度分数的最小阈值
                          </div>
                        </el-form-item>
                      </div>
                    </div>
                  </div>
                </el-form-item>
              </div>
            </div>
          </div>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button size="small" @click="cancel">取 消</el-button>
            <el-button type="primary" size="small" @click="submitForm"
              >确 定</el-button
            >
          </div>
        </template>
        <!-- </div>
        </div> -->
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import MarkdownIt from "markdown-it";
import "highlight.js/styles/xcode.min.css";
import hljs from "highlight.js";
import "@/assets/app/style/dify_table_2.0.css";
import useUserStore from "@/store/system/user.js";
import word from "@/assets/app/office/WORD.png";
import excel from "@/assets/app/office/ECEL.png";
import pdf from "@/assets/app/office/PDF.png";
import ppt from "@/assets/app/office/PPT.png";
import tet from "@/assets/app/office/TET.png";
import { WarningFilled, Operation } from "@element-plus/icons-vue"; // 新增导入WarningFilled

import {
  getKnowledgeBase,
  getRerank,
  getTextEmbedding,
  recallTest,
  recallDebug,
  clearRagCache
} from "@/api/kmc/knowledgeBase/knowledgeBase.js";
import { getFileFormat } from "@/utils/app/chat/chat.js";
import { listLog } from "@/api/kmc/knowledgeBase/log.js";
import { updateKnowledgeBase } from "@/api/kmc/knowledgeBase/knowledgeBase.js";
import { watchEffect } from "vue";

const { proxy } = getCurrentInstance();
const userStore = useUserStore();

const defaultSort = ref({ prop: "createTime", order: "descending" });

const debugMode = ref(false);
const debugInfo = ref(null);
const contextPreview = ref("");
const clearingCache = ref(false);

const drawer = ref(false);
const loading = ref(false);
const knowledgeBase = ref({
  indexingTechnique: "high_quality", // 默认值
  searchMethod: "hybrid_search", // 默认值
  topK: 1,
  query: "",
});
const recallList = ref([]);
const recallLogList = ref([]);
const fileImg = {
  doc: word,
  docx: word,
  xlsx: excel,
  xls: excel,
  ppt: ppt,
  pptx: ppt,
  pdf: pdf,
  txt: tet,
};
const vectorData = ref({
  rerankingEnable: false,
  rerankingModelName: "",
  topK: 1,
  scoreThresholdEnabled: false,
  scoreThreshold: 0,
});
const fullTextData = ref({
  rerankingEnable: false,
  rerankingModelName: "",
  topK: 1,
  scoreThresholdEnabled: false,
  scoreThreshold: 0,
});
const mixData = ref({
  rerankingEnable: true,
  rerankingModelName: "",
  topK: 1,
  scoreThresholdEnabled: false,
  scoreThreshold: 0,
  weightedTopK: 1,
  weightedScoreThreshold: 0,
  weightedScoreThresholdEnabled: false,
  rerankTopK: 1,
  rerankScoreThreshold: 0,
  rerankScoreThresholdEnabled: false,
  keywordWeight: 0.5,
  vectorWeight: 0.5,
  rerankingMode: "weighted_score",
});
const rerankingModel = ref([]); // 补充模型数据容器
const dataList = ref({});
const route = useRoute();

function getDebugCount(legacyKey, phaseKey) {
  const value = debugInfo.value?.[phaseKey] ?? debugInfo.value?.[legacyKey];
  return Number.isFinite(Number(value)) ? Number(value) : 0;
}

// 补充表单验证规则（对齐原页面）
const rules = ref({
  topK: [
    {
      // 移除默认 required: true，将空值校验整合到自定义 validator 中
      validator: (rule, value, callback) => {
        let currentTopK = null;
        // 1. 保持原有逻辑，获取业务实际的 Top K 值
        if (knowledgeBase.value.indexingTechnique === "economy") {
          currentTopK = knowledgeBase.value.topK;
        } else if (knowledgeBase.value.searchMethod === "hybrid_search") {
          currentTopK =
            mixData.value.rerankingMode === "weighted_score"
              ? mixData.value.weightedTopK
              : mixData.value.rerankTopK;
        } else if (knowledgeBase.value.searchMethod === "semantic_search") {
          currentTopK = vectorData.value.topK;
        } else if (knowledgeBase.value.searchMethod === "full_text_search") {
          currentTopK = fullTextData.value.topK;
        }

        // 2. 优先校验空值（针对实际业务有效值 currentTopK）
        // 覆盖 null/undefined/空字符串 等空值场景
        if (
          currentTopK === null ||
          currentTopK === undefined ||
          currentTopK === ""
        ) {
          callback(new Error("Top K不能为空"));
          return; // 空值校验失败，直接返回，不执行后续范围校验
        }

        // 3. 转换为数值类型，避免字符串格式导致校验异常
        const numTopK = Number(currentTopK);
        // 校验是否为有效数字
        if (isNaN(numTopK)) {
          callback(new Error("Top K必须为有效数字"));
          return;
        }

        // 4. 原有范围校验逻辑
        if (numTopK < 1 || numTopK > 10) {
          callback(new Error("Top K必须在 1 到 10 之间"));
        } else {
          callback(); // 校验通过，执行回调
        }
      },
      // 统一触发时机，覆盖 blur（失去焦点）和 change（值/关联字段变化）场景
      trigger: ["blur", "change"],
    },
  ],
  scoreThreshold: [
    {
      required: true,
      message: "Score阈值不能为空",
      trigger: "blur",
      validator: (rule, value, callback) => {
        let enabled = false;
        let currentScoreThreshold = null;
        if (knowledgeBase.value.searchMethod === "hybrid_search") {
          if (mixData.value.rerankingMode === "weighted_score") {
            enabled = mixData.value.weightedScoreThresholdEnabled;
            currentScoreThreshold = mixData.value.weightedScoreThreshold;
          } else {
            enabled = mixData.value.rerankScoreThresholdEnabled;
            currentScoreThreshold = mixData.value.rerankScoreThreshold;
          }
        } else if (knowledgeBase.value.searchMethod === "semantic_search") {
          enabled = vectorData.value.scoreThresholdEnabled;
          currentScoreThreshold = vectorData.value.scoreThreshold;
        } else if (knowledgeBase.value.searchMethod === "full_text_search") {
          enabled = fullTextData.value.scoreThresholdEnabled;
          currentScoreThreshold = fullTextData.value.scoreThreshold;
        }
        if (enabled && !currentScoreThreshold && currentScoreThreshold !== 0) {
          callback(new Error("Score阈值不能为空"));
        } else {
          callback();
        }
      },
    },
    {
      validator: (rule, value, callback) => {
        let currentScoreThreshold = null;
        if (knowledgeBase.value.searchMethod === "hybrid_search") {
          currentScoreThreshold =
            mixData.value.rerankingMode === "weighted_score"
              ? mixData.value.weightedScoreThreshold
              : mixData.value.rerankScoreThreshold;
        } else if (knowledgeBase.value.searchMethod === "semantic_search") {
          currentScoreThreshold = vectorData.value.scoreThreshold;
        } else if (knowledgeBase.value.searchMethod === "full_text_search") {
          currentScoreThreshold = fullTextData.value.scoreThreshold;
        }
        if (!currentScoreThreshold && currentScoreThreshold !== 0) {
          callback();
          return;
        }
        if (currentScoreThreshold < 0 || currentScoreThreshold > 1) {
          callback(new Error("Score阈值必须在 0 到 1 之间"));
        } else {
          const decimalLen = (
            currentScoreThreshold.toString().split(".")[1] || ""
          ).length;
          if (decimalLen > 1) {
            callback(new Error("Score阈值最多保留 1 位小数"));
          } else {
            callback();
          }
        }
      },
      trigger: ["blur", "change"],
    },
  ],
  weightAllocation: [
    {
      required: true,
      message: "权重分配不能为空",
      trigger: "blur",
      validator: (rule, value, callback) => {
        // 仅在混合检索-权重模式下校验必填
        if (
          knowledgeBase.value.searchMethod === "hybrid_search" &&
          mixData.value.rerankingMode === "weighted_score"
        ) {
          if (
            !mixData.value.keywordWeight &&
            mixData.value.keywordWeight !== 0
          ) {
            callback(new Error("权重分配不能为空"));
          } else {
            callback();
          }
        } else {
          callback();
        }
      },
    },
    {
      validator: (rule, value, callback) => {
        const currentWeight = mixData.value.keywordWeight;
        // 为空时不校验（避免禁用状态下的校验报错）
        if (!currentWeight && currentWeight !== 0) {
          callback();
          return;
        }
        // 校验0-1范围
        if (currentWeight < 0 || currentWeight > 1) {
          callback(new Error("权重分配必须在 0 到 1 之间"));
        } else {
          // 校验最多1位小数
          const decimalLen = (currentWeight.toString().split(".")[1] || "")
            .length;
          if (decimalLen > 1) {
            callback(new Error("权重分配最多保留 1 位小数"));
          } else {
            callback();
          }
        }
      },
      trigger: ["blur", "change"],
    },
  ],
});

// 权重计算监听
watch(
  () => mixData.value.keywordWeight,
  (newVal) => {
    mixData.value.vectorWeight = Math.round((1.0 - newVal) * 100) / 100;
  },
  { immediate: true }
);

watch(
  () => route.params.kbId,
  (newKbId) => {
    getBase(newKbId);
    getLogList();
  },
  { deep: true, immediate: true }
);

const getFileType = (name) => {
  if (!name) {
    return tet;
  }
  return fileImg[getFileFormat(name)] || tet;
};

function getScorePercent(score) {
  const value = Number(score);
  if (!Number.isFinite(value)) {
    return 0;
  }
  return value >= 1 ? 100 : Math.max(0, Math.min(100, value * 100));
}

function getScoreText(score) {
  const value = Number(score);
  return Number.isFinite(value) ? value.toFixed(2) : '0.00';
}

const md = new MarkdownIt({
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const copyHtml = `<div id="copy" data-copy='${str}' style="position: absolute; right: 10px; top: 5px; color: #fff;cursor: pointer;">复制</div>`;
        return `<pre style="position: relative;">${copyHtml}<code class="hljs">${
          hljs.highlight(lang, str, true).value
        }</code></pre>`;
      } catch (__) {}
    }
    return ``;
  },
});

/** 渲染 markdown */
const renderedMarkdown = (content) => {
  return md.render(content);
};

function getBase(id) {
  if (id) {
    getKnowledgeBase(id).then((res) => {
      knowledgeBase.value = res.data;
      if (knowledgeBase.value.searchMethod == "semantic_search") {
        Object.assign(vectorData.value, {
          rerankingEnable: knowledgeBase.value.rerankingEnable,
          rerankingModelName: knowledgeBase.value.rerankingModelName,
          topK: knowledgeBase.value.topK,
          scoreThresholdEnabled: knowledgeBase.value.scoreThresholdEnabled,
          scoreThreshold: knowledgeBase.value.scoreThreshold,
        });
      } else if (knowledgeBase.value.searchMethod == "full_text_search") {
        Object.assign(fullTextData.value, {
          rerankingEnable: knowledgeBase.value.rerankingEnable,
          rerankingModelName: knowledgeBase.value.rerankingModelName,
          topK: knowledgeBase.value.topK,
          scoreThresholdEnabled: knowledgeBase.value.scoreThresholdEnabled,
          scoreThreshold: knowledgeBase.value.scoreThreshold,
        });
      } else if (knowledgeBase.value.searchMethod == "hybrid_search") {
        Object.assign(mixData.value, {
          rerankingEnable: knowledgeBase.value.rerankingEnable,
          rerankingModelName: knowledgeBase.value.rerankingModelName,
          keywordWeight: knowledgeBase.value.keywordWeight || 0.5,
          vectorWeight: knowledgeBase.value.vectorWeight || 0.5,
          rerankingMode: knowledgeBase.value.rerankingMode || "weighted_score",
          weightedTopK:
            knowledgeBase.value.rerankingMode === "weighted_score"
              ? knowledgeBase.value.topK
              : 1,
          weightedScoreThreshold:
            knowledgeBase.value.rerankingMode === "weighted_score"
              ? knowledgeBase.value.scoreThreshold
              : 0,
          weightedScoreThresholdEnabled:
            knowledgeBase.value.rerankingMode === "weighted_score"
              ? knowledgeBase.value.scoreThresholdEnabled
              : false,
          rerankTopK:
            knowledgeBase.value.rerankingMode === "reranking_model"
              ? knowledgeBase.value.topK
              : 1,
          rerankScoreThreshold:
            knowledgeBase.value.rerankingMode === "reranking_model"
              ? knowledgeBase.value.scoreThreshold
              : 0,
          rerankScoreThresholdEnabled:
            knowledgeBase.value.rerankingMode === "reranking_model"
              ? knowledgeBase.value.scoreThresholdEnabled
              : false,
          topK: knowledgeBase.value.topK,
          scoreThreshold: knowledgeBase.value.scoreThreshold,
          scoreThresholdEnabled: knowledgeBase.value.scoreThresholdEnabled,
        });
      }
      dataList.value = res.data;
      knowledgeBase.value.query = "";
    });
  }
}

const cancel = () => {
  knowledgeBase.value = { ...dataList.value };
  drawer.value = false;
};

const submitForm = () => {
  proxy.$refs["knowledgeBaseRef"].validate((valid) => {
    if (valid) {
      if (knowledgeBase.value.indexingTechnique !== "economy") {
        if (knowledgeBase.value.searchMethod === "semantic_search") {
          knowledgeBase.value.rerankingEnable =
            vectorData.value.rerankingEnable;
          knowledgeBase.value.rerankingModelName =
            vectorData.value.rerankingModelName;
          knowledgeBase.value.topK = vectorData.value.topK;
          knowledgeBase.value.scoreThresholdEnabled =
            vectorData.value.scoreThresholdEnabled;
          knowledgeBase.value.scoreThreshold = vectorData.value.scoreThreshold;
        } else if (knowledgeBase.value.searchMethod === "full_text_search") {
          knowledgeBase.value.rerankingEnable =
            fullTextData.value.rerankingEnable;
          knowledgeBase.value.rerankingModelName =
            fullTextData.value.rerankingModelName;
          knowledgeBase.value.topK = fullTextData.value.topK;
          knowledgeBase.value.scoreThresholdEnabled =
            fullTextData.value.scoreThresholdEnabled;
          knowledgeBase.value.scoreThreshold =
            fullTextData.value.scoreThreshold;
        } else if (knowledgeBase.value.searchMethod === "hybrid_search") {
          if (mixData.value.rerankingMode === "weighted_score") {
            mixData.value.topK = mixData.value.weightedTopK;
            mixData.value.scoreThreshold = mixData.value.weightedScoreThreshold;
            mixData.value.scoreThresholdEnabled =
              mixData.value.weightedScoreThresholdEnabled;
          } else {
            mixData.value.topK = mixData.value.rerankTopK;
            mixData.value.scoreThreshold = mixData.value.rerankScoreThreshold;
            mixData.value.scoreThresholdEnabled =
              mixData.value.rerankScoreThresholdEnabled;
          }

          knowledgeBase.value.rerankingEnable = mixData.value.rerankingEnable;
          knowledgeBase.value.rerankingModelName =
            mixData.value.rerankingModelName;
          knowledgeBase.value.topK = mixData.value.topK;
          knowledgeBase.value.scoreThresholdEnabled =
            mixData.value.scoreThresholdEnabled;
          knowledgeBase.value.scoreThreshold = mixData.value.scoreThreshold;
          knowledgeBase.value.keywordWeight = mixData.value.keywordWeight;
          knowledgeBase.value.vectorWeight = mixData.value.vectorWeight;
          knowledgeBase.value.rerankingMode = mixData.value.rerankingMode;
        }
      }

      if (
        knowledgeBase.value.rerankingModelName &&
        knowledgeBase.value.rerankingModelName !== ""
      ) {
        const selectedModel = knowledgeBase.value.rerankingModelName;
        for (const group of rerankingModel.value) {
          const found = group.models.find(
            (item) => item.model === selectedModel
          );
          if (found) {
            knowledgeBase.value.rerankingProviderName = group.provider;
            break;
          }
        }
      }

      drawer.value = false;
      updateKnowledgeBase(knowledgeBase.value).then(() => {
        proxy.$modal.msgSuccess("设置保存成功");
      }).catch(() => {
        proxy.$modal.msgError("设置保存失败");
      });
    }
  });
};

function getRecall() {
  loading.value = true;
  if (debugMode.value) {
    recallDebug(knowledgeBase.value)
      .then((res) => {
        loading.value = false;
        recallList.value = res.data.results || [];
        debugInfo.value = res.data.debugInfo || {};
        contextPreview.value = res.data.contextPreview || "";
        getLogList();
      })
      .catch((err) => {
        loading.value = false;
      });
  } else {
    recallTest(knowledgeBase.value)
      .then((res) => {
        loading.value = false;
        recallList.value = res.data;
        debugInfo.value = null;
        contextPreview.value = "";
        getLogList();
      })
      .catch((err) => {
        loading.value = false;
      });
  }
}

function handleClearCache() {
  const kbId = route.params.kbId;
  if (!kbId) return;
  proxy.$modal
    .confirm("确定要清除该知识库的检索缓存吗？")
    .then(() => {
      clearingCache.value = true;
      return clearRagCache(kbId);
    })
    .then(() => {
      clearingCache.value = false;
      proxy.$modal.msgSuccess("清除缓存成功");
    })
    .catch(() => {
      clearingCache.value = false;
    });
}

const getSourceText = (source) => {
  const map = {
    'vector': '向量检索',
    'keyword': '关键字检索',
    'metadata': '元数据检索',
    'rerank': 'Rerank',
    'agent': 'Agent召回',
    'cache': '缓存'
  };
  return map[source] || source;
};

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  const queryParams = {
    orderByColumn: column.prop,
    isAsc: column.order,
  };
  getLogList(queryParams);
}

function getLogList(queryParams) {
  listLog({
    knowledgeId: route.params.kbId,
    creatorId: userStore.id,
    pageNum: 1,
    pageSize: 4,
    orderByColumn: queryParams ? queryParams.orderByColumn : "createTime",
    isAsc: queryParams ? queryParams.isAsc : "desc",
  }).then((res) => {
    recallLogList.value = res.data.list;
  });
}

const handleClose = (done) => {
  done();
};

const getSearchName = () => {
  const searchObj = {
    semantic_search: "向量检索",
    full_text_search: "全文检索",
    hybrid_search: "混合检索",
  };
  return searchObj[knowledgeBase.value.searchMethod];
};

const goRecallLog = () => {
  proxy.$router.push({
    path: `/kmc/${route.params.kbId}/recallLog`,
  });
};

function init() {
  // getTextEmbedding().then(response => {
  //   embeddingModel.value = response.data;
  // });
  getRerank().then((response) => {
    rerankingModel.value = response.data;
    if (rerankingModel.value.length) {
      const defaultModel = rerankingModel.value[0].models[0].model;
      const defaultModelProviderName = rerankingModel.value[0].provider;
      //只有值为空时，才赋值默认值（避免覆盖接口返回的已有值）
      if (!vectorData.value.rerankingModelName) {
        vectorData.value.rerankingModelName = defaultModel;
        vectorData.value.rerankingProviderName = defaultModelProviderName;
      }
      if (!fullTextData.value.rerankingModelName) {
        fullTextData.value.rerankingModelName = defaultModel;
        fullTextData.value.rerankingProviderName = defaultModelProviderName;
      }
      if (!mixData.value.rerankingModelName) {
        // 重点：mixData 只在空时赋值
        mixData.value.rerankingModelName = defaultModel;
        mixData.value.rerankingProviderName = defaultModelProviderName;
      }
      // 兜底默认值（仅初始化，不覆盖已有值）
      if (vectorData.value.rerankingEnable === null) {
        vectorData.value.rerankingEnable = false;
      }
      if (fullTextData.value.rerankingEnable === null) {
        fullTextData.value.rerankingEnable = false;
      }
      if (mixData.value.rerankingEnable === null) {
        mixData.value.rerankingEnable = true;
      }
    }
    loading.value = false; // 新增：模型数据加载完成，关闭加载
  });
}

// 初始化
init();
</script>

<style scoped lang="scss">
.app-container {
  .pagecont-top {
    padding: 0px;
    //background-color: #f0f2f5;
  }

  // 区块样式
  .section-block {
    background-color: #ffffff;
    padding: 0px 0px 0px 20px;

    .blue-bar {
      background-color: #2666fb;
      width: 6px;
      height: 16px;
      margin-right: 10px;
      border-radius: 3px;
    }

    .header-text {
      display: flex;
      align-items: center;
      margin: 10px 0px 0px 0px;
      font-weight: 500;
      font-size: 14px;
      color: #333;
    }

    .header-left {
      display: flex;
      align-items: center;
      color: #666666;
      font-size: 16px;
      line-height: 24px;
      font-family: PingFangSC-Medium-;
    }
  }
}

// 检索方式组
.search-method-group {
  display: flex;
  gap: 1px;
  flex-wrap: wrap;
  flex-direction: column;

  .search-method-row {
    @extend %search-method;
  }

  .search-method-item {
    display: flex;
    //padding-top: 5px;
    cursor: pointer;
    transition: all 0.2s;
    align-items: center;
    margin-left: 17px;

    .search-title {
      min-width: 56px;
      font-size: 14px;
      font-family: PingFang SC-Medium;
      color: rgba(0, 0, 0, 0.85);
      line-height: 22px;

      .search-recommend {
        display: flex;
        align-items: center;

        .recommend-tag {
          margin-left: 8px;
        }
      }
    }
  }

  .search-desc {
    display: flex;
    align-items: center;
    font-size: 14px;
    line-height: 22px;
    font-family: Microsoft YaHei-Regular;
    margin-left: 18px;
    //width: 532px;
    //height: 32px;
    //padding: 6px 0px 6px 18px;
    //background-color: #FFFDF0;
    //border: 1px solid #FFE58F;
    color: #888;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    .desc-icon {
      margin-right: 3px;
      //color: #EFBD47;
    }
  }
}

// 检索规则卡片
.search-rule-card {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 0px 19px 23px 19px;
  margin: 10px 13px 0px 0px;
  // height: 490px;

  .query-rule-mix {
    padding: 20px 0px 10px 15px;
    margin: 10px 0px 10px 0px;
    border: 1px solid #ebebeb;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    justify-items: start;

    .form-item-inline {
      .option-label {
        font-size: 14px;
        font-family: PingFangSC-Regular-;
        line-height: 22px;
        color: #333333;
      }

      .score-margin {
        margin-left: 28px;
      }
    }

    .topk-margin {
      margin-left: 37px;
    }

    .topk-margin-r {
      margin-left: 19px;
    }
  }

  .query-rule {
    display: grid;
    //grid-template-columns: 550px 1fr 400px;
    grid-template-columns: repeat(2, 1fr);
    padding: 20px 20px 20px 20px;
    justify-items: start;

    @media (min-width: 1280px) {
      grid-template-columns: repeat(2, minmax(200px, 2fr));
    }
    @media (min-width: 1366px) {
      grid-template-columns: repeat(2, minmax(250px, 2fr));
    }
    @media (min-width: 1440px) {
      grid-template-columns: repeat(2, minmax(300px, 2fr));
    }
    @media (min-width: 1536px) {
      grid-template-columns: repeat(2, minmax(350px, 2fr));
    }
    @media (min-width: 1712px) {
      grid-template-columns: repeat(2, minmax(400px, 2fr));
    }
    @media (min-width: 2560px) {
      grid-template-columns: repeat(2, minmax(500px, 2fr));
    }

    .form-item-inline {
      .option-label {
        font-family: PingFangSC-Regular-;
        font-size: 14px;
        line-height: 22px;
        color: #333333;
      }
    }
  }

  .form-item-inline {
    display: flex;
    align-items: center;

    .option-label {
      margin-right: 12px;
    }

    .quan-rerank {
      flex: 1;
    }

    .label-input-icon {
      display: flex;
      align-items: flex-start;
      flex-direction: column;

      .label-input {
        display: flex;
        align-items: center;
        //margin-bottom: 8px;
      }

      .icon-text {
        display: flex;
        align-items: center;
        font-size: 14px;
        line-height: 22px;
        font-family: Microsoft YaHei-Regular;
        width: 372px;
        height: 32px;
        margin-left: 52px;
        padding: 6px 0px 6px 0px;
        //background-color: #FFFDF0;
        //border: 1px solid #FFE58F;
        color: #888;

        .desc-icon {
          margin-right: 3px;
          //color: #EFBD47;
        }
      }

      .icon-margin {
        margin-left: 80px;
      }
    }

    .label-input-query {
      display: flex;
      flex-direction: column;

      .search-method-row-mix {
        @extend %search-method;
        padding-top: 0px !important;
      }

      .label-input {
        display: flex;
        align-items: center;

        .mode-title {
          min-width: 56px;
          font-family: PingFang SC-Medium;
          font-size: 14px;
          line-height: 22px;
          color: rgba(0, 0, 0, 0.85);
        }
        .mode-titlelon {
          min-width: 74px;
        }
      }

      .icon-text {
        display: flex;
        align-items: center;
        font-size: 14px;
        font-family: Microsoft YaHei-Regular;
        line-height: 22px;
        //width: 552px;
        //height: 32px;
        padding: 6px 0px 6px 0px;
        //background-color: #fffdf1;
        //border: 1px solid #f5ecbe;
        color: #888;
        margin-left: 17px;

        .desc-icon {
          margin-right: 3px;
          //color: #ebbd4d;
        }
      }
    }
    .select-short {
      width: 372px;
      height: 32px;
      @media (max-width: 1260px) {
        width: 100px;
      }
      @media (min-width: 1260px) {
        width: 170px;
      }
      @media (min-width: 1366px) {
        width: 190px;
      }
      @media (min-width: 1440px) {
        width: 210px;
      }
      @media (min-width: 1536px) {
        width: 270px;
      }
      @media (min-width: 1712px) {
        width: 300px;
      }
      @media (min-width: 2560px) {
        width: 400px;
      }
      ::v-deep {
        .el-select__wrapper {
          width: 100% !important;
          height: 100% !important;
          min-height: unset !important;
          align-items: center;
          border-radius: 3px !important;
        }
      }
    }

    .switch-margin {
      margin-left: 8px;
    }
  }
}

// 按钮区
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  margin: 10px 10px 10px 0px;
}

.radio-label-hidden {
  ::v-deep .el-radio__label {
    display: none !important;
  }

  margin-right: 15px;
}

// 混合检索时去除 search-rule-card 边框
.search-rule-card.no-border {
  border: none !important;
}

// 原有页面样式保留
.recall-glass-app.app-container {
  background: linear-gradient(135deg, #f6f8fd 0%, #f1f5f9 100%) !important;
  background-image: 
    radial-gradient(at 0% 0%, rgba(38, 102, 251, 0.08) 0, transparent 50%), 
    radial-gradient(at 100% 0%, rgba(0, 208, 255, 0.08) 0, transparent 50%) !important;
  padding: 24px !important;
  min-height: calc(100vh - 84px);
}

.container {
  font-family: PingFangSC, PingFang SC, sans-serif;
  display: flex;
  gap: 24px;
  overflow: hidden;
  height: 100%;
  background-color: transparent;

  :deep(.el-progress-bar__inner) {
    background: var(--el-color-primary);
  }

  :deep(.el-progress-bar__innerText) {
    height: 100%;
  }
}

.left-container {
  flex: 1;
  min-width: 0;
  gap: 20px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding-right: 5px;
}

.right-container {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  
  :deep(.el-card) {
    border-radius: 12px;
    border: 1px solid rgba(0,0,0,0.04);
    box-shadow: 0 2px 10px rgba(0,0,0,0.02);
    transition: all 0.3s ease;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 30px rgba(0,0,0,0.08);
    }
    
    .el-card__header {
      border-bottom: 1px solid rgba(0,0,0,0.03);
      padding: 15px 20px;
      background-color: rgba(248,250,252, 0.5);
    }
    
    .el-card__body {
      padding: 20px;
    }
  }

  .title {
    display: flex;
    align-items: center;

    img {
      width: 20px;
      margin-right: 5px;
    }

    span {
      --tw-text-opacity: 1;
      color: rgb(52 64 84 / var(--tw-text-opacity));
      font-size: 14px;
      font-weight: 500;
      text-overflow: ellipsis;
      overflow: hidden;
      white-space: nowrap;
    }
  }
}

.border-item {
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.08);
  display: inline-flex;
  flex-direction: column;
}

.border-item .border-item-head {
  height: 64px;
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0,0,0,0.03);
  gap: 16px;
}

.border-item .border-item-head .head-title {
  font-size: 18px;
  color: #1e293b;
  display: flex;
  align-items: center;
  font-family: PingFang SC, sans-serif;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.border-item .border-item-head .head-title:before {
  display: none;
}

.border-item .border-item-body {
  height: calc(100% - 50px);
  position: relative;
  margin: 20px;
}

.module-1 {
  height: 420px;
  flex-shrink: 0;

  .border-item-body {
    border-radius: 12px;
    border: 1px solid rgba(0,0,0,0.04);
    overflow: hidden;

    .title {
      padding: 12px 20px;
      background-color: transparent;
      border-bottom: 1px solid rgba(0,0,0,0.03);
      width: 100%;
      display: flex;
      align-items: center;

      span {
        font-size: 15px;
        color: #475569;
        font-weight: 500;
      }
    }

    .content {
      display: flex;
      flex-direction: column;
      padding-bottom: 15px;
      background-color: #f1f5f9;

      .input-text {
        height: 220px;
        padding: 15px 20px;

        :deep(.el-textarea__inner) {
          height: 100%;
          box-shadow: none;
          background: transparent;
          border: none;
          resize: none;
          padding: 0;
          font-size: 14px;
          color: #334155;
          
          &:focus {
            box-shadow: none;
          }
        }
      }

      .input-btn {
        margin-left: auto;
        margin-right: 20px;
        width: 120px;
        border-radius: 8px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.05);
      }
    }
  }
}

.module-2 {
  flex: 1;
  height: auto;
  min-height: 350px;
  flex-shrink: 0;
}

%search-method {
  display: flex;
  align-items: center;
  padding-top: 10px;
}

//// 弹窗样式优化
:deep(.search-setting-dialog .el-dialog__body) {
  padding: 0px !important;
  //padding: 0px;
  //width: 80%;
  //overflow-y: auto;
}

:deep(.el-divider--horizontal) {
  margin: 15px 0px 15px 0px;
}

//:deep(.search-setting-dialog .el-dialog__header) {
//  padding: 15px 20px;
//  border-bottom: 1px solid #e8e8e8;
//}
//:deep(.search-setting-dialog .el-dialog__title) {
//  font-size: 16px;
//  font-weight: 500;
//}
:deep(.topk-rule-posi- .el-form-item__error) {
  margin-left: 37px !important; // 距离输入框底部的距离
}

:deep(.score-rule-posi-mix .el-form-item__error) {
  margin-left: 28px !important; // 距离输入框底部的距离
}
:deep(.el-dialog__body) {
  overflow-y: auto !important;
  max-height: calc(100vh - 200px);
  padding: 20px 40px !important;
}
</style>
