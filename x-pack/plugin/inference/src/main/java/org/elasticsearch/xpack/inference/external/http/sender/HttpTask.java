/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.external.http.sender;

import org.apache.http.client.protocol.HttpClientContext;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;

public abstract class HttpTask extends AbstractRunnable {
    protected HttpClientContext context;

    public boolean shouldShutdown() {
        return false;
    }

    public void setContext(HttpClientContext context) {
        this.context = context;
    }
}
