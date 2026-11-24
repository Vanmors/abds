package com.vanmors.abds.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Not found");
    }
}
