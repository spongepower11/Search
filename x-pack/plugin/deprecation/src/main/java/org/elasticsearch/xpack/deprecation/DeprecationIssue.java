/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.deprecation;

import org.elasticsearch.Version;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Information about deprecated items
 */
public class DeprecationIssue implements Writeable, ToXContentObject {

    public enum Level implements Writeable {
        /**
         * Resolving this issue is advised but not required to upgrade. There may be undesired changes in behavior unless this issue is
         * resolved before upgrading.
         */
        WARNING,
        /**
         * This issue must be resolved to upgrade. Failures will occur unless this is resolved before upgrading.
         */
        CRITICAL
        ;

        public static Level fromString(String value) {
            return Level.valueOf(value.toUpperCase(Locale.ROOT));
        }

        public static Level readFromStream(StreamInput in) throws IOException {
            int ordinal = in.readVInt();
            if (ordinal < 0 || ordinal >= values().length) {
                throw new IOException("Unknown Level ordinal [" + ordinal + "]");
            }
            return values()[ordinal];
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeVInt(ordinal());
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Level level;
    private final String message;
    private final String url;
    private final String details;
    private final boolean resolveDuringRollingUpgrade;

    public DeprecationIssue(Level level, String message, String url, @Nullable String details, boolean resolveDuringRollingUpgrade) {
        this.level = level;
        this.message = message;
        this.url = url;
        this.details = details;
        this.resolveDuringRollingUpgrade = resolveDuringRollingUpgrade;
    }

    public DeprecationIssue(StreamInput in) throws IOException {
        level = Level.readFromStream(in);
        message = in.readString();
        url = in.readString();
        details = in.readOptionalString();
        resolveDuringRollingUpgrade = in.getVersion().onOrAfter(Version.V_8_0_0) && in.readBoolean();
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getUrl() {
        return url;
    }

    public String getDetails() {
        return details;
    }

    /**
     * @return whether a deprecation issue can only be resolved during a rolling upgrade when a node is offline.
     */
    public boolean isResolveDuringRollingUpgrade() {
        return resolveDuringRollingUpgrade;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        level.writeTo(out);
        out.writeString(message);
        out.writeString(url);
        out.writeOptionalString(details);
        if (out.getVersion().onOrAfter(Version.V_8_0_0)) {
            out.writeBoolean(resolveDuringRollingUpgrade);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject()
            .field("level", level)
            .field("message", message)
            .field("url", url);
        if (details != null) {
            builder.field("details", details);
        }
        builder.field("resolve_during_rolling_upgrade", resolveDuringRollingUpgrade);
        return builder.endObject();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeprecationIssue that = (DeprecationIssue) o;
        return Objects.equals(level, that.level) &&
            Objects.equals(message, that.message) &&
            Objects.equals(url, that.url) &&
            Objects.equals(details, that.details) &&
            Objects.equals(resolveDuringRollingUpgrade, that.resolveDuringRollingUpgrade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, message, url, details, resolveDuringRollingUpgrade);
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }
}

