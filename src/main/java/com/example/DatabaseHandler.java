package com.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHandler {

    private final String dbUrl;
    // Ad-hoc lock object — Bank example pattern from Threads slides (slides 103-105)
    private final Object lock = new Object();

    public DatabaseHandler() { this.dbUrl = "jdbc:sqlite:store.db"; }
    DatabaseHandler(String dbUrl) { this.dbUrl = dbUrl; }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void setupDatabase() throws StoreException {
        String sql = "CREATE TABLE IF NOT EXISTS products (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, category TEXT NOT NULL, " +
            "price REAL NOT NULL, quantity INTEGER NOT NULL DEFAULT 0)";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[DB] Database ready.");
        } catch (SQLException e) {
            throw new StoreException("Setup failed: " + e.getMessage());
        }
    }

    // Write operations use synchronized(lock) so the main thread cannot
    // modify products concurrently with any background thread.

    public void addProduct(String name, String category, double price, int qty)
            throws StoreException {
        String sql = "INSERT INTO products(name,category,price,quantity) VALUES(?,?,?,?)";
        synchronized (lock) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.setDouble(3, price);
                ps.setInt(4, qty);
                ps.executeUpdate();
                System.out.println("[DB] Added: " + name);
            } catch (SQLException e) {
                throw new StoreException("Add failed: " + e.getMessage());
            }
        }
    }

    public List<String[]> getAllProducts() throws StoreException {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT id,name,category,price,quantity FROM products ORDER BY name";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next())
                list.add(new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("name"),
                    rs.getString("category"),        String.valueOf(rs.getDouble("price")),
                    String.valueOf(rs.getInt("quantity"))
                });
        } catch (SQLException e) {
            throw new StoreException("Select failed: " + e.getMessage());
        }
        return list;
    }

    public String[] getProductById(int id) throws StoreException {
        String sql = "SELECT id,name,category,price,quantity FROM products WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return new String[]{
                    String.valueOf(rs.getInt("id")), rs.getString("name"),
                    rs.getString("category"),        String.valueOf(rs.getDouble("price")),
                    String.valueOf(rs.getInt("quantity"))
                };
        } catch (SQLException e) {
            throw new StoreException("Lookup failed: " + e.getMessage());
        }
        return null;
    }

    public void updateProduct(int id, String name, String category, double price, int qty)
            throws StoreException {
        String sql = "UPDATE products SET name=?,category=?,price=?,quantity=? WHERE id=?";
        synchronized (lock) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name); ps.setString(2, category);
                ps.setDouble(3, price); ps.setInt(4, qty); ps.setInt(5, id);
                int rows = ps.executeUpdate();
                System.out.println(rows > 0 ? "[DB] Updated." : "[DB] Product not found.");
            } catch (SQLException e) {
                throw new StoreException("Update failed: " + e.getMessage());
            }
        }
    }

    public void deleteProduct(int id) throws StoreException {
        synchronized (lock) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM products WHERE id=?")) {
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                System.out.println(rows > 0 ? "[DB] Deleted." : "[DB] Product not found.");
            } catch (SQLException e) {
                throw new StoreException("Delete failed: " + e.getMessage());
            }
        }
    }

    public void showProducts() {
        try {
            List<String[]> list = getAllProducts();
            System.out.println("\n--- Inventory ---");
            if (list.isEmpty()) { System.out.println("No products."); return; }
            System.out.printf("%-5s %-20s %-12s %-12s %-6s%n",
                "ID", "Name", "Category", "Price(SAR)", "Qty");
            System.out.println("-".repeat(58));
            for (String[] p : list)
                System.out.printf("%-5s %-20s %-12s %-12s %-6s%n",
                    p[0], p[1], p[2], p[3], p[4]);
        } catch (StoreException e) {
            System.out.println("[DB] Error: " + e.getMessage());
        }
    }
}
