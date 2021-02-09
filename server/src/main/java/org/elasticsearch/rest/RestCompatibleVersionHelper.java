/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.rest;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.Version;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.compatibility.RestApiCompatibleVersion;
import org.elasticsearch.common.xcontent.MediaType;
import org.elasticsearch.common.xcontent.ParsedMediaType;

/**
 * A helper that is responsible for parsing a Compatible REST API version from RestRequest.
 * It also performs a validation of allowed combination of versions provided on those headers.
 * Package scope as it is only aimed to be used by RestRequest
 */
class RestCompatibleVersionHelper {

    static RestApiCompatibleVersion getCompatibleVersion(
        @Nullable ParsedMediaType acceptHeader,
        @Nullable ParsedMediaType contentTypeHeader,
        boolean hasContent
    ) {
        Byte aVersion = parseVersion(acceptHeader);
        byte acceptVersion = aVersion == null ? RestApiCompatibleVersion.currentVersion().major : Integer.valueOf(aVersion).byteValue();
        Byte cVersion = parseVersion(contentTypeHeader);
        byte contentTypeVersion = cVersion == null ?
            RestApiCompatibleVersion.currentVersion().major : Integer.valueOf(cVersion).byteValue();

        // accept version must be current or prior
        if (acceptVersion > RestApiCompatibleVersion.currentVersion().major ||
            acceptVersion < RestApiCompatibleVersion.minimumSupported().major) {
            throw new ElasticsearchStatusException(
                "Accept version must be either version {} or {}, but found {}. Accept={}",
                RestStatus.BAD_REQUEST,
                RestApiCompatibleVersion.currentVersion().major,
                RestApiCompatibleVersion.minimumSupported().major,
                acceptVersion,
                acceptHeader
            );
        }
        if (hasContent) {

            // content-type version must be current or prior
            if (contentTypeVersion > RestApiCompatibleVersion.currentVersion().major
                || contentTypeVersion < RestApiCompatibleVersion.minimumSupported().major) {
                throw new ElasticsearchStatusException(
                    "Content-Type version must be either version {} or {}, but found {}. Content-Type={}",
                    RestStatus.BAD_REQUEST,
                    RestApiCompatibleVersion.currentVersion().major,
                    RestApiCompatibleVersion.minimumSupported().major,
                    contentTypeVersion,
                    contentTypeHeader
                );
            }
            // if both accept and content-type are sent, the version must match
            if (contentTypeVersion != acceptVersion) {
                throw new ElasticsearchStatusException(
                    "A compatible version is required on both Content-Type and Accept headers "
                        + "if either one has requested a compatible version "
                        + "and the compatible versions must match. Accept={}, Content-Type={}",
                    RestStatus.BAD_REQUEST,
                    acceptHeader,
                    contentTypeHeader
                );
            }
            // both headers should be versioned or none
            if ((cVersion == null && aVersion != null) || (aVersion == null && cVersion != null)) {
                throw new ElasticsearchStatusException(
                    "A compatible version is required on both Content-Type and Accept headers "
                        + "if either one has requested a compatible version. Accept={}, Content-Type={}",
                    RestStatus.BAD_REQUEST,
                    acceptHeader,
                    contentTypeHeader
                );
            }
            if (contentTypeVersion < Version.CURRENT.major) {
                return RestApiCompatibleVersion.fromMajorVersion(Version.CURRENT.previousMajor().major);
            }
        }

        if (acceptVersion < Version.CURRENT.major) {
            return RestApiCompatibleVersion.fromMajorVersion(Version.CURRENT.previousMajor().major);
        }

        return RestApiCompatibleVersion.fromMajorVersion(Version.CURRENT.major);
    }

    static Byte parseVersion(ParsedMediaType parsedMediaType) {
        if (parsedMediaType != null) {
            String version = parsedMediaType.getParameters().get(MediaType.COMPATIBLE_WITH_PARAMETER_NAME);
            return version != null ? Byte.parseByte(version) : null;
        }
        return null;
    }
}
