package com.example;

import java.io.*;
import java.util.Date;
import java.util.List;

/**
 * ReportService — I/O Streams pillar.
 *
 * Demonstrates three stream strategies from the IOStream slides:
 *
 *   exportProductsCSV  — connected streams: FileWriter → BufferedWriter → PrintWriter
 *                        (IOStream slides 23-25, 48-52: chaining for buffering + formatting)
 *
 *   appendToLog        — BufferedWriter with FileWriter in append mode
 *                        (IOStream slide 22: FileWriter(name, true) appends instead of overwriting)
 *                        synchronized so two threads never write at the same time
 *
 *   displayLog         — BufferedReader + readLine() to read the log line by line
 *                        (IOStream slides 53-55: BufferedReader example)
 */
public class ReportService {

    private static final String LOG_FILE = "activity.log";

    // -------------------------------------------------------------------------
    // CSV Export — connected streams (FileWriter → BufferedWriter → PrintWriter)
    // -------------------------------------------------------------------------

    /**
     * Writes all products to products.csv.
     *
     * Stream chain:
     *   FileWriter     — opens the file for writing
     *   BufferedWriter — wraps FileWriter to add buffering (fewer disk writes)
     *   PrintWriter    — wraps BufferedWriter to enable println / printf formatting
     *
     * try-with-resources closes all three streams automatically when done.
     */
    public void exportProductsCSV(List<String[]> products) {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("products.csv")))) {
            pw.println("ID,Name,Category,Price(SAR),Quantity"); // header row
            for (String[] p : products) pw.println(String.join(",", p));
            System.out.println("[IO] Exported to products.csv");
            appendToLog("Exported inventory to products.csv");
        } catch (IOException e) {
            System.out.println("[IO] Export error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Activity log — BufferedWriter with append mode
    // -------------------------------------------------------------------------

    /**
     * Appends one timestamped line to activity.log.
     *
     * FileWriter(LOG_FILE, true) — the boolean true means append, not overwrite
     *                              (IOStream slide 22)
     * synchronized               — only one thread can write at a time, preventing
     *                              garbled output if two threads call this concurrently
     */
    public synchronized void appendToLog(String message) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            bw.write("[" + new Date() + "] " + message);
            bw.newLine(); // writes the platform line separator (\r\n on Windows)
        } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------
    // Log display — BufferedReader + readLine()
    // -------------------------------------------------------------------------

    /**
     * Reads and prints every line of activity.log.
     *
     * BufferedReader wraps FileReader and adds an internal buffer so the JVM
     * reads a large chunk at once instead of one character at a time.
     * readLine() returns null at end-of-file, which ends the loop.
     * (IOStream slides 53-55: BufferedReader example)
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
