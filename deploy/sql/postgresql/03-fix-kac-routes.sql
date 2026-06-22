-- ============================================================================
-- Fix KAC Route Configuration
-- ============================================================================

-- Fix 横向通用应用 route (menu_id=2407)
UPDATE system_menu SET component = 'kac/horizontal/index' WHERE menu_id = 2407;

-- Fix 我的解决方案 route (menu_id=2409)
UPDATE system_menu SET component = 'kac/mySolution/index' WHERE menu_id = 2409;

-- Fix 纵向行业应用 route (menu_id=2408)
UPDATE system_menu SET component = 'kac/vertical/index' WHERE menu_id = 2408;

-- Fix 我的应用 route (menu_id=2410)
UPDATE system_menu SET component = 'kac/myApp/index' WHERE menu_id = 2410;

-- Fix 解决方案 route (menu_id=2406)
UPDATE system_menu SET component = 'kac/solution/index' WHERE menu_id = 2406;
