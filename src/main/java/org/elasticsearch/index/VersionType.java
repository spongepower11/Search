/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.lucene.uid.Versions;

/**
 *
 */
public enum VersionType {
    INTERNAL((byte) 0) {
        /**
         * - always returns false if currentVersion == {@link Versions#NOT_SET}
         * - always accepts expectedVersion == {@link Versions#MATCH_ANY}
         * - if expectedVersion is set, always conflict if currentVersion == {@link Versions#NOT_FOUND}
         */
        @Override
        public boolean isVersionConflict(long currentVersion, long expectedVersion) {
            return currentVersion != Versions.NOT_SET && expectedVersion != Versions.MATCH_ANY
                    && (currentVersion == Versions.NOT_FOUND || currentVersion != expectedVersion);
        }

        @Override
        public long updateVersion(long currentVersion, long expectedVersion) {
            return (currentVersion == Versions.NOT_SET || currentVersion == Versions.NOT_FOUND) ? 1 : currentVersion + 1;
        }

        @Override
        public boolean validateVersion(long version) {
            // not allowing Versions.NOT_FOUND as it is not a valid input value.
            return version > 0L || version == Versions.MATCH_ANY;
        }

        @Override
        public VersionType versionTypeForReplicationAndRecovery() {
            // replicas get the version from the primary after increment. The same version is stored in
            // the transaction log. -> the should use the external semantics.
            return EXTERNAL;
        }
    },
    EXTERNAL((byte) 1) {
        /**
         * - always returns false if currentVersion == {@link Versions#NOT_SET}
         * - always conflict if expectedVersion == {@link Versions#MATCH_ANY} (we need something to set)
         * - accepts currentVersion == {@link Versions#NOT_FOUND}
         */
        @Override
        public boolean isVersionConflict(long currentVersion, long expectedVersion) {
            return currentVersion != Versions.NOT_SET && currentVersion != Versions.NOT_FOUND
                    && (expectedVersion == Versions.MATCH_ANY || currentVersion >= expectedVersion);
        }

        @Override
        public long updateVersion(long currentVersion, long expectedVersion) {
            return expectedVersion;
        }

        @Override
        public boolean validateVersion(long version) {
            return version > 0L;
        }
    },
    EXTERNAL_GTE((byte) 2) {
        /**
         * - always returns false if currentVersion == {@link Versions#NOT_SET}
         * - always conflict if expectedVersion == {@link Versions#MATCH_ANY} (we need something to set)
         * - accepts currentVersion == {@link Versions#NOT_FOUND}
         */
        @Override
        public boolean isVersionConflict(long currentVersion, long expectedVersion) {
            return currentVersion != Versions.NOT_SET && currentVersion != Versions.NOT_FOUND
                    && (expectedVersion == Versions.MATCH_ANY || currentVersion > expectedVersion);
        }

        @Override
        public long updateVersion(long currentVersion, long expectedVersion) {
            return expectedVersion;
        }

        @Override
        public boolean validateVersion(long version) {
            return version > 0L;
        }
    },
    /**
     * Warning: this version type should be used with care. Concurrent indexing may result in loss of data on replicas
     */
    FORCE((byte) 3) {
        /**
         * - always returns false if currentVersion == {@link Versions#NOT_SET}
         * - always conflict if expectedVersion == {@link Versions#MATCH_ANY} (we need something to set)
         * - accepts currentVersion == {@link Versions#NOT_FOUND}
         */
        @Override
        public boolean isVersionConflict(long currentVersion, long expectedVersion) {
            return currentVersion != Versions.NOT_SET && currentVersion != Versions.NOT_FOUND
                    && expectedVersion == Versions.MATCH_ANY;
        }

        @Override
        public long updateVersion(long currentVersion, long expectedVersion) {
            return expectedVersion;
        }

        @Override
        public boolean validateVersion(long version) {
            return version > 0L;
        }
    };

    private final byte value;

    VersionType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * Checks whether the current version conflicts with the expected version, based on the current version type.
     *
     * @return true if versions conflict false o.w.
     */
    public abstract boolean isVersionConflict(long currentVersion, long expectedVersion);

    /**
     * Returns the new version for a document, based on its current one and the specified in the request
     *
     * @return new version
     */
    public abstract long updateVersion(long currentVersion, long expectedVersion);

    /** validate the version is a valid value for this type.
     * @return true if valid, false o.w
     */
    public abstract boolean validateVersion(long version);

    /** Some version types require different semantics for primary and replicas. This version allows
     * the type to override the default behavior.
     */
    public VersionType versionTypeForReplicationAndRecovery() {
        return this;
    }

    public static VersionType fromString(String versionType) {
        if ("internal".equals(versionType)) {
            return INTERNAL;
        } else if ("external".equals(versionType)) {
            return EXTERNAL;
        } else if ("external_gt".equals(versionType)) {
            return EXTERNAL;
        } else if ("external_gte".equals(versionType)) {
            return EXTERNAL_GTE;
        } else if ("force".equals(versionType)) {
            return FORCE;
        }
        throw new ElasticsearchIllegalArgumentException("No version type match [" + versionType + "]");
    }

    public static VersionType fromString(String versionType, VersionType defaultVersionType) {
        if (versionType == null) {
            return defaultVersionType;
        }
        return fromString(versionType);
    }

    public static VersionType fromValue(byte value) {
        if (value == 0) {
            return INTERNAL;
        } else if (value == 1) {
            return EXTERNAL;
        } else if (value == 2) {
            return EXTERNAL_GTE;
        } else if (value == 3) {
            return FORCE;
        }
        throw new ElasticsearchIllegalArgumentException("No version type match [" + value + "]");
    }
}
