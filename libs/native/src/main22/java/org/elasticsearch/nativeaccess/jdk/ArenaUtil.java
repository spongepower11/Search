/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.nativeaccess.jdk;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.Charset;

public class ArenaUtil {

    static MemorySegment allocateFrom(Arena arena, ValueLayout.OfChar layout, char value) {
        return arena.allocateFrom(layout, value);
    }

    static MemorySegment allocateFrom(Arena arena, String str, Charset charset) {
        return arena.allocateFrom(str, charset);
    }

    private ArenaUtil() {}
}
