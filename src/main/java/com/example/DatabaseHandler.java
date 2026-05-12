package com.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseHandler — Database pillar (JDBC).
 *
 * Demonstrates:
 *   - DriverManager.getConnection()  to open a SQLite connection
 *   - Statement                      for simple queries (CREATE, SELECT *)
 *   - PreparedStatement              for parameterised queries (INSERT, UPDATE, DELETE)
 *   - ResultSet + rs.next()          to iterate query results
 *   - try-with-resources             to auto-close Connection/Statement/ResultSet
 *   - StoreException (checked)       wraps every SQLException so callers must handle it
 *   - synchronized(lock)             protects write operations (Threads slides 103-105)
 */
public class DatabaseHandler {

    private final String dbUrl;

    // Ad-hoc lock object — same "Bank" pattern from Threads slides 103-105.
    // Using a plain Object instead of synchronizing the whole method keeps only
    // the critical section locked, allowing reads to proceed concurrently.
    private final Object lock = new Object();

    // Default constructor uses a file-based SQLite database
    public DatabaseHandler() { this.dbUrl = "jdbc:sqlite:store.db"; }

    // Package-private constructor lets unit tests inject a temp-file URL
    DatabaseHandler(String dbUrl) { this.dbUrl = dbUrl; }

    // Opens a new connection each call — SQLite handles this efficiently
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    // -------------------------------------------------------------------------
    // Schema setup — called once at startup
    // -------------------------------------------------------------------------

    /**
     * Creates the products table if it does not already exist.
     * Uses Statement (no parameters needed for DDL).
     * Throws StoreException so main() can abort if the DB cannot be initialised.
     */
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

    // -------------------------------------------------------------------------
    // CRUD — Create
    // -------------------------------------------------------------------------

    /**
     * Inserts a new product using PreparedStatement with ? placeholders.
     * synchronized(lock) prevents concurrent inserts from interleaving.
     */
    public void addProduct(String name, String category, double price, int qty)
            throws StoreException {
        String sql = "INSERT INTO products(name,category,price,quantity) VALUES(?,?,?,?)";
        synchronized (lock) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);      // bind parameter 1
                ps.setString(2, category);  // bind parameter 2
                ps.setDouble(3, price);     // bind parameter 3
                ps.setInt(4, qty);          // bind parameter 4
                ps.executeUpdate();
                System.out.println("[DB] Added: " + name);
            } catch (SQLException e) {
                throw new StoreException("Add failed: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // CRUD — Read
    // -------------------------------------------------------------------------

    /**
     * Returns all products ordered by name.
     * Uses Statement (no parameters) and iterates the ResultSet with rs.next().
     */
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

    /**
     * Finds a single product by its primary key.
     * Uses PreparedStatement with a WHERE id=? clause.
     * Returns null if no row is found.
     */
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

    // -------------------------------------------------------------------------
    // CRUD — Update
    // -------------------------------------------------------------------------

    /**
     * Updates all fields of an existing product.
     * synchronized(lock) ensures no other write can interleave mid-update.
     */
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

    // -------------------------------------------------------------------------
    // CRUD — Delete
    // -------------------------------------------------------------------------

    /**
     * Removes a product by its primary key.
     * synchronized(lock) prevents a concurrent read from seeing a half-deleted row.
     */
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

    // -------------------------------------------------------------------------
    // Display helper
    // -------------------------------------------------------------------------

    /** Prints a formatted product table to stdout. Catches StoreException internally. */
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
