package com.example.jobmaster.watcher.adapter;

import java.time.Instant;

/** One posting as returned by a portal adapter, before filtering/diffing. */
public record FetchedJob(
        String externalId,
        String title,
        String location,
        String department,
        String url,
        Instant postedAt
) {
}
