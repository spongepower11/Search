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
import org.elasticsearch.index.seqno.SequenceNumbers;

import java.io.IOException;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * An {@link IndexDeletionPolicy} keeps the latest (eg. youngest) commit whose local checkpoint is not
 * greater than the current global checkpoint, and also keeps all subsequent commits. Once those
 * commits are kept, a {@link CombinedDeletionPolicy} will retain translog operations at least up to
 * the current global checkpoint.
 */
public final class KeepUntilGlobalCheckpointDeletionPolicy extends IndexDeletionPolicy {
    private final LongSupplier globalCheckpointSupplier;

    public KeepUntilGlobalCheckpointDeletionPolicy(LongSupplier globalCheckpointSupplier) {
        this.globalCheckpointSupplier = globalCheckpointSupplier;
    }

    @Override
    public void onInit(List<? extends IndexCommit> commits) throws IOException {
        onCommit(commits);
    }

    @Override
    public void onCommit(List<? extends IndexCommit> commits) throws IOException {
        final long globalCheckpoint = globalCheckpointSupplier.getAsLong();
        for (int i = commits.size() - 1; i >= 0; i--) {
            if (localCheckpoint(commits.get(i)) <= globalCheckpoint) {
                i--; // This is the youngest commit whose local checkpoint <= global checkpoint - reserve it, then delete all previous ones.
                for (; i >= 0; i--) {
                    commits.get(i).delete();
                }
                break;
            }
        }
    }

    private static long localCheckpoint(IndexCommit commit) throws IOException {
        return Long.parseLong(commit.getUserData().get(SequenceNumbers.LOCAL_CHECKPOINT_KEY));
    }
}
