/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package org.elasticsearch.action.bulk;

import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.action.support.replication.TransportWriteAction;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.translog.Translog;

import java.util.Arrays;

/**
 * This is a utility class that holds the per request state needed to perform bulk operations on the primary.
 * More specifically, it maintains an index to the current executing bulk item, which allows execution
 * to stop and wait for external events such as mapping updates.
 */
class BulkExecutionContext implements Releasable {

    enum ItemProcessingState {
        /** Item execution is ready to start, no operations have been performed yet */
        INITIAL,
        /**
         * The incoming request has been translated to a request that can be executed on the shard.
         * This is used to convert update requests to a fully specified index or delete requests.
         */
        TRANSLATED,
        /**
         * the request can not execute with the current mapping and should wait for a new mapping
         * to arrive from the master. A mapping request for the needed changes has already been
         * submitted
         */
        WAIT_FOR_MAPPING_UPDATE,
        /** The request has been executed on the primary shard (successfully or not) */
        INDEXED,
        /**
         * No further handling of current request is needed. The result has been converted to a user response
         * and execution can continue to the next item (if available).
         */
        COMPLETED
    }

    private final BulkShardRequest request;
    private final IndexShard primary;
    private Translog.Location locationToSync = null;
    private int currentIndexIndex = -1;
    private int currentWriteIndex = -1;

    private ItemProcessingState currentItemState;
    private DocWriteRequest requestToIndex;
    private BulkItemResponse executionResult;
    private IndexShard.OperationContext[] indexContexts;
    private int retryCounter;


    BulkExecutionContext(BulkShardRequest request, IndexShard primary) {
        this.request = request;
        this.indexContexts = new IndexShard.OperationContext[request.items().length];
        this.primary = primary;
        advance();
        advanceWritten();
    }


    private int findNextNonAborted(int startIndex) {
        final int length = request.items().length;
        while (startIndex < length && isAborted(request.items()[startIndex].getPrimaryResponse())) {
            startIndex++;
        }
        return startIndex;
    }

    private int findNextNeedsWrite(int startIndex) {
        while (startIndex < currentIndexIndex && indexContexts[startIndex] == null) {
            startIndex++;
        }
        return startIndex;
    }

    private static boolean isAborted(BulkItemResponse response) {
        return response != null && response.isFailed() && response.getFailure().isAborted();
    }

    /** move to the next item to execute */
    private void advance() {
        assert currentItemState == ItemProcessingState.COMPLETED || currentIndexIndex == -1 :
            "moving to next but current item wasn't completed (state: " + currentItemState + ")";
        currentItemState = ItemProcessingState.INITIAL;
        currentIndexIndex =  findNextNonAborted(currentIndexIndex + 1);
        retryCounter = 0;
        requestToIndex = null;
        executionResult = null;
        assert assertInvariants(ItemProcessingState.INITIAL);
    }

    private void advanceWritten() {
        currentWriteIndex = findNextNeedsWrite(currentWriteIndex + 1);
    }

    /** gets the current, untranslated item request */
    public DocWriteRequest<?> getCurrent() {
        return getCurrentIndexItem().request();
    }

    public IndexShard.OperationContext getCurrentWrite() {
        return getCurrentWriteItem();
    }

    public BulkShardRequest getBulkShardRequest() {
        return request;
    }

    /** returns the result of the request that has been executed on the shard */
    public BulkItemResponse getExecutionResult() {
        assert assertInvariants(ItemProcessingState.INDEXED);
        return executionResult;
    }

    /** returns the number of times the current operation has been retried */
    public int getRetryCounter() {
        return retryCounter;
    }

    /** returns true if the current request has been executed on the primary */
    public boolean isOperationExecuted() {
        return currentItemState == ItemProcessingState.INDEXED;
    }

    /** returns true if the request needs to wait for a mapping update to arrive from the master */
    public boolean requiresWaitingForMappingUpdate() {
        return currentItemState == ItemProcessingState.WAIT_FOR_MAPPING_UPDATE;
    }

    /**
     * returns true if the current request has been completed and it's result translated to a user
     * facing response
     */
    public boolean isCompleted() {
        return currentItemState == ItemProcessingState.COMPLETED;
    }

    /**
     * returns true if the current request is in INITIAL state
     */
    public boolean isInitial() {
        return currentItemState == ItemProcessingState.INITIAL;
    }

    /**
     * returns true if {@link #advance()} has moved the current item beyond the
     * end of the {@link BulkShardRequest#items()} array.
     */
    public boolean hasMoreOperationsToIndex() {
        return currentIndexIndex < request.items().length;
    }

    /** returns the name of the index the current request used */
    public String getConcreteIndex() {
        return getCurrentIndexItem().index();
    }

    /** returns a translog location that is needed to be synced in order to persist all operations executed so far */
    public Translog.Location getLocationToSync() {
        assert hasMoreOperationsToIndex() == false;
        // we always get to the end of the list by using advance, which in turn sets the state to INITIAL
        assert assertInvariants(ItemProcessingState.INITIAL);
        return locationToSync;
    }

    private BulkItemRequest getCurrentIndexItem() {
        return request.items()[currentIndexIndex];
    }

    private IndexShard.OperationContext getCurrentWriteItem() {
        return indexContexts[currentWriteIndex];
    }

    /** returns the primary shard */
    public IndexShard getPrimary() {
        return primary;
    }

    /**
     * sets the request that should actually be executed on the primary. This can be different then the request
     * received from the user (specifically, an update request is translated to an indexing or delete request).
     */
    public void setRequestToIndex(DocWriteRequest writeRequest) {
        assert assertInvariants(ItemProcessingState.INITIAL);
        requestToIndex = writeRequest;
        currentItemState = ItemProcessingState.TRANSLATED;
        assert assertInvariants(ItemProcessingState.TRANSLATED);
    }

    /** returns the request that should be executed on the shard. */
    public <T extends DocWriteRequest<T>> T getRequestToIndex() {
        assert assertInvariants(ItemProcessingState.TRANSLATED);
        return (T) requestToIndex;
    }

    /** indicates that the current operation can not be completed and needs to wait for a new mapping from the master */
    public void markAsRequiringMappingUpdate() {
        assert assertInvariants(ItemProcessingState.TRANSLATED);
        currentItemState = ItemProcessingState.WAIT_FOR_MAPPING_UPDATE;
        requestToIndex = null;
        assert assertInvariants(ItemProcessingState.WAIT_FOR_MAPPING_UPDATE);
    }

    /** resets the current item state, prepare for a new execution */
    public void resetForExecutionForRetry() {
        assertInvariants(ItemProcessingState.WAIT_FOR_MAPPING_UPDATE, ItemProcessingState.INDEXED);
        currentItemState = ItemProcessingState.INITIAL;
        requestToIndex = null;
        executionResult = null;
        assertInvariants(ItemProcessingState.INITIAL);
    }

    /** completes the operation without doing anything on the primary */
    public void markOperationAsNoOp(DocWriteResponse response) {
        assertInvariants(ItemProcessingState.INITIAL);
        executionResult = new BulkItemResponse(getCurrentIndexItem().id(), getCurrentIndexItem().request().opType(), response);
        currentItemState = ItemProcessingState.INDEXED;
        assertInvariants(ItemProcessingState.INDEXED);
    }

    /** indicates that the operation needs to be failed as the required mapping didn't arrive in time */
    public void failOnMappingUpdate(Exception cause) {
        assert assertInvariants(ItemProcessingState.WAIT_FOR_MAPPING_UPDATE);
        currentItemState = ItemProcessingState.INDEXED;
        final DocWriteRequest docWriteRequest = getCurrentIndexItem().request();
        executionResult = new BulkItemResponse(getCurrentIndexItem().id(), docWriteRequest.opType(),
            // Make sure to use getCurrentItem().index() here, if you use docWriteRequest.index() it will use the
            // concrete index instead of an alias if used!
            new BulkItemResponse.Failure(getCurrentIndexItem().index(), docWriteRequest.id(), cause));
        markAsIndexedComplete(executionResult, null);
    }

    /** the current operation has been executed on the primary with the specified result */
    public void markOperationAsIndexed(Engine.Result result) {
        assertInvariants(ItemProcessingState.TRANSLATED);
        final BulkItemRequest current = getCurrentIndexItem();
        DocWriteRequest docWriteRequest = getRequestToIndex();
        switch (result.getResultType()) {
            case SUCCESS:
                final DocWriteResponse response;
                if (result.getOperationType() == Engine.Operation.TYPE.INDEX) {
                    Engine.IndexResult indexResult = (Engine.IndexResult) result;
                    response = new IndexResponse(primary.shardId(), requestToIndex.id(),
                        result.getSeqNo(), result.getTerm(), indexResult.getVersion(), indexResult.isCreated());
                } else if (result.getOperationType() == Engine.Operation.TYPE.DELETE) {
                    Engine.DeleteResult deleteResult = (Engine.DeleteResult) result;
                    response = new DeleteResponse(primary.shardId(), requestToIndex.id(),
                        deleteResult.getSeqNo(), result.getTerm(), deleteResult.getVersion(), deleteResult.isFound());

                } else {
                    throw new AssertionError("unknown result type :" + result.getResultType());
                }
                executionResult = new BulkItemResponse(current.id(), current.request().opType(), response);
                // set a blank ShardInfo so we can safely send it to the replicas. We won't use it in the real response though.
                executionResult.getResponse().setShardInfo(new ReplicationResponse.ShardInfo());
                break;
            case FAILURE:
                executionResult = new BulkItemResponse(current.id(), docWriteRequest.opType(),
                    // Make sure to use request.index() here, if you
                    // use docWriteRequest.index() it will use the
                    // concrete index instead of an alias if used!
                    new BulkItemResponse.Failure(request.index(), docWriteRequest.id(),
                        result.getFailure(), result.getSeqNo(), result.getTerm()));
                break;
            default:
                throw new AssertionError("unknown result type for " + getCurrentIndexItem() + ": " + result.getResultType());
        }
        currentItemState = ItemProcessingState.INDEXED;
    }

    public boolean hasPendingWrites() {
        return currentIndexIndex != currentWriteIndex;
    }

    public void markOperationAsWritten(Translog.Location location) {
        if (location != null) {
            locationToSync = TransportWriteAction.locationToSync(locationToSync, location);
        }
        advanceWritten();
    }

    /** finishes the execution of the current request, with the response that should be returned to the user */
    public void markAsIndexedComplete(BulkItemResponse translatedResponse, IndexShard.OperationContext context) {
        assertInvariants(ItemProcessingState.INDEXED);
        assert executionResult != null && translatedResponse.getItemId() == executionResult.getItemId();
        assert translatedResponse.getItemId() == getCurrentIndexItem().id();

        if (translatedResponse.isFailed() == false && requestToIndex != null && requestToIndex != getCurrent())  {
            request.items()[currentIndexIndex] = new BulkItemRequest(request.items()[currentIndexIndex].id(), requestToIndex);
        }
        indexContexts[currentIndexIndex] = context;
        getCurrentIndexItem().setPrimaryResponse(translatedResponse);
        currentItemState = ItemProcessingState.COMPLETED;
        advance();
    }

    /** builds the bulk shard response to return to the user */
    public BulkShardResponse buildShardResponse() {
        assert hasMoreOperationsToIndex() == false;
        return new BulkShardResponse(request.shardId(),
            Arrays.stream(request.items()).map(BulkItemRequest::getPrimaryResponse).toArray(BulkItemResponse[]::new));
    }

    @Override
    public void close() {
        Releasables.close(indexContexts);
    }

    private boolean assertInvariants(ItemProcessingState... expectedCurrentState) {
        assert Arrays.asList(expectedCurrentState).contains(currentItemState):
            "expected current state [" + currentItemState + "] to be one of " + Arrays.toString(expectedCurrentState);
        assert currentIndexIndex >= 0 : currentIndexIndex;
        assert retryCounter >= 0 : retryCounter;
        switch (currentItemState) {
            case INITIAL:
                assert requestToIndex == null : requestToIndex;
                assert executionResult == null : executionResult;
                break;
            case TRANSLATED:
                assert requestToIndex != null;
                assert executionResult == null : executionResult;
                break;
            case WAIT_FOR_MAPPING_UPDATE:
                assert requestToIndex == null;
                assert executionResult == null : executionResult;
                break;
            case INDEXED:
                // requestToExecute can be null if the update ended up as NOOP
                assert executionResult != null;
                break;
            case COMPLETED:
                assert requestToIndex != null;
                assert executionResult != null;
                assert getCurrentIndexItem().getPrimaryResponse() != null;
                break;
        }
        return true;
    }
}
