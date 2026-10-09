# Inventory & Billing System
> **Stock Management with Instant Receipt Generation**  
> Built with: **Java Servlets · JDBC · MySQL · HTML / CSS / JS**  
> *"No frameworks — just the core Java, JDBC, and web stack we already know."*

---

## 👥 Project Team & Modules

| Team Member | Module & Role | Key Responsibilities |
| :--- | :--- | :--- |
| **Suraj Gusain** | **Backend** (Servlets & JDBC) | `ProductServlet`, `BillingServlet`, `SalesHistoryServlet`, `DBConnection`, JDBC Transaction processing |
| **Siddhesh Jagtap** | **Database** (Design & Queries) | Schema DDL (`schema.sql`), Seed data (`seed_data.sql`), Table relationships, Low-stock & Sales queries |
| **Saurabh Mishra** | **Frontend** (HTML / CSS / JS) | POS Billing Screen, Live Cart calculation, Thermal Receipt template, Inventory management UI |

---

## 🎯 Problems Solved (Slide 2 & 3)

1. **Live Stock Count**: Replaces manual registers with a live catalog that updates after every transaction.
2. **Instant Receipt Generation**: Items are selected on screen, totals/taxes auto-calculated, and receipts are generated and printed instantly.
3. **Atomic Stock Deduction (Slide 5 Flow)**: In a single request, the Java Servlet reads the cart, opens a JDBC transaction in MySQL, deducts stock, records the sale, and returns the receipt.
4. **Low-Stock Warning Center**: Automatically flags and highlights items where current quantity is at or below the minimum threshold.
5. **Searchable Sales History**: Filter past receipts by date range, invoice number, or product keyword with one-click reprint.

---

## 📂 Project Directory Structure

```text
inventory-billing-system/
├── database/
│   ├── schema.sql                       # Siddhesh Jagtap: MySQL tables, indexes & queries
│   └── seed_data.sql                    # Initial product catalog & sample receipts
├── src/
│   └── main/
│       ├── java/
│       │   └── com/inventory/
│       │       ├── config/
│       │       │   └── DBConnection.java       # JDBC connection manager
│       │       ├── model/
│       │       │   ├── Product.java            # Product POJO
│       │       │   ├── Sale.java               # Sale header POJO
│       │       │   └── SaleItem.java           # Receipt line item POJO
│       │       ├── dao/
│       │       │   ├── ProductDAO.java         # JDBC inventory CRUD & stock checks
│       │       │   └── SaleDAO.java            # Atomic JDBC billing transactions & search
│       │       ├── servlet/
│       │       │   ├── ProductServlet.java     # /api/products
│       │       │   ├── BillingServlet.java     # /api/billing (Instant receipt generator)
│       │       │   ├── SalesHistoryServlet.java # /api/sales (Searchable sales history)
│       │       │   └── DashboardServlet.java   # /api/dashboard (Real-time metrics)
│       │       └── server/
│       │           └── AppServer.java          # Embedded standalone Java server
│       └── webapp/
│           ├── WEB-INF/
│           │   └── web.xml                     # Servlet 4.0 deployment descriptor
│           ├── css/
│           │   └── styles.css                  # Presentation theme & print styling
│           ├── js/
│           │   ├── api.js                      # Dual-mode API client (Servlet / Demo)
│           │   └── app.js                      # POS controller, cart & receipt printer
│           └── index.html                      # Unified single-page web application
├── pom.xml                                     # Maven build file with Tomcat plugin
└── README.md
```

---

## 🚀 How to Run the Project

### Option A: Instant Browser Demo (Zero Setup)
The frontend features an intelligent **Dual Mode**. If opened directly without a running server, it seamlessly initializes in **Interactive Demo Mode** with the sample dataset:
1. Navigate to `src/main/webapp/index.html`.
2. Double-click to open in any modern browser (Chrome, Edge, Firefox).
3. Experience all features: billing checkout, instant receipt popup, stock deduction, restock actions, and sales history searching.

---

### Option B: Running with Java & MySQL (Full Stack)

#### 1. Setup MySQL Database (Siddhesh Jagtap's Module)
Open MySQL Workbench, phpMyAdmin (XAMPP), or MySQL CLI and run:
```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed_data.sql
```
*Note: If your MySQL password is not `root`, set the environment variable `DB_PASS=your_password` or edit `DBConnection.java`.*

#### 2. Run with Embedded Java AppServer (Zero Tomcat Setup)
Compile and launch the standalone Java server directly:
```bash
# Using Maven:
mvn compile exec:java -Dexec.mainClass="com.inventory.server.AppServer"
```
Or run `AppServer.java` directly inside VS Code, Eclipse, or IntelliJ IDEA.  
Then open your browser at: **`http://localhost:8080/`**

#### 3. Run with Apache Tomcat / Maven Tomcat Plugin
```bash
mvn tomcat7:run
```
Access the application at: **`http://localhost:8080/inventory-billing/`**

---

## ⚡ The Core Transaction Flow (Slide 5)

```text
[ Billing Screen ] (HTML/CSS/JS)
       │
       ▼ (Single POST with cart items)
[ BillingServlet ] (Receipt Generator)
       │
       ▼ (JDBC Transaction: conn.setAutoCommit(false))
[ MySQL: inventory_db ]
  1. Check stock & FOR UPDATE lock
  2. Deduct inventory: UPDATE products SET stock_quantity = stock_quantity - ?
  3. Insert into `sales` table
  4. Insert items into `sale_items` table
  5. Commit transaction
       │
       ▼ (Instant Receipt JSON returned)
[ Customer Thermal / Standard Printable Receipt Modal ]
```

---

## 📄 License & Attribution
Created for academic presentation & demonstration. All rights reserved by Suraj Gusain, Siddhesh Jagtap, and Saurabh Mishra.
