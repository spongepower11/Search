/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.common.util;

import net.jpountz.util.Utils;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.RamUsageEstimator;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

import static org.elasticsearch.common.util.PageCacheRecycler.INT_PAGE_SIZE;

/**
 * Int array abstraction able to support more than 2B values. This implementation slices data into fixed-sized blocks of
 * configurable length.
 */
final class BigIntArray extends AbstractBigArray implements IntArray {

    private static final BigIntArray ESTIMATOR = new BigIntArray(0, BigArrays.NON_RECYCLING_INSTANCE, false);

    private static final VarHandle intPlatformNative = MethodHandles.byteArrayViewVarHandle(int[].class, Utils.NATIVE_BYTE_ORDER);

    private byte[][] pages;

    /** Constructor. */
    BigIntArray(long size, BigArrays bigArrays, boolean clearOnResize) {
        super(INT_PAGE_SIZE, bigArrays, clearOnResize);
        this.size = size;
        pages = new byte[numPages(size)][];
        for (int i = 0; i < pages.length; ++i) {
            pages[i] = newBytePage(i);
        }
    }

    @Override
    public int get(long index) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        return (int) intPlatformNative.get(pages[pageIndex], indexInPage);
    }

    @Override
    public int set(long index, int value) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        final byte[] page = pages[pageIndex];
        final int ret = (int) intPlatformNative.get(page, indexInPage);
        intPlatformNative.set(page, indexInPage, value);
        return ret;
    }

    @Override
    public int increment(long index, int inc) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        final byte[] page = pages[pageIndex];
        final int newVal = (int) intPlatformNative.get(page, indexInPage) + inc;
        intPlatformNative.set(page, newVal);
        return newVal;
    }

    @Override
    public void fill(long fromIndex, long toIndex, int value) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException();
        }
        final int fromPage = pageIndex(fromIndex);
        final int toPage = pageIndex(toIndex - 1);
        if (fromPage == toPage) {
            fill(pages[fromPage], indexInPage(fromIndex), indexInPage(toIndex - 1) + 1, value);
        } else {
            fill(pages[fromPage], indexInPage(fromIndex), pageSize(), value);
            for (int i = fromPage + 1; i < toPage; ++i) {
                fill(pages[i], 0, pageSize(), value);
            }
            fill(pages[toPage], 0, indexInPage(toIndex - 1) + 1, value);
        }
    }

    public static void fill(byte[] page, int from, int to, long value) {
        for (int i = from; i < to; i++) {
            intPlatformNative.set(page, i, value);
        }
    }

    @Override
    protected int numBytesPerElement() {
        return Integer.BYTES;
    }

    /** Change the size of this array. Content between indexes <code>0</code> and <code>min(size(), newSize)</code> will be preserved. */
    @Override
    public void resize(long newSize) {
        final int numPages = numPages(newSize);
        if (numPages > pages.length) {
            pages = Arrays.copyOf(pages, ArrayUtil.oversize(numPages, RamUsageEstimator.NUM_BYTES_OBJECT_REF));
        }
        for (int i = numPages - 1; i >= 0 && pages[i] == null; --i) {
            pages[i] = newBytePage(i);
        }
        for (int i = numPages; i < pages.length && pages[i] != null; ++i) {
            pages[i] = null;
            releasePage(i);
        }
        this.size = newSize;
    }

    /** Estimates the number of bytes that would be consumed by an array of the given size. */
    public static long estimateRamBytes(final long size) {
        return ESTIMATOR.ramBytesEstimated(size);
    }

}
