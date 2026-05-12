package com.example;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;

public class NetworkService {

    private ServerSocket serverSocket;
    private volatile boolean serverRunning = false;

    /**
     * Starts a multi-client TCP server (ServerSocket + Thread-per-client).
     * Based on Example6: ThreadedServer from the Networking slides.
     */
    public void startServer(int port, DatabaseHandler db) {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                serverRunning = true;
                System.out.println("[Server] TCP server started on port " + port);
                while (serverRunning) {
                    Socket incoming = serverSocket.accept();
                    Thread clientThread = new Thread(new ClientHandler(incoming, db), "client-handler");
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                if (serverRunning) System.out.println("[Server] Error: " + e.getMessage());
            }
        }, "tcp-server-thread");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /** Handles one client in its own thread — Scanner + PrintWriter (slides 34-36). */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final DatabaseHandler db;

        ClientHandler(Socket socket, DatabaseHandler db) {
            this.socket = socket;
            this.db = db;
        }

        @Override
        public void run() {
            try {
                try (Scanner in = new Scanner(socket.getInputStream(), StandardCharsets.UTF_8);
                     PrintWriter out = new PrintWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                         true)) {

                    out.println("=== Store Management Server ===");
                    out.println("Commands: STATUS | INVENTORY | QUIT");

                    while (in.hasNextLine()) {
                        String cmd = in.nextLine().trim().toUpperCase();
                        if (cmd.equals("STATUS")) {
                            out.println("Status: RUNNING | Products: " + db.getAllProducts().size());
                        } else if (cmd.equals("INVENTORY")) {
                            List<String[]> products = db.getAllProducts();
                            if (products.isEmpty()) {
                                out.println("Inventory is empty.");
                            } else {
                                out.printf("%-5s %-20s %-12s %-10s %s%n",
                                    "ID", "Name", "Category", "Price(SAR)", "Qty");
                                for (String[] p : products)
                                    out.printf("%-5s %-20s %-12s %-10s %s%n",
                                        p[0], p[1], p[2], p[3], p[4]);
                            }
                        } else if (cmd.equals("QUIT")) {
                            out.println("Goodbye!"); break;
                        } else {
                            out.println("Unknown command. Available: STATUS, INVENTORY, QUIT");
                        }
                    }
                }
            } catch (IOException | StoreException e) {
                System.out.println("[Server] Client error: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Socket client — sends STATUS to our own server and returns the reply.
     * Demonstrates the client side of Socket communication (Networking slides).
     */
    public String requestStatus(int port) {
        try (Socket s = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            in.readLine(); // welcome line
            in.readLine(); // commands hint
            out.println("STATUS");
            String resp = in.readLine();
            out.println("QUIT");
            return resp != null ? resp : "No response from server.";
        } catch (IOException e) {
            return "Cannot connect to server: " + e.getMessage();
        }
    }

    /**
     * Fetches live SAR->USD rate via Java 11 HttpClient.
     * Based on Example9: HTTPGet1 from the Networking slides.
     */
    public String fetchExchangeRate() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.er-api.com/v6/latest/SAR"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            int idx = body.indexOf("\"USD\":");
            if (idx != -1) {
                int start = idx + 6;
                int end   = body.indexOf(",", start);
                if (end == -1) end = body.indexOf("}", start);
                return "1 SAR = " + body.substring(start, end).trim() + " USD  [open.er-api.com]";
            }
            return "Rate unavailable (HTTP " + response.statusCode() + ")";
        } catch (Exception e) {
            return "Rate unavailable: " + e.getMessage();
        }
    }

    public boolean isServerRunning() { return serverRunning; }

    public void stopServer() {
        serverRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
