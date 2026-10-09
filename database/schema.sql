-- ====================================================================
-- Inventory & Billing System - Database Schema
-- Designed by: Siddhesh Jagtap (Database Design & Queries)
-- Tech: MySQL
-- ====================================================================

-- 1. Create Database if not exists
CREATE DATABASE IF NOT EXISTS inventory_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE inventory_db;

-- 2. Drop existing tables if needed (in reverse order of dependencies)
DROP TABLE IF EXISTS sale_items;
DROP TABLE IF EXISTS sales;
DROP TABLE IF EXISTS products;

-- ====================================================================
-- 3. Products Table (Inventory)
-- Stores product catalog, pricing, current stock and low-stock threshold
-- ====================================================================
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL DEFAULT 'General',
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    low_stock_threshold INT NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_category (category),
    INDEX idx_product_stock (stock_quantity)
) ENGINE=InnoDB;

-- ====================================================================
-- 4. Sales Table (Receipt Headers)
-- Stores individual sales transactions and invoice records
-- ====================================================================
CREATE TABLE sales (
    id INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(100) NOT NULL DEFAULT 'Walk-in Customer',
    customer_phone VARCHAR(20) DEFAULT '',
    subtotal DECIMAL(10, 2) NOT NULL CHECK (subtotal >= 0),
    tax DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (tax >= 0),
    discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 CHECK (discount >= 0),
    grand_total DECIMAL(10, 2) NOT NULL CHECK (grand_total >= 0),
    payment_method VARCHAR(50) NOT NULL DEFAULT 'Cash',
    sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sale_date (sale_date),
    INDEX idx_invoice_number (invoice_number)
) ENGINE=InnoDB;

-- ====================================================================
-- 5. Sale Items Table (Receipt Line Items)
-- Stores individual items billed in each sale
-- ====================================================================
CREATE TABLE sale_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sale_id INT NOT NULL,
    product_id INT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    quantity INT NOT NULL CHECK (quantity > 0),
    total_price DECIMAL(10, 2) NOT NULL CHECK (total_price >= 0),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    INDEX idx_sale_items_sale_id (sale_id),
    INDEX idx_sale_items_product_id (product_id)
) ENGINE=InnoDB;

-- ====================================================================
-- 6. Core Query Reference (for Siddhesh Jagtap's module & presentation)
-- ====================================================================

-- Query A: Get All In-Stock Products
-- SELECT * FROM products ORDER BY name ASC;

-- Query B: Low Stock Warning (Flags items below set threshold)
-- SELECT * FROM products WHERE stock_quantity <= low_stock_threshold ORDER BY stock_quantity ASC;

-- Query C: Deduct Stock on Sale (Executed per line item in a JDBC transaction)
-- UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?;

-- Query D: Search Past Receipts by Date Range
-- SELECT * FROM sales WHERE sale_date BETWEEN ? AND ? ORDER BY sale_date DESC;

-- Query E: Search Past Receipts by Product Name
-- SELECT DISTINCT s.* FROM sales s
-- JOIN sale_items si ON s.id = si.sale_id
-- WHERE si.product_name LIKE ?
-- ORDER BY s.sale_date DESC;
