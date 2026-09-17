-- ============================================================================
-- 08-sidebar-menu-svg-icons.sql
-- 全系统侧边栏菜单项专属矢量 SVG 图标补齐迁移脚本
-- 覆盖知识中心、知识图谱、Bot管理、知识抽取、模型服务及数据管理等所有叶子菜单
-- ============================================================================

-- 1. 知识中心子菜单 (图1重点：知识文件、知识分类)
UPDATE system_menu SET icon = 'file-text-line' WHERE menu_id = 2231; -- 知识文件
UPDATE system_menu SET icon = 'folder-5-fill' WHERE menu_id = 2224; -- 知识分类

-- 2. 知识图谱 (图1重点：图谱探索)
UPDATE system_menu SET icon = 'kac-entity-graph' WHERE menu_id = 2420; -- 图谱探索

-- 3. Bot 管理子菜单 (图2重点：工作流、Chatflow、Agent)
UPDATE system_menu SET icon = 'flow-chart' WHERE menu_id = 2412; -- 工作流
UPDATE system_menu SET icon = 'message-ai-3-fill' WHERE menu_id = 2413; -- Chatflow
UPDATE system_menu SET icon = 'brain-ai-3-line' WHERE menu_id = 2414; -- Agent

-- 4. 知识抽取子菜单 (概念配置、关系配置、非结构化抽取、结构化抽取、抽取日志)
UPDATE system_menu SET icon = 'atom-fill' WHERE menu_id = 2016; -- 概念配置
UPDATE system_menu SET icon = 'link' WHERE menu_id = 2023; -- 关系配置
UPDATE system_menu SET icon = 'file-ai-line' WHERE menu_id = 2030; -- 非结构化抽取
UPDATE system_menu SET icon = 'table' WHERE menu_id = 2037; -- 结构化抽取
UPDATE system_menu SET icon = 'log' WHERE menu_id = 2055; -- 抽取日志

-- 5. 知识应用与数据源
UPDATE system_menu SET icon = 'kac-entity-graph' WHERE menu_id = 2045; -- 图谱探索
UPDATE system_menu SET icon = 'database-2-line' WHERE menu_id = 2047; -- 数据源

-- 6. 知识库与模型服务
UPDATE system_menu SET icon = 'book-open-fill' WHERE menu_id = 2124; -- 知识库
UPDATE system_menu SET icon = 'apps-ai-fill' WHERE menu_id = 2322; -- 模型市场
UPDATE system_menu SET icon = 'ai-generate-3d-fill' WHERE menu_id = 2323; -- 我的模型

-- 7. 知识库配置子项 (基础设置、权限设置、检索设置、删除设置)
UPDATE system_menu SET icon = 'tools-line' WHERE menu_id = 2315; -- 基础设置
UPDATE system_menu SET icon = 'lock' WHERE menu_id = 2316; -- 权限设置
UPDATE system_menu SET icon = 'search' WHERE menu_id = 2317; -- 检索设置
UPDATE system_menu SET icon = 'alert-triangle-fill' WHERE menu_id = 2318; -- 删除设置
