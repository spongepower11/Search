/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.spatial.index.fielddata;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.elasticsearch.geometry.GeometryCollection;
import org.elasticsearch.geometry.ShapeType;

import java.io.IOException;

/**
 * Like {@link ShapeType} but has specific
 * types for when the geometry is a {@link GeometryCollection} and
 * more information about what the highest-dimensional sub-shape
 * is.
 */
public enum DimensionalShapeType {
    POINT,
    LINE,
    POLYGON;

    private static final DimensionalShapeType[] values = values();

    public static DimensionalShapeType fromOrdinalByte(byte ordinal) {
        return values[Byte.toUnsignedInt(ordinal)];
    }

    public void writeTo(DataOutput out) throws IOException {
        out.writeByte((byte) ordinal());
    }

    public static DimensionalShapeType readFrom(DataInput in) throws IOException {
        return fromOrdinalByte(in.readByte());
    }
}
