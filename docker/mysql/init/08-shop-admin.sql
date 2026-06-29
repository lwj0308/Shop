-- ============================================
-- Shop 商城项目 - 管理后台建表脚本
-- 数据库：shop_admin
-- 说明：包含管理员、角色、权限、部门、操作日志、登录日志、安全事件等表
-- ============================================

USE shop_admin;

-- 管理员信息表
-- 管理后台的操作人员，每个管理员可以分配多个角色
CREATE TABLE IF NOT EXISTS `admin_user` (
    `id` BIGINT NOT NULL COMMENT '管理员ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名（登录账号）',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称（显示名称）',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `password_change_time` DATETIME DEFAULT NULL COMMENT '密码最后修改时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员信息';

-- 角色表
-- 定义不同的角色，每个角色拥有不同的权限集合
CREATE TABLE IF NOT EXISTS `admin_role` (
    `id` BIGINT NOT NULL COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称（如：运营管理员）',
    `role_key` VARCHAR(50) NOT NULL COMMENT '角色标识（如：operator，用于代码中判断）',
    `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据权限范围：1全部数据 2本部门 3本部门及下级 4仅本人',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色信息';

-- 权限/菜单表
-- 树形结构，包含目录、菜单、按钮三种类型，控制前端菜单显示和按钮权限
CREATE TABLE IF NOT EXISTS `admin_permission` (
    `id` BIGINT NOT NULL COMMENT '权限ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID（0表示顶级）',
    `name` VARCHAR(50) NOT NULL COMMENT '权限名称（如：用户管理）',
    `type` TINYINT NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
    `permission_key` VARCHAR(100) DEFAULT NULL COMMENT '权限标识（如user:list、user:edit）',
    `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径（前端菜单用）',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '菜单图标',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/菜单';

-- 管理员-角色关联表
-- 一个管理员可以有多个角色，一个角色可以分配给多个管理员（多对多关系）
CREATE TABLE IF NOT EXISTS `admin_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '管理员ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色关联';

-- 角色-权限关联表
-- 一个角色拥有多个权限，一个权限可以属于多个角色（多对多关系）
CREATE TABLE IF NOT EXISTS `admin_role_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- 部门表
-- 树形结构，用于数据权限控制（不同角色看到不同范围的数据）
CREATE TABLE IF NOT EXISTS `admin_dept` (
    `id` BIGINT NOT NULL COMMENT '部门ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级部门ID（0表示顶级）',
    `name` VARCHAR(50) NOT NULL COMMENT '部门名称',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `leader` VARCHAR(50) DEFAULT NULL COMMENT '部门负责人',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门信息';

-- 操作日志表
-- 记录管理员的所有增删改操作，用于审计追踪
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `module` VARCHAR(50) DEFAULT NULL COMMENT '操作模块（如：用户管理）',
    `operation_type` VARCHAR(20) DEFAULT NULL COMMENT '操作类型（新增/修改/删除/查询/导出）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '操作描述',
    `request_url` VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT '请求方法（GET/POST/PUT/DELETE）',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON格式）',
    `response_result` TEXT DEFAULT NULL COMMENT '响应结果（JSON格式）',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址',
    `location` VARCHAR(100) DEFAULT NULL COMMENT '操作地点',
    `duration` INT DEFAULT NULL COMMENT '执行时长（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态：0失败 1成功',
    `error_msg` TEXT DEFAULT NULL COMMENT '错误信息（失败时记录）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- 登录日志表
-- 记录管理员的登录成功/失败信息，用于安全审计
CREATE TABLE IF NOT EXISTS `admin_login_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '登录用户名',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP地址',
    `location` VARCHAR(100) DEFAULT NULL COMMENT '登录地点',
    `browser` VARCHAR(50) DEFAULT NULL COMMENT '浏览器',
    `os` VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '登录状态：0失败 1成功',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 安全事件表
-- 记录异常安全事件，如频繁登录失败、异常IP访问、越权操作等
CREATE TABLE IF NOT EXISTS `admin_security_event` (
    `id` BIGINT NOT NULL COMMENT '事件ID',
    `event_type` VARCHAR(30) NOT NULL COMMENT '事件类型（频繁登录失败/异常IP/权限越权/敏感操作）',
    `user_id` BIGINT DEFAULT NULL COMMENT '相关用户ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '相关用户名',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT '事件详情',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '事件IP地址',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1已处理 2已忽略',
    `handle_note` VARCHAR(200) DEFAULT NULL COMMENT '处理备注',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件';

-- Banner管理表
-- 管理后台首页轮播图，用于展示促销活动、推荐商品等
CREATE TABLE IF NOT EXISTS `admin_banner` (
    `id` BIGINT NOT NULL COMMENT 'Banner ID',
    `title` VARCHAR(100) NOT NULL COMMENT 'Banner标题',
    `image` VARCHAR(255) NOT NULL COMMENT '图片地址',
    `link` VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Banner管理';

-- 公告管理表
-- 系统公告，如通知公告、活动公告、维护公告等
CREATE TABLE IF NOT EXISTS `admin_notice` (
    `id` BIGINT NOT NULL COMMENT '公告ID',
    `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告内容',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1通知 2活动 3维护',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告管理';

-- ============================================
-- 七、shop_admin 库测试数据
-- ============================================
USE shop_admin;

-- -------------------------------------------
-- 7.1 部门表 admin_dept（4个部门，树形结构）
-- 总公司 > 运营部/审核部/客服部
-- -------------------------------------------
INSERT INTO `admin_dept` (`id`, `parent_id`, `name`, `sort`, `leader`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(1, 0, '总公司', 1, '张总', 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY), 0),
(2, 1, '运营部', 1, '李经理', 1, DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0),
(3, 1, '审核部', 2, '王经理', 1, DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0),
(4, 1, '客服部', 3, '赵经理', 1, DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0);

-- -------------------------------------------
-- 7.2 角色表 admin_role（4个预设角色）
-- 超级管理员/运营管理员/审核管理员/客服管理员
-- -------------------------------------------
INSERT INTO `admin_role` (`id`, `role_name`, `role_key`, `data_scope`, `status`, `remark`, `create_time`, `update_time`, `deleted`) VALUES
(1, '超级管理员', 'admin', 1, 1, '拥有全部权限，不可删除', DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY), 0),
(2, '运营管理员', 'operator', 1, 1, '负责用户/商家/商品/订单/内容管理', DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0),
(3, '审核管理员', 'auditor', 2, 1, '负责商家审核/商品审核/退款审核', DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0),
(4, '客服管理员', 'service', 3, 1, '负责订单查看/退款处理/用户查看', DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), 0);

-- -------------------------------------------
-- 7.3 权限/菜单表 admin_permission（完整的权限树）
-- 一级：目录(type=1) > 二级：菜单(type=2) > 三级：按钮(type=3)
-- -------------------------------------------
INSERT INTO `admin_permission` (`id`, `parent_id`, `name`, `type`, `permission_key`, `path`, `icon`, `sort`, `status`, `create_time`, `update_time`) VALUES
-- ===== 一级目录 =====
(1, 0, '仪表盘', 1, NULL, '/dashboard', 'Odometer', 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(2, 0, '用户管理', 1, NULL, '/user', 'User', 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(3, 0, '商家管理', 1, NULL, '/merchant', 'Shop', 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(4, 0, '商品管理', 1, NULL, '/product', 'Goods', 4, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(5, 0, '订单管理', 1, NULL, '/order', 'List', 5, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(6, 0, '内容管理', 1, NULL, '/content', 'Reading', 6, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(7, 0, '系统管理', 1, NULL, '/system', 'Setting', 7, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(8, 0, '安全审计', 1, NULL, '/security', 'Lock', 8, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 仪表盘菜单 =====
(101, 1, '数据概览', 2, 'dashboard:view', '/dashboard/index', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 用户管理菜单+按钮 =====
(201, 2, '用户列表', 2, 'user:list', '/user/index', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(202, 201, '查看详情', 3, 'user:detail', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(203, 201, '禁用用户', 3, 'user:disable', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(204, 201, '解禁用户', 3, 'user:enable', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 商家管理菜单+按钮 =====
(301, 3, '商家列表', 2, 'merchant:list', '/merchant/index', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(302, 301, '查看详情', 3, 'merchant:detail', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(303, 301, '审核通过', 3, 'merchant:audit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(304, 301, '禁用商家', 3, 'merchant:disable', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(305, 301, '启用商家', 3, 'merchant:enable', NULL, NULL, 4, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 商品管理菜单+按钮 =====
(401, 4, '商品列表', 2, 'product:list', '/product/index', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(402, 401, '查看详情', 3, 'product:detail', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(403, 401, '商品审核', 3, 'product:audit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(404, 401, '强制下架', 3, 'product:off', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(411, 4, '分类管理', 2, 'category:list', '/product/category', NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(412, 411, '新增分类', 3, 'category:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(413, 411, '编辑分类', 3, 'category:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(414, 411, '删除分类', 3, 'category:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(421, 4, '品牌管理', 2, 'brand:list', '/product/brand', NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(422, 421, '新增品牌', 3, 'brand:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(423, 421, '编辑品牌', 3, 'brand:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(424, 421, '删除品牌', 3, 'brand:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 订单管理菜单+按钮 =====
(501, 5, '订单列表', 2, 'order:list', '/order/index', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(502, 501, '查看详情', 3, 'order:detail', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(503, 501, '发货操作', 3, 'order:delivery', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(511, 5, '退款管理', 2, 'refund:list', '/order/refund', NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(512, 511, '审核退款', 3, 'refund:audit', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 内容管理菜单+按钮 =====
(601, 6, 'Banner管理', 2, 'banner:list', '/content/banner', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(602, 601, '新增Banner', 3, 'banner:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(603, 601, '编辑Banner', 3, 'banner:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(604, 601, '删除Banner', 3, 'banner:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(611, 6, '公告管理', 2, 'notice:list', '/content/notice', NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(612, 611, '新增公告', 3, 'notice:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(613, 611, '编辑公告', 3, 'notice:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(614, 611, '删除公告', 3, 'notice:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 系统管理菜单+按钮 =====
(701, 7, '管理员管理', 2, 'admin:list', '/system/admin', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(702, 701, '新增管理员', 3, 'admin:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(703, 701, '编辑管理员', 3, 'admin:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(704, 701, '删除管理员', 3, 'admin:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(711, 7, '角色管理', 2, 'role:list', '/system/role', NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(712, 711, '新增角色', 3, 'role:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(713, 711, '编辑角色', 3, 'role:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(714, 711, '删除角色', 3, 'role:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(721, 7, '权限管理', 2, 'permission:list', '/system/permission', NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(731, 7, '部门管理', 2, 'dept:list', '/system/dept', NULL, 4, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(732, 731, '新增部门', 3, 'dept:add', NULL, NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(733, 731, '编辑部门', 3, 'dept:edit', NULL, NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(734, 731, '删除部门', 3, 'dept:delete', NULL, NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),

-- ===== 安全审计菜单 =====
(801, 8, '操作日志', 2, 'log:operation', '/security/operation', NULL, 1, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(802, 8, '登录日志', 2, 'log:login', '/security/login', NULL, 2, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY)),
(803, 8, '安全事件', 2, 'security:event', '/security/event', NULL, 3, 1, DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY));

-- -------------------------------------------
-- 7.4 管理员表 admin_user（4个测试账号）
-- 密码统一为 BCrypt 加密的 "admin123"
-- -------------------------------------------
INSERT INTO `admin_user` (`id`, `username`, `password`, `nickname`, `avatar`, `email`, `phone`, `dept_id`, `status`, `last_login_ip`, `last_login_time`, `password_change_time`, `create_time`, `update_time`, `deleted`) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '超级管理员', 'https://dummyimage.com/100x100/333/fff&text=Admin', 'admin@shop.com', '13900000001', 1, 1, '127.0.0.1', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 365 DAY), DATE_SUB(NOW(), INTERVAL 1 HOUR), 0),
(2, 'operator', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '运营管理员', 'https://dummyimage.com/100x100/333/fff&text=Operator', 'operator@shop.com', '13900000002', 2, 1, '192.168.1.100', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 3 HOUR), 0),
(3, 'auditor', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '审核管理员', 'https://dummyimage.com/100x100/333/fff&text=Auditor', 'auditor@shop.com', '13900000003', 3, 1, '192.168.1.101', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 5 HOUR), 0),
(4, 'service', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '客服管理员', 'https://dummyimage.com/100x100/333/fff&text=Service', 'service@shop.com', '13900000004', 4, 1, '192.168.1.102', DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 360 DAY), DATE_SUB(NOW(), INTERVAL 8 HOUR), 0);

-- -------------------------------------------
-- 7.5 管理员-角色关联表 admin_user_role
-- -------------------------------------------
INSERT INTO `admin_user_role` (`id`, `user_id`, `role_id`) VALUES
(1, 1, 1),  -- admin -> 超级管理员
(2, 2, 2),  -- operator -> 运营管理员
(3, 3, 3),  -- auditor -> 审核管理员
(4, 4, 4);  -- service -> 客服管理员

-- -------------------------------------------
-- 7.6 角色-权限关联表 admin_role_permission
-- 超级管理员拥有全部权限，其他角色按职责分配
-- -------------------------------------------
-- 超级管理员(角色ID=1)：全部权限
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
(1,1,1),(2,1,101),
(3,1,2),(4,1,201),(5,1,202),(6,1,203),(7,1,204),
(8,1,3),(9,1,301),(10,1,302),(11,1,303),(12,1,304),(13,1,305),
(14,1,4),(15,1,401),(16,1,402),(17,1,403),(18,1,404),(19,1,411),(20,1,412),(21,1,413),(22,1,414),(23,1,421),(24,1,422),(25,1,423),(26,1,424),
(27,1,5),(28,1,501),(29,1,502),(30,1,503),(31,1,511),(32,1,512),
(33,1,6),(34,1,601),(35,1,602),(36,1,603),(37,1,604),(38,1,611),(39,1,612),(40,1,613),(41,1,614),
(42,1,7),(43,1,701),(44,1,702),(45,1,703),(46,1,704),(47,1,711),(48,1,712),(49,1,713),(50,1,714),(51,1,721),(52,1,731),(53,1,732),(54,1,733),(55,1,734),
(56,1,8),(57,1,801),(58,1,802),(59,1,803);

-- 运营管理员(角色ID=2)：用户/商家/商品/订单/内容管理
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
(100,2,1),(101,2,101),
(102,2,2),(103,2,201),(104,2,202),(105,2,203),(106,2,204),
(107,2,3),(108,2,301),(109,2,302),(110,2,304),(111,2,305),
(112,2,4),(113,2,401),(114,2,402),(115,2,411),(116,2,412),(117,2,413),(118,2,421),(119,2,422),(120,2,423),
(121,2,5),(122,2,501),(123,2,502),(124,2,511),
(125,2,6),(126,2,601),(127,2,602),(128,2,603),(129,2,611),(130,2,612),(131,2,613);

-- 审核管理员(角色ID=3)：商家审核/商品审核/退款审核
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
(200,3,1),(201,3,101),
(202,3,3),(203,3,301),(204,3,302),(205,3,303),
(206,3,4),(207,3,401),(208,3,402),(209,3,403),(210,3,404),
(211,3,5),(212,3,511),(213,3,512);

-- 客服管理员(角色ID=4)：订单查看/退款处理/用户查看
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
(300,4,1),(301,4,101),
(302,4,2),(303,4,201),(304,4,202),
(305,4,5),(306,4,501),(307,4,502),(308,4,511),(309,4,512);

-- -------------------------------------------
-- 7.7 操作日志表 admin_operation_log（5条示例日志）
-- -------------------------------------------
INSERT INTO `admin_operation_log` (`id`, `user_id`, `username`, `module`, `operation_type`, `description`, `request_url`, `request_method`, `request_params`, `response_result`, `ip`, `location`, `duration`, `status`, `error_msg`, `create_time`) VALUES
(1, 1, 'admin', '商家管理', '修改', '审核通过商家：张三的数码店', '/admin/merchant/audit', 'POST', '{"merchantId":2001,"action":"approve"}', '{"code":200,"message":"操作成功"}', '127.0.0.1', '本地', 45, 1, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(2, 2, 'operator', '用户管理', '修改', '禁用用户：孙七', '/admin/user/disable', 'POST', '{"userId":1005}', '{"code":200,"message":"操作成功"}', '192.168.1.100', '本地局域网', 32, 1, NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(3, 3, 'auditor', '商品管理', '修改', '商品审核通过：Apple iPhone 15 Pro Max', '/admin/product/audit', 'POST', '{"productId":4001,"action":"approve"}', '{"code":200,"message":"操作成功"}', '192.168.1.101', '本地局域网', 28, 1, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(4, 4, 'service', '退款管理', '修改', '同意退款：订单2024061900010005', '/admin/refund/audit', 'POST', '{"refundId":14001,"action":"approve"}', '{"code":200,"message":"操作成功"}', '192.168.1.102', '本地局域网', 56, 1, NULL, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(5, 1, 'admin', '系统管理', '新增', '新增管理员：运营管理员', '/admin/user', 'POST', '{"username":"operator","nickname":"运营管理员"}', '{"code":200,"message":"操作成功"}', '127.0.0.1', '本地', 120, 1, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- -------------------------------------------
-- 7.8 登录日志表 admin_login_log（8条示例日志）
-- -------------------------------------------
INSERT INTO `admin_login_log` (`id`, `username`, `ip`, `location`, `browser`, `os`, `status`, `fail_reason`, `login_time`) VALUES
(1, 'admin', '127.0.0.1', '本地', 'Chrome 120', 'Windows 11', 1, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 'operator', '192.168.1.100', '本地局域网', 'Chrome 120', 'Windows 11', 1, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(3, 'auditor', '192.168.1.101', '本地局域网', 'Firefox 121', 'macOS 14', 1, NULL, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 'service', '192.168.1.102', '本地局域网', 'Chrome 120', 'Windows 10', 1, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(5, 'admin', '10.0.0.55', '未知', 'Chrome 120', 'Windows 11', 0, '密码错误', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6, 'admin', '127.0.0.1', '本地', 'Chrome 120', 'Windows 11', 1, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(7, 'operator', '192.168.1.100', '本地局域网', 'Edge 120', 'Windows 11', 1, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(8, 'unknown', '45.33.32.156', '未知', 'Python-requests', 'Linux', 0, '用户不存在', DATE_SUB(NOW(), INTERVAL 4 DAY));

-- -------------------------------------------
-- 7.9 安全事件表 admin_security_event（3条示例事件）
-- -------------------------------------------
INSERT INTO `admin_security_event` (`id`, `event_type`, `user_id`, `username`, `detail`, `ip`, `status`, `handle_note`, `handle_time`, `create_time`) VALUES
(1, '频繁登录失败', NULL, 'admin', '5分钟内连续3次登录失败', '10.0.0.55', 1, '经核实为管理员本人操作，输入密码错误', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, '异常IP', NULL, 'unknown', '从不明IP尝试登录管理员账号', '45.33.32.156', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(3, '敏感操作', 1, 'admin', '超级管理员禁用用户账号', '127.0.0.1', 2, '正常业务操作', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR));

-- -------------------------------------------
-- 7.10 Banner管理表 admin_banner（5条轮播图）
-- 首页轮播图，展示促销活动、推荐商品等
-- -------------------------------------------
INSERT INTO `admin_banner` (`id`, `title`, `image`, `link`, `sort`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(1, '618年中大促', 'https://dummyimage.com/1200x400/E4393C/fff&text=618+Sale', '/activity/618', 1, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(2, 'iPhone 15 Pro Max 新品首发', 'https://dummyimage.com/1200x400/E4393C/fff&text=iPhone15+Launch', '/product/4001', 2, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(3, '华为Mate 60 Pro 火热预售', 'https://dummyimage.com/1200x400/E4393C/fff&text=Mate60+Presale', '/product/4002', 3, 1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 0),
(4, '优衣库夏季新品', 'https://dummyimage.com/1200x400/E4393C/fff&text=UNIQLO+Summer', '/shop/3002', 4, 1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 0),
(5, '双11预热活动', 'https://dummyimage.com/1200x400/E4393C/fff&text=Double11+Warmup', '/activity/double11', 5, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0);

-- -------------------------------------------
-- 7.11 公告管理表 admin_notice（4条公告）
-- 系统公告，包含通知、活动、维护等类型
-- -------------------------------------------
INSERT INTO `admin_notice` (`id`, `title`, `content`, `type`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(1, '618年中大促活动通知',
 '亲爱的用户们：\n\n618年中大促活动即将开始！\n\n活动时间：2026年6月1日 - 6月18日\n活动内容：\n1. 全场满300减50\n2. 数码产品最高优惠2000元\n3. 服饰鞋包第二件半价\n4. 美妆护肤买赠活动\n\n活动期间下单还有机会赢取iPhone 15 Pro Max一台！\n\n赶快行动吧！',
 2, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), 0),

(2, '系统维护通知',
 '尊敬的用户：\n\n为了提供更好的服务体验，系统将于2026年6月20日凌晨2:00-6:00进行维护升级。\n\n维护期间将无法正常访问网站和下单，请您提前安排好购物计划。\n\n给您带来的不便，敬请谅解！',
 3, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0),

(3, '新版隐私政策更新通知',
 '尊敬的用户：\n\n我们已更新《隐私政策》，主要变更如下：\n1. 增加了用户数据保护条款\n2. 优化了个人信息收集和使用规则\n3. 明确了用户权利和选择机制\n\n新版政策自2026年6月1日起生效。如有疑问，请联系客服。',
 1, 1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 0),

(4, '夏季清仓特卖活动',
 '夏季清仓特卖来啦！\n\n精选500+款商品，低至3折起！\n\n活动时间：2026年6月15日 - 6月30日\n参与品牌：优衣库、耐克、兰蔻等\n\n数量有限，先到先得，售完即止！',
 2, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0);
