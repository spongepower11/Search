/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.application.analytics.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.application.analytics.AnalyticsCollectionService;
import org.elasticsearch.xpack.application.utils.LicenseUtils;

public class TransportPutAnalyticsCollectionAction extends TransportMasterNodeAction<
    PutAnalyticsCollectionAction.Request,
    PutAnalyticsCollectionAction.Response> {

    private final AnalyticsCollectionService analyticsCollectionService;

    private final XPackLicenseState licenseState;

    @Inject
    public TransportPutAnalyticsCollectionAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver,
        AnalyticsCollectionService analyticsCollectionService,
        XPackLicenseState licenseState
    ) {
        super(
            PutAnalyticsCollectionAction.NAME,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            PutAnalyticsCollectionAction.Request::new,
            indexNameExpressionResolver,
            PutAnalyticsCollectionAction.Response::new,
            ThreadPool.Names.SAME
        );
        this.analyticsCollectionService = analyticsCollectionService;
        this.licenseState = licenseState;
    }

    @Override
    protected ClusterBlockException checkBlock(PutAnalyticsCollectionAction.Request request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(
        Task task,
        PutAnalyticsCollectionAction.Request request,
        ClusterState state,
        ActionListener<PutAnalyticsCollectionAction.Response> listener
    ) {
        LicenseUtils.runIfSupportedLicense(
            licenseState,
            () -> analyticsCollectionService.putAnalyticsCollection(state, request, listener),
            listener::onFailure
        );
    }

}
