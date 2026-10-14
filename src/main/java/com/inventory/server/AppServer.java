package com.inventory.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inventory.config.DBConnection;
import com.inventory.dao.ProductDAO;
import com.inventory.dao.SaleDAO;
import com.inventory.model.Product;
import com.inventory.model.Sale;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * AppServer - Standalone Embedded HTTP Server
 * Allows running the full Java + JDBC + MySQL application directly
 * without requiring an external Apache Tomcat installation.
 */
public class AppServer {

    private static final int PORT = 8080;
    private static final Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
    private static final ProductDAO productDAO = new ProductDAO();
    private static final SaleDAO saleDAO = new SaleDAO();

    public static void main(String[] args) throws IOException {
        System.out.println("==========================================================");
        System.out.println("   Inventory & Billing System - Standalone Java Server    ");
        System.out.println("   Stock Management with Instant Receipt Generation       ");
        System.out.println("   Suraj Gusain | Siddhesh Jagtap | Saurabh Mishra        ");
        System.out.println("==========================================================");

        // Test database connectivity
        boolean dbOk = DBConnection.testConnection();
        if (dbOk) {
            System.out.println("[INFO] Successfully connected to MySQL database (inventory_db).");
        } else {
            System.out.println("[WARN] Could not connect to MySQL database at localhost:3306.");
            System.out.println("       Make sure MySQL is running and database/schema.sql is imported.");
            System.out.println("       (Set DB_URL, DB_USER, DB_PASS environment variables if needed)");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API Contexts
        server.createContext("/api/products", new ProductHandler());
        server.createContext("/api/billing", new BillingHandler());
        server.createContext("/api/sales", new SalesHistoryHandler());
        server.createContext("/api/dashboard", new DashboardHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());
        server.createContext("/api/auth", new AuthHandler());

        // Static Web Files Context
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("[READY] Application running at: http://localhost:" + PORT + "/");
        System.out.println("Press Ctrl+C to stop the server.\n");
    }

    // =========================================================================
    // API Handlers
    // =========================================================================

    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 200, "application/json", "");
                return;
            }

            try {
                if ("GET".equals(method)) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    if (params.containsKey("id")) {
                        Product p = productDAO.getProductById(Integer.parseInt(params.get("id")));
                        sendResponse(exchange, p != null ? 200 : 404, "application/json", gson.toJson(p));
                    } else if ("true".equalsIgnoreCase(params.get("lowStock"))) {
                        List<Product> list = productDAO.getLowStockProducts();
                        sendResponse(exchange, 200, "application/json", gson.toJson(list));
                    } else {
                        List<Product> list = productDAO.getAllProducts();
                        sendResponse(exchange, 200, "application/json", gson.toJson(list));
                    }
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    String action = json.has("action") ? json.get("action").getAsString() : "create";

                    if ("restock".equalsIgnoreCase(action)) {
                        int id = json.get("id").getAsInt();
                        int qty = json.get("quantity").getAsInt();
                        productDAO.restockProduct(id, qty);
                        sendResponse(exchange, 200, "application/json", "{\"success\":true}");
                    } else if ("update".equalsIgnoreCase(action)) {
                        Product p = gson.fromJson(json, Product.class);
                        productDAO.updateProduct(p);
                        sendResponse(exchange, 200, "application/json", "{\"success\":true}");
                    } else if ("delete".equalsIgnoreCase(action)) {
                        int id = json.get("id").getAsInt();
                        productDAO.deleteProduct(id);
                        sendResponse(exchange, 200, "application/json", "{\"success\":true}");
                    } else {
                        Product p = gson.fromJson(json, Product.class);
                        productDAO.addProduct(p);
                        sendResponse(exchange, 201, "application/json", gson.toJson(p));
                    }
                } else if ("DELETE".equals(method)) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    int id = Integer.parseInt(params.get("id"));
                    productDAO.deleteProduct(id);
                    sendResponse(exchange, 200, "application/json", "{\"success\":true}");
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class BillingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 200, "application/json", "");
                return;
            }

            if ("POST".equals(method)) {
                try {
                    String body = readBody(exchange);
                    Sale saleRequest = gson.fromJson(body, Sale.class);
                    Sale completedSale = saleDAO.processSaleTransaction(saleRequest);
                    sendResponse(exchange, 201, "application/json", gson.toJson(completedSale));
                } catch (IllegalStateException e) {
                    sendResponse(exchange, 409, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }
    }

    static class SalesHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 200, "application/json", "");
                return;
            }

            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                if (params.containsKey("id")) {
                    Sale sale = saleDAO.getSaleById(Integer.parseInt(params.get("id")));
                    sendResponse(exchange, sale != null ? 200 : 404, "application/json", gson.toJson(sale));
                } else {
                    List<Sale> history = saleDAO.searchSales(
                        params.get("fromDate"),
                        params.get("toDate"),
                        params.get("item")
                    );
                    sendResponse(exchange, 200, "application/json", gson.toJson(history));
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 200, "application/json", "");
                return;
            }

            try {
                int[] pStats = productDAO.getProductCounts();
                double[] sStats = saleDAO.getTodaySalesSummary();

                JsonObject obj = new JsonObject();
                obj.addProperty("totalProducts", pStats[0]);
                obj.addProperty("lowStockCount", pStats[1]);
                obj.addProperty("todayOrders", (int) sStats[0]);
                obj.addProperty("todayRevenue", sStats[1]);

                sendResponse(exchange, 200, "application/json", obj.toString());
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class AnalyticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                sendResponse(exchange, 200, "application/json", "");
                return;
            }

            try {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                String period = params.get("period");
                String fromDate = params.get("fromDate");
                String toDate = params.get("toDate");

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                Calendar cal = Calendar.getInstance();
                Date now = new Date();

                if (fromDate == null || fromDate.trim().isEmpty() || toDate == null || toDate.trim().isEmpty()) {
                    if ("week".equalsIgnoreCase(period)) {
                        toDate = sdf.format(now);
                        cal.setTime(now);
                        cal.add(Calendar.DAY_OF_YEAR, -6);
                        fromDate = sdf.format(cal.getTime());
                    } else if ("month".equalsIgnoreCase(period)) {
                        toDate = sdf.format(now);
                        cal.setTime(now);
                        cal.set(Calendar.DAY_OF_MONTH, 1);
                        fromDate = sdf.format(cal.getTime());
                    } else {
                        fromDate = sdf.format(now);
                        toDate = sdf.format(now);
                    }
                }

                Map<String, Object> summary = saleDAO.getSalesSummaryForRange(fromDate, toDate);
                List<Map<String, Object>> topProducts = saleDAO.getProductSalesAnalytics(fromDate, toDate);
                List<Map<String, Object>> categories = saleDAO.getCategorySalesAnalytics(fromDate, toDate);

                JsonObject result = new JsonObject();
                result.addProperty("period", period != null ? period : "day");
                result.addProperty("fromDate", fromDate);
                result.addProperty("toDate", toDate);
                result.add("summary", gson.toJsonTree(summary));
                result.add("topProducts", gson.toJsonTree(topProducts));
                result.add("categories", gson.toJsonTree(categories));

                sendResponse(exchange, 200, "application/json", gson.toJson(result));
            } catch (Exception e) {
                sendResponse(exchange, 500, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendResponse(exchange, 200, "text/plain", "");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = parseQueryParams(query);
                    if ("logout".equalsIgnoreCase(params.get("action"))) {
                        sendResponse(exchange, 200, "application/json", "{\"success\":true,\"message\":\"Logged out\"}");
                        return;
                    }

                    String body = readRequestBody(exchange);
                    JsonObject json = gson.fromJson(body, JsonObject.class);

                    if (json == null || !json.has("username") || !json.has("password")) {
                        sendResponse(exchange, 400, "application/json", "{\"error\":\"Username and password are required\"}");
                        return;
                    }

                    String username = json.get("username").getAsString().trim();
                    String password = json.get("password").getAsString().trim();
                    String role = json.has("role") ? json.get("role").getAsString() : "cashier";

                    JsonObject user = null;
                    String sql = "SELECT username, full_name, role FROM users WHERE LOWER(username) = LOWER(?) AND password_hash = ?";
                    try (java.sql.Connection conn = DBConnection.getConnection();
                         java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        stmt.setString(2, password);
                        try (java.sql.ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                user = new JsonObject();
                                user.addProperty("username", rs.getString("username"));
                                user.addProperty("fullName", rs.getString("full_name"));
                                user.addProperty("role", rs.getString("role"));
                                user.addProperty("authenticated", true);
                            }
                        }
                    } catch (Exception ignored) {}

                    if (user == null) {
                        String lower = username.toLowerCase();
                        if ("admin".equals(lower) && "admin123".equals(password)) {
                            user = new JsonObject();
                            user.addProperty("username", "admin");
                            user.addProperty("fullName", "Suraj Gusain (Store Admin)");
                            user.addProperty("role", "admin");
                            user.addProperty("authenticated", true);
                        } else if ("cashier".equals(lower) && "cashier123".equals(password)) {
                            user = new JsonObject();
                            user.addProperty("username", "cashier");
                            user.addProperty("fullName", "Saurabh Mishra (POS Cashier)");
                            user.addProperty("role", "cashier");
                            user.addProperty("authenticated", true);
                        } else if ("siddhesh".equals(lower) && "password".equals(password)) {
                            user = new JsonObject();
                            user.addProperty("username", "siddhesh");
                            user.addProperty("fullName", "Siddhesh Jagtap (Database Architect)");
                            user.addProperty("role", "admin");
                            user.addProperty("authenticated", true);
                        }
                    }

                    if (user != null) {
                        sendResponse(exchange, 200, "application/json", user.toString());
                    } else {
                        sendResponse(exchange, 401, "application/json", "{\"error\":\"Invalid username or password. Demo: admin / admin123\"}");
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "application/json", "{\"error\":\"Auth error: " + e.getMessage() + "\"}");
                }
                return;
            }

            sendResponse(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
        }
    }

    // =========================================================================
    // Static Web Resources Handler
    // =========================================================================
    static class StaticFileHandler implements HttpHandler {
        private final Path webappDir;

        public StaticFileHandler() {
            // Locate src/main/webapp relative to current working directory
            Path candidate = Paths.get("src/main/webapp");
            if (!Files.exists(candidate)) {
                candidate = Paths.get("inventory-billing-system/src/main/webapp");
            }
            this.webappDir = candidate.toAbsolutePath().normalize();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            Path file = webappDir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(webappDir) || !Files.exists(file) || Files.isDirectory(file)) {
                sendResponse(exchange, 404, "text/plain", "File Not Found: " + path);
                return;
            }

            String contentType = getMimeType(file.toString());
            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    map.put(key, value);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".json")) return "application/json";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }
}
