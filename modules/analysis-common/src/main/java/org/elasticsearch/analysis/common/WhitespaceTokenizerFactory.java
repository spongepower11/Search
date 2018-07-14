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

package org.elasticsearch.analysis.common;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;

public class WhitespaceTokenizerFactory extends AbstractTokenizerFactory {

    static final String MAX_TOKEN_LENGTH = "max_token_length";
    private Integer maxTokenLength;

    WhitespaceTokenizerFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, settings);
        maxTokenLength = settings.getAsInt(MAX_TOKEN_LENGTH, StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH);
    }

    @Override
    public Tokenizer get() {
        return new WhitespaceTokenizer(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY, maxTokenLength);
    }
}
