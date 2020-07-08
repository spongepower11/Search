/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.eql.action;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.ql.expression.Order.OrderDirection;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.hamcrest.Matchers.containsString;
import static org.elasticsearch.xpack.eql.action.RequestDefaults.FIELD_DEFAULT_ORDER;

public class EqlRequestParserTests extends ESTestCase {

    private static NamedXContentRegistry registry =
        new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of()).getNamedXContents());

    public void testUnknownFieldParsingErrors() throws IOException {
        assertParsingErrorMessage("{\"key\" : \"value\"}", "unknown field [key]", EqlSearchRequest::fromXContent);
    }

    public void testSearchRequestParser() throws IOException {
        assertParsingErrorMessage("{\"filter\" : 123}", "filter doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"timestamp_field\" : 123}", "timestamp_field doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"event_category_field\" : 123}", "event_category_field doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"implicit_join_key_field\" : 123}",
            "implicit_join_key_field doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"search_after\" : 123}", "search_after doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"size\" : \"foo\"}", "failed to parse field [size]", "For input string: \"foo\"",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"query\" : 123}", "query doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"query\" : \"whatever\", \"size\":\"abc\"}", "failed to parse field [size]",
            "For input string: \"abc\"", EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"case_sensitive\" : \"whatever\"}", "failed to parse field [case_sensitive]",
            "Failed to parse value [whatever] as only [true] or [false] are allowed.", EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"default_order\" : 123}", "default_order doesn't support values of type: VALUE_NUMBER",
            EqlSearchRequest::fromXContent);
        assertParsingErrorMessage("{\"default_order\" : \"xyz\"}", "failed to parse field [default_order]",
            "invalid default_order value, expected [asc/desc] but got: [xyz]", EqlSearchRequest::fromXContent);

        boolean setIsCaseSensitive = randomBoolean();
        boolean isCaseSensitive = randomBoolean();
        boolean setDefaultOrder = randomBoolean();
        String defaultOrder = randomFrom(OrderDirection.values()).toString();

        EqlSearchRequest request = generateRequest("endgame-*", "{\"filter\" : {\"match\" : {\"foo\":\"bar\"}}, "
            + "\"timestamp_field\" : \"tsf\", "
            + "\"event_category_field\" : \"etf\","
            + "\"implicit_join_key_field\" : \"imjf\","
            + "\"search_after\" : [ 12345678, \"device-20184\", \"/user/local/foo.exe\", \"2019-11-26T00:45:43.542\" ],"
            + "\"size\" : \"101\","
            + "\"query\" : \"file where user != 'SYSTEM' by file_path\""
            + (setIsCaseSensitive ? (",\"case_sensitive\" : " + isCaseSensitive) : "")
            + (setDefaultOrder ? (",\"default_order\" : \"" + defaultOrder + "\"") : "")
            + "}", EqlSearchRequest::fromXContent);
        assertArrayEquals(new String[]{"endgame-*"}, request.indices());
        assertNotNull(request.query());
        assertTrue(request.filter() instanceof MatchQueryBuilder);
        MatchQueryBuilder filter = (MatchQueryBuilder)request.filter();
        assertEquals("foo", filter.fieldName());
        assertEquals("bar", filter.value());
        assertEquals("tsf", request.timestampField());
        assertEquals("etf", request.eventCategoryField());
        assertEquals("imjf", request.implicitJoinKeyField());
        assertArrayEquals(new Object[]{12345678, "device-20184", "/user/local/foo.exe", "2019-11-26T00:45:43.542"}, request.searchAfter());
        assertEquals(101, request.size());
        assertEquals(1000, request.fetchSize());
        assertEquals("file where user != 'SYSTEM' by file_path", request.query());
        assertEquals(setIsCaseSensitive && isCaseSensitive, request.isCaseSensitive());
        assertEquals(setDefaultOrder ? defaultOrder : FIELD_DEFAULT_ORDER, request.defaultOrder());
    }

    private EqlSearchRequest generateRequest(String index, String json, Function<XContentParser, EqlSearchRequest> fromXContent)
            throws IOException {
        XContentParser parser = parser(json);
        return fromXContent.apply(parser).indices(new String[]{index});
    }

    private void assertParsingErrorMessage(String json, String errorMessage, Consumer<XContentParser> consumer) throws IOException {
        assertParsingErrorMessage(json, errorMessage, null, consumer);
    }

    private void assertParsingErrorMessage(String json, String errorMessage, String causeErrorMessage,
        Consumer<XContentParser> consumer) throws IOException {
        XContentParser parser = parser(json);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> consumer.accept(parser));
        assertThat(e.getMessage(), containsString(errorMessage));
        if (e.getCause() != null) {
            if (causeErrorMessage == null) {
                fail("Exception cause assertion expected");
            } else {
                assertThat(e.getCause().getMessage(), containsString(causeErrorMessage));
            }
        } else {
            if (causeErrorMessage != null) {
                fail("Exception cause assertion expected");
            }
        }
    }

    private XContentParser parser(String content) throws IOException {
        XContentType xContentType = XContentType.JSON;

        return xContentType.xContent().createParser(registry, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, content);
    }
}
