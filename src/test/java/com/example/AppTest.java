package com.example;

import org.junit.jupiter.api.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AppTest — Unit Tests pillar (JUnit 5).
 *
 * Each test gets its own isolated SQLite database created from a temp file.
 * This avoids the problem of tests sharing state through a single store.db —
 * every @Test starts with a clean, empty database regardless of run order.
 *
 * @BeforeEach creates the temp file and initialises the schema.
 * @AfterEach  deletes the temp file so no test data lingers on disk.
 */
class AppTest {

    private DatabaseHandler db;
    private File tempDb;

    @BeforeEach
    void setUp() throws IOException, StoreException {
        // createTempFile gives each test its own unique file path
        tempDb = File.createTempFile("test_store_", ".db");
        tempDb.deleteOnExit(); // safety net in case tearDown is skipped
        db = new DatabaseHandler("jdbc:sqlite:" + tempDb.getAbsolutePath());
        db.setupDatabase();    // creates the products table
    }

    @AfterEach
    void tearDown() {
        // Explicitly delete so the file is gone before the next test starts
        if (tempDb != null) tempDb.delete();
    }

    // -------------------------------------------------------------------------
    // CRUD tests
    // -------------------------------------------------------------------------

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
        // Retrieve the auto-generated ID from the inserted row
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
        // After deletion, getProductById should return null
        assertNull(db.getProductById(id), "Product should not exist after deletion");
    }

    // -------------------------------------------------------------------------
    // Exception test
    // -------------------------------------------------------------------------

    @Test
    void testStoreException() {
        // Verify that StoreException correctly stores and returns the message
        StoreException e = new StoreException("test error");
        assertEquals("test error", e.getMessage(),
            "StoreException should preserve the message");
    }
}
