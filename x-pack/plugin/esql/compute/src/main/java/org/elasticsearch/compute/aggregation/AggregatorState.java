/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.aggregation;

import org.elasticsearch.compute.data.Block;
import org.elasticsearch.core.Releasable;

public interface AggregatorState extends Releasable {

    /** Extracts an intermediate view of the contents of this state.  */
    void toIntermediate(Block[] blocks, int offset);
}
