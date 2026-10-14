/**
 * app.js - Inventory & Billing System Controller
 * Designed for: Saurabh Mishra (Frontend - HTML/CSS/JS)
 * Handles POS Cart, Receipt Generation, Stock Updates, Low-Stock Warnings & History
 */

// Application State
let catalogProducts = [];
let billingCart = [];
let currentReceiptData = null;
let currentUser = null;

// =============================================================================
// Initialization
// =============================================================================
document.addEventListener('DOMContentLoaded', async () => {
    // 1. Detect backend status
    const isLive = await window.api.checkBackendHealth();
    updateBackendStatusBadge(isLive);

    // 2. Check Auth State
    currentUser = window.api.getCurrentUser();
    if (!currentUser) {
        showLoginView();
    } else {
        showAppView(currentUser);
        await refreshAllData();
    }

    // 3. Set default date filter to today
    const today = new Date().toISOString().slice(0, 10);
    const toInput = document.getElementById('filter-to-date');
    if (toInput) toInput.value = today;

    // 4. Modal escape key listener
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeAllModals();
        }
    });
});

// =============================================================================
// Authentication & Terminal Session
// =============================================================================
function showLoginView() {
    const loginView = document.getElementById('login-view');
    const appHeader = document.getElementById('app-header');
    const appMain = document.querySelector('.app-main');
    const appFooter = document.querySelector('.app-footer');
    const userBadge = document.getElementById('user-profile-badge');

    if (loginView) loginView.style.display = 'flex';
    if (appHeader) appHeader.style.display = 'none';
    if (appMain) appMain.style.display = 'none';
    if (appFooter) appFooter.style.display = 'none';
    if (userBadge) userBadge.style.display = 'none';
}

function showAppView(user) {
    const loginView = document.getElementById('login-view');
    const appHeader = document.getElementById('app-header');
    const appMain = document.querySelector('.app-main');
    const appFooter = document.querySelector('.app-footer');
    const userBadge = document.getElementById('user-profile-badge');
    const userNameElem = document.getElementById('current-user-name');
    const userRoleElem = document.getElementById('current-user-role');

    if (loginView) loginView.style.display = 'none';
    if (appHeader) appHeader.style.display = 'block';
    if (appMain) appMain.style.display = 'block';
    if (appFooter) appFooter.style.display = 'block';

    if (userBadge && user) {
        userBadge.style.display = 'inline-flex';
        if (userNameElem) userNameElem.textContent = user.fullName || user.username;
        if (userRoleElem) userRoleElem.textContent = user.role === 'admin' ? 'Store Admin' : 'POS Cashier';
    }
}

async function handleLogin(e) {
    if (e && e.preventDefault) e.preventDefault();
    const usernameInput = document.getElementById('login-username');
    const passwordInput = document.getElementById('login-password');
    const roleSelect = document.getElementById('login-role');
    const alertBox = document.getElementById('login-alert');
    const alertText = document.getElementById('login-alert-text');
    const btnSubmit = document.getElementById('btn-login-submit');

    if (alertBox) alertBox.style.display = 'none';
    if (btnSubmit) btnSubmit.disabled = true;

    try {
        const user = await window.api.login(usernameInput.value, passwordInput.value, roleSelect.value);
        currentUser = user;
        showAppView(user);
        await refreshAllData();
    } catch (err) {
        if (alertBox && alertText) {
            alertText.textContent = err.message || 'Login failed. Please check credentials.';
            alertBox.style.display = 'flex';
        }
    } finally {
        if (btnSubmit) btnSubmit.disabled = false;
    }
}

function fillQuickLogin(username, password, role) {
    const usernameInput = document.getElementById('login-username');
    const passwordInput = document.getElementById('login-password');
    const roleSelect = document.getElementById('login-role');
    const alertBox = document.getElementById('login-alert');

    if (usernameInput) usernameInput.value = username;
    if (passwordInput) passwordInput.value = password;
    if (roleSelect) roleSelect.value = role;
    if (alertBox) alertBox.style.display = 'none';

    handleLogin(new Event('submit'));
}

function logoutUser() {
    if (confirm('Are you sure you want to log out of the terminal?')) {
        window.api.logout();
        currentUser = null;
        const passwordInput = document.getElementById('login-password');
        if (passwordInput) passwordInput.value = '';
        showLoginView();
    }
}

function updateBackendStatusBadge(isLive) {
    const badge = document.getElementById('backend-status');
    const text = document.getElementById('backend-status-text');
    if (isLive) {
        badge.className = 'status-badge status-live';
        text.textContent = 'Live Backend (Servlets + MySQL)';
    } else {
        badge.className = 'status-badge status-demo';
        text.textContent = 'Demo Mode (Interactive Simulation)';
    }
}

async function refreshAllData() {
    catalogProducts = await window.api.getProducts();
    populateCategoryDropdowns();
    renderPosCatalog();
    renderInventoryTable();
    renderLowStockAlerts();
    await updateDashboardMetrics();
    await loadSalesHistory();
}

async function updateDashboardMetrics() {
    const stats = await window.api.getDashboardStats();
    
    document.getElementById('metric-total-products').textContent = stats.totalProducts || 0;
    
    const lowStockCount = stats.lowStockCount || 0;
    document.getElementById('metric-low-stock').textContent = lowStockCount;
    document.getElementById('nav-alert-count').textContent = lowStockCount;
    
    document.getElementById('metric-today-orders').textContent = stats.todayOrders || 0;
    document.getElementById('metric-today-revenue').textContent = `₹${(stats.todayRevenue || 0).toFixed(2)}`;
}

// =============================================================================
// Tab Switching
// =============================================================================
function switchTab(tabId) {
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabId);
    });

    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.toggle('active', content.id === `tab-${tabId}`);
    });

    if (tabId === 'history') {
        loadSalesHistory();
    } else if (tabId === 'alerts') {
        renderLowStockAlerts();
    } else if (tabId === 'inventory') {
        renderInventoryTable();
    } else if (tabId === 'billing') {
        renderPosCatalog();
    } else if (tabId === 'analytics') {
        loadAnalytics();
    }
}

// =============================================================================
// TAB 1: POS BILLING & INSTANT RECEIPT GENERATOR
// =============================================================================

function populateCategoryDropdowns() {
    const categories = Array.from(new Set(catalogProducts.map(p => p.category || 'General'))).sort();
    
    const posSelect = document.getElementById('pos-category-filter');
    const invSelect = document.getElementById('inv-category-filter');

    const buildOptions = (currentVal) => {
        let html = '<option value="">All Categories</option>';
        categories.forEach(cat => {
            html += `<option value="${escapeHtml(cat)}">${escapeHtml(cat)}</option>`;
        });
        return html;
    };

    if (posSelect) posSelect.innerHTML = buildOptions();
    if (invSelect) invSelect.innerHTML = buildOptions();
}

function filterPosCatalog() {
    renderPosCatalog();
}

function renderPosCatalog() {
    const container = document.getElementById('pos-catalog-container');
    const searchVal = (document.getElementById('pos-search-input')?.value || '').toLowerCase().trim();
    const categoryVal = document.getElementById('pos-category-filter')?.value || '';

    const filtered = catalogProducts.filter(p => {
        const matchesSearch = !searchVal || 
            p.name.toLowerCase().includes(searchVal) || 
            p.sku.toLowerCase().includes(searchVal);
        const matchesCategory = !categoryVal || p.category === categoryVal;
        return matchesSearch && matchesCategory;
    });

    if (filtered.length === 0) {
        container.innerHTML = `<div class="empty-state" style="grid-column: 1 / -1;">No matching products found.</div>`;
        return;
    }

    container.innerHTML = filtered.map(p => {
        const isOutOfStock = p.stockQuantity <= 0;
        const isLow = p.stockQuantity <= p.lowStockThreshold && !isOutOfStock;
        
        let stockClass = '';
        let stockText = `${p.stockQuantity} in stock`;
        if (isOutOfStock) {
            stockClass = 'zero-stock';
            stockText = 'Out of Stock';
        } else if (isLow) {
            stockClass = 'low-stock';
            stockText = `Low: ${p.stockQuantity} left`;
        }

        return `
            <div class="pos-item-card ${isOutOfStock ? 'out-of-stock' : ''}" onclick="addItemToCart(${p.id})">
                <span class="pos-item-category">${escapeHtml(p.category || 'General')}</span>
                <h4 class="pos-item-name">${escapeHtml(p.name)}</h4>
                <div class="pos-item-footer">
                    <span class="pos-item-price">₹${Number(p.price).toFixed(2)}</span>
                    <span class="pos-item-stock ${stockClass}">${stockText}</span>
                </div>
            </div>
        `;
    }).join('');
}

function addItemToCart(productId) {
    const prod = catalogProducts.find(p => p.id === productId);
    if (!prod) return;

    if (prod.stockQuantity <= 0) {
        alert(`Cannot add "${prod.name}": Item is currently out of stock.`);
        return;
    }

    const existingIndex = billingCart.findIndex(item => item.productId === productId);
    if (existingIndex !== -1) {
        if (billingCart[existingIndex].quantity + 1 > prod.stockQuantity) {
            alert(`Cannot add more than ${prod.stockQuantity} items of "${prod.name}" (Current stock limit).`);
            return;
        }
        billingCart[existingIndex].quantity += 1;
    } else {
        billingCart.push({
            productId: prod.id,
            productName: prod.name,
            unitPrice: Number(prod.price),
            quantity: 1,
            maxStock: prod.stockQuantity
        });
    }

    renderCartTable();
    calculateBillTotals();
}

function updateCartQuantity(productId, newQty) {
    const item = billingCart.find(i => i.productId === productId);
    if (!item) return;

    newQty = parseInt(newQty);
    if (isNaN(newQty) || newQty <= 0) {
        removeCartItem(productId);
        return;
    }

    if (newQty > item.maxStock) {
        alert(`Maximum available stock for "${item.productName}" is ${item.maxStock}.`);
        item.quantity = item.maxStock;
    } else {
        item.quantity = newQty;
    }

    renderCartTable();
    calculateBillTotals();
}

function removeCartItem(productId) {
    billingCart = billingCart.filter(i => i.productId !== productId);
    renderCartTable();
    calculateBillTotals();
}

function clearCart() {
    billingCart = [];
    renderCartTable();
    calculateBillTotals();
}

function renderCartTable() {
    const tbody = document.getElementById('cart-items-body');
    if (billingCart.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-state">No items in active bill. Click products to add.</td></tr>`;
        return;
    }

    tbody.innerHTML = billingCart.map(item => {
        const lineTotal = (item.unitPrice * item.quantity).toFixed(2);
        return `
            <tr>
                <td><strong>${escapeHtml(item.productName)}</strong></td>
                <td>₹${item.unitPrice.toFixed(2)}</td>
                <td>
                    <div class="qty-control">
                        <button type="button" class="qty-btn" onclick="updateCartQuantity(${item.productId}, ${item.quantity - 1})">-</button>
                        <input type="text" class="qty-input" value="${item.quantity}" onchange="updateCartQuantity(${item.productId}, this.value)">
                        <button type="button" class="qty-btn" onclick="updateCartQuantity(${item.productId}, ${item.quantity + 1})">+</button>
                    </div>
                </td>
                <td style="font-weight: 700; font-family: var(--font-mono);">₹${lineTotal}</td>
                <td>
                    <button class="btn-remove-item" onclick="removeCartItem(${item.productId})" title="Remove item">&times;</button>
                </td>
            </tr>
        `;
    }).join('');
}

function calculateBillTotals() {
    const subtotal = billingCart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
    const tax = Math.round(subtotal * 0.05 * 100) / 100; // 5% GST
    const discountInput = document.getElementById('bill-discount');
    const discount = Math.max(0, parseFloat(discountInput?.value || 0));
    const grandTotal = Math.max(0, Math.round((subtotal + tax - discount) * 100) / 100);

    document.getElementById('bill-subtotal').textContent = `₹${subtotal.toFixed(2)}`;
    document.getElementById('bill-tax').textContent = `₹${tax.toFixed(2)}`;
    document.getElementById('bill-grand-total').textContent = `₹${grandTotal.toFixed(2)}`;

    return { subtotal, tax, discount, grandTotal };
}

/**
 * Slide 5 Core Flow:
 * "Each sale runs through one flow: the Servlet reads the order,
 *  JDBC updates MySQL, stock is deducted, and a receipt is generated and sent back — all in a single request."
 */
async function processSaleAndGenerateReceipt() {
    if (billingCart.length === 0) {
        alert('Active bill is empty. Please select products to bill.');
        return;
    }

    const { subtotal, tax, discount, grandTotal } = calculateBillTotals();
    const customerName = document.getElementById('cust-name').value.trim() || 'Walk-in Customer';
    const customerPhone = document.getElementById('cust-phone').value.trim();
    const paymentMethod = document.getElementById('payment-method').value;

    const salePayload = {
        customerName: customerName,
        customerPhone: customerPhone,
        paymentMethod: paymentMethod,
        subtotal: subtotal,
        tax: tax,
        discount: discount,
        grandTotal: grandTotal,
        items: billingCart.map(item => ({
            productId: item.productId,
            productName: item.productName,
            unitPrice: item.unitPrice,
            quantity: item.quantity
        }))
    };

    const generateBtn = document.getElementById('btn-generate-receipt');
    generateBtn.disabled = true;
    generateBtn.innerHTML = '<span>⏳ Processing transaction...</span>';

    try {
        const completedReceipt = await window.api.processSale(salePayload);
        
        // Instant Receipt Generated & Stock Deducted!
        clearCart();
        await refreshAllData();
        displayReceiptModal(completedReceipt);

    } catch (err) {
        alert('Billing Transaction Error: ' + err.message);
    } finally {
        generateBtn.disabled = false;
        generateBtn.innerHTML = '<span class="btn-icon">⚡</span> Generate Receipt & Update Stock';
    }
}

// =============================================================================
// FORMAL COMMERCIAL TAX INVOICE & AMOUNT IN WORDS
// =============================================================================

/**
 * Converts numbers into Indian Currency Words with formal grammar.
 * e.g. 541.75 -> "Indian Rupees Five Hundred Forty-One and Seventy-Five Paise Only"
 */
function numberToWordsINR(num) {
    if (isNaN(num) || num < 0) return 'Indian Rupees Zero Only';
    num = Math.round(num * 100) / 100;
    const parts = num.toString().split('.');
    let rupees = parseInt(parts[0], 10);
    let paise = parts.length > 1 ? parseInt(parts[1].padEnd(2, '0').slice(0, 2), 10) : 0;

    if (rupees === 0 && paise === 0) return 'Indian Rupees Zero Only';

    const units = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
                   'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
    const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

    function convertTwoDigits(n) {
        if (n < 20) return units[n];
        const t = tens[Math.floor(n / 10)];
        const u = units[n % 10];
        return u ? `${t}-${u}` : t;
    }

    function convertThreeDigits(n) {
        let str = '';
        if (n >= 100) {
            str += units[Math.floor(n / 100)] + ' Hundred ';
            n %= 100;
        }
        if (n > 0) {
            str += convertTwoDigits(n) + ' ';
        }
        return str.trim();
    }

    // Indian Numbering System: Crores, Lakhs, Thousands, Hundreds
    let words = '';
    const crore = Math.floor(rupees / 10000000);
    rupees %= 10000000;
    const lakh = Math.floor(rupees / 100000);
    rupees %= 100000;
    const thousand = Math.floor(rupees / 1000);
    rupees %= 1000;
    const remainder = rupees;

    if (crore > 0) words += convertTwoDigits(crore) + ' Crore ';
    if (lakh > 0) words += convertTwoDigits(lakh) + ' Lakh ';
    if (thousand > 0) words += convertTwoDigits(thousand) + ' Thousand ';
    if (remainder > 0) words += convertThreeDigits(remainder) + ' ';

    words = words.trim();
    let result = 'Indian Rupees ' + (words ? words : 'Zero');

    if (paise > 0) {
        result += ' and ' + convertTwoDigits(paise) + ' Paise';
    }
    result += ' Only';

    return result;
}

function displayReceiptModal(receipt) {
    currentReceiptData = receipt;

    // Invoice Identifiers
    document.getElementById('rec-invoice-no').textContent = receipt.invoiceNumber;
    document.getElementById('rec-barcode-number').textContent = receipt.invoiceNumber;

    // Date & Time formatting
    const rawDate = receipt.saleDate ? new Date(receipt.saleDate.replace(' ', 'T')) : new Date();
    const formattedDate = isNaN(rawDate.getTime()) 
        ? (receipt.saleDate ? receipt.saleDate.slice(0, 10) : new Date().toLocaleDateString('en-GB'))
        : rawDate.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    const formattedTime = isNaN(rawDate.getTime())
        ? '12:00 PM'
        : rawDate.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });

    document.getElementById('rec-date').textContent = formattedDate;
    document.getElementById('rec-time').textContent = formattedTime;

    // Customer & Payment Details
    document.getElementById('rec-customer-name').textContent = receipt.customerName || 'Walk-in Customer';
    document.getElementById('rec-customer-phone').textContent = receipt.customerPhone || 'Not Provided';
    document.getElementById('rec-payment-mode').textContent = receipt.paymentMethod || 'Cash';

    // Itemized Table (Formal with S.No, Description, SKU, Qty, Rate, Total)
    const itemsTbody = document.getElementById('rec-items-body');
    itemsTbody.innerHTML = receipt.items.map((it, idx) => {
        const prod = catalogProducts.find(p => p.id === it.productId) || {};
        const sku = prod.sku || `SKU-${1000 + (it.productId || idx + 1)}`;
        const lineTotal = (it.quantity * it.unitPrice).toFixed(2);

        return `
            <tr>
                <td style="text-align: center;">${idx + 1}</td>
                <td><strong>${escapeHtml(it.productName)}</strong></td>
                <td style="text-align: center;"><code>${escapeHtml(sku)}</code></td>
                <td style="text-align: center; font-weight: 600;">${it.quantity}</td>
                <td style="text-align: right; font-family: var(--font-mono);">₹${Number(it.unitPrice).toFixed(2)}</td>
                <td style="text-align: right; font-weight: 700; font-family: var(--font-mono);">₹${lineTotal}</td>
            </tr>
        `;
    }).join('');

    // Taxable subtotal & 5% GST split into 2.5% CGST + 2.5% SGST
    const subtotal = Number(receipt.subtotal || 0);
    const taxTotal = Number(receipt.tax || 0);
    const halfTax = Math.round((taxTotal / 2) * 100) / 100;
    const discount = Number(receipt.discount || 0);
    const grandTotal = Number(receipt.grandTotal || 0);

    document.getElementById('rec-subtotal').textContent = `₹${subtotal.toFixed(2)}`;
    document.getElementById('rec-cgst').textContent = `₹${halfTax.toFixed(2)}`;
    document.getElementById('rec-sgst').textContent = `₹${halfTax.toFixed(2)}`;

    const discountRow = document.getElementById('rec-discount-row');
    if (discount > 0) {
        discountRow.style.display = 'flex';
        document.getElementById('rec-discount').textContent = `-₹${discount.toFixed(2)}`;
    } else {
        discountRow.style.display = 'none';
    }

    document.getElementById('rec-grand-total').textContent = `₹${grandTotal.toFixed(2)}`;

    // Amount in Words
    document.getElementById('rec-amount-words').textContent = numberToWordsINR(grandTotal);

    openModal('modal-receipt');
}

// =============================================================================
// TAB 2: STOCK INVENTORY (ADD & UPDATE STOCK)
// =============================================================================

function renderInventoryTable() {
    const tbody = document.getElementById('inventory-table-body');
    const searchVal = (document.getElementById('inv-search-input')?.value || '').toLowerCase().trim();
    const categoryVal = document.getElementById('inv-category-filter')?.value || '';

    const filtered = catalogProducts.filter(p => {
        const matchesSearch = !searchVal || 
            p.name.toLowerCase().includes(searchVal) || 
            p.sku.toLowerCase().includes(searchVal);
        const matchesCategory = !categoryVal || p.category === categoryVal;
        return matchesSearch && matchesCategory;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty-state">No products found matching criteria.</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(p => {
        let statusBadge = '<span class="badge badge-success">● In Stock</span>';
        if (p.stockQuantity <= 0) {
            statusBadge = '<span class="badge badge-danger">✖ Out of Stock</span>';
        } else if (p.stockQuantity <= p.lowStockThreshold) {
            statusBadge = '<span class="badge badge-warning">⚠ Low Stock Alert</span>';
        }

        return `
            <tr>
                <td><code>${escapeHtml(p.sku)}</code></td>
                <td><strong>${escapeHtml(p.name)}</strong></td>
                <td><span class="text-muted">${escapeHtml(p.category)}</span></td>
                <td style="font-family: var(--font-mono);">₹${Number(p.price).toFixed(2)}</td>
                <td style="font-family: var(--font-mono); font-weight: 700;">${p.stockQuantity}</td>
                <td>${p.lowStockThreshold} units</td>
                <td>${statusBadge}</td>
                <td style="text-align: right; white-space: nowrap;">
                    <button class="btn btn-sm btn-secondary" onclick="openRestockModal(${p.id})">+ Restock</button>
                    <button class="btn btn-sm btn-outline" onclick="openEditProductModal(${p.id})">Edit</button>
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteProductById(${p.id})">Delete</button>
                </td>
            </tr>
        `;
    }).join('');
}

function openAddProductModal() {
    document.getElementById('modal-product-title').textContent = 'Add New Product';
    document.getElementById('product-form').reset();
    document.getElementById('prod-id').value = '';
    document.getElementById('prod-sku').value = `SKU-${1000 + catalogProducts.length + 1}`;
    openModal('modal-product');
}

function openEditProductModal(productId) {
    const prod = catalogProducts.find(p => p.id === productId);
    if (!prod) return;

    document.getElementById('modal-product-title').textContent = 'Edit Product Details';
    document.getElementById('prod-id').value = prod.id;
    document.getElementById('prod-sku').value = prod.sku;
    document.getElementById('prod-category').value = prod.category;
    document.getElementById('prod-name').value = prod.name;
    document.getElementById('prod-price').value = prod.price;
    document.getElementById('prod-stock').value = prod.stockQuantity;
    document.getElementById('prod-threshold').value = prod.lowStockThreshold;

    openModal('modal-product');
}

async function handleProductFormSubmit(e) {
    e.preventDefault();
    const id = document.getElementById('prod-id').value;
    const productData = {
        id: id ? parseInt(id) : undefined,
        sku: document.getElementById('prod-sku').value.trim(),
        category: document.getElementById('prod-category').value.trim(),
        name: document.getElementById('prod-name').value.trim(),
        price: parseFloat(document.getElementById('prod-price').value),
        stockQuantity: parseInt(document.getElementById('prod-stock').value),
        lowStockThreshold: parseInt(document.getElementById('prod-threshold').value),
        action: id ? 'update' : 'create'
    };

    try {
        await window.api.saveProduct(productData);
        closeModal('modal-product');
        await refreshAllData();
    } catch (err) {
        alert('Failed to save product: ' + err.message);
    }
}

function openRestockModal(productId) {
    const prod = catalogProducts.find(p => p.id === productId);
    if (!prod) return;

    document.getElementById('restock-product-id').value = prod.id;
    document.getElementById('restock-product-label').textContent = `${prod.name} (Current: ${prod.stockQuantity})`;
    document.getElementById('restock-qty').value = 10;
    openModal('modal-restock');
}

async function handleRestockSubmit(e) {
    e.preventDefault();
    const id = parseInt(document.getElementById('restock-product-id').value);
    const qty = parseInt(document.getElementById('restock-qty').value);

    try {
        await window.api.restockProduct(id, qty);
        closeModal('modal-restock');
        await refreshAllData();
    } catch (err) {
        alert('Failed to restock: ' + err.message);
    }
}

async function deleteProductById(productId) {
    const prod = catalogProducts.find(p => p.id === productId);
    if (!prod) return;

    if (confirm(`Are you sure you want to delete "${prod.name}"?`)) {
        try {
            await window.api.deleteProduct(productId);
            await refreshAllData();
        } catch (err) {
            alert('Failed to delete product: ' + err.message);
        }
    }
}

// =============================================================================
// TAB 3: LOW-STOCK WARNINGS
// =============================================================================

function renderLowStockAlerts() {
    const tbody = document.getElementById('low-stock-table-body');
    const lowStockItems = catalogProducts.filter(p => p.stockQuantity <= p.lowStockThreshold);

    if (lowStockItems.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty-state" style="color: var(--success); font-weight: 600;">✓ Excellent! All products have healthy stock levels above minimum thresholds.</td></tr>`;
        return;
    }

    tbody.innerHTML = lowStockItems.map(p => {
        const isOutOfStock = p.stockQuantity <= 0;
        const deficit = p.lowStockThreshold - p.stockQuantity;

        return `
            <tr>
                <td><code>${escapeHtml(p.sku)}</code></td>
                <td><strong>${escapeHtml(p.name)}</strong></td>
                <td>${escapeHtml(p.category)}</td>
                <td>₹${Number(p.price).toFixed(2)}</td>
                <td style="font-weight: 700; color: ${isOutOfStock ? 'var(--danger)' : 'var(--warning)'}; font-family: var(--font-mono);">
                    ${p.stockQuantity}
                </td>
                <td>${p.lowStockThreshold} units</td>
                <td>
                    <span class="badge ${isOutOfStock ? 'badge-danger' : 'badge-warning'}">
                        ${isOutOfStock ? 'Critical Stockout' : `Deficit: -${deficit} units`}
                    </span>
                </td>
                <td style="text-align: right;">
                    <button class="btn btn-sm btn-primary" onclick="openRestockModal(${p.id})">Restock Now</button>
                </td>
            </tr>
        `;
    }).join('');
}

// =============================================================================
// TAB 4: SALES HISTORY (SEARCHABLE BY DATE & ITEM)
// =============================================================================

async function loadSalesHistory() {
    const fromDate = document.getElementById('filter-from-date')?.value || '';
    const toDate = document.getElementById('filter-to-date')?.value || '';
    const searchItem = document.getElementById('filter-search-item')?.value || '';

    const history = await window.api.getSalesHistory(fromDate, toDate, searchItem);
    renderSalesHistoryTable(history);
}

function applyHistoryFilters() {
    loadSalesHistory();
}

function resetHistoryFilters() {
    if (document.getElementById('filter-from-date')) document.getElementById('filter-from-date').value = '';
    if (document.getElementById('filter-to-date')) document.getElementById('filter-to-date').value = '';
    if (document.getElementById('filter-search-item')) document.getElementById('filter-search-item').value = '';
    loadSalesHistory();
}

function renderSalesHistoryTable(sales) {
    const tbody = document.getElementById('history-table-body');
    if (!sales || sales.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty-state">No sales transactions found for the specified filters.</td></tr>`;
        return;
    }

    tbody.innerHTML = sales.map(s => {
        const itemsSummary = s.items && s.items.length 
            ? `${s.items.map(i => `${i.productName} (${i.quantity}x)`).join(', ')}`
            : 'Items billed';

        return `
            <tr>
                <td><strong>${escapeHtml(s.invoiceNumber)}</strong></td>
                <td class="text-muted">${escapeHtml(s.saleDate)}</td>
                <td>${escapeHtml(s.customerName)}</td>
                <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(itemsSummary)}">
                    ${escapeHtml(itemsSummary)}
                </td>
                <td><span class="badge" style="background:#e2e8f0; color:#334155;">${escapeHtml(s.paymentMethod || 'Cash')}</span></td>
                <td style="font-weight: 700; font-family: var(--font-mono); color: var(--primary);">₹${Number(s.grandTotal).toFixed(2)}</td>
                <td style="text-align: right;">
                    <button class="btn btn-sm btn-outline" onclick='displayReceiptModal(${JSON.stringify(s)})'>
                        <span>👁️ View Receipt</span>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

// =============================================================================
// TAB 5: BUSINESS REPORTS & PRODUCT ANALYTICS CONTROLLER
// =============================================================================

let currentAnalyticsPeriod = 'day';
let currentAnalyticsData = null;

async function loadAnalytics(period = currentAnalyticsPeriod, customFrom = '', customTo = '') {
    currentAnalyticsPeriod = period;

    try {
        const data = await window.api.getAnalyticsData(period, customFrom, customTo);
        currentAnalyticsData = data;
        renderAnalyticsUI(data);
    } catch (err) {
        console.error('Failed to load analytics', err);
    }
}

function setAnalyticsPeriod(period) {
    currentAnalyticsPeriod = period;

    // Toggle button active states
    document.querySelectorAll('.btn-period').forEach(b => b.classList.remove('active'));
    const targetBtn = document.getElementById(`btn-period-${period}`);
    if (targetBtn) targetBtn.classList.add('active');

    loadAnalytics(period);
}

function applyCustomAnalyticsRange() {
    const fromDate = document.getElementById('analytics-from-date')?.value;
    const toDate = document.getElementById('analytics-to-date')?.value;

    if (!fromDate || !toDate) {
        alert('Please select both From and To dates.');
        return;
    }

    document.querySelectorAll('.btn-period').forEach(b => b.classList.remove('active'));
    loadAnalytics('custom', fromDate, toDate);
}

function renderAnalyticsUI(data) {
    if (!data) return;

    // 1. Period Banner Info
    const bannerTitle = document.getElementById('analytics-banner-title');
    const bannerRange = document.getElementById('analytics-banner-range');

    let titleText = 'End of Day (Today) Report';
    if (data.period === 'week') titleText = 'End of Week (Last 7 Days) Report';
    else if (data.period === 'month') titleText = 'End of Month (Monthly) Report';
    else if (data.period === 'custom') titleText = 'Custom Range Business Report';

    if (bannerTitle) bannerTitle.textContent = titleText;
    if (bannerRange) bannerRange.textContent = `Range: ${data.fromDate} to ${data.toDate}`;

    // 2. Summary KPI Cards
    const summary = data.summary || {};
    document.getElementById('kpi-gross-revenue').textContent = `₹${Number(summary.totalRevenue || 0).toFixed(2)}`;
    document.getElementById('kpi-total-orders').textContent = `${summary.totalOrders || 0} bills`;
    document.getElementById('kpi-avg-order').textContent = `₹${Number(summary.avgOrderValue || 0).toFixed(2)}`;
    document.getElementById('kpi-units-sold').textContent = `${summary.totalUnitsSold || 0} units`;

    // 3. Product Sales Ranking Table
    const prodTbody = document.getElementById('product-analytics-tbody');
    const products = data.topProducts || [];
    const rankCount = document.getElementById('prod-rank-count');
    if (rankCount) rankCount.textContent = `${products.length} product(s) sold in this period`;

    if (products.length === 0) {
        prodTbody.innerHTML = `<tr><td colspan="7" class="empty-state">No product sales recorded in this period.</td></tr>`;
    } else {
        prodTbody.innerHTML = products.map((p, idx) => {
            const stockRemaining = p.stockRemaining !== undefined ? p.stockRemaining : '-';
            let statusBadge = '<span class="badge badge-success">Healthy Stock</span>';
            if (p.stockRemaining !== undefined) {
                if (p.stockRemaining <= 0) {
                    statusBadge = '<span class="badge badge-danger">Out of Stock</span>';
                } else if (p.stockRemaining <= 5) {
                    statusBadge = '<span class="badge badge-warning">Low Stock</span>';
                }
            }

            return `
                <tr>
                    <td style="font-weight: 700; color: #64748b; text-align: center;">#${idx + 1}</td>
                    <td><strong>${escapeHtml(p.productName)}</strong></td>
                    <td><span class="text-muted">${escapeHtml(p.category || 'General')}</span></td>
                    <td style="font-weight: 700; font-family: var(--font-mono);">${p.unitsSold} units</td>
                    <td style="font-family: var(--font-mono); color: var(--primary); font-weight: 700;">₹${Number(p.revenueGenerated).toFixed(2)}</td>
                    <td style="font-family: var(--font-mono); text-align: center;">${stockRemaining}</td>
                    <td>${statusBadge}</td>
                </tr>
            `;
        }).join('');
    }

    // 4. Category Performance Table
    const catTbody = document.getElementById('category-analytics-tbody');
    const categories = data.categories || [];
    if (categories.length === 0) {
        catTbody.innerHTML = `<tr><td colspan="3" class="empty-state">No category data.</td></tr>`;
    } else {
        catTbody.innerHTML = categories.map(c => `
            <tr>
                <td><strong>${escapeHtml(c.category)}</strong></td>
                <td style="font-family: var(--font-mono);">${c.unitsSold} units</td>
                <td style="font-family: var(--font-mono); font-weight: 700; color: var(--primary);">₹${Number(c.revenue).toFixed(2)}</td>
            </tr>
        `).join('');
    }

    // 5. Daily Trend Table
    const trendTbody = document.getElementById('daily-trend-tbody');
    const trend = data.dailyTrend || [];
    if (trend.length === 0) {
        trendTbody.innerHTML = `<tr><td colspan="3" class="empty-state">No trend data available.</td></tr>`;
    } else {
        trendTbody.innerHTML = trend.map(t => `
            <tr>
                <td><code>${escapeHtml(t.date)}</code></td>
                <td>${t.orders} bills</td>
                <td style="font-family: var(--font-mono); font-weight: 700;">₹${Number(t.revenue).toFixed(2)}</td>
            </tr>
        `).join('');
    }
}

// =============================================================================
// PRINTABLE STORE REPORT MODAL (Z-REPORT)
// =============================================================================

function openPrintableReportModal() {
    if (!currentAnalyticsData) {
        alert('Analytics data is still loading. Please try again.');
        return;
    }

    const data = currentAnalyticsData;
    const summary = data.summary || {};

    let periodHeader = 'END OF DAY REPORT (Z-REPORT)';
    if (data.period === 'week') periodHeader = 'WEEKLY BUSINESS PERFORMANCE REPORT';
    else if (data.period === 'month') periodHeader = 'MONTHLY TURNOVER & SALES REPORT';
    else if (data.period === 'custom') periodHeader = 'PERIOD PERFORMANCE SUMMARY REPORT';

    document.getElementById('rep-period-title').textContent = periodHeader;
    document.getElementById('rep-period-date-range').textContent = `Period: ${data.fromDate} to ${data.toDate}`;

    document.getElementById('rep-kpi-revenue').textContent = `₹${Number(summary.totalRevenue || 0).toFixed(2)}`;
    document.getElementById('rep-kpi-orders').textContent = summary.totalOrders || 0;
    document.getElementById('rep-kpi-avg').textContent = `₹${Number(summary.avgOrderValue || 0).toFixed(2)}`;
    document.getElementById('rep-kpi-units').textContent = summary.totalUnitsSold || 0;

    // Report Products Table
    const repProdTbody = document.getElementById('rep-products-tbody');
    const products = data.topProducts || [];
    if (products.length === 0) {
        repProdTbody.innerHTML = `<tr><td colspan="6" class="empty-state">No items sold during this period.</td></tr>`;
    } else {
        repProdTbody.innerHTML = products.map((p, idx) => `
            <tr>
                <td style="text-align: center;">${idx + 1}</td>
                <td><strong>${escapeHtml(p.productName)}</strong></td>
                <td>${escapeHtml(p.category || 'General')}</td>
                <td style="text-align: center;">${p.unitsSold}</td>
                <td style="text-align: right; font-family: var(--font-mono);">₹${Number(p.revenueGenerated).toFixed(2)}</td>
                <td style="text-align: center;">${p.stockRemaining !== undefined ? p.stockRemaining : '-'}</td>
            </tr>
        `).join('');
    }

    // Report Category Table
    const repCatTbody = document.getElementById('rep-categories-tbody');
    const categories = data.categories || [];
    if (categories.length === 0) {
        repCatTbody.innerHTML = `<tr><td colspan="3" class="empty-state">No category breakdown.</td></tr>`;
    } else {
        repCatTbody.innerHTML = categories.map(c => `
            <tr>
                <td><strong>${escapeHtml(c.category)}</strong></td>
                <td style="text-align: center;">${c.unitsSold}</td>
                <td style="text-align: right; font-family: var(--font-mono);">₹${Number(c.revenue).toFixed(2)}</td>
            </tr>
        `).join('');
    }

    document.getElementById('rep-generated-timestamp').textContent = new Date().toLocaleString();

    openModal('modal-period-report');
}

function printStoreReport() {
    window.print();
}

// =============================================================================
// Modal Helpers
// =============================================================================
function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.add('show');
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.remove('show');
}

function closeAllModals() {
    document.querySelectorAll('.modal-backdrop').forEach(m => m.classList.remove('show'));
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
