-- ============================================================================
-- 06-kac-horizontal-svg-icons.sql
-- 针对横向通用应用 10 套原子技能，补齐并绑定专属矢量 SVG 图标
-- ============================================================================

UPDATE kac_apply SET icon = 'kac-article-write' WHERE id = 1;
UPDATE kac_apply SET icon = 'kac-batch-search' WHERE id = 2;
UPDATE kac_apply SET icon = 'kac-exact-search' WHERE id = 3;
UPDATE kac_apply SET icon = 'kac-entity-graph' WHERE id = 4;
UPDATE kac_apply SET icon = 'kac-semantic-search' WHERE id = 5;
UPDATE kac_apply SET icon = 'kac-qa-chat' WHERE id = 6;
UPDATE kac_apply SET icon = 'kac-template-report' WHERE id = 7;
UPDATE kac_apply SET icon = 'kac-calendar-report' WHERE id = 8;
UPDATE kac_apply SET icon = 'kac-data-analysis' WHERE id = 9;
UPDATE kac_apply SET icon = 'kac-smart-summary' WHERE id = 10;
