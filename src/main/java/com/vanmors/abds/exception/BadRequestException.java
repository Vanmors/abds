package com.vanmors.abds.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public static BadRequestException invalidField(String field) {
        return new BadRequestException("invalid \"" + field + "\" field");
    }

    public static BadRequestException invalidParameter(String parameter) {
        return new BadRequestException("invalid \"" + parameter + "\" parameter");
    }
}
