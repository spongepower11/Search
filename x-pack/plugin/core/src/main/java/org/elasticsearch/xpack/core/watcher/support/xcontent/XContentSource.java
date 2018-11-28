/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.watcher.support.xcontent;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectPath;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.XContentUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the xcontent source
 */
public class XContentSource implements ToXContent {

    private final BytesReference bytes;
    private final XContentType contentType;
    private Object data;

    /**
     * Constructs a new XContentSource out of the given bytes reference.
     */
    public XContentSource(BytesReference bytes, XContentType xContentType) throws ElasticsearchParseException {
        if (xContentType == null) {
            throw new IllegalArgumentException("xContentType must not be null");
        }
        this.bytes = bytes;
        this.contentType = xContentType;
    }

    /**
     * Constructs a new xcontent source from the bytes of the given xcontent builder
     */
    public XContentSource(XContentBuilder builder) {
        this(BytesReference.bytes(builder), builder.contentType());
    }

    /**
     * @return The bytes reference of the source
     */
    public BytesReference getBytes() {
        return bytes;
    }

    /**
     * @return true if the top level value of the source is a map
     */
    public boolean isMap() {
        return data() instanceof Map;
    }

    /**
     * @return The source as a map
     */
    public Map<String, Object> getAsMap() {
        return (Map<String, Object>) data();
    }

    /**
     * @return true if the top level value of the source is a list
     */
    public boolean isList() {
        return data() instanceof List;
    }

    /**
     * @return The source as a list
     */
    public List<Object> getAsList() {
        return (List<Object>) data();
    }

    /**
     * Extracts a value identified by the given path in the source.
     *
     * @param path a dot notation path to the requested value
     * @return The extracted value or {@code null} if no value is associated with the given path
     */
    public <T> T getValue(String path) {
        return (T) ObjectPath.eval(path, data());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        // EMPTY is safe here because we never use namedObject
        try (InputStream stream = bytes.streamInput();
             XContentParser parser = parser(NamedXContentRegistry.EMPTY, stream)) {
            parser.nextToken();
            builder.generator().copyCurrentStructure(parser);
            return builder;
        }
    }

    public XContentParser parser(NamedXContentRegistry xContentRegistry, InputStream stream) throws IOException {
        return contentType.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, stream);
    }

    public static XContentSource readFrom(StreamInput in) throws IOException {
        return new XContentSource(in.readBytesReference(), in.readEnum(XContentType.class));
    }

    public static void writeTo(XContentSource source, StreamOutput out) throws IOException {
        out.writeBytesReference(source.bytes);
        out.writeEnum(source.contentType);
    }

    private Object data() {
        if (data == null) {
            // EMPTY is safe here because we never use namedObject
            try (InputStream stream = bytes.streamInput();
                 XContentParser parser = parser(NamedXContentRegistry.EMPTY, stream)) {
                data = XContentUtils.readValue(parser, parser.nextToken());
            } catch (IOException ex) {
                throw new ElasticsearchException("failed to read value", ex);
            }
        }
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XContentSource that = (XContentSource) o;
        return Objects.equals(bytes, that.bytes) &&
            contentType == that.contentType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes, contentType);
    }
}
