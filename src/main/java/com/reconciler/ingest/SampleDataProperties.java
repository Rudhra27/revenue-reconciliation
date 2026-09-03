package com.reconciler.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.sample-data.enabled (env APP_SAMPLE_DATA_ENABLED) — exposes the bundled demo files. */
@ConfigurationProperties(prefix = "app.sample-data")
public record SampleDataProperties(boolean enabled) {
}
