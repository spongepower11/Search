/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.script.field.vectors;

import org.apache.lucene.index.BinaryDocValues;
import org.elasticsearch.index.mapper.vectors.DenseVectorFieldMapper.ElementType;

public class BitBinaryDenseVectorDocValuesField extends ByteBinaryDenseVectorDocValuesField {

    public BitBinaryDenseVectorDocValuesField(BinaryDocValues input, String name, ElementType elementType, int dims) {
        super(input, name, elementType, dims / 8);
    }

    @Override
    protected DenseVector getVector() {
        return new BitBinaryDenseVector(vectorValue, value, dims);
    }
}
