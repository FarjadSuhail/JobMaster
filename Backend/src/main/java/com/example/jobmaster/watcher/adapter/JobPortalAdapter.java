package com.example.jobmaster.watcher.adapter;

import com.example.jobmaster.watcher.domain.AdapterType;
import com.example.jobmaster.watcher.domain.WatchedCompany;

import java.util.List;

/**
 * Fetches the currently open postings of one company. Implementations map to
 * ATS platforms (SmartRecruiters, Personio, ...), configured per company via
 * WatchedCompany.configJson.
 */
public interface JobPortalAdapter {

    AdapterType type();

    /** All currently open postings; throws on any fetch/parse failure. */
    List<FetchedJob> fetch(WatchedCompany company) throws Exception;

    /**
     * Full plain-text job description of one posting, fetched on demand when
     * the user applies. Null when the platform can't provide it — the user
     * then pastes the JD manually, so failures here must never block anything.
     */
    default String fetchDescription(WatchedCompany company, String externalId, String url)
            throws Exception {
        return null;
    }
}
