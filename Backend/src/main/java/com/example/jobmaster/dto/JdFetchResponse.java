package com.example.jobmaster.dto;

/** Result of fetching a job description from a posting URL. */
public record JdFetchResponse(
        String jobTitle,
        String company,
        String location,
        String jobDescription,
        /** Where the text came from, e.g. "Greenhouse API" — shown as a hint in the UI. */
        String source,
        /** What happened watch-list-wise: added / already watched / not supported. */
        String watchMessage
) {
}
