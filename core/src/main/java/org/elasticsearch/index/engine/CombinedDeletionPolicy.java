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

package org.elasticsearch.index.engine;

import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.index.translog.TranslogDeletionPolicy;
import org.elasticsearch.index.translog.TranslogReader;
import org.elasticsearch.index.translog.TranslogWriter;

import java.io.IOException;
import java.util.List;

public class CombinedDeletionPolicy extends SnapshotDeletionPolicy implements org.elasticsearch.index.translog.DeletionPolicy {

    public CombinedDeletionPolicy() {
        this(new KeepOnlyLastCommitDeletionPolicy());
    }

    CombinedDeletionPolicy(IndexDeletionPolicy indexDeletionPolicy) {
        super(indexDeletionPolicy);
        translogDeletionPolicy = new TranslogDeletionPolicy();
    }

    private final TranslogDeletionPolicy translogDeletionPolicy;

    @Override
    public synchronized void onInit(List<? extends IndexCommit> commits) throws IOException {
        super.onInit(commits);
        setLastCommittedTranslogGeneration(commits);
    }
    @Override
    public synchronized void onTranslogRollover(List<TranslogReader> readers, TranslogWriter currentWriter) {
    }

    @Override
    public synchronized void onCommit(List<? extends IndexCommit> commits) throws IOException {
        super.onCommit(commits);
        setLastCommittedTranslogGeneration(commits);
    }

    private void setLastCommittedTranslogGeneration(List<? extends IndexCommit> commits) throws IOException {
        long minGen = Long.MAX_VALUE;
        for (IndexCommit indexCommit : commits) {
            if (indexCommit.isDeleted() == false) {
                long refGen = Long.parseLong(indexCommit.getUserData().get(Translog.TRANSLOG_GENERATION_KEY));
                minGen = Math.min(minGen, refGen);
            }
        }
    }

    @Override
    public long acquireTranslogGenForView() {
        return translogDeletionPolicy.acquireTranslogGenForView();
    }

    @Override
    public int pendingViewsCount() {
        return translogDeletionPolicy.pendingViewsCount();
    }

    @Override
    public void releaseTranslogGenView(long translogGen) {
        translogDeletionPolicy.releaseTranslogGenView(translogGen);
    }

    @Override
    public synchronized long minTranslogGenRequired(List<TranslogReader> readers, TranslogWriter currentWriter) {
        return translogDeletionPolicy.minTranslogGenRequired(readers, currentWriter);
    }

    /** returns the translog generation that will be used as a basis of a future store/peer recovery */
    @Override
    public long getMinTranslogGenerationForRecovery() {
        return translogDeletionPolicy.getMinTranslogGenerationForRecovery();
    }
}
