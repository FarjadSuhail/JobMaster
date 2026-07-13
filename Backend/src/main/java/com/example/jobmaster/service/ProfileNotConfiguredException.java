package com.example.jobmaster.service;

public class ProfileNotConfiguredException extends RuntimeException {

    public ProfileNotConfiguredException() {
        super("No profile configured yet. PUT /api/profile with your CV data first.");
    }
}
