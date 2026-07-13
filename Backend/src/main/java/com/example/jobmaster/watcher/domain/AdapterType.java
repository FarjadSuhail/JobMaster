package com.example.jobmaster.watcher.domain;

/**
 * Which ATS platform a career portal runs on. Adapters map to platforms, not
 * companies — onboarding a company whose platform is listed here needs no code.
 */
public enum AdapterType {
    SMARTRECRUITERS,
    PERSONIO,
    SUCCESSFACTORS,
    GREENHOUSE,
    WORKDAY,
    ASHBY,
    /** Milch & Zucker BeeSite (e.g. Deutsche Bank careers). */
    BEESITE,
    /** German Federal Employment Agency job board — a keyword search across
     *  all of Germany rather than one company's portal. */
    ARBEITSAGENTUR
}
