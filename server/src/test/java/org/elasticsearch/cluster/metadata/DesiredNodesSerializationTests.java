/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.test.AbstractSerializingTestCase;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;

import static org.elasticsearch.cluster.metadata.DesiredNodesTestCase.randomDesiredNodes;

public class DesiredNodesSerializationTests extends AbstractSerializingTestCase<DesiredNodes> {
    @Override
    protected Writeable.Reader<DesiredNodes> instanceReader() {
        return DesiredNodes::readFrom;
    }

    @Override
    protected DesiredNodes createTestInstance() {
        return randomDesiredNodes();
    }

    @Override
    protected DesiredNodes doParseInstance(XContentParser parser) throws IOException {
        return DesiredNodes.fromXContent(parser);
    }

    @Override
    protected DesiredNodes mutateInstance(DesiredNodes instance) throws IOException {
        return mutateDesiredNodes(instance);
    }

    public static DesiredNodes mutateDesiredNodes(DesiredNodes instance) {
        final var mutationBranch = randomInt(3);
        return switch (mutationBranch) {
            case 0 -> DesiredNodes.create(randomAlphaOfLength(10), instance.version(), instance.nodes());
            case 1 -> DesiredNodes.create(instance.historyID(), instance.version() + 1, instance.nodes());
            case 2 -> DesiredNodes.create(
                instance.historyID(),
                instance.version(),
                instance.nodes().size() > 1
                    ? randomSubsetOf(randomIntBetween(1, instance.nodes().size() - 1), instance.nodes())
                    : randomList(1, 10, DesiredNodesTestCase::randomDesiredNodeWithStatus)
            );
            case 3 -> DesiredNodes.create(
                instance.historyID(),
                instance.version(),
                instance.nodes().stream().map(DesiredNodeWithStatusSerializationTests::mutateDesiredNodeWithStatus).toList()
            );
            default -> throw new IllegalStateException("Unexpected value: " + mutationBranch);
        };
    }
}
