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

package org.elasticsearch.painless.lookup;

public class PainlessCast {

    /** Create a standard cast with no boxing/unboxing. */
    public static PainlessCast originalTypetoTargetType(Class<?> originalType, Class<?> targetType, boolean explicitCast) {
        return new PainlessCast(originalType, targetType, explicitCast, null, null, null, null);
    }

    /** Create a cast where the original type will be unboxed, and then the cast will be performed. */
    public static PainlessCast unboxOriginalType(
            Class<?> originalType, Class<?> targetType, boolean explicitCast, Class<?> unboxOriginalType) {

        return new PainlessCast(originalType, targetType, explicitCast, unboxOriginalType, null, null, null);
    }

    /** Create a cast where the target type will be unboxed, and then the cast will be performed. */
    public static PainlessCast unboxTargetType(
            Class<?> originalType, Class<?> targetType, boolean explicitCast, Class<?> unboxTargetType) {

        return new PainlessCast(originalType, targetType, explicitCast, null, unboxTargetType, null, null);
    }

    /** Create a cast where the original type will be boxed, and then the cast will be performed. */
    public static PainlessCast boxOriginalType(
            Class<?> originalType, Class<?> targetType, boolean explicitCast, Class<?> boxOriginalType) {

        return new PainlessCast(originalType, targetType, explicitCast, null, null, boxOriginalType, null);
    }

    /** Create a cast where the target type will be boxed, and then the cast will be performed. */
    public static PainlessCast boxTargetType(
            Class<?> originalType, Class<?> targetType, boolean explicitCast, Class<?> boxTargetType) {

        return new PainlessCast(originalType, targetType, explicitCast, null, null, null, boxTargetType);
    }

    public final Class<?> originalType;
    public final Class<?> targetType;
    public final boolean explicitCast;
    public final Class<?> unboxOriginalType;
    public final Class<?> unboxTargetType;
    public final Class<?> boxOriginalType;
    public final Class<?> boxTargetType;

    private PainlessCast(Class<?> originalType, Class<?> targetType, boolean explicitCast,
            Class<?> unboxOriginalType, Class<?> unboxTargetType, Class<?> boxOriginalType, Class<?> boxTargetType) {

        this.originalType = originalType;
        this.targetType = targetType;
        this.explicitCast = explicitCast;
        this.unboxOriginalType = unboxOriginalType;
        this.unboxTargetType = unboxTargetType;
        this.boxOriginalType = boxOriginalType;
        this.boxTargetType = boxTargetType;
    }
}
