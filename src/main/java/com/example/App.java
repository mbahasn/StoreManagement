package com.example;

import java.util.List;
import java.util.Scanner;

/**
 * App — Main entry point and CLI menu.
 *
 * Ties all four pillars together:
 *   DatabaseHandler  — JDBC (products table, CRUD operations)
 *   ReportService    — I/O Streams (CSV export, activity log)
 *   NetworkService   — Networking (TCP server + HttpClient exchange rate)
 *   StoreException   — Exception Handling (checked exception from DB layer)
 *
 * Threading:
 *   The TCP server is started as a daemon thread so it runs in the background
 *   without blocking the main menu loop.
 */
public class App {

    // Shared instances used across all menu methods
    private static final DatabaseHandler db      = new DatabaseHandler();
    private static final ReportService   report  = new ReportService();
    private static final NetworkService  network = new NetworkService();
    private static final Scanner         sc      = new Scanner(System.in);

    public static void main(String[] args) {
        // Initialise the database — abort if setup fails (StoreException is checked)
        try {
            db.setupDatabase();
        } catch (StoreException e) {
            System.out.println("[FATAL] " + e.getMessage());
            return;
        }

        // Start the TCP server in a background daemon thread (Networking pillar)
        network.startServer(8080, db);
        report.appendToLog("System started.");

        // Main menu loop — keeps running until the user chooses 0
        boolean running = true;
        while (running) {
            System.out.println("\n========================================");
            System.out.println("       STORE MANAGEMENT SYSTEM");
            System.out.println("========================================");
            System.out.println("1. View All Products   5. Export to CSV");
            System.out.println("2. Add Product         6. View Activity Log");
            System.out.println("3. Update Product      7. Exchange Rate (HttpClient)");
            System.out.println("4. Delete Product      8. TCP Server Status (Socket)");
            System.out.println("                       0. Exit");
            System.out.print("Select: ");

            switch (sc.nextLine().trim()) {
                case "1": db.showProducts(); break;
                case "2": addProduct(); break;
                case "3": updateProduct(); break;
                case "4": deleteProduct(); break;
                case "5": exportCSV(); break;
                case "6": report.displayLog(); break;
                case "7": System.out.println("Exchange Rate: " + network.fetchExchangeRate()); break;
                case "8": tcpReport(); break;
                case "0":
                    running = false;
                    network.stopServer();
                    report.appendToLog("System exited.");
                    System.out.println("Goodbye!");
                    break;
                default: System.out.println("Invalid option — try again.");
            }
        }
        sc.close();
    }

    // -------------------------------------------------------------------------
    // Menu action methods — each catches StoreException from the DB layer
    // -------------------------------------------------------------------------

    /** Reads product details from the user and inserts a new row via JDBC. */
    private static void addProduct() {
        try {
            System.out.print("Name: ");        String name  = sc.nextLine().trim();
            System.out.print("Category: ");    String cat   = sc.nextLine().trim();
            System.out.print("Price (SAR): "); double price = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Quantity: ");    int qty      = Integer.parseInt(sc.nextLine().trim());
            db.addProduct(name, cat, price, qty);
            report.appendToLog("Added product: " + name);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number — operation cancelled.");
        } catch (StoreException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** Shows current products, then lets the user change a product's price and quantity. */
    private static void updateProduct() {
        db.showProducts();
        System.out.print("Product ID to update: ");
        try {
            int id = Integer.parseInt(sc.nextLine().trim());
            String[] p = db.getProductById(id);
            if (p == null) { System.out.println("Product not found."); return; }
            System.out.printf("Current: %s | %s | SAR %s | Qty %s%n", p[1], p[2], p[3], p[4]);

            // Pressing Enter with no input keeps the existing value
            System.out.print("New Price (SAR) [Enter to keep]: ");
            String ps = sc.nextLine().trim();
            double price = ps.isEmpty() ? Double.parseDouble(p[3]) : Double.parseDouble(ps);

            System.out.print("New Quantity   [Enter to keep]: ");
            String qs = sc.nextLine().trim();
            int qty = qs.isEmpty() ? Integer.parseInt(p[4]) : Integer.parseInt(qs);

            db.updateProduct(id, p[1], p[2], price, qty);
            report.appendToLog("Updated product ID " + id);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input — operation cancelled.");
        } catch (StoreException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** Shows current products, then removes the chosen product from the database. */
    private static void deleteProduct() {
        db.showProducts();
        System.out.print("Product ID to delete: ");
        try {
            int id = Integer.parseInt(sc.nextLine().trim());
            db.deleteProduct(id);
            report.appendToLog("Deleted product ID " + id);
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID.");
        } catch (StoreException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** Fetches all products from the DB and writes them to products.csv (I/O Streams). */
    private static void exportCSV() {
        try {
            report.exportProductsCSV(db.getAllProducts());
        } catch (StoreException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /** Opens a Socket connection to our own TCP server and prints the STATUS response. */
    private static void tcpReport() {
        System.out.println("[Network] Connecting to TCP server on port 8080...");
        System.out.println(network.requestStatus(8080));
    }
}
