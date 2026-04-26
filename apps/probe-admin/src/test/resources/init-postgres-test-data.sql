-- PostgreSQL测试数据初始化脚本
-- 用于PostgreSQL数据采集链路集成测试

-- 创建测试schema
CREATE SCHEMA IF NOT EXISTS test_schema;

-- 创建测试表
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10, 2),
    status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2),
    stock_quantity INTEGER DEFAULT 0
);

-- 插入测试数据
INSERT INTO users (username, email) VALUES
    ('test_user1', 'user1@example.com'),
    ('test_user2', 'user2@example.com'),
    ('test_user3', 'user3@example.com')
ON CONFLICT (username) DO NOTHING;

INSERT INTO products (name, description, price, stock_quantity) VALUES
    ('Product A', 'Description A', 99.99, 100),
    ('Product B', 'Description B', 149.99, 50),
    ('Product C', 'Description C', 199.99, 25)
ON CONFLICT DO NOTHING;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);

-- 添加注释
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.email IS '邮箱';

COMMENT ON TABLE orders IS '订单表';
COMMENT ON TABLE products IS '产品表';
