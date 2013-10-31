/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.action.admin.cluster.reroute.ClusterRerouteClusterStateUpdateRequest;
import org.elasticsearch.action.admin.cluster.reroute.ClusterRerouteClusterStateUpdateResponse;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsClusterStateUpdateRequest;
import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsClusterStateUpdateResponse;
import org.elasticsearch.cluster.AckedClusterStateUpdateTask;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ack.ClusterStateUpdateListener;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.allocation.AllocationService;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.cluster.settings.ClusterDynamicSettings;
import org.elasticsearch.cluster.settings.DynamicSettings;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;

import java.util.Map;

import static org.elasticsearch.cluster.ClusterState.builder;
import static org.elasticsearch.cluster.ClusterState.newClusterStateBuilder;

/**
 * Service responsible for submitting cluster update settings and reroute operations
 */
public class MetaDataClusterService extends AbstractComponent {

    private final ClusterService clusterService;
    private final AllocationService allocationService;
    private final DynamicSettings dynamicSettings;

    @Inject
    public MetaDataClusterService(Settings settings, ClusterService clusterService, AllocationService allocationService, @ClusterDynamicSettings DynamicSettings dynamicSettings) {
        super(settings);
        this.clusterService = clusterService;
        this.allocationService = allocationService;
        this.dynamicSettings = dynamicSettings;
    }

    public void reroute(final ClusterRerouteClusterStateUpdateRequest request, final ClusterStateUpdateListener<ClusterRerouteClusterStateUpdateResponse> listener) {

        clusterService.submitStateUpdateTask("cluster_reroute (api)", Priority.URGENT, new AckedClusterStateUpdateTask<ClusterRerouteClusterStateUpdateResponse>(request, listener) {

            private volatile ClusterState clusterStateToSend;

            @Override
            protected ClusterRerouteClusterStateUpdateResponse newResponse(boolean acknowledged) {
                return new ClusterRerouteClusterStateUpdateResponse(acknowledged, clusterStateToSend);
            }

            @Override
            public ClusterState execute(ClusterState currentState) {
                RoutingAllocation.Result routingResult = allocationService.reroute(currentState, request.allocationCommands());
                ClusterState newState = newClusterStateBuilder().state(currentState).routingResult(routingResult).build();
                clusterStateToSend = newState;
                if (request.dryRun()) {
                    return currentState;
                }
                return newState;
            }
        });
    }

    public void updateSettings(final ClusterUpdateSettingsClusterStateUpdateRequest request, final ClusterStateUpdateListener<ClusterUpdateSettingsClusterStateUpdateResponse> listener) {

        final ImmutableSettings.Builder transientUpdates = ImmutableSettings.settingsBuilder();
        final ImmutableSettings.Builder persistentUpdates = ImmutableSettings.settingsBuilder();

        clusterService.submitStateUpdateTask("cluster_update_settings", Priority.URGENT, new AckedClusterStateUpdateTask<ClusterUpdateSettingsClusterStateUpdateResponse>(request, listener) {

            private volatile boolean changed = false;

            private ClusterUpdateSettingsClusterStateUpdateResponse buildNewResponse(boolean acknowledged) {
                return new ClusterUpdateSettingsClusterStateUpdateResponse(acknowledged)
                        .transientSettings(transientUpdates.build())
                        .persistentSettings(persistentUpdates.build());
            }

            @Override
            protected ClusterUpdateSettingsClusterStateUpdateResponse newResponse(boolean acknowledged) {
                return buildNewResponse(acknowledged);
            }

            @Override
            public void onAllNodesAcked(@Nullable Throwable t) {
                if (changed) {
                    reroute(true);
                } else {
                    super.onAllNodesAcked(t);
                }
            }

            @Override
            public void onAckTimeout() {
                if (changed) {
                    reroute(false);
                } else {
                    super.onAckTimeout();
                }
            }

            private void reroute(final boolean updateSettingsAcked) {
                //we reuse the same timeouts that were set in the cluster update settings, will cause the request to take a bit longer than expected in case of timeout
                //we reuse the original listener too as we want to return when the reroute is completed
                clusterService.submitStateUpdateTask("reroute_after_cluster_update_settings", Priority.URGENT, new AckedClusterStateUpdateTask<ClusterUpdateSettingsClusterStateUpdateResponse>(request, listener) {

                    @Override
                    protected ClusterUpdateSettingsClusterStateUpdateResponse newResponse(boolean acknowledged) {
                        return buildNewResponse(acknowledged);
                    }

                    @Override
                    public boolean mustAck(DiscoveryNode discoveryNode) {
                        //we wait for the reroute ack only if the update settings was acknowledged
                        return updateSettingsAcked;
                    }

                    @Override
                    public void onAllNodesAcked(@Nullable Throwable t) {
                        //we return when the cluster reroute is acked (the acknowledged flag depends on whether the update settings was acknowledged)
                        listener.onResponse(newResponse(updateSettingsAcked));
                    }

                    @Override
                    public void onFailure(String source, Throwable t) {
                        //if the reroute fails we only log
                        logger.debug("failed to perform [{}]", t, source);
                    }

                    @Override
                    public ClusterState execute(final ClusterState currentState) {
                        // now, reroute in case things that require it changed (e.g. number of replicas)
                        RoutingAllocation.Result routingResult = allocationService.reroute(currentState);
                        if (!routingResult.changed()) {
                            return currentState;
                        }
                        return newClusterStateBuilder().state(currentState).routingResult(routingResult).build();
                    }
                });
            }

            @Override
            public ClusterState execute(final ClusterState currentState) {
                ImmutableSettings.Builder transientSettings = ImmutableSettings.settingsBuilder();
                transientSettings.put(currentState.metaData().transientSettings());
                for (Map.Entry<String, String> entry : request.transientSettings().getAsMap().entrySet()) {
                    if (dynamicSettings.hasDynamicSetting(entry.getKey()) || entry.getKey().startsWith("logger.")) {
                        String error = dynamicSettings.validateDynamicSetting(entry.getKey(), entry.getValue());
                        if (error == null) {
                            transientSettings.put(entry.getKey(), entry.getValue());
                            transientUpdates.put(entry.getKey(), entry.getValue());
                            changed = true;
                        } else {
                            logger.warn("ignoring transient setting [{}], [{}]", entry.getKey(), error);
                        }
                    } else {
                        logger.warn("ignoring transient setting [{}], not dynamically updateable", entry.getKey());
                    }
                }

                ImmutableSettings.Builder persistentSettings = ImmutableSettings.settingsBuilder();
                persistentSettings.put(currentState.metaData().persistentSettings());
                for (Map.Entry<String, String> entry : request.persistentSettings().getAsMap().entrySet()) {
                    if (dynamicSettings.hasDynamicSetting(entry.getKey()) || entry.getKey().startsWith("logger.")) {
                        String error = dynamicSettings.validateDynamicSetting(entry.getKey(), entry.getValue());
                        if (error == null) {
                            persistentSettings.put(entry.getKey(), entry.getValue());
                            persistentUpdates.put(entry.getKey(), entry.getValue());
                            changed = true;
                        } else {
                            logger.warn("ignoring persistent setting [{}], [{}]", entry.getKey(), error);
                        }
                    } else {
                        logger.warn("ignoring persistent setting [{}], not dynamically updateable", entry.getKey());
                    }
                }

                if (!changed) {
                    return currentState;
                }

                MetaData.Builder metaData = MetaData.builder().metaData(currentState.metaData())
                        .persistentSettings(persistentSettings.build())
                        .transientSettings(transientSettings.build());

                ClusterBlocks.Builder blocks = ClusterBlocks.builder().blocks(currentState.blocks());
                boolean updatedReadOnly = metaData.persistentSettings().getAsBoolean(MetaData.SETTING_READ_ONLY, false) || metaData.transientSettings().getAsBoolean(MetaData.SETTING_READ_ONLY, false);
                if (updatedReadOnly) {
                    blocks.addGlobalBlock(MetaData.CLUSTER_READ_ONLY_BLOCK);
                } else {
                    blocks.removeGlobalBlock(MetaData.CLUSTER_READ_ONLY_BLOCK);
                }

                return builder().state(currentState).metaData(metaData).blocks(blocks).build();
            }
        });
    }
}
