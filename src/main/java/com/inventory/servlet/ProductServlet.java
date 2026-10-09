package com.inventory.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inventory.dao.ProductDAO;
import com.inventory.model.Product;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * ProductServlet - REST Servlet for Stock Management
 * Supports:
 * - GET /api/products -> list all products or filter ?lowStock=true
 * - GET /api/products?id=123 -> get single product
 * - POST /api/products -> add new product or action=restock/action=update
 * - DELETE /api/products?id=123 -> delete product
 */
@WebServlet("/api/products")
public class ProductServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String idParam = req.getParameter("id");
            String lowStockOnly = req.getParameter("lowStock");

            if (idParam != null && !idParam.isEmpty()) {
                Product p = productDAO.getProductById(Integer.parseInt(idParam));
                if (p != null) {
                    out.print(gson.toJson(p));
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"error\":\"Product not found\"}");
                }
                return;
            }

            List<Product> products;
            if ("true".equalsIgnoreCase(lowStockOnly)) {
                products = productDAO.getLowStockProducts();
            } else {
                products = productDAO.getAllProducts();
            }
            out.print(gson.toJson(products));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String body = readRequestBody(req);
            JsonObject json = gson.fromJson(body, JsonObject.class);

            String action = json.has("action") ? json.get("action").getAsString() : "create";

            if ("restock".equalsIgnoreCase(action)) {
                int id = json.get("id").getAsInt();
                int qty = json.get("quantity").getAsInt();
                boolean success = productDAO.restockProduct(id, qty);
                out.print("{\"success\":" + success + ",\"message\":\"Stock updated successfully\"}");
                return;
            }

            if ("update".equalsIgnoreCase(action)) {
                Product p = gson.fromJson(json, Product.class);
                boolean success = productDAO.updateProduct(p);
                out.print("{\"success\":" + success + ",\"message\":\"Product details updated\"}");
                return;
            }

            if ("delete".equalsIgnoreCase(action)) {
                int id = json.get("id").getAsInt();
                boolean success = productDAO.deleteProduct(id);
                out.print("{\"success\":" + success + ",\"message\":\"Product removed\"}");
                return;
            }

            // Default: Create Product
            Product p = gson.fromJson(json, Product.class);
            if (p.getSku() == null || p.getSku().trim().isEmpty()) {
                p.setSku("SKU-" + System.currentTimeMillis() % 100000);
            }
            boolean success = productDAO.addProduct(p);
            if (success) {
                resp.setStatus(HttpServletResponse.SC_CREATED);
                out.print(gson.toJson(p));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Failed to save product\"}");
            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String idParam = req.getParameter("id");
            if (idParam == null || idParam.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing required id parameter\"}");
                return;
            }
            int id = Integer.parseInt(idParam);
            boolean success = productDAO.deleteProduct(id);
            out.print("{\"success\":" + success + "}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
