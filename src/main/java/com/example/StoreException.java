package com.example;

/**
 * StoreException — Custom Checked Exception (Exception Handling pillar).
 *
 * Extends Exception (not RuntimeException), making it a checked exception.
 * The compiler forces every caller to either catch it or declare it with throws,
 * which is the key difference between checked and unchecked exceptions.
 */
public class StoreException extends Exception {

    // Pass the error message up to the Exception superclass
    public StoreException(String message) {
        super(message);
    }
}
