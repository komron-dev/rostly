// exception/NotFoundException.java
package com.komron.rostly.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}