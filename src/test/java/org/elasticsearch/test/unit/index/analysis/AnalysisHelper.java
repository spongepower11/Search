/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.test.unit.index.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.testng.Assert;

public class AnalysisHelper {
    public static AnalysisService createAnalysisServiceFromClassPath(String resource) {
        Settings settings = ImmutableSettings.settingsBuilder()
                .loadFromClasspath(resource).build();

        Index index = new Index("test");

        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings),
                new EnvironmentModule(new Environment(settings)), new IndicesAnalysisModule()).createInjector();

        AnalysisModule analysisModule = new AnalysisModule(settings,
                parentInjector.getInstance(IndicesAnalysisService.class));

        Injector injector = new ModulesBuilder().add(new IndexSettingsModule(index, settings),
                new IndexNameModule(index), analysisModule).createChildInjector(parentInjector);

        return injector.getInstance(AnalysisService.class);
    }

    public static void assertSimpleTSOutput(TokenStream stream, String[] expected) throws IOException {
        stream.reset();
        CharTermAttribute termAttr = stream.getAttribute(CharTermAttribute.class);
        Assert.assertNotNull(termAttr);
        int i = 0;
        while (stream.incrementToken()) {
            Assert.assertTrue(i < expected.length, "got extra term: " + termAttr.toString());
            Assert.assertEquals(termAttr.toString(), expected[i], "expected different term at index " + i);
            i++;
        }
        Assert.assertEquals(i, expected.length, "not all tokens produced");
    }
}
