package com.example.urlshortener.exception;

public class LinkGoneException extends RuntimeException {
    public LinkGoneException(String message) {
        super(message);
    }
}
