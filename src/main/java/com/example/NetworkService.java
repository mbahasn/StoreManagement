package com.example;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;

/**
 * NetworkService — Networking pillar.
 *
 * Demonstrates three networking concepts from the Networking slides:
 *   1. ServerSocket  — listens for incoming TCP connections (slide 32)
 *   2. Socket        — client connects to the server (slide 18)
 *   3. HttpClient    — sends an HTTP GET request to fetch exchange rate (slide 95)
 *
 * The server uses the Thread-per-client pattern (slide 47-48):
 *   each accepted connection is handed to a new Thread so multiple
 *   clients can be served at the same time without blocking each other.
 */
public class NetworkService {

    private ServerSocket serverSocket;

    // volatile ensures the serverRunning flag is read from main memory,
    // not a thread-local CPU cache, so the server loop sees the updated value
    // when stopServer() is called from a different thread.
    private volatile boolean serverRunning = false;

    // -------------------------------------------------------------------------
    // Server side — ServerSocket + Thread-per-client
    // -------------------------------------------------------------------------

    /**
     * Opens a ServerSocket on the given port and starts accepting clients.
     * The accept loop runs in a daemon thread so it does not prevent the JVM
     * from exiting when the main thread finishes.
     */
    public void startServer(int port, DatabaseHandler db) {
        Thread serverThread = new Thread(() -> {
            try {
                // Bind to the port — clients connect here (Networking slide 32)
                serverSocket = new ServerSocket(port);
                serverRunning = true;
                System.out.println("[Server] TCP server started on port " + port);

                while (serverRunning) {
                    // Blocks until a client connects; returns a Socket for that client
                    Socket incoming = serverSocket.accept();

                    // Spawn a new thread for each client — Thread-per-client pattern
                    // (Networking slides 47-48: ThreadedEchoHandler example)
                    Thread clientThread = new Thread(new ClientHandler(incoming, db), "client-handler");
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                if (serverRunning) System.out.println("[Server] Error: " + e.getMessage());
            }
        }, "tcp-server-thread");
        serverThread.setDaemon(true); // daemon — won't block JVM shutdown
        serverThread.start();
    }

    /**
     * Handles one connected client in its own thread.
     *
     * Stream setup follows Networking slide 35:
     *   Scanner      wraps the socket InputStream  for line-by-line reading
     *   PrintWriter  wraps the socket OutputStream for formatted text output
     *   autoFlush=true means each println() is sent immediately without buffering
     *
     * Supported text commands: STATUS | INVENTORY | QUIT
     */
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
                // try-with-resources closes both streams (and the socket) automatically
                try (Scanner in = new Scanner(socket.getInputStream(), StandardCharsets.UTF_8);
                     PrintWriter out = new PrintWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                         true /* autoFlush */)) {

                    // Greet the client
                    out.println("=== Store Management Server ===");
                    out.println("Commands: STATUS | INVENTORY | QUIT");

                    // Read commands line by line until the client disconnects or sends QUIT
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
                // Always close the socket, even if an exception occurred
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Client side — Socket
    // -------------------------------------------------------------------------

    /**
     * Connects to our own server as a Socket client, sends STATUS, and returns
     * the response. Demonstrates the client side of Socket communication
     * (Networking slide 18: new Socket(host, port)).
     */
    public String requestStatus(int port) {
        // try-with-resources closes the socket and streams when done
        try (Socket s = new Socket("localhost", port);
             BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            in.readLine(); // discard welcome line
            in.readLine(); // discard commands hint
            out.println("STATUS");          // send command to server
            String resp = in.readLine();    // read server response
            out.println("QUIT");            // cleanly close the session
            return resp != null ? resp : "No response from server.";
        } catch (IOException e) {
            return "Cannot connect to server: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // HTTP client — HttpClient (Java 11)
    // -------------------------------------------------------------------------

    /**
     * Fetches the live SAR → USD exchange rate using Java 11 HttpClient.
     * Based on Example9: HTTPGet1 from the Networking slides (slide 95).
     *
     * Three classes used exactly as shown in the slides:
     *   HttpClient   — built with newBuilder()
     *   HttpRequest  — built with newBuilder().uri(...).GET().build()
     *   HttpResponse — received via client.send(..., BodyHandlers.ofString())
     *
     * The JSON body is parsed manually (no external library) by locating
     * the "USD": key and extracting the value that follows it.
     */
    public String fetchExchangeRate() {
        try {
            // Build the HTTP client (slide 96)
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            // Build the GET request (slide 96)
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.er-api.com/v6/latest/SAR"))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

            // Send the request and receive the response body as a String (slide 96)
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse the USD rate from the JSON response without an external library
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

    /** Signals the accept loop to stop and closes the ServerSocket. */
    public void stopServer() {
        serverRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }
}
