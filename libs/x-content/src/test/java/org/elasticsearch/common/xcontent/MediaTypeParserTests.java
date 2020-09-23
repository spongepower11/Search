/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.xcontent;

import org.elasticsearch.test.ESTestCase;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class MediaTypeParserTests extends ESTestCase {

    MediaTypeParser<XContentType> mediaTypeParser = new MediaTypeParser.Builder<XContentType>()
        .withMediaTypeAndParams("application/vnd.elasticsearch+json",
            XContentType.JSON, Map.of("compatible-with", Pattern.compile("\\d+"),
            "charset", Pattern.compile("UTF-8")))
        .build();

    public void testJsonWithParameters() throws Exception {
        String mediaType = "application/vnd.elasticsearch+json";
        assertThat(mediaTypeParser.parseMediaType(mediaType).getParameters(),
            equalTo(Collections.emptyMap()));
        assertThat(mediaTypeParser.parseMediaType(mediaType + ";").getParameters(),
            equalTo(Collections.emptyMap()));
        assertThat(mediaTypeParser.parseMediaType(mediaType + "; charset=UTF-8").getParameters(),
            equalTo(Map.of("charset", "utf-8")));
        assertThat(mediaTypeParser.parseMediaType(mediaType + "; compatible-with=123;charset=UTF-8").getParameters(),
            equalTo(Map.of("charset", "utf-8", "compatible-with", "123")));
    }

    public void testWhiteSpaceInTypeSubtype() {
        String mediaType = " application/vnd.elasticsearch+json ";
        assertThat(mediaTypeParser.parseMediaType(mediaType).getMediaType(),
            equalTo(XContentType.JSON));

        assertThat(mediaTypeParser.parseMediaType(mediaType + "; compatible-with=123; charset=UTF-8").getParameters(),
            equalTo(Map.of("charset", "utf-8", "compatible-with", "123")));
        assertThat(mediaTypeParser.parseMediaType(mediaType + "; compatible-with=123;\n charset=UTF-8").getParameters(),
            equalTo(Map.of("charset", "utf-8", "compatible-with", "123")));

       String  mediaTypeWithWhitespace = " application / json ";
        expectThrows(IllegalArgumentException.class , () -> mediaTypeParser.parseMediaType(mediaTypeWithWhitespace));
    }

    public void testInvalidParameters() {
        String mediaType = "application/vnd.elasticsearch+json";
        expectThrows(IllegalArgumentException.class , () -> mediaTypeParser.parseMediaType(mediaType + "; keyvalueNoEqualsSign"));

        expectThrows(IllegalArgumentException.class , () -> mediaTypeParser.parseMediaType(mediaType + "; key = value"));

        expectThrows(IllegalArgumentException.class , () -> mediaTypeParser.parseMediaType(mediaType + "; key="));
    }
}
