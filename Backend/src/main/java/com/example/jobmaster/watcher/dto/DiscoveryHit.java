package com.example.jobmaster.watcher.dto;

import com.example.jobmaster.watcher.domain.AdapterType;

/** A verified career board found by discovery, ready to add to the watch list. */
public record DiscoveryHit(
        String name,
        AdapterType adapterType,
        String identifier,
        String configJson,
        long jobCount,
        boolean alreadyWatched,
        /** Name of the existing watch entry when alreadyWatched. */
        String watchedAs
) {
}
