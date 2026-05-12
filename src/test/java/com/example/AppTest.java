package com.example;

import org.junit.jupiter.api.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    private DatabaseHandler db;
    private File tempDb;

    @BeforeEach
    void setUp() throws IOException, StoreException {
        // File-based temp DB so each test gets a clean, isolated SQLite database
        tempDb = File.createTempFile("test_store_", ".db");
        tempDb.deleteOnExit();
        db = new DatabaseHandler("jdbc:sqlite:" + tempDb.getAbsolutePath());
        db.setupDatabase();
    }

    @AfterEach
    void tearDown() { if (tempDb != null) tempDb.delete(); }

    @Test
    void testAddAndGetAllProducts() throws StoreException {
        db.addProduct("Apple", "Fruit", 5.0, 100);
        List<String[]> list = db.getAllProducts();
        assertFalse(list.isEmpty(), "Product list should not be empty after insert");
        assertEquals("Apple", list.get(0)[1]);
        assertEquals("Fruit", list.get(0)[2]);
    }

    @Test
    void testGetProductById() throws StoreException {
        db.addProduct("Banana", "Fruit", 2.5, 50);
        int id = Integer.parseInt(db.getAllProducts().get(0)[0]);
        String[] p = db.getProductById(id);
        assertNotNull(p, "Should find product by ID");
        assertEquals("Banana", p[1]);
    }

    @Test
    void testUpdateProduct() throws StoreException {
        db.addProduct("Orange", "Fruit", 4.0, 30);
        int id = Integer.parseInt(db.getAllProducts().get(0)[0]);
        db.updateProduct(id, "Orange", "Fruit", 6.0, 25);
        String[] p = db.getProductById(id);
        assertNotNull(p);
        assertEquals("6.0", p[3], "Price should be updated");
        assertEquals("25",  p[4], "Quantity should be updated");
    }

    @Test
    void testDeleteProduct() throws StoreException {
        db.addProduct("Mango", "Fruit", 8.0, 20);
        int id = Integer.parseInt(db.getAllProducts().get(0)[0]);
        db.deleteProduct(id);
        assertNull(db.getProductById(id), "Product should not exist after deletion");
    }

    @Test
    void testStoreException() {
        StoreException e = new StoreException("test error");
        assertEquals("test error", e.getMessage(),
            "StoreException should preserve the message");
    }
}
