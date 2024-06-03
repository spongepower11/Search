/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.core.type;

import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.test.AbstractNamedWriteableTestCase;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public abstract class AbstractEsFieldTypeTests<T extends EsField> extends AbstractNamedWriteableTestCase<EsField> {
    @Override
    protected abstract T createTestInstance();

    protected abstract T mutate(T instance) throws IOException;

    /**
     * Generate sub-properties.
     * @param maxDepth the maximum number of levels of properties to make
     */
    static Map<String, EsField> randomProperties(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("depth must be >= 0");
        }
        if (maxDepth == 0 || randomBoolean()) {
            return Map.of();
        }
        int targetSize = between(1, 5);
        Map<String, EsField> properties = new TreeMap<>();
        while (properties.size() < targetSize) {
            properties.put(randomAlphaOfLength(properties.size() + 1), switch (between(0, 5)) {
                case 0 -> EsFieldTests.randomEsField(maxDepth - 1);
                case 1 -> DateEsFieldTests.randomDateEsField(maxDepth - 1);
                case 2 -> InvalidMappedFieldTests.randomInvalidMappedField(maxDepth - 1);
                case 3 -> KeywordEsFieldTests.randomKeywordEsField(maxDepth - 1);
                case 4 -> TextEsFieldTests.randomTextEsField(maxDepth - 1);
                case 5 -> UnsupportedEsFieldTests.randomUnsupportedEsField(maxDepth - 1);
                default -> throw new IllegalArgumentException();
            });
        }
        return properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final T mutateInstance(EsField instance) throws IOException {
        return mutate((T) instance);
    }

    @Override
    protected final NamedWriteableRegistry getNamedWriteableRegistry() {
        return new NamedWriteableRegistry(EsField.getNamedWriteables());
    }

    @Override
    protected final Class<EsField> categoryClass() {
        return EsField.class;
    }
}
