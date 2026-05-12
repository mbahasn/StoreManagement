package com.example;

import java.io.*;
import java.util.Date;
import java.util.List;

public class ReportService {

    private static final String LOG_FILE = "activity.log";

    /**
     * Export products to CSV.
     * Uses PrintWriter wrapping BufferedWriter wrapping FileWriter — connected streams
     * (IOStream slides: FileWriter -> BufferedWriter -> PrintWriter chain).
     */
    public void exportProductsCSV(List<String[]> products) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("products.csv")))) {
            pw.println("ID,Name,Category,Price(SAR),Quantity");
            for (String[] p : products) pw.println(String.join(",", p));
            System.out.println("[IO] Exported to products.csv");
            appendToLog("Exported inventory to products.csv");
        } catch (IOException e) {
            System.out.println("[IO] Export error: " + e.getMessage());
        }
    }

    /**
     * Append a timestamped entry to the activity log.
     * synchronized so the main thread and the InventoryMonitor daemon thread
     * never interleave writes (IOStream slides: BufferedWriter + append mode).
     */
    public synchronized void appendToLog(String message) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
                bw.write("[" + new Date() + "] " + message);
            bw.newLine();
        } catch (IOException ignored) {}
    }

    /**
     * Read and print the full activity log.
     * Uses BufferedReader + readLine() (IOStream slides: BufferedReader pattern).
     */
    public void displayLog() {
        System.out.println("\n--- Activity Log ---");
        File f = new File(LOG_FILE);
        if (!f.exists()) { System.out.println("Log is empty."); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) System.out.println(line);
        } catch (IOException e) {
            System.out.println("[IO] Log read error: " + e.getMessage());
        }
    }
}
