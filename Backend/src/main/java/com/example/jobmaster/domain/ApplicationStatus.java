package com.example.jobmaster.domain;

/** Pipeline stages of a job application, in board order. */
public enum ApplicationStatus {
    SAVED,
    APPLIED,
    INTERVIEWING,
    OFFER,
    REJECTED
}
