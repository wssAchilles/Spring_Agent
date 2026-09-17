-- ============================================================================
-- 07-kac-vertical-svg-icons.sql
-- 针对 8 套垂直行业中枢应用，补齐并绑定专属矢量 SVG 图标
-- ============================================================================

UPDATE kac_apply SET icon = 'kac-industry-finance' WHERE id = 101;
UPDATE kac_apply SET icon = 'kac-industry-medical' WHERE id = 102;
UPDATE kac_apply SET icon = 'kac-industry-manufacturing' WHERE id = 103;
UPDATE kac_apply SET icon = 'kac-industry-education' WHERE id = 104;
UPDATE kac_apply SET icon = 'kac-industry-government' WHERE id = 105;
UPDATE kac_apply SET icon = 'kac-industry-ecommerce' WHERE id = 106;
UPDATE kac_apply SET icon = 'kac-industry-water' WHERE id = 107;
UPDATE kac_apply SET icon = 'kac-industry-energy' WHERE id = 108;
