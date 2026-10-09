package com.inventory.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inventory.dao.SaleDAO;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * AnalyticsServlet - Sales and Product Analytics for End of Day, Week, and Month
 */
@WebServlet("/api/analytics")
public class AnalyticsServlet extends HttpServlet {

    private final SaleDAO saleDAO = new SaleDAO();
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
            String period = req.getParameter("period");
            String fromDate = req.getParameter("fromDate");
            String toDate = req.getParameter("toDate");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
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
                    // Default: End of Day (Today)
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

            out.print(gson.toJson(result));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Failed to compute analytics: " + escape(e.getMessage()) + "\"}");
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
