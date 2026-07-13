package com.example.jobmaster.watcher.domain;

public enum JobPostingStatus {
    /** Newly discovered — waiting for the user to act. */
    NEW,
    /** Known posting (baseline import or user marked as seen). */
    SEEN,
    /** User is not interested. */
    DISMISSED,
    /** Turned into a board application. */
    CONVERTED
}
