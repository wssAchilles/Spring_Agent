<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识图谱</span>
          <el-button type="primary" @click="showAddNodeDialog = true">添加节点</el-button>
        </div>
      </template>
      
      <div v-if="loading" class="loading-container">
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="graphData.nodes.length === 0" class="empty-container">
        <el-empty description="暂无图谱数据">
          <el-button type="primary" @click="showAddNodeDialog = true">创建第一个节点</el-button>
        </el-empty>
      </div>
      
      <div v-else ref="graphContainer" class="graph-container"></div>
    </el-card>

    <!-- Add Node Dialog -->
    <el-dialog v-model="showAddNodeDialog" title="添加节点" width="500px">
      <el-form :model="nodeForm" label-width="80px">
        <el-form-item label="节点名称">
          <el-input v-model="nodeForm.label" placeholder="请输入节点名称" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-select v-model="nodeForm.type" placeholder="请选择节点类型">
            <el-option label="概念" value="concept" />
            <el-option label="技术" value="technology" />
            <el-option label="工具" value="tool" />
            <el-option label="框架" value="framework" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="nodeForm.description" type="textarea" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddNodeDialog = false">取消</el-button>
        <el-button type="primary" @click="addNode">确定</el-button>
      </template>
    </el-dialog>

    <!-- Add Edge Dialog -->
    <el-dialog v-model="showAddEdgeDialog" title="添加关系" width="500px">
      <el-form :model="edgeForm" label-width="80px">
        <el-form-item label="起始节点">
          <el-select v-model="edgeForm.sourceId" placeholder="请选择起始节点">
            <el-option v-for="node in graphData.nodes" :key="node.id" :label="node.label" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标节点">
          <el-select v-model="edgeForm.targetId" placeholder="请选择目标节点">
            <el-option v-for="node in graphData.nodes" :key="node.id" :label="node.label" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关系类型">
          <el-input v-model="edgeForm.label" placeholder="请输入关系类型" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddEdgeDialog = false">取消</el-button>
        <el-button type="primary" @click="addEdge">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { Loading } from '@element-plus/icons-vue';
import request from '@/utils/request';

// vis-network will be loaded dynamically
let Network = null;
let DataSet = null;

const loading = ref(true);
const graphContainer = ref(null);
const showAddNodeDialog = ref(false);
const showAddEdgeDialog = ref(false);

const graphData = ref({
  nodes: [],
  edges: []
});

const nodeForm = ref({
  label: '',
  type: 'concept',
  description: ''
});

const edgeForm = ref({
  sourceId: null,
  targetId: null,
  label: 'related_to'
});

const fetchGraphData = async () => {
  try {
    loading.value = true;
    const response = await request({
      url: '/kg/graph/data',
      method: 'get'
    });
    const data = response.data || response;
    graphData.value = {
      nodes: data.nodes || [],
      edges: data.edges || []
    };

    loading.value = false;
    await nextTick();
    await nextTick();

    if (graphData.value.nodes.length > 0) {
      renderGraph();
    }
  } catch (error) {
    console.error('Failed to fetch graph data:', error);
    ElMessage.error('获取图谱数据失败，请确认后端服务已启动');
    loading.value = false;
  }
};

const renderGraph = async () => {
  if (!graphContainer.value || graphData.value.nodes.length === 0) return;

  try {
    if (!Network) {
      const visModule = await import('vis-network/standalone');
      Network = visModule.Network;
      DataSet = visModule.DataSet;
    }

    if (!Network || !DataSet) {
      ElMessage.error('加载图谱组件失败');
      return;
    }

    const nodes = new DataSet(
      graphData.value.nodes.map(node => ({
        id: node.id,
        label: node.label,
        title: `${node.label}\n类型: ${node.type}${node.properties ? '\n' + JSON.parse(node.properties).description : ''}`,
        color: getNodeColor(node.type),
        font: { size: 14, color: '#333' },
        size: 25
      }))
    );

    const edges = new DataSet(
      graphData.value.edges.map(edge => ({
        id: edge.id,
        from: edge.source,
        to: edge.target,
        label: edge.label,
        arrows: 'to',
        font: { size: 10, align: 'middle' },
        color: { color: '#848484', highlight: '#409EFF' }
      }))
    );

    const data = { nodes, edges };
    const options = {
      physics: {
        enabled: true,
        solver: 'forceAtlas2Based',
        forceAtlas2Based: {
          gravitationalConstant: -50,
          centralGravity: 0.005,
          springLength: 200,
          springConstant: 0.18
        },
        stabilization: { iterations: 150 }
      },
      interaction: {
        hover: true,
        tooltipDelay: 200,
        zoomView: true,
        dragView: true
      },
      nodes: {
        shape: 'dot',
        size: 25,
        borderWidth: 2,
        shadow: true
      },
      edges: {
        smooth: {
          type: 'continuous'
        }
      }
    };

    new Network(graphContainer.value, data, options);
  } catch (err) {
    console.error('Failed to render graph:', err);
    ElMessage.error('渲染图谱失败，请检查 vis-network 依赖是否安装');
  }
};

// Get node color based on type
const getNodeColor = (type) => {
  const colors = {
    technology: { background: '#409EFF', border: '#337ecc' },
    concept: { background: '#67C23A', border: '#529b2e' },
    tool: { background: '#E6A23C', border: '#b88230' },
    framework: { background: '#F56C6C', border: '#c45656' }
  };
  return colors[type] || colors.concept;
};

// Add node
const addNode = async () => {
  try {
    await request({
      url: '/kg/graph/node',
      method: 'post',
      data: {
        label: nodeForm.value.label,
        type: nodeForm.value.type,
        properties: JSON.stringify({ description: nodeForm.value.description })
      }
    });
    ElMessage.success('节点添加成功');
    showAddNodeDialog.value = false;
    nodeForm.value = { label: '', type: 'concept', description: '' };
    fetchGraphData();
  } catch (error) {
    ElMessage.error('添加节点失败');
  }
};

// Add edge
const addEdge = async () => {
  try {
    await request({
      url: '/kg/graph/edge',
      method: 'post',
      data: {
        sourceId: edgeForm.value.sourceId,
        targetId: edgeForm.value.targetId,
        label: edgeForm.value.label,
        properties: '{}'
      }
    });
    ElMessage.success('关系添加成功');
    showAddEdgeDialog.value = false;
    edgeForm.value = { sourceId: null, targetId: null, label: 'related_to' };
    fetchGraphData();
  } catch (error) {
    ElMessage.error('添加关系失败');
  }
};

onMounted(() => {
  fetchGraphData();
});
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.graph-container {
  width: 100%;
  height: 600px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.loading-container {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-container {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
