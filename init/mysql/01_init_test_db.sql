-- ============================================================
-- MySQL 测试数据库初始化脚本
-- 用途：为 probe-agent 提供可监控的 MySQL 测试数据
-- ============================================================

-- 确保使用 test_db
USE test_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `email` VARCHAR(128) NOT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 产品表
CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '产品ID',
    `name` VARCHAR(200) NOT NULL COMMENT '产品名称',
    `category` VARCHAR(64) NOT NULL COMMENT '产品分类',
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品表';

-- 订单表
CREATE TABLE IF NOT EXISTS `order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '产品ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `unit_price` DECIMAL(10,2) NOT NULL COMMENT '单价',
    `subtotal` DECIMAL(12,2) NOT NULL COMMENT '小计',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 员工表
CREATE TABLE IF NOT EXISTS `employee` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '员工ID',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `department` VARCHAR(64) NOT NULL COMMENT '部门',
    `position` VARCHAR(64) NOT NULL COMMENT '职位',
    `salary` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '薪资',
    `hire_date` DATE NOT NULL COMMENT '入职日期',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-离职 1-在职',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_department` (`department`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

-- ============================================================
-- 插入测试数据
-- ============================================================

-- 用户数据
INSERT INTO `user` (`username`, `email`, `phone`, `status`) VALUES
('zhangsan', 'zhangsan@example.com', '13800001001', 1),
('lisi', 'lisi@example.com', '13800001002', 1),
('wangwu', 'wangwu@example.com', '13800001003', 1),
('zhaoliu', 'zhaoliu@example.com', '13800001004', 0),
('sunqi', 'sunqi@example.com', '13800001005', 1),
('zhouba', 'zhouba@example.com', '13800001006', 1),
('wujiu', 'wujiu@example.com', '13800001007', 1),
('zhengshi', 'zhengshi@example.com', '13800001008', 0),
('qianyi', 'qianyi@example.com', '13800001009', 1),
('testuser', 'testuser@example.com', '13800001010', 1);

-- 产品数据
INSERT INTO `product` (`name`, `category`, `price`, `stock`, `status`) VALUES
('ThinkPad X1 Carbon', '电脑', 8999.00, 120, 1),
('iPhone 16 Pro', '手机', 7999.00, 350, 1),
('AirPods Pro 3', '耳机', 1899.00, 500, 1),
('LG 27英寸4K显示器', '显示器', 2499.00, 80, 1),
('Logitech MX Master 3S', '鼠标', 549.00, 200, 1),
('HHKB Professional', '键盘', 2199.00, 60, 1),
('Samsung 1TB SSD', '存储', 599.00, 400, 1),
('MacBook Air M3', '电脑', 8499.00, 180, 1),
('小米路由器 AX9000', '网络设备', 499.00, 300, 1),
('已下架商品-旧款手机', '手机', 999.00, 0, 0);

-- 订单数据
INSERT INTO `order` (`order_no`, `user_id`, `total_amount`, `status`, `remark`) VALUES
('ORD-20240101-0001', 1, 10898.00, 3, NULL),
('ORD-20240102-0002', 2, 7999.00, 1, '请尽快发货'),
('ORD-20240103-0003', 3, 2448.00, 2, NULL),
('ORD-20240104-0004', 5, 8999.00, 0, NULL),
('ORD-20240105-0005', 6, 549.00, 3, '需要发票'),
('ORD-20240106-0006', 7, 16998.00, 4, '取消订单'),
('ORD-20240107-0007', 1, 2199.00, 1, NULL),
('ORD-20240108-0008', 3, 599.00, 3, NULL),
('ORD-20240109-0009', 5, 17498.00, 2, '分批发货'),
('ORD-20240110-0010', 9, 499.00, 0, NULL);

-- 订单明细数据
INSERT INTO `order_item` (`order_id`, `product_id`, `quantity`, `unit_price`, `subtotal`) VALUES
(1, 1, 1, 8999.00, 8999.00),
(1, 3, 1, 1899.00, 1899.00),
(2, 2, 1, 7999.00, 7999.00),
(3, 5, 2, 549.00, 1098.00),
(3, 3, 1, 1899.00, 1899.00),
(4, 1, 1, 8999.00, 8999.00),
(5, 5, 1, 549.00, 549.00),
(6, 8, 2, 8499.00, 16998.00),
(7, 6, 1, 2199.00, 2199.00),
(8, 7, 1, 599.00, 599.00),
(9, 8, 1, 8499.00, 8499.00),
(9, 1, 1, 8999.00, 8999.00),
(10, 9, 1, 499.00, 499.00);

-- 员工数据
INSERT INTO `employee` (`name`, `department`, `position`, `salary`, `hire_date`, `status`) VALUES
('张三', '技术部', '高级工程师', 28000.00, '2020-03-15', 1),
('李四', '技术部', '工程师', 20000.00, '2021-07-01', 1),
('王五', '产品部', '产品经理', 25000.00, '2019-11-20', 1),
('赵六', '市场部', '市场总监', 32000.00, '2018-05-10', 0),
('孙七', '技术部', '架构师', 38000.00, '2017-01-08', 1),
('周八', '人事部', 'HR经理', 22000.00, '2020-09-01', 1),
('吴九', '财务部', '财务主管', 24000.00, '2019-04-22', 1),
('郑十', '技术部', '测试工程师', 18000.00, '2022-01-15', 0),
('钱一', '产品部', '产品助理', 15000.00, '2023-03-01', 1),
('陈二', '市场部', '运营专员', 16000.00, '2022-08-20', 1);
