/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.aggregations.metrics;

import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.xcontent.ToXContentFragment;

/**
 * Generic interface for both geographic and cartesian centroid aggregations.
 */
public interface CentroidAggregation<T extends ToXContentFragment> extends Aggregation {
    T centroid();

    long count();
}
