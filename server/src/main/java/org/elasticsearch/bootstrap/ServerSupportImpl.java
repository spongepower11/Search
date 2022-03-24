/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.bootstrap;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.logging.HeaderWarning;
import org.elasticsearch.common.logging.NodeAndClusterIdStateListener;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.logging.internal.spi.ServerSupport;

/* SPI for logging support. */
public class ServerSupportImpl implements ServerSupport {

    private static Settings settings;

    // -- Header Warning
    @Override
    public void addHeaderWarning(String message, Object... params) {
        HeaderWarning.addWarning(message, params);
    }

    @Override
    public String getXOpaqueIdHeader() {
        return HeaderWarning.getXOpaqueId();
    }

    @Override
    public String getProductOriginHeader() {
        return HeaderWarning.getProductOrigin();
    }

    @Override
    public String getTraceIdHeader() {
        return HeaderWarning.getTraceId();
    }

    // --

    @Override
    public String nodeId() {
        return NodeAndClusterIdStateListener.getNodeIdAndClusterId().v1();
    }

    @Override
    public String clusterId() {
        return NodeAndClusterIdStateListener.getNodeIdAndClusterId().v2();
    }
    // -- settings

    @Override
    public String getClusterNameSettingValue() {
        return ClusterName.CLUSTER_NAME_SETTING.get(settings).value();
        // Node.NODE_NAME_SETTING.get(settings));
    }

    @Override
    public String getNodeNameSettingValue() {
        return null;
    }
}
