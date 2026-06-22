-- ============================================================================
-- Knowledge Graph Sample Data - Flutter Technology Graph
-- 15 nodes + 20 edges
-- ============================================================================

-- Clear existing sample data (if any) to avoid duplicates
DELETE FROM kg_edge WHERE workspace_id = 1001 AND id BETWEEN 101 AND 120;
DELETE FROM kg_node WHERE workspace_id = 1001 AND id BETWEEN 101 AND 115;

-- ===========================
-- Nodes (15)
-- ===========================
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (101, 1001, 'Flutter', 'technology', '{"description": "跨平台UI框架", "category": "framework"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (102, 1001, 'Dart', 'technology', '{"description": "编程语言", "category": "language"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (103, 1001, 'Widget', 'concept', '{"description": "UI组件", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (104, 1001, 'StatefulWidget', 'concept', '{"description": "有状态组件", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (105, 1001, 'StatelessWidget', 'concept', '{"description": "无状态组件", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (106, 1001, 'Provider', 'technology', '{"description": "状态管理", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (107, 1001, 'Riverpod', 'technology', '{"description": "状态管理", "category": "library"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (108, 1001, 'Navigator', 'concept', '{"description": "路由导航", "category": "core"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (109, 1001, 'Material Design', 'concept', '{"description": "设计语言", "category": "design"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (110, 1001, 'Cupertino', 'concept', '{"description": "iOS风格", "category": "design"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (111, 1001, 'Firebase', 'technology', '{"description": "后端服务", "category": "backend"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (112, 1001, 'REST API', 'concept', '{"description": "接口规范", "category": "architecture"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (113, 1001, 'JSON Serialization', 'concept', '{"description": "数据序列化", "category": "utility"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (114, 1001, 'Testing', 'concept', '{"description": "测试框架", "category": "quality"}');
INSERT INTO kg_node (id, workspace_id, label, type, properties) VALUES (115, 1001, 'Deployment', 'concept', '{"description": "部署方案", "category": "devops"}');

-- ===========================
-- Edges (20)
-- ===========================
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (101, 1001, 101, 102, '使用', '{"description": "Flutter使用Dart语言开发"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (102, 1001, 101, 103, '包含', '{"description": "Flutter由Widget构成"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (103, 1001, 103, 104, '继承', '{"description": "StatefulWidget继承自Widget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (104, 1001, 103, 105, '继承', '{"description": "StatelessWidget继承自Widget"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (105, 1001, 101, 106, '支持', '{"description": "Flutter支持Provider状态管理"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (106, 1001, 101, 107, '支持', '{"description": "Flutter支持Riverpod状态管理"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (107, 1001, 101, 108, '包含', '{"description": "Flutter内置Navigator路由"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (108, 1001, 101, 109, '遵循', '{"description": "Flutter遵循Material Design规范"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (109, 1001, 101, 110, '支持', '{"description": "Flutter支持Cupertino iOS风格"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (110, 1001, 101, 111, '集成', '{"description": "Flutter可集成Firebase服务"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (111, 1001, 101, 112, '调用', '{"description": "Flutter可调用REST API"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (112, 1001, 102, 113, '支持', '{"description": "Dart支持JSON序列化"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (113, 1001, 101, 114, '支持', '{"description": "Flutter内置测试框架"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (114, 1001, 101, 115, '部署', '{"description": "Flutter支持多平台部署"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (115, 1001, 106, 104, '管理状态', '{"description": "Provider管理StatefulWidget状态"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (116, 1001, 107, 106, '替代', '{"description": "Riverpod是Provider的改进替代方案"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (117, 1001, 108, 103, '导航', '{"description": "Navigator负责Widget间的导航"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (118, 1001, 111, 112, '使用', '{"description": "Firebase通过REST API通信"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (119, 1001, 114, 103, '测试', '{"description": "Testing框架测试Widget组件"}');
INSERT INTO kg_edge (id, workspace_id, source_id, target_id, label, properties) VALUES (120, 1001, 115, 101, '发布', '{"description": "Deployment发布Flutter应用"}');

-- Update sequences to avoid ID conflicts
SELECT setval('kg_node_id_seq', GREATEST((SELECT MAX(id) FROM kg_node), 100));
SELECT setval('kg_edge_id_seq', GREATEST((SELECT MAX(id) FROM kg_edge), 100));
