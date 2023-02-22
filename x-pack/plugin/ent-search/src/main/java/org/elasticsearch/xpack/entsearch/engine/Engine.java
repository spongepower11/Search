/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.entsearch.engine;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.ReleasableBytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.xcontent.ConstructingObjectParser.optionalConstructorArg;

public class Engine implements Writeable, ToXContentObject {
    private final String name;
    private final String[] indices;

    private final TimeValue createdAt;

    private final TimeValue updatedAt;
    private final String analyticsCollectionName;

    /**
     * Public constructor.
     *
     * @param name                    The name of the engine.
     * @param indices                 The list of indices targeted by this engine.
     * @param analyticsCollectionName The name of the associated analytics collection.
     * @param createdAt               Timestamp this Engine was created. Null uses current timestamp.
     * @param updatedAt               Timestamp this Engine was last updated. Null uses current timestamp.
     */
    public Engine(
        String name,
        String[] indices,
        @Nullable String analyticsCollectionName,
        @Nullable TimeValue createdAt,
        @Nullable TimeValue updatedAt
    ) {
        this.name = name;
        this.indices = indices;
        Arrays.sort(indices);
        this.analyticsCollectionName = analyticsCollectionName;
        TimeValue timestamp = new TimeValue(System.currentTimeMillis());
        this.createdAt = (createdAt != null ? createdAt : timestamp);
        this.updatedAt = (updatedAt != null ? updatedAt : timestamp);
    }

    /**
     * Public constructor.
     *
     * @param name                    The name of the engine.
     * @param indices                 The list of indices targeted by this engine.
     * @param analyticsCollectionName The name of the associated analytics collection.
     */
    public Engine(String name, String[] indices, @Nullable String analyticsCollectionName) {
        this(name, indices, analyticsCollectionName, null, null);
    }

    public Engine(StreamInput in) throws IOException {
        this.name = in.readString();
        this.indices = in.readStringArray();
        this.analyticsCollectionName = in.readOptionalString();
        this.createdAt = in.readTimeValue();
        this.updatedAt = in.readTimeValue();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeStringArray(indices);
        out.writeOptionalString(analyticsCollectionName);
        out.writeTimeValue(createdAt);
        out.writeTimeValue(updatedAt);
    }

    private static final ConstructingObjectParser<Engine, String> PARSER = new ConstructingObjectParser<>(
        "engine",
        false,
        (params, engineName) -> {
            @SuppressWarnings("unchecked")
            final String[] indices = ((List<String>) params[0]).toArray(String[]::new);
            final String analyticsCollectionName = (String) params[1];
            final TimeValue createdAt = new TimeValue((Long) params[2]);
            final TimeValue updatedAt = new TimeValue((Long) params[2]);

            return new Engine(engineName, indices, analyticsCollectionName, createdAt, updatedAt);
        }
    );

    public static final ParseField NAME_FIELD = new ParseField("name");
    public static final ParseField INDICES_FIELD = new ParseField("indices");
    public static final ParseField ANALYTICS_COLLECTION_NAME_FIELD = new ParseField("analytics_collection_name");
    public static final ParseField CREATED_AT_FIELD = new ParseField("created_at");
    public static final ParseField UPDATED_AT_FIELD = new ParseField("updated_at");
    public static final ParseField BINARY_CONTENT_FIELD = new ParseField("binary_content");

    static {
        PARSER.declareStringArray(constructorArg(), INDICES_FIELD);
        PARSER.declareStringOrNull(optionalConstructorArg(), ANALYTICS_COLLECTION_NAME_FIELD);
        PARSER.declareString(optionalConstructorArg(), CREATED_AT_FIELD);
        PARSER.declareString(optionalConstructorArg(), UPDATED_AT_FIELD);
    }

    /**
     * Parses an {@link Engine} from its {@param xContentType} representation in bytes.
     *
     * @param resourceName The name of the resource (must match the {@link Engine} name).
     * @param source The bytes that represents the {@link Engine}.
     * @param xContentType The format of the representation.
     *
     * @return The parsed {@link Engine}.
     */
    public static Engine fromXContentBytes(String resourceName, BytesReference source, XContentType xContentType) {
        try (XContentParser parser = XContentHelper.createParser(XContentParserConfiguration.EMPTY, source, xContentType)) {
            return Engine.fromXContent(resourceName, parser);
        } catch (IOException e) {
            throw new ElasticsearchParseException("Failed to parse: " + source.utf8ToString(), e);
        }
    }

    /**
     * Parses an {@link Engine} through the provided {@param parser}.
     *
     * @param resourceName The name of the resource (must match the {@link Engine} name).
     * @param parser The {@link XContentType} parser.
     *
     * @return The parsed {@link Engine}.
     */
    public static Engine fromXContent(String resourceName, XContentParser parser) throws IOException {
        return PARSER.parse(parser, resourceName);
    }

    /**
     * Converts the {@link Engine} to XContent.
     *
     * @return The {@link XContentBuilder} containing the serialized {@link Engine}.
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(INDICES_FIELD.getPreferredName(), indices);
        if (analyticsCollectionName != null) {
            builder.field(ANALYTICS_COLLECTION_NAME_FIELD.getPreferredName(), analyticsCollectionName);
        }
        builder.field(CREATED_AT_FIELD.getPreferredName(), createdAt);
        builder.field(UPDATED_AT_FIELD.getPreferredName(), updatedAt);
        builder.endObject();
        return builder;
    }

    /**
     * Returns the name of the {@link Engine}.
     *
     * @return The name of the {@link Engine}.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the list of indices targeted by the {@link Engine}.
     *
     * @return The list of indices targeted by the {@link Engine}.
     */
    public String[] indices() {
        return indices;
    }

    /**
     * Returns the name of the analytics collection linked with this {@link Engine}.
     *
     * @return The analytics collection name.
     */
    public @Nullable String analyticsCollectionName() {
        return analyticsCollectionName;
    }

    public TimeValue createdAt() {
        return createdAt;
    }

    public TimeValue updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Engine engine = (Engine) o;
        return name.equals(engine.name)
            && Arrays.equals(indices, engine.indices)
            && Objects.equals(analyticsCollectionName, engine.analyticsCollectionName);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, analyticsCollectionName);
        result = 31 * result + Arrays.hashCode(indices);
        return result;
    }

    @Override
    public String toString() {
        return Strings.toString(this);
    }

    /**
     * Returns the merged {@link Engine} from the current state and the provided {@param update}.
     * This function returns the current instance if the update is a noop.
     *
     * @param update The source of the update represented in bytes.
     * @param xContentType The format of the bytes.
     * @param bigArrays The {@link BigArrays} to use to recycle bytes array.
     *
     * @return The merged {@link Engine}.
     */
    Engine merge(BytesReference update, XContentType xContentType, BigArrays bigArrays) {
        final Tuple<XContentType, Map<String, Object>> sourceAndContent;
        try (ReleasableBytesStreamOutput sourceBuffer = new ReleasableBytesStreamOutput(0, bigArrays.withCircuitBreaking())) {
            try (XContentBuilder builder = XContentFactory.jsonBuilder(sourceBuffer)) {
                toXContent(builder, EMPTY_PARAMS);
            }
            sourceAndContent = XContentHelper.convertToMap(sourceBuffer.bytes(), true, XContentType.JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final Tuple<XContentType, Map<String, Object>> updateAndContent = XContentHelper.convertToMap(update, true, xContentType);
        final Map<String, Object> newSourceAsMap = new HashMap<>(sourceAndContent.v2());
        final boolean noop = XContentHelper.update(newSourceAsMap, updateAndContent.v2(), true) == false;
        if (noop) {
            return this;
        }

        try (ReleasableBytesStreamOutput newSourceBuffer = new ReleasableBytesStreamOutput(0, bigArrays.withCircuitBreaking())) {
            try (XContentBuilder builder = XContentFactory.jsonBuilder(newSourceBuffer)) {
                builder.value(newSourceAsMap);
            }
            return Engine.fromXContentBytes(name, newSourceBuffer.bytes(), XContentType.JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
