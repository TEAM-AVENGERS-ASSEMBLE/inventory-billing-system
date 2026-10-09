package com.inventory.dao;

import com.inventory.config.DBConnection;
import com.inventory.model.Sale;
import com.inventory.model.SaleItem;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * SaleDAO - Data Access Object for Billing & Receipt Generation
 * Implements atomic stock deduction and transactional receipt persistence
 * Key Flow: Slide 5 - Single request atomic transaction
 */
public class SaleDAO {

    /**
     * Executes atomic sale checkout:
     * 1. Validates and locks product stock
     * 2. Deducts stock quantity from products table
     * 3. Saves sale header into sales table
     * 4. Saves line items into sale_items table
     * 5. Commits transaction and returns receipt
     *
     * @param sale The sale object containing customer details and line items
     * @return Completed Sale object with generated invoice number and ID
     * @throws SQLException, IllegalStateException on validation or database error
     */
    public Sale processSaleTransaction(Sale sale) throws SQLException, IllegalStateException {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot process sale: Cart has no items.");
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            // Start JDBC Transaction
            conn.setAutoCommit(false);

            // Step 1: Verify stock and deduct inventory for each item
            double calculatedSubtotal = 0.0;
            String checkAndLockSql = "SELECT name, price, stock_quantity FROM products WHERE id = ? FOR UPDATE";
            String deductStockSql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?";

            try (PreparedStatement lockPs = conn.prepareStatement(checkAndLockSql);
                 PreparedStatement deductPs = conn.prepareStatement(deductStockSql)) {

                for (SaleItem item : sale.getItems()) {
                    lockPs.setInt(1, item.getProductId());
                    try (ResultSet rs = lockPs.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Product ID " + item.getProductId() + " not found.");
                        }
                        String productName = rs.getString("name");
                        double currentPrice = rs.getDouble("price");
                        int currentStock = rs.getInt("stock_quantity");

                        if (currentStock < item.getQuantity()) {
                            throw new IllegalStateException(
                                String.format("Insufficient stock for '%s'. Available: %d, Requested: %d",
                                    productName, currentStock, item.getQuantity())
                            );
                        }

                        // Sync item details
                        item.setProductName(productName);
                        if (item.getUnitPrice() <= 0) {
                            item.setUnitPrice(currentPrice);
                        }
                        item.setTotalPrice(item.getUnitPrice() * item.getQuantity());
                        calculatedSubtotal += item.getTotalPrice();

                        // Deduct stock in DB
                        deductPs.setInt(1, item.getQuantity());
                        deductPs.setInt(2, item.getProductId());
                        deductPs.executeUpdate();
                    }
                }
            }

            // Step 2: Calculate financials
            sale.setSubtotal(calculatedSubtotal);
            double tax = sale.getTax() > 0 ? sale.getTax() : (calculatedSubtotal * 0.05); // 5% default tax
            sale.setTax(Math.round(tax * 100.0) / 100.0);
            double grandTotal = (calculatedSubtotal + sale.getTax()) - sale.getDiscount();
            sale.setGrandTotal(Math.max(0.0, Math.round(grandTotal * 100.0) / 100.0));

            // Generate Invoice Number if not set
            if (sale.getInvoiceNumber() == null || sale.getInvoiceNumber().trim().isEmpty()) {
                String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
                String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                sale.setInvoiceNumber("INV-" + datePart + "-" + randomPart);
            }

            // Step 3: Insert into sales table
            String insertSaleSql = "INSERT INTO sales (invoice_number, customer_name, customer_phone, " +
                                   "subtotal, tax, discount, grand_total, payment_method) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement salePs = conn.prepareStatement(insertSaleSql, Statement.RETURN_GENERATED_KEYS)) {
                salePs.setString(1, sale.getInvoiceNumber());
                salePs.setString(2, sale.getCustomerName() == null ? "Walk-in Customer" : sale.getCustomerName());
                salePs.setString(3, sale.getCustomerPhone() == null ? "" : sale.getCustomerPhone());
                salePs.setDouble(4, sale.getSubtotal());
                salePs.setDouble(5, sale.getTax());
                salePs.setDouble(6, sale.getDiscount());
                salePs.setDouble(7, sale.getGrandTotal());
                salePs.setString(8, sale.getPaymentMethod() == null ? "Cash" : sale.getPaymentMethod());

                salePs.executeUpdate();

                try (ResultSet keys = salePs.getGeneratedKeys()) {
                    if (keys.next()) {
                        sale.setId(keys.getInt(1));
                    }
                }
            }

            // Step 4: Insert items into sale_items table
            String insertItemSql = "INSERT INTO sale_items (sale_id, product_id, product_name, unit_price, quantity, total_price) " +
                                   "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement itemPs = conn.prepareStatement(insertItemSql)) {
                for (SaleItem item : sale.getItems()) {
                    item.setSaleId(sale.getId());
                    itemPs.setInt(1, sale.getId());
                    itemPs.setInt(2, item.getProductId());
                    itemPs.setString(3, item.getProductName());
                    itemPs.setDouble(4, item.getUnitPrice());
                    itemPs.setInt(5, item.getQuantity());
                    itemPs.setDouble(6, item.getTotalPrice());
                    itemPs.addBatch();
                }
                itemPs.executeBatch();
            }

            // Step 5: Commit JDBC Transaction
            conn.commit();
            sale.setSaleDate(new Timestamp(System.currentTimeMillis()));
            return sale;

        } catch (SQLException | RuntimeException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Transaction rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("Error closing connection: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * Search past sales receipts by date range and/or product name
     * (Slide 3: "Every past receipt searchable by date or item")
     */
    public List<Sale> searchSales(String fromDate, String toDate, String itemKeyword) throws SQLException {
        List<Sale> sales = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT s.* FROM sales s ");
        
        boolean filterItem = (itemKeyword != null && !itemKeyword.trim().isEmpty());
        if (filterItem) {
            sql.append("JOIN sale_items si ON s.id = si.sale_id ");
        }

        sql.append("WHERE 1=1 ");
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND s.sale_date >= ? ");
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND s.sale_date <= ? ");
        }
        if (filterItem) {
            sql.append("AND si.product_name LIKE ? ");
        }
        sql.append("ORDER BY s.sale_date DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (fromDate != null && !fromDate.trim().isEmpty()) {
                ps.setString(paramIndex++, fromDate.trim() + " 00:00:00");
            }
            if (toDate != null && !toDate.trim().isEmpty()) {
                ps.setString(paramIndex++, toDate.trim() + " 23:59:59");
            }
            if (filterItem) {
                ps.setString(paramIndex++, "%" + itemKeyword.trim() + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sale s = mapResultSetToSale(rs);
                    // Fetch line items for this sale
                    s.setItems(getSaleItems(conn, s.getId()));
                    sales.add(s);
                }
            }
        }
        return sales;
    }

    /**
     * Retrieves full sale receipt by primary key ID.
     */
    public Sale getSaleById(int saleId) throws SQLException {
        String sql = "SELECT * FROM sales WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    sale.setItems(getSaleItems(conn, sale.getId()));
                    return sale;
                }
            }
        }
        return null;
    }

    /**
     * Retrieves dashboard summary: today's sales count and today's total revenue.
     */
    public double[] getTodaySalesSummary() throws SQLException {
        double count = 0.0;
        double revenue = 0.0;
        String sql = "SELECT COUNT(*) as today_orders, COALESCE(SUM(grand_total), 0) as today_revenue " +
                     "FROM sales WHERE DATE(sale_date) = CURDATE()";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                count = rs.getDouble("today_orders");
                revenue = rs.getDouble("today_revenue");
            }
        }
        return new double[]{count, revenue};
    }

    /**
     * Retrieves Sales KPI summary for a specified date range (Day, Week, Month).
     */
    public java.util.Map<String, Object> getSalesSummaryForRange(String fromDate, String toDate) throws SQLException {
        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        String sql = "SELECT COUNT(*) as total_orders, " +
                     "COALESCE(SUM(grand_total), 0) as total_revenue, " +
                     "COALESCE(AVG(grand_total), 0) as avg_order_value " +
                     "FROM sales WHERE sale_date >= ? AND sale_date <= ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fromDate + " 00:00:00");
            ps.setString(2, toDate + " 23:59:59");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("totalOrders", rs.getInt("total_orders"));
                    summary.put("totalRevenue", rs.getDouble("total_revenue"));
                    summary.put("avgOrderValue", rs.getDouble("avg_order_value"));
                }
            }
        }
        return summary;
    }

    /**
     * Retrieves top-selling products and quantities for a date range.
     */
    public List<java.util.Map<String, Object>> getProductSalesAnalytics(String fromDate, String toDate) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT si.product_id, si.product_name, p.category, p.stock_quantity, " +
                     "SUM(si.quantity) as units_sold, SUM(si.total_price) as revenue_generated " +
                     "FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "LEFT JOIN products p ON si.product_id = p.id " +
                     "WHERE s.sale_date >= ? AND s.sale_date <= ? " +
                     "GROUP BY si.product_id, si.product_name, p.category, p.stock_quantity " +
                     "ORDER BY units_sold DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fromDate + " 00:00:00");
            ps.setString(2, toDate + " 23:59:59");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("productId", rs.getInt("product_id"));
                    map.put("productName", rs.getString("product_name"));
                    map.put("category", rs.getString("category") != null ? rs.getString("category") : "General");
                    map.put("stockRemaining", rs.getInt("stock_quantity"));
                    map.put("unitsSold", rs.getInt("units_sold"));
                    map.put("revenueGenerated", rs.getDouble("revenue_generated"));
                    list.add(map);
                }
            }
        }
        return list;
    }

    /**
     * Retrieves sales breakdown by category for a date range.
     */
    public List<java.util.Map<String, Object>> getCategorySalesAnalytics(String fromDate, String toDate) throws SQLException {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT COALESCE(p.category, 'General') as category, " +
                     "SUM(si.quantity) as total_units, SUM(si.total_price) as total_revenue " +
                     "FROM sale_items si " +
                     "JOIN sales s ON si.sale_id = s.id " +
                     "LEFT JOIN products p ON si.product_id = p.id " +
                     "WHERE s.sale_date >= ? AND s.sale_date <= ? " +
                     "GROUP BY COALESCE(p.category, 'General') " +
                     "ORDER BY total_revenue DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fromDate + " 00:00:00");
            ps.setString(2, toDate + " 23:59:59");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("category", rs.getString("category"));
                    map.put("unitsSold", rs.getInt("total_units"));
                    map.put("revenue", rs.getDouble("total_revenue"));
                    list.add(map);
                }
            }
        }
        return list;
    }

    private List<SaleItem> getSaleItems(Connection conn, int saleId) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = "SELECT * FROM sale_items WHERE sale_id = ? ORDER BY id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleItem item = new SaleItem();
                    item.setId(rs.getInt("id"));
                    item.setSaleId(rs.getInt("sale_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setProductName(rs.getString("product_name"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setTotalPrice(rs.getDouble("total_price"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private Sale mapResultSetToSale(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setId(rs.getInt("id"));
        s.setInvoiceNumber(rs.getString("invoice_number"));
        s.setCustomerName(rs.getString("customer_name"));
        s.setCustomerPhone(rs.getString("customer_phone"));
        s.setSubtotal(rs.getDouble("subtotal"));
        s.setTax(rs.getDouble("tax"));
        s.setDiscount(rs.getDouble("discount"));
        s.setGrandTotal(rs.getDouble("grand_total"));
        s.setPaymentMethod(rs.getString("payment_method"));
        s.setSaleDate(rs.getTimestamp("sale_date"));
        return s;
    }
}
