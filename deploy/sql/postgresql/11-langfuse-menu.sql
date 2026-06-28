-- LangFuse 可观测性页面菜单注册
-- 路由: /kd/observability
-- 父菜单: kd 首页 (menu_id=2061)

INSERT INTO system_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, create_by, create_time)
VALUES (2430, 'LLM 可观测性', 2061, 10, 'observability', 'kd/observability/index', 'C', '0', '0', 'kd:observability:view', 'monitor', 'admin', NOW())
ON CONFLICT (menu_id) DO NOTHING;

-- 授权 admin 角色
INSERT INTO system_role_menu (role_id, menu_id) VALUES (1, 2430) ON CONFLICT DO NOTHING;
