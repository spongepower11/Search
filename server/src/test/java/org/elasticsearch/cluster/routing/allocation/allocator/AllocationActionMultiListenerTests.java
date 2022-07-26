/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.equalTo;

public class AllocationActionMultiListenerTests extends ESTestCase {

    public void testShouldDelegateWhenBothComplete() {
        var listener = new AllocationActionMultiListener<Integer>();

        var l1 = new AtomicInteger();
        var l2 = new AtomicInteger();
        listener.delay(ActionListener.wrap(l1::set, exception -> { throw new AssertionError("Should not fail in test"); })).onResponse(1);
        listener.delay(ActionListener.wrap(l2::set, exception -> { throw new AssertionError("Should not fail in test"); })).onResponse(2);
        if (randomBoolean()) {
            listener.reroute().onResponse(null);
        } else {
            listener.noRerouteNeeded();
        }

        assertThat(l1.get(), equalTo(1));
        assertThat(l2.get(), equalTo(2));
    }

    public void testShouldNotDelegateWhenOnlyOneComplete() {
        var listener = new AllocationActionMultiListener<AcknowledgedResponse>();

        var completed = new AtomicBoolean(false);
        var delegate = listener.delay(
            ActionListener.wrap(ignore -> completed.set(true), exception -> { throw new AssertionError("Should not fail in test"); })
        );

        switch (randomInt(2)) {
            case 0 -> delegate.onResponse(AcknowledgedResponse.TRUE);
            case 1 -> listener.reroute().onResponse(null);
            case 2 -> listener.noRerouteNeeded();
        }

        assertThat(completed.get(), equalTo(false));
    }

    public void testShouldDelegateFailureImmediately() {
        var listener = new AllocationActionMultiListener<AcknowledgedResponse>();

        var completed = new AtomicBoolean(false);
        listener.delay(
            ActionListener.wrap(ignore -> { throw new AssertionError("Should not complete in test"); }, exception -> completed.set(true))
        ).onFailure(new RuntimeException());

        assertThat(completed.get(), equalTo(true));
    }

    public void testConcurrency() throws InterruptedException {

        var listener = new AllocationActionMultiListener<AcknowledgedResponse>();

        var count = randomIntBetween(1, 100);
        var completed = new CountDownLatch(count);

        var start = new CountDownLatch(3);
        var threadPool = new TestThreadPool(getTestName());

        threadPool.executor(ThreadPool.Names.CLUSTER_COORDINATION).submit(() -> {
            start.countDown();
            awaitQuietly(start);
            for (int i = 0; i < count; i++) {
                listener.delay(
                    ActionListener.wrap(
                        ignore -> completed.countDown(),
                        exception -> { throw new AssertionError("Should not fail in test"); }
                    )
                ).onResponse(AcknowledgedResponse.TRUE);
            }
        });

        threadPool.executor(ThreadPool.Names.GENERIC).submit(() -> {
            start.countDown();
            awaitQuietly(start);
            if (randomBoolean()) {
                listener.reroute().onResponse(null);
            } else {
                listener.noRerouteNeeded();
            }
        });
        start.countDown();

        assertTrue("Expected to call all delayed listeners within timeout", completed.await(10, TimeUnit.SECONDS));
        terminate(threadPool);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            assertTrue("Latch did not complete within timeout", latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError("Interrupted while waiting for test to start", e);
        }
    }
}
