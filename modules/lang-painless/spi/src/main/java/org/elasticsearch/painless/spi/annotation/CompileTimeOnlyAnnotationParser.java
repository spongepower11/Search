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

package org.elasticsearch.painless.spi.annotation;

import java.util.Map;

/**
 * Methods annotated with {@link CompileTimeOnlyAnnotation} must be run at
 * compile time so their arguments must all be constant and they produce a
 * constant.
 */
public class CompileTimeOnlyAnnotationParser implements WhitelistAnnotationParser {

    public static final CompileTimeOnlyAnnotationParser INSTANCE = new CompileTimeOnlyAnnotationParser();

    private CompileTimeOnlyAnnotationParser() {}

    @Override
    public Object parse(Map<String, String> arguments) {
        if (arguments.isEmpty() == false) {
            throw new IllegalArgumentException(
                "unexpected parameters for [@" + CompileTimeOnlyAnnotation.NAME + "] annotation, found " + arguments
            );
        }

        return CompileTimeOnlyAnnotation.INSTANCE;
    }
}
