-- Remove only the original code-generation demo menus; retain customized entries.
-- No business tables, user content or deployment credentials are changed.
CREATE TEMPORARY TABLE nus_demo_menu_cleanup (id BIGINT PRIMARY KEY);
INSERT INTO nus_demo_menu_cleanup (id)
SELECT id FROM system_menu WHERE
  (id = 2525 AND name = 'WebSocket' AND permission = '' AND parent_id = 2 AND path = 'websocket' AND component = 'infra/webSocket/index')
  OR   (id = 1070 AND name = '代码生成案例' AND permission = '' AND parent_id = 2 AND path = 'demo' AND component = 'infra/testDemo/index')
  OR (id = 2472 AND name = '主子表（内嵌）' AND permission = '' AND parent_id = 1070 AND path = 'demo03-inner' AND component = 'infra/demo/demo03/inner/index')
  OR (id = 2478 AND name = '单表（增删改查）' AND permission = '' AND parent_id = 1070 AND path = 'demo01-contact' AND component = 'infra/demo/demo01/index')
  OR (id = 2479 AND name = '示例联系人查询' AND permission = 'infra:demo01-contact:query' AND parent_id = 2478 AND path = '' AND component = '')
  OR (id = 2480 AND name = '示例联系人创建' AND permission = 'infra:demo01-contact:create' AND parent_id = 2478 AND path = '' AND component = '')
  OR (id = 2481 AND name = '示例联系人更新' AND permission = 'infra:demo01-contact:update' AND parent_id = 2478 AND path = '' AND component = '')
  OR (id = 2482 AND name = '示例联系人删除' AND permission = 'infra:demo01-contact:delete' AND parent_id = 2478 AND path = '' AND component = '')
  OR (id = 2483 AND name = '示例联系人导出' AND permission = 'infra:demo01-contact:export' AND parent_id = 2478 AND path = '' AND component = '')
  OR (id = 2484 AND name = '树表（增删改查）' AND permission = '' AND parent_id = 1070 AND path = 'demo02-category' AND component = 'infra/demo/demo02/index')
  OR (id = 2485 AND name = '示例分类查询' AND permission = 'infra:demo02-category:query' AND parent_id = 2484 AND path = '' AND component = '')
  OR (id = 2486 AND name = '示例分类创建' AND permission = 'infra:demo02-category:create' AND parent_id = 2484 AND path = '' AND component = '')
  OR (id = 2487 AND name = '示例分类更新' AND permission = 'infra:demo02-category:update' AND parent_id = 2484 AND path = '' AND component = '')
  OR (id = 2488 AND name = '示例分类删除' AND permission = 'infra:demo02-category:delete' AND parent_id = 2484 AND path = '' AND component = '')
  OR (id = 2489 AND name = '示例分类导出' AND permission = 'infra:demo02-category:export' AND parent_id = 2484 AND path = '' AND component = '')
  OR (id = 2490 AND name = '主子表（标准）' AND permission = '' AND parent_id = 1070 AND path = 'demo03-normal' AND component = 'infra/demo/demo03/normal/index')
  OR (id = 2491 AND name = '学生查询' AND permission = 'infra:demo03-student:query' AND parent_id = 2490 AND path = '' AND component = '')
  OR (id = 2492 AND name = '学生创建' AND permission = 'infra:demo03-student:create' AND parent_id = 2490 AND path = '' AND component = '')
  OR (id = 2493 AND name = '学生更新' AND permission = 'infra:demo03-student:update' AND parent_id = 2490 AND path = '' AND component = '')
  OR (id = 2494 AND name = '学生删除' AND permission = 'infra:demo03-student:delete' AND parent_id = 2490 AND path = '' AND component = '')
  OR (id = 2495 AND name = '学生导出' AND permission = 'infra:demo03-student:export' AND parent_id = 2490 AND path = '' AND component = '')
  OR (id = 2497 AND name = '主子表（ERP）' AND permission = '' AND parent_id = 1070 AND path = 'demo03-erp' AND component = 'infra/demo/demo03/erp/index');

-- Protect a candidate parent when it has a non-candidate child (up to 3 levels).
CREATE TEMPORARY TABLE nus_demo_menu_candidates LIKE nus_demo_menu_cleanup;
DELETE FROM nus_demo_menu_candidates;
INSERT INTO nus_demo_menu_candidates SELECT id FROM nus_demo_menu_cleanup;
DELETE candidate FROM nus_demo_menu_cleanup candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_demo_menu_candidates known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_demo_menu_candidates;
INSERT INTO nus_demo_menu_candidates SELECT id FROM nus_demo_menu_cleanup;
DELETE candidate FROM nus_demo_menu_cleanup candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_demo_menu_candidates known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;
DELETE FROM nus_demo_menu_candidates;
INSERT INTO nus_demo_menu_candidates SELECT id FROM nus_demo_menu_cleanup;
DELETE candidate FROM nus_demo_menu_cleanup candidate
JOIN system_menu child ON child.parent_id = candidate.id AND child.deleted = b'0'
LEFT JOIN nus_demo_menu_candidates known_child ON known_child.id = child.id
WHERE known_child.id IS NULL;

DELETE relation FROM system_role_menu relation
JOIN nus_demo_menu_cleanup candidate ON candidate.id = relation.menu_id;
UPDATE system_menu menu
JOIN nus_demo_menu_cleanup candidate ON candidate.id = menu.id
SET menu.deleted = b'1', menu.status = 1;
DROP TEMPORARY TABLE nus_demo_menu_candidates;
DROP TEMPORARY TABLE nus_demo_menu_cleanup;
