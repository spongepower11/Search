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


package org.elasticsearch.search.aggregations.bucket.significant.heuristics;

import com.google.common.collect.Lists;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.multibindings.Multibinder;

import java.util.List;


public class SignificantTermsHeuristicModule extends AbstractModule {

    private List<Class<? extends SignificanceHeuristicParser>> parsers = Lists.newArrayList();
    private List<SignificanceHeuristicStreams.Stream> streams = Lists.newArrayList();

    public SignificantTermsHeuristicModule() {
        registerHeuristic(DefaultHeuristic.DefaultHeuristicParser.class, DefaultHeuristic.STREAM);
        registerHeuristic(MutualInformation.MutualInformationParser.class, MutualInformation.STREAM);
    }

    public void registerHeuristic(Class<? extends SignificanceHeuristicParser> parser, SignificanceHeuristicStreams.Stream stream) {
        parsers.add(parser);
        streams.add(stream);
    }

    @Override
    protected void configure() {
        Multibinder<SignificanceHeuristicParser> parserMapBinder = Multibinder.newSetBinder(binder(), SignificanceHeuristicParser.class);
        for (Class<? extends SignificanceHeuristicParser> clazz : parsers) {
            parserMapBinder.addBinding().to(clazz);
        }
        bind(SignificanceHeuristicParserMapper.class);
        for (SignificanceHeuristicStreams.Stream stream : streams) {
            SignificanceHeuristicStreams.registerStream(stream, stream.getNames());
        }
    }
}
