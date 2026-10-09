package com.inventory.servlet;

import com.google.gson.JsonObject;
import com.inventory.dao.ProductDAO;
import com.inventory.dao.SaleDAO;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * DashboardServlet - Summary Statistics for POS & Inventory Header
 */
@WebServlet("/api/dashboard")
public class DashboardServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();
    private final SaleDAO saleDAO = new SaleDAO();

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
            int[] productStats = productDAO.getProductCounts();
            double[] todayStats = saleDAO.getTodaySalesSummary();

            JsonObject stats = new JsonObject();
            stats.addProperty("totalProducts", productStats[0]);
            stats.addProperty("lowStockCount", productStats[1]);
            stats.addProperty("todayOrders", (int) todayStats[0]);
            stats.addProperty("todayRevenue", todayStats[1]);

            out.print(stats.toString());

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Failed to fetch dashboard stats: " + escape(e.getMessage()) + "\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
