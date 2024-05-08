/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentLocation;
import org.elasticsearch.xcontent.XContentParseException;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.esql.parser.ContentLocation;
import org.elasticsearch.xpack.esql.parser.Params;
import org.elasticsearch.xpack.esql.parser.TypedParamValue;
import org.elasticsearch.xpack.esql.plugin.QueryPragmas;
import org.elasticsearch.xpack.ql.InvalidArgumentException;
import org.elasticsearch.xpack.ql.QlIllegalArgumentException;
import org.elasticsearch.xpack.ql.type.DataType;
import org.elasticsearch.xpack.ql.type.DataTypes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.elasticsearch.common.xcontent.XContentParserUtils.parseFieldsValue;
import static org.elasticsearch.xcontent.ObjectParser.ValueType.VALUE_OBJECT_ARRAY;

/** Static methods for parsing xcontent requests to transport requests. */
final class RequestXContent {

    private static class TempObjects {
        String type = null;
        Object value = null;
        Map<String, Object> fields = new HashMap<>();

        TempObjects() {}

        void addField(String key, Object value) {
            fields.put(key, value);
        }

        Map<String, Object> getFields() {
            return fields;
        }

        void setType(String type) {
            this.type = type;
        }

        void setValue(Object value) {
            this.value = value;
        }

        void reset() {
            this.type = null;
            this.value = null;
            this.fields.clear();
        }
    }

    private static final ObjectParser<TempObjects, Void> PARAM_PARSER = new ObjectParser<>(
        "params",
        TempObjects::addField,
        TempObjects::new
    );

    private static final ParseField VALUE = new ParseField("value");
    private static final ParseField TYPE = new ParseField("type");

    static {
        PARAM_PARSER.declareField(TempObjects::setValue, (p, c) -> parseFieldsValue(p), VALUE, ObjectParser.ValueType.VALUE);
        PARAM_PARSER.declareString(TempObjects::setType, TYPE);
    }

    static final ParseField ESQL_VERSION_FIELD = new ParseField("version");
    static final ParseField QUERY_FIELD = new ParseField("query");
    private static final ParseField COLUMNAR_FIELD = new ParseField("columnar");
    private static final ParseField FILTER_FIELD = new ParseField("filter");
    static final ParseField PRAGMA_FIELD = new ParseField("pragma");
    private static final ParseField PARAMS_FIELD = new ParseField("params");
    private static final ParseField LOCALE_FIELD = new ParseField("locale");
    private static final ParseField PROFILE_FIELD = new ParseField("profile");
    static final ParseField TABLES_FIELD = new ParseField("tables");

    static final ParseField WAIT_FOR_COMPLETION_TIMEOUT = new ParseField("wait_for_completion_timeout");
    static final ParseField KEEP_ALIVE = new ParseField("keep_alive");
    static final ParseField KEEP_ON_COMPLETION = new ParseField("keep_on_completion");

    private static final ObjectParser<EsqlQueryRequest, Void> SYNC_PARSER = objectParserSync(EsqlQueryRequest::syncEsqlQueryRequest);
    private static final ObjectParser<EsqlQueryRequest, Void> ASYNC_PARSER = objectParserAsync(EsqlQueryRequest::asyncEsqlQueryRequest);

    /** Parses a synchronous request. */
    static EsqlQueryRequest parseSync(XContentParser parser) {
        return SYNC_PARSER.apply(parser, null);
    }

    /** Parses an asynchronous request. */
    static EsqlQueryRequest parseAsync(XContentParser parser) {
        return ASYNC_PARSER.apply(parser, null);
    }

    private static void objectParserCommon(ObjectParser<EsqlQueryRequest, ?> parser) {
        parser.declareString(EsqlQueryRequest::esqlVersion, ESQL_VERSION_FIELD);
        parser.declareString(EsqlQueryRequest::query, QUERY_FIELD);
        parser.declareBoolean(EsqlQueryRequest::columnar, COLUMNAR_FIELD);
        parser.declareObject(EsqlQueryRequest::filter, (p, c) -> AbstractQueryBuilder.parseTopLevelQuery(p), FILTER_FIELD);
        parser.declareObject(
            EsqlQueryRequest::pragmas,
            (p, c) -> new QueryPragmas(Settings.builder().loadFromMap(p.map()).build()),
            PRAGMA_FIELD
        );
        parser.declareField(EsqlQueryRequest::params, RequestXContent::parseParams, PARAMS_FIELD, VALUE_OBJECT_ARRAY);
        parser.declareString((request, localeTag) -> request.locale(Locale.forLanguageTag(localeTag)), LOCALE_FIELD);
        parser.declareBoolean(EsqlQueryRequest::profile, PROFILE_FIELD);
        parser.declareField((p, r, c) -> new ParseTables(r, p).parseTables(), TABLES_FIELD, ObjectParser.ValueType.OBJECT);
    }

    private static ObjectParser<EsqlQueryRequest, Void> objectParserSync(Supplier<EsqlQueryRequest> supplier) {
        ObjectParser<EsqlQueryRequest, Void> parser = new ObjectParser<>("esql/query", false, supplier);
        objectParserCommon(parser);
        return parser;
    }

    private static ObjectParser<EsqlQueryRequest, Void> objectParserAsync(Supplier<EsqlQueryRequest> supplier) {
        ObjectParser<EsqlQueryRequest, Void> parser = new ObjectParser<>("esql/async_query", false, supplier);
        objectParserCommon(parser);
        parser.declareBoolean(EsqlQueryRequest::keepOnCompletion, KEEP_ON_COMPLETION);
        parser.declareField(
            EsqlQueryRequest::waitForCompletionTimeout,
            (p, c) -> TimeValue.parseTimeValue(p.text(), WAIT_FOR_COMPLETION_TIMEOUT.getPreferredName()),
            WAIT_FOR_COMPLETION_TIMEOUT,
            ObjectParser.ValueType.VALUE
        );
        parser.declareField(
            EsqlQueryRequest::keepAlive,
            (p, c) -> TimeValue.parseTimeValue(p.text(), KEEP_ALIVE.getPreferredName()),
            KEEP_ALIVE,
            ObjectParser.ValueType.VALUE
        );
        return parser;
    }

    private static Params parseParams(XContentParser p) throws IOException {
        List<TypedParamValue> result = new ArrayList<>();
        XContentParser.Token token = p.currentToken();
        boolean namedParameter = false;
        boolean unnamedParameter = false;

        if (token == XContentParser.Token.START_ARRAY) {
            Object value = null;
            String type = null;
            TypedParamValue previousParam = null;
            TypedParamValue currentParam = null;
            TempObjects param;
            DataType dataType = DataTypes.KEYWORD;

            while ((token = p.nextToken()) != XContentParser.Token.END_ARRAY) {
                XContentLocation loc = p.getTokenLocation();

                if (token == XContentParser.Token.START_OBJECT) {
                    // we are at the start of a value/type pair... hopefully
                    param = PARAM_PARSER.apply(p, null);
                    assert param.getFields().size() <= 1;
                    if (param.getFields().size() == 0) {
                        // value and type should be specified as a pair
                        if ((param.type != null && param.type.equalsIgnoreCase("null") != true && param.value == null)
                            || (param.value != null && param.type == null)) {
                            throw new InvalidArgumentException("Required a [value] and [type] pair");
                        }
                        currentParam = new TypedParamValue(null, param.type, param.value);
                        unnamedParameter = true;
                    } else {
                        for (Map.Entry<String, Object> entry : param.fields.entrySet()) {
                            // don't allow integer as a key
                            if (entry.getKey().matches("[0-9]+")) {
                                throw new InvalidArgumentException("Integer " + entry.getKey() + " is not a valid name for a parameter ");
                            }
                            try {
                                dataType = DataTypes.fromJava(entry.getValue());
                            } catch (QlIllegalArgumentException ex) {
                                throw new InvalidArgumentException(
                                    "Unexpected actual parameter type [" + param.value.getClass().getName() + "] " + ex.getMessage()
                                );
                            }
                            currentParam = new TypedParamValue(
                                entry.getKey(),
                                dataType == null ? DataTypes.KEYWORD.toString() : dataType.toString(),
                                entry.getValue(),
                                false
                            );
                        }
                        namedParameter = true;
                    }
                    /*
                     * Always set the xcontentlocation for the first param just in case the first one happens to not meet the parsing rules
                     * that are checked later in validateParams method.
                     * Also, set the xcontentlocation of the param that is different from the previous param in list when it comes to
                     * its type being explicitly set or inferred.
                     */
                    if ((previousParam != null && previousParam.hasExplicitType() == false) || result.isEmpty()) {
                        currentParam.tokenLocation(toProto(loc));
                    }
                } else {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        value = p.text();
                        type = "keyword";
                    } else if (token == XContentParser.Token.VALUE_NUMBER) {
                        XContentParser.NumberType numberType = p.numberType();
                        if (numberType == XContentParser.NumberType.INT) {
                            value = p.intValue();
                            type = "integer";
                        } else if (numberType == XContentParser.NumberType.LONG) {
                            value = p.longValue();
                            type = "long";
                        } else if (numberType == XContentParser.NumberType.DOUBLE) {
                            value = p.doubleValue();
                            type = "double";
                        }
                    } else if (token == XContentParser.Token.VALUE_BOOLEAN) {
                        value = p.booleanValue();
                        type = "boolean";
                    } else if (token == XContentParser.Token.VALUE_NULL) {
                        value = null;
                        type = "null";
                    } else {
                        throw new XContentParseException(loc, "Failed to parse object: unexpected token [" + token + "] found");
                    }

                    currentParam = new TypedParamValue(null, type, value, false);
                    if ((previousParam != null && previousParam.hasExplicitType()) || result.isEmpty()) {
                        currentParam.tokenLocation(toProto(loc));
                    }
                    unnamedParameter = true;
                }

                result.add(currentParam);
                previousParam = currentParam;
            }
        }

        if (unnamedParameter && namedParameter) {
            throw new InvalidArgumentException("Params contain both named and unnamed parameters");
        }

        return new Params(result);
    }

    static ContentLocation toProto(XContentLocation toProto) {
        if (toProto == null) {
            return null;
        }
        return new ContentLocation(toProto.lineNumber(), toProto.columnNumber());
    }

    static XContentLocation fromProto(ContentLocation fromProto) {
        if (fromProto == null) {
            return null;
        }
        return new XContentLocation(fromProto.lineNumber, fromProto.columnNumber);
    }
}
