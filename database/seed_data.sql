-- ====================================================================
-- Inventory & Billing System - Seed Data
-- Designed by: Siddhesh Jagtap
-- ====================================================================

USE inventory_db;

-- 1. Insert Initial Product Catalog
-- Includes healthy stock, low-stock items (<= threshold), and zero-stock items
INSERT INTO products (sku, name, category, price, stock_quantity, low_stock_threshold) VALUES
('SKU-1001', 'Classmate Spiral Notebook (200 pgs)', 'Stationery', 85.00, 35, 10),
('SKU-1002', 'Reynolds Blue Ball Pen (Pack of 5)', 'Stationery', 50.00, 4, 10),  -- LOW STOCK ALERT (4 <= 10)
('SKU-1003', 'A4 Copier Paper (500 Sheets Ream)', 'Stationery', 260.00, 8, 5),
('SKU-1004', 'Camlin Permanent Marker (Black)', 'Stationery', 30.00, 2, 5),   -- LOW STOCK ALERT (2 <= 5)
('SKU-1005', 'Tata Tea Premium (500g)', 'Groceries', 230.00, 20, 5),
('SKU-1006', 'Nescafe Classic Instant Coffee (100g)', 'Groceries', 310.00, 3, 5), -- LOW STOCK ALERT (3 <= 5)
('SKU-1007', 'Fortune Sunflower Oil (1 Litre)', 'Groceries', 145.00, 15, 6),
('SKU-1008', 'Aashirvaad Whole Wheat Atta (5kg)', 'Groceries', 275.00, 12, 4),
('SKU-1009', 'Amul Butter (500g)', 'Dairy', 275.00, 18, 5),
('SKU-1010', 'Dettol Original Handwash (200ml)', 'Personal Care', 99.00, 25, 8),
('SKU-1011', 'Colgate Total Toothpaste (150g)', 'Personal Care', 120.00, 5, 8), -- LOW STOCK ALERT (5 <= 8)
('SKU-1012', 'Boat Fast USB Type-C Cable', 'Electronics', 199.00, 14, 5);

-- 2. Insert Sample Past Transactions for History Search Demo
INSERT INTO sales (id, invoice_number, customer_name, customer_phone, subtotal, tax, discount, grand_total, payment_method, sale_date) VALUES
(1, 'INV-20261001-001', 'Rohan Sharma', '9876543210', 445.00, 22.25, 0.00, 467.25, 'UPI', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 'INV-20261002-002', 'Pooja Verma', '9822012345', 535.00, 26.75, 20.00, 541.75, 'Cash', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 'INV-20261003-003', 'Walk-in Customer', '', 310.00, 15.50, 0.00, 325.50, 'Card', NOW());

-- 3. Insert Items for the Sample Sales
INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, quantity, total_price) VALUES
(1, 1, 'Classmate Spiral Notebook (200 pgs)', 85.00, 2, 170.00),
(1, 8, 'Aashirvaad Whole Wheat Atta (5kg)', 275.00, 1, 275.00),
(2, 5, 'Tata Tea Premium (500g)', 230.00, 1, 230.00),
(2, 12, 'Boat Fast USB Type-C Cable', 199.00, 1, 199.00),
(2, 10, 'Dettol Original Handwash (200ml)', 99.00, 1, 99.00),
(3, 6, 'Nescafe Classic Instant Coffee (100g)', 310.00, 1, 310.00);
