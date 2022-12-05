/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.security.authc;

import org.elasticsearch.Version;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.CachedSupplier;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptorsIntersection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class RemoteAccessAuthentication {
    public static final String REMOTE_ACCESS_AUTHENTICATION_HEADER_KEY = "_remote_access_authentication";
    private final Authentication authentication;
    private final Supplier<List<BytesReference>> roleDescriptorsBytesSupplier;

    private RemoteAccessAuthentication(Authentication authentication, Supplier<List<BytesReference>> roleDescriptorsBytesSupplier) {
        this.authentication = authentication;
        this.roleDescriptorsBytesSupplier = roleDescriptorsBytesSupplier;
    }

    public RemoteAccessAuthentication(Authentication authentication, RoleDescriptorsIntersection roleDescriptorsIntersection) {
        this(authentication, new CachedSupplier<>(() -> roleDescriptorsToBytes(roleDescriptorsIntersection)));
    }

    public void writeToContext(final ThreadContext ctx) throws IOException {
        ctx.putHeader(REMOTE_ACCESS_AUTHENTICATION_HEADER_KEY, encode());
    }

    public static RemoteAccessAuthentication readFromContext(final ThreadContext ctx) throws IOException {
        return decode(ctx.getHeader(REMOTE_ACCESS_AUTHENTICATION_HEADER_KEY));
    }

    public static Set<RoleDescriptor> parseRoleDescriptorsBytes(final BytesReference roleDescriptorsBytes) {
        try (
            XContentParser parser = XContentHelper.createParser(XContentParserConfiguration.EMPTY, roleDescriptorsBytes, XContentType.JSON)
        ) {
            final List<RoleDescriptor> roleDescriptors = new ArrayList<>();
            parser.nextToken();
            while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                parser.nextToken();
                final String roleName = parser.currentName();
                roleDescriptors.add(RoleDescriptor.parse(roleName, parser, false));
            }
            return Set.copyOf(roleDescriptors);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String encode() throws IOException {
        final List<BytesReference> roleDescriptorsByteRefs = roleDescriptorsBytesSupplier.get();
        final BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(authentication.getEffectiveSubject().getVersion());
        Version.writeVersion(authentication.getEffectiveSubject().getVersion(), out);
        authentication.writeTo(out);
        out.writeCollection(roleDescriptorsByteRefs, StreamOutput::writeBytesReference);
        return Base64.getEncoder().encodeToString(BytesReference.toBytes(out.bytes()));
    }

    private static List<BytesReference> roleDescriptorsToBytes(RoleDescriptorsIntersection rdsIntersection) {
        try {
            final List<BytesReference> bytes = new ArrayList<>();
            for (Set<RoleDescriptor> roleDescriptors : rdsIntersection.roleDescriptorsList()) {
                final XContentBuilder builder = XContentFactory.jsonBuilder();
                builder.startObject();
                for (RoleDescriptor roleDescriptor : roleDescriptors) {
                    builder.field(roleDescriptor.getName(), roleDescriptor);
                }
                builder.endObject();
                bytes.add(BytesReference.bytes(builder));
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static RemoteAccessAuthentication decode(final String header) throws IOException {
        Objects.requireNonNull(header);
        final byte[] bytes = Base64.getDecoder().decode(header);
        final StreamInput in = StreamInput.wrap(bytes);
        final Version version = Version.readVersion(in);
        in.setVersion(version);
        final Authentication authentication = new Authentication(in);
        final List<BytesReference> roleDescriptorsBytesIntersection = in.readImmutableList(StreamInput::readBytesReference);
        return new RemoteAccessAuthentication(authentication, () -> roleDescriptorsBytesIntersection);
    }

    public Authentication authentication() {
        return authentication;
    }

    public List<BytesReference> roleDescriptorsBytesIntersection() {
        return roleDescriptorsBytesSupplier.get();
    }
}
