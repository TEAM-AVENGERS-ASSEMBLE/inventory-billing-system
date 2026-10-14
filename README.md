#                        📦 Inventory & Billing System

[![Java](https://img.shields.io/badge/Java-Servlets_4.0-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-InnoDB-00758F?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Web](https://img.shields.io/badge/Frontend-HTML5_·_CSS3_·_ES6-E34F26?style=flat-square&logo=html5&logoColor=white)](src/main/webapp/)
[![Vercel Ready](https://img.shields.io/badge/Deploy-Vercel-black?style=flat-square&logo=vercel&logoColor=white)](https://vercel.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

> **High-performance retail stock tracking, sub-second POS checkout, and GST tax invoicing built with core Java, JDBC, and standard web technologies—zero external frameworks.**

---

## ⚡ Core Capabilities

- **🛒 Instant POS Checkout**: Single-click billing, real-time cart validation, and automated tax calculations (CGST/SGST @ 2.5%).
- **🔒 Atomic Stock Deductions**: Isolated JDBC transactions with `SELECT ... FOR UPDATE` row locking prevent race conditions and overselling.
- **🧾 Legal Tax Invoice**: Instant printable cash memos with customer metadata, GSTIN, HSN codes, and **Indian Currency Amount-in-Words** (`₹541.75` → *"Five Hundred Forty-One and Seventy-Five Paise Only"*).
- **⚠️ Low-Stock Alerts**: Real-time warning center for products at or below safety buffer thresholds.
- **📊 Business Analytics**: Automated End-of-Day (Z-Report), 7-day revenue velocity, and fast-moving inventory rankings.
- **🌐 Dual-Mode Deployment**: Connects live to Java Servlets & MySQL, or runs 100% client-side via LocalStorage for zero-dependency demos and Vercel hosting.

---

## 🏗️ Architecture & Core Workflow

```
[ Browser POS UI ]  ──( POST /api/billing )──>  [ BillingServlet ]
                                                        │
                      ┌─────────────────────────────────┴─────────────────────────────────┐
                      ▼                                                                   ▼
         conn.setAutoCommit(false)                                             Atomic Commit / Rollback
                      │                                                                   ▲
                      ▼                                                                   │
   [ 1. Lock Stock (FOR UPDATE) ] ──> [ 2. Deduct Units ] ──> [ 3. Insert Sale & Items Batch ]
```

---

## 📂 Project Structure

```text
inventory-billing-system/
├── database/            # schema.sql (DDL + indexes) & seed_data.sql
├── src/main/
│   ├── java/com/inventory/
│   │   ├── config/      # DBConnection.java (JDBC connection manager)
│   │   ├── dao/         # ProductDAO.java & SaleDAO.java (ACID transactions)
│   │   ├── model/       # Product, Sale, and SaleItem POJOs
│   │   ├── server/      # AppServer.java (Embedded standalone server)
│   │   └── servlet/     # Billing, Product, Sales, and Analytics Servlets
│   └── webapp/          # POS interface, styles.css, app.js, and api.js
├── vercel.json          # Pre-configured static deployment routing
└── pom.xml              # Maven project descriptor
```

---

## 🚀 Quick Start

### 1. Instant Browser Demo (Zero Setup)
Open [`src/main/webapp/index.html`](src/main/webapp/index.html) in any browser. It initializes in client-side interactive mode with sample catalog and billing data.

### 2. Full-Stack (Java + MySQL)
```bash
# Setup MySQL Database
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed_data.sql

# Launch Standalone Embedded Server
mvn compile exec:java -Dexec.mainClass="com.inventory.server.AppServer"
# Open http://localhost:8080
```

### 3. Deploy to Vercel
1. Push this repository to GitHub.
2. Import the repository in [Vercel](https://vercel.com/new).
3. Click **Deploy** (Vercel automatically serves `src/main/webapp` via [`vercel.json`](vercel.json)).

---

## 👥 Project Contributors

- **Suraj Gusain** — Backend (Java Servlets 4.0, JDBC, ACID Transactions)
- **Siddhesh Jagtap** — Database (Schema DDL, Relational Constraints, Indexing)
- **Saurabh Mishra** — Frontend (POS Interface, Dynamic Cart, Invoice Print Engine)

---

## 📄 License
Released under the [MIT License](LICENSE).
