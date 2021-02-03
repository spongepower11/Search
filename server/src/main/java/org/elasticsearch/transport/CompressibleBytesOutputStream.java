/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.transport;

import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressorFactory;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.io.stream.BytesStream;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * This class exists to provide a stream with optional compression. This is useful as using compression
 * requires that the underlying {@link DeflaterOutputStream} be closed to write EOS bytes. However, the
 * {@link BytesStream} should not be closed yet, as we have not used the bytes. This class handles these
 * intricacies.
 *
 * {@link CompressibleBytesOutputStream#materializeBytes()} should be called when all the bytes have been
 * written to this stream. If compression is enabled, the proper EOS bytes will be written at that point.
 * The underlying {@link BytesReference} will be returned.
 *
 * {@link CompressibleBytesOutputStream#close()} will NOT close the underlying stream. The byte stream passed
 * in the constructor must be closed individually.
 */
final class CompressibleBytesOutputStream extends StreamOutput {

    private final OutputStream stream;
    private final BytesStream bytesStreamOutput;
    private final boolean shouldCompress;

    CompressibleBytesOutputStream(BytesStream bytesStreamOutput, boolean shouldCompress) throws IOException {
        this.bytesStreamOutput = bytesStreamOutput;
        this.shouldCompress = shouldCompress;
        if (shouldCompress) {
            this.stream = CompressorFactory.COMPRESSOR.threadLocalOutputStream(Streams.flushOnCloseStream(bytesStreamOutput));
        } else {
            this.stream = bytesStreamOutput;
        }
    }

    /**
     * This method ensures that compression is complete and returns the underlying bytes.
     *
     * @return bytes underlying the stream
     * @throws IOException if an exception occurs when writing or flushing
     */
    BytesReference materializeBytes() throws IOException {
        // If we are using compression the stream needs to be closed to ensure that EOS marker bytes are written.
        // The actual ReleasableBytesStreamOutput will not be closed yet as it is wrapped in flushOnCloseStream when
        // passed to the deflater stream.
        if (shouldCompress) {
            stream.close();
        }

        return bytesStreamOutput.bytes();
    }

    @Override
    public void writeByte(byte b) throws IOException {
        stream.write(b);
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        stream.write(b, offset, length);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() throws IOException {
        if (stream != bytesStreamOutput) {
            assert shouldCompress : "If the streams are different we should be compressing";
            IOUtils.close(stream);
        }
    }

    @Override
    public void reset() throws IOException {
        throw new UnsupportedOperationException();
    }
}
