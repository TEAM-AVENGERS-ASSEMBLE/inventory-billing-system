package com.inventory.model;

import java.sql.Timestamp;

/**
 * Product Model - Represents items in shop inventory
 */
public class Product {
    private int id;
    private String sku;
    private String name;
    private String category;
    private double price;
    private int stockQuantity;
    private int lowStockThreshold;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Derived flag for low stock warning
    private boolean isLowStock;

    public Product() {
        this.lowStockThreshold = 5;
    }

    public Product(int id, String sku, String name, String category, double price, int stockQuantity, int lowStockThreshold) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.lowStockThreshold = lowStockThreshold;
        this.isLowStock = stockQuantity <= lowStockThreshold;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { 
        this.stockQuantity = stockQuantity;
        this.isLowStock = (this.stockQuantity <= this.lowStockThreshold);
    }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { 
        this.lowStockThreshold = lowStockThreshold;
        this.isLowStock = (this.stockQuantity <= this.lowStockThreshold);
    }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isLowStock() { return isLowStock; }
    public void setLowStock(boolean lowStock) { isLowStock = lowStock; }
}
