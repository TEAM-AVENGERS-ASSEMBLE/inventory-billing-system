/**
 * api.js - Dual-Mode API Client
 * Seamlessly connects to Java Servlets & MySQL backend (/api/*),
 * with automatic fallback to client-side demo mode for instant presentations.
 */

const API_BASE = '/api';

// Initial Mock Seed Data (mirrors database/seed_data.sql)
const DEFAULT_PRODUCTS = [
    { id: 1, sku: 'SKU-1001', name: 'Classmate Spiral Notebook (200 pgs)', category: 'Stationery', price: 85.00, stockQuantity: 35, lowStockThreshold: 10 },
    { id: 2, sku: 'SKU-1002', name: 'Reynolds Blue Ball Pen (Pack of 5)', category: 'Stationery', price: 50.00, stockQuantity: 4, lowStockThreshold: 10 }, // Low Stock
    { id: 3, sku: 'SKU-1003', name: 'A4 Copier Paper (500 Sheets Ream)', category: 'Stationery', price: 260.00, stockQuantity: 8, lowStockThreshold: 5 },
    { id: 4, sku: 'SKU-1004', name: 'Camlin Permanent Marker (Black)', category: 'Stationery', price: 30.00, stockQuantity: 2, lowStockThreshold: 5 }, // Low Stock
    { id: 5, sku: 'SKU-1005', name: 'Tata Tea Premium (500g)', category: 'Groceries', price: 230.00, stockQuantity: 20, lowStockThreshold: 5 },
    { id: 6, sku: 'SKU-1006', name: 'Nescafe Classic Instant Coffee (100g)', category: 'Groceries', price: 310.00, stockQuantity: 3, lowStockThreshold: 5 }, // Low Stock
    { id: 7, sku: 'SKU-1007', name: 'Fortune Sunflower Oil (1 Litre)', category: 'Groceries', price: 145.00, stockQuantity: 15, lowStockThreshold: 6 },
    { id: 8, sku: 'SKU-1008', name: 'Aashirvaad Whole Wheat Atta (5kg)', category: 'Groceries', price: 275.00, stockQuantity: 12, lowStockThreshold: 4 },
    { id: 9, sku: 'SKU-1009', name: 'Amul Butter (500g)', category: 'Dairy', price: 275.00, stockQuantity: 18, lowStockThreshold: 5 },
    { id: 10, sku: 'SKU-1010', name: 'Dettol Original Handwash (200ml)', category: 'Personal Care', price: 99.00, stockQuantity: 25, lowStockThreshold: 8 },
    { id: 11, sku: 'SKU-1011', name: 'Colgate Total Toothpaste (150g)', category: 'Personal Care', price: 120.00, stockQuantity: 5, lowStockThreshold: 8 }, // Low Stock
    { id: 12, sku: 'SKU-1012', name: 'Boat Fast USB Type-C Cable', category: 'Electronics', price: 199.00, stockQuantity: 14, lowStockThreshold: 5 }
];

const DEFAULT_SALES = [
    {
        id: 1,
        invoiceNumber: 'INV-20261001-001',
        customerName: 'Rohan Sharma',
        customerPhone: '9876543210',
        subtotal: 445.00,
        tax: 22.25,
        discount: 0.00,
        grandTotal: 467.25,
        paymentMethod: 'UPI',
        saleDate: '2026-10-05 11:30:00',
        items: [
            { id: 1, productId: 1, productName: 'Classmate Spiral Notebook (200 pgs)', unitPrice: 85.00, quantity: 2, totalPrice: 170.00 },
            { id: 2, productId: 8, productName: 'Aashirvaad Whole Wheat Atta (5kg)', unitPrice: 275.00, quantity: 1, totalPrice: 275.00 }
        ]
    },
    {
        id: 2,
        invoiceNumber: 'INV-20261002-002',
        customerName: 'Pooja Verma',
        customerPhone: '9822012345',
        subtotal: 535.00,
        tax: 26.75,
        discount: 20.00,
        grandTotal: 541.75,
        paymentMethod: 'Cash',
        saleDate: '2026-10-06 16:45:00',
        items: [
            { id: 3, productId: 5, productName: 'Tata Tea Premium (500g)', unitPrice: 230.00, quantity: 1, totalPrice: 230.00 },
            { id: 4, productId: 12, productName: 'Boat Fast USB Type-C Cable', unitPrice: 199.00, quantity: 1, totalPrice: 199.00 },
            { id: 5, productId: 10, productName: 'Dettol Original Handwash (200ml)', unitPrice: 99.00, quantity: 1, totalPrice: 99.00 }
        ]
    }
];

const DEFAULT_USERS = [
    { username: 'admin', password: 'admin123', fullName: 'Suraj Gusain (Store Admin)', role: 'admin' },
    { username: 'cashier', password: 'cashier123', fullName: 'Saurabh Mishra (POS Cashier)', role: 'cashier' },
    { username: 'siddhesh', password: 'password', fullName: 'Siddhesh Jagtap (Database)', role: 'admin' }
];

class InventoryApiClient {
    constructor() {
        this.isLiveBackend = false;
        this.initLocalStorage();
    }

    initLocalStorage() {
        if (!localStorage.getItem('inv_products')) {
            localStorage.setItem('inv_products', JSON.stringify(DEFAULT_PRODUCTS));
        }
        if (!localStorage.getItem('inv_sales')) {
            localStorage.setItem('inv_sales', JSON.stringify(DEFAULT_SALES));
        }
        if (!localStorage.getItem('inv_users')) {
            localStorage.setItem('inv_users', JSON.stringify(DEFAULT_USERS));
        }
    }

    // =========================================================================
    // Authentication API (Live Servlet or Client Demo Mode)
    // =========================================================================
    async login(username, password, role = 'admin') {
        const cleanUser = (username || '').trim().toLowerCase();
        const cleanPass = (password || '').trim();

        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/auth`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: cleanUser, password: cleanPass, role: role })
                });
                if (res.ok) {
                    const data = await res.json();
                    sessionStorage.setItem('inv_current_user', JSON.stringify(data));
                    return data;
                }
                const err = await res.json();
                throw new Error(err.error || 'Authentication failed');
            } catch (err) {
                if (this.isLiveBackend && !err.message.includes('fetch')) throw err;
            }
        }

        // Local Storage / Demo Simulation
        const users = JSON.parse(localStorage.getItem('inv_users') || JSON.stringify(DEFAULT_USERS));
        const matched = users.find(u => u.username.toLowerCase() === cleanUser && u.password === cleanPass);

        if (!matched) {
            throw new Error('Invalid credentials. Demo accounts: admin / admin123 or cashier / cashier123');
        }

        const sessionUser = {
            username: matched.username,
            fullName: matched.fullName || matched.username,
            role: matched.role || role
        };

        sessionStorage.setItem('inv_current_user', JSON.stringify(sessionUser));
        return sessionUser;
    }

    getCurrentUser() {
        const raw = sessionStorage.getItem('inv_current_user');
        return raw ? JSON.parse(raw) : null;
    }

    logout() {
        sessionStorage.removeItem('inv_current_user');
        if (this.isLiveBackend) {
            fetch(`${API_BASE}/auth?action=logout`, { method: 'POST' }).catch(() => {});
        }
    }

    async checkBackendHealth() {
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 1500);
            const res = await fetch(`${API_BASE}/dashboard`, { signal: controller.signal });
            clearTimeout(timeoutId);
            if (res.ok) {
                this.isLiveBackend = true;
                return true;
            }
        } catch (e) {
            this.isLiveBackend = false;
        }
        return false;
    }

    // =========================================================================
    // Products API
    // =========================================================================
    async getProducts(lowStockOnly = false) {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/products${lowStockOnly ? '?lowStock=true' : ''}`);
                if (res.ok) return await res.json();
            } catch (err) {
                console.warn('Backend call failed, using local fallback', err);
            }
        }

        // Local Storage Simulation
        let products = JSON.parse(localStorage.getItem('inv_products') || '[]');
        products = products.map(p => ({
            ...p,
            isLowStock: Number(p.stockQuantity) <= Number(p.lowStockThreshold)
        }));

        if (lowStockOnly) {
            return products.filter(p => p.isLowStock);
        }
        return products;
    }

    async saveProduct(productData) {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/products`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(productData)
                });
                if (res.ok) return await res.json();
                const err = await res.json();
                throw new Error(err.error || 'Failed to save product');
            } catch (err) {
                if (this.isLiveBackend) throw err;
            }
        }

        // Local Storage Simulation
        let products = JSON.parse(localStorage.getItem('inv_products') || '[]');
        if (productData.id) {
            const index = products.findIndex(p => p.id == productData.id);
            if (index !== -1) {
                products[index] = { ...products[index], ...productData };
            }
        } else {
            const newId = products.length ? Math.max(...products.map(p => p.id)) + 1 : 1;
            const newProduct = {
                ...productData,
                id: newId,
                sku: productData.sku || `SKU-${1000 + newId}`,
                stockQuantity: Number(productData.stockQuantity || 0),
                lowStockThreshold: Number(productData.lowStockThreshold || 5),
                price: Number(productData.price || 0)
            };
            products.push(newProduct);
        }
        localStorage.setItem('inv_products', JSON.stringify(products));
        return { success: true };
    }

    async restockProduct(productId, quantityToAdd) {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/products`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'restock', id: productId, quantity: quantityToAdd })
                });
                if (res.ok) return await res.json();
            } catch (err) {
                if (this.isLiveBackend) throw err;
            }
        }

        // Local Storage Simulation
        let products = JSON.parse(localStorage.getItem('inv_products') || '[]');
        const prod = products.find(p => p.id == productId);
        if (prod) {
            prod.stockQuantity = Number(prod.stockQuantity) + Number(quantityToAdd);
            localStorage.setItem('inv_products', JSON.stringify(products));
            return { success: true, message: 'Stock restocked successfully' };
        }
        throw new Error('Product not found');
    }

    async deleteProduct(productId) {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/products?id=${productId}`, { method: 'DELETE' });
                if (res.ok) return await res.json();
            } catch (err) {
                if (this.isLiveBackend) throw err;
            }
        }

        let products = JSON.parse(localStorage.getItem('inv_products') || '[]');
        products = products.filter(p => p.id != productId);
        localStorage.setItem('inv_products', JSON.stringify(products));
        return { success: true };
    }

    // =========================================================================
    // Billing & Instant Receipt Generation (Slide 5 Flow)
    // =========================================================================
    async processSale(saleOrder) {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/billing`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(saleOrder)
                });
                const data = await res.json();
                if (!res.ok) {
                    throw new Error(data.error || 'Checkout failed');
                }
                return data;
            } catch (err) {
                if (this.isLiveBackend) throw err;
            }
        }

        // Local Storage Simulation: Exact transactional stock deduction
        let products = JSON.parse(localStorage.getItem('inv_products') || '[]');

        // Step 1: Stock verification
        for (const item of saleOrder.items) {
            const prod = products.find(p => p.id == item.productId);
            if (!prod) {
                throw new Error(`Product "${item.productName}" not found in catalog.`);
            }
            if (Number(prod.stockQuantity) < Number(item.quantity)) {
                throw new Error(`Insufficient stock for "${prod.name}". Available: ${prod.stockQuantity}, Requested: ${item.quantity}`);
            }
        }

        // Step 2: Atomic Stock Deduction
        for (const item of saleOrder.items) {
            const prod = products.find(p => p.id == item.productId);
            prod.stockQuantity = Number(prod.stockQuantity) - Number(item.quantity);
        }
        localStorage.setItem('inv_products', JSON.stringify(products));

        // Step 3: Record Sale & Line Items
        let sales = JSON.parse(localStorage.getItem('inv_sales') || '[]');
        const newSaleId = sales.length ? Math.max(...sales.map(s => s.id)) + 1 : 1;
        const now = new Date();
        const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
        const randomCode = Math.floor(1000 + Math.random() * 9000);
        const invoiceNumber = `INV-${dateStr}-${randomCode}`;

        const completedSale = {
            id: newSaleId,
            invoiceNumber: invoiceNumber,
            customerName: saleOrder.customerName || 'Walk-in Customer',
            customerPhone: saleOrder.customerPhone || '',
            subtotal: Number(saleOrder.subtotal),
            tax: Number(saleOrder.tax),
            discount: Number(saleOrder.discount || 0),
            grandTotal: Number(saleOrder.grandTotal),
            paymentMethod: saleOrder.paymentMethod || 'Cash',
            saleDate: now.toISOString().replace('T', ' ').slice(0, 19),
            items: saleOrder.items.map((it, idx) => ({
                id: idx + 1,
                productId: it.productId,
                productName: it.productName,
                unitPrice: Number(it.unitPrice),
                quantity: Number(it.quantity),
                totalPrice: Number(it.unitPrice) * Number(it.quantity)
            }))
        };

        sales.unshift(completedSale);
        localStorage.setItem('inv_sales', JSON.stringify(sales));

        return completedSale;
    }

    // =========================================================================
    // Sales History API (Slide 3: Searchable by date or item)
    // =========================================================================
    async getSalesHistory(fromDate = '', toDate = '', searchKeyword = '') {
        if (this.isLiveBackend) {
            try {
                const params = new URLSearchParams();
                if (fromDate) params.append('fromDate', fromDate);
                if (toDate) params.append('toDate', toDate);
                if (searchKeyword) params.append('item', searchKeyword);

                const res = await fetch(`${API_BASE}/sales?${params.toString()}`);
                if (res.ok) return await res.json();
            } catch (err) {
                console.warn('Backend history fetch failed, using local fallback', err);
            }
        }

        let sales = JSON.parse(localStorage.getItem('inv_sales') || '[]');
        
        return sales.filter(s => {
            // Date filter
            if (fromDate && s.saleDate.slice(0, 10) < fromDate) return false;
            if (toDate && s.saleDate.slice(0, 10) > toDate) return false;

            // Search by item name or invoice number
            if (searchKeyword) {
                const kw = searchKeyword.toLowerCase();
                const matchesInvoice = s.invoiceNumber.toLowerCase().includes(kw);
                const matchesCustomer = s.customerName.toLowerCase().includes(kw);
                const matchesItem = s.items && s.items.some(i => i.productName.toLowerCase().includes(kw));
                if (!matchesInvoice && !matchesCustomer && !matchesItem) return false;
            }

            return true;
        });
    }

    // =========================================================================
    // Dashboard Stats API
    // =========================================================================
    async getDashboardStats() {
        if (this.isLiveBackend) {
            try {
                const res = await fetch(`${API_BASE}/dashboard`);
                if (res.ok) return await res.json();
            } catch (err) {
                console.warn('Backend stats fetch failed, using local fallback', err);
            }
        }

        const products = JSON.parse(localStorage.getItem('inv_products') || '[]');
        const sales = JSON.parse(localStorage.getItem('inv_sales') || '[]');

        const lowStockCount = products.filter(p => Number(p.stockQuantity) <= Number(p.lowStockThreshold)).length;
        
        const todayStr = new Date().toISOString().slice(0, 10);
        const todaySales = sales.filter(s => s.saleDate && s.saleDate.startsWith(todayStr));
        const todayRevenue = todaySales.reduce((sum, s) => sum + Number(s.grandTotal || 0), 0);

        return {
            totalProducts: products.length,
            lowStockCount: lowStockCount,
            todayOrders: todaySales.length,
            todayRevenue: todayRevenue
        };
    }

    // =========================================================================
    // Sales & Product Analytics API (Day, Week, Month)
    // =========================================================================
    async getAnalyticsData(period = 'day', customFrom = '', customTo = '') {
        if (this.isLiveBackend) {
            try {
                const params = new URLSearchParams();
                if (period) params.append('period', period);
                if (customFrom) params.append('fromDate', customFrom);
                if (customTo) params.append('toDate', customTo);

                const res = await fetch(`${API_BASE}/analytics?${params.toString()}`);
                if (res.ok) return await res.json();
            } catch (err) {
                console.warn('Backend analytics fetch failed, using local fallback', err);
            }
        }

        // Local Storage Simulation for Analytics
        const sales = JSON.parse(localStorage.getItem('inv_sales') || '[]');
        const products = JSON.parse(localStorage.getItem('inv_products') || '[]');

        const now = new Date();
        const formatDate = (d) => d.toISOString().slice(0, 10);

        let fromDate = customFrom;
        let toDate = customTo;

        if (!fromDate || !toDate) {
            toDate = formatDate(now);
            if (period === 'week') {
                const weekAgo = new Date();
                weekAgo.setDate(now.getDate() - 6);
                fromDate = formatDate(weekAgo);
            } else if (period === 'month') {
                const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
                fromDate = formatDate(monthStart);
            } else {
                // Day (Today)
                fromDate = formatDate(now);
            }
        }

        // Filter sales
        const filteredSales = sales.filter(s => {
            const sDate = (s.saleDate || '').slice(0, 10);
            return sDate >= fromDate && sDate <= toDate;
        });

        const totalOrders = filteredSales.length;
        const totalRevenue = filteredSales.reduce((acc, s) => acc + Number(s.grandTotal || 0), 0);
        const avgOrderValue = totalOrders > 0 ? (totalRevenue / totalOrders) : 0;

        // Product and Category level aggregation
        const productStatsMap = {};
        const categoryStatsMap = {};
        let totalUnitsSold = 0;

        filteredSales.forEach(sale => {
            if (sale.items && Array.isArray(sale.items)) {
                sale.items.forEach(it => {
                    const pid = it.productId;
                    totalUnitsSold += Number(it.quantity || 0);

                    if (!productStatsMap[pid]) {
                        const origProd = products.find(p => p.id === pid) || {};
                        productStatsMap[pid] = {
                            productId: pid,
                            productName: it.productName,
                            category: origProd.category || 'General',
                            stockRemaining: origProd.stockQuantity !== undefined ? origProd.stockQuantity : 0,
                            unitsSold: 0,
                            revenueGenerated: 0
                        };
                    }
                    productStatsMap[pid].unitsSold += Number(it.quantity || 0);
                    productStatsMap[pid].revenueGenerated += Number(it.totalPrice || (it.unitPrice * it.quantity) || 0);

                    const cat = productStatsMap[pid].category;
                    if (!categoryStatsMap[cat]) {
                        categoryStatsMap[cat] = { category: cat, unitsSold: 0, revenue: 0 };
                    }
                    categoryStatsMap[cat].unitsSold += Number(it.quantity || 0);
                    categoryStatsMap[cat].revenue += Number(it.totalPrice || (it.unitPrice * it.quantity) || 0);
                });
            }
        });

        const topProducts = Object.values(productStatsMap).sort((a, b) => b.unitsSold - a.unitsSold);
        const categories = Object.values(categoryStatsMap).sort((a, b) => b.revenue - a.revenue);

        // Daily trend list in range
        const dailyTrend = [];
        const curr = new Date(fromDate);
        const end = new Date(toDate);
        while (curr <= end) {
            const dStr = formatDate(curr);
            const dSales = filteredSales.filter(s => (s.saleDate || '').startsWith(dStr));
            dailyTrend.push({
                date: dStr,
                orders: dSales.length,
                revenue: dSales.reduce((sum, s) => sum + Number(s.grandTotal || 0), 0)
            });
            curr.setDate(curr.getDate() + 1);
        }

        return {
            period: period,
            fromDate: fromDate,
            toDate: toDate,
            summary: {
                totalOrders: totalOrders,
                totalRevenue: totalRevenue,
                avgOrderValue: avgOrderValue,
                totalUnitsSold: totalUnitsSold
            },
            topProducts: topProducts,
            categories: categories,
            dailyTrend: dailyTrend
        };
    }
}

// Global API instance
window.api = new InventoryApiClient();
