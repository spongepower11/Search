/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.connector.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.injection.guice.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.application.connector.ConnectorIndexService;

public class TransportPutConnectorAction extends HandledTransportAction<PutConnectorAction.Request, ConnectorCreateActionResponse> {

    protected final ConnectorIndexService connectorIndexService;

    @Inject
    public TransportPutConnectorAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(
            PutConnectorAction.NAME,
            transportService,
            actionFilters,
            PutConnectorAction.Request::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.connectorIndexService = new ConnectorIndexService(client);
    }

    @Override
    protected void doExecute(Task task, PutConnectorAction.Request request, ActionListener<ConnectorCreateActionResponse> listener) {
        connectorIndexService.createConnector(
            request.getConnectorId(),
            request.getDescription(),
            request.getIndexName(),
            request.getIsNative(),
            request.getLanguage(),
            request.getName(),
            request.getServiceType(),
            listener
        );
    }
}
