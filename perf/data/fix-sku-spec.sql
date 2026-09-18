-- 修复 SKU 100861 的 spec_values 格式
-- 原数据是 JSON 数组 '["标准版"]'，需要改为 JSON 对象 '{"版本":"标准版"}'
-- 原因：ProductSku.java 中 specValues 字段类型为 Map<String, String>，无法反序列化数组
UPDATE shop_product.product_sku SET spec_values = '{"版本":"标准版"}' WHERE id = 100861;

-- 验证更新结果
SELECT id, product_id, spec_values FROM shop_product.product_sku WHERE id = 100861;
