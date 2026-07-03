CREATE (t:KgNode {workspace_id: 1001, del_flag: 0, name: '特斯拉', label: 'Company'});
CREATE (m:KgNode {workspace_id: 1001, del_flag: 0, name: '埃隆·马斯克', label: 'Person'});
CREATE (e:KgNode {workspace_id: 1001, del_flag: 0, name: '电动车', label: 'Product'});
CREATE (c:KgNode {workspace_id: 1001, del_flag: 0, name: '中国市场', label: 'Market'});

MATCH (t:KgNode {name: '特斯拉', workspace_id: 1001}), (m:KgNode {name: '埃隆·马斯克', workspace_id: 1001})
CREATE (t)-[:KgEdge {workspace_id: 1001, type: 'FOUNDER'}]->(m);

MATCH (t:KgNode {name: '特斯拉', workspace_id: 1001}), (e:KgNode {name: '电动车', workspace_id: 1001})
CREATE (t)-[:KgEdge {workspace_id: 1001, type: 'PRODUCES'}]->(e);

MATCH (t:KgNode {name: '特斯拉', workspace_id: 1001}), (c:KgNode {name: '中国市场', workspace_id: 1001})
CREATE (t)-[:KgEdge {workspace_id: 1001, type: 'OPERATES_IN'}]->(c);
