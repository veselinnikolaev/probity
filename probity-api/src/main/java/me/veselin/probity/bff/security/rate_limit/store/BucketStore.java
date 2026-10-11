package me.veselin.probity.bff.security.rate_limit.store;

import io.github.bucket4j.Bucket;
import me.veselin.probity.bff.security.rate_limit.RateLimit;

public interface BucketStore {
    Bucket getOrCreate(String key, RateLimit annotation);
}
