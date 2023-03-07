/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.rank.rrf;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.search.rank.RankContextBuilderTests;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;

public class RRFRankContextBuilderTests extends RankContextBuilderTests<RRFRankContextBuilder> {

    @Override
    protected RRFRankContextBuilder doCreateTestInstance() {
        RRFRankContextBuilder builder = new RRFRankContextBuilder();
        builder.rankConstant(randomIntBetween(1, Integer.MAX_VALUE));
        return builder;
    }

    @Override
    protected RRFRankContextBuilder doMutateInstance(RRFRankContextBuilder instance) throws IOException {
        RRFRankContextBuilder builder = new RRFRankContextBuilder();
        if (randomBoolean()) {
            builder.rankConstant(instance.rankConstant() == 1 ? 2 : instance.rankConstant() - 1);
        }
        return builder;
    }

    @Override
    protected Writeable.Reader<RRFRankContextBuilder> instanceReader() {
        return RRFRankContextBuilder::new;
    }

    @Override
    protected RRFRankContextBuilder doParseInstance(XContentParser parser) throws IOException {
        parser.nextToken();
        assertEquals(parser.currentToken(), XContentParser.Token.START_OBJECT);
        parser.nextToken();
        assertEquals(parser.currentToken(), XContentParser.Token.FIELD_NAME);
        assertEquals(parser.currentName(), RRFRankContextBuilder.NAME);
        RRFRankContextBuilder builder = RRFRankContextBuilder.fromXContent(parser);
        parser.nextToken();
        assertEquals(parser.currentToken(), XContentParser.Token.END_OBJECT);
        parser.nextToken();
        assertNull(parser.currentToken());
        return builder;
    }
}
