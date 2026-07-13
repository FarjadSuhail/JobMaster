package com.example.jobmaster.service;

/** A job-description fetch failed in a way the user should read and act on. */
public class JdFetchException extends RuntimeException {

    public JdFetchException(String message) {
        super(message);
    }

    public JdFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
