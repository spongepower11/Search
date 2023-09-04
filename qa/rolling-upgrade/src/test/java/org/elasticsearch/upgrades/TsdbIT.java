/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.upgrades;

import org.elasticsearch.Version;
import org.elasticsearch.client.Request;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.common.time.FormatNames;
import org.elasticsearch.test.rest.ObjectPath;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.backingIndexEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class TsdbIT extends AbstractRollingTestCase {

    private static final String TEMPLATE = """
        {
            "settings":{
                "index": {
                    "mode": "time_series"
                }
            },
            "mappings":{
                "dynamic_templates": [
                    {
                        "labels": {
                            "path_match": "pod.labels.*",
                            "mapping": {
                                "type": "keyword",
                                "time_series_dimension": true
                            }
                        }
                    }
                ],
                "properties": {
                    "@timestamp" : {
                        "type": "date"
                    },
                    "metricset": {
                        "type": "keyword",
                        "time_series_dimension": true
                    },
                    "k8s": {
                        "properties": {
                            "pod": {
                                "properties": {
                                    "uid": {
                                        "type": "keyword",
                                        "time_series_dimension": true
                                    },
                                    "name": {
                                        "type": "keyword"
                                    },
                                    "ip": {
                                        "type": "ip"
                                    },
                                    "network": {
                                        "properties": {
                                            "tx": {
                                                "type": "long"
                                            },
                                            "rx": {
                                                "type": "long"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        """;
    private static final String BULK =
        """
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959507","ip": "10.10.55.1", "network": {"tx": 2001818691, "rx": 802133794}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "hamster", "uid":"947e4ced-1786-4e53-9e0c-5c447e959508","ip": "10.10.55.1", "network": {"tx": 2005177954, "rx": 801479970}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cow", "uid":"947e4ced-1786-4e53-9e0c-5c447e959509","ip": "10.10.55.1", "network": {"tx": 2006223737, "rx": 802337279}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "rat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959510","ip": "10.10.55.2", "network": {"tx": 2012916202, "rx": 803685721}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "dog", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9","ip": "10.10.55.3", "network": {"tx": 1434521831, "rx": 530575198}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "tiger", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea10","ip": "10.10.55.3", "network": {"tx": 1434577921, "rx": 530600088}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "lion", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876e11","ip": "10.10.55.3", "network": {"tx": 1434587694, "rx": 530604797}}}}
            {"create": {}}
            {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "elephant", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876eb4","ip": "10.10.55.3", "network": {"tx": 1434595272, "rx": 530605511}}}}
            """;

    private static final String DOC = """
        {
            "@timestamp": "$time",
            "metricset": "pod",
            "k8s": {
                "pod": {
                    "name": "dog",
                    "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9",
                    "ip": "10.10.55.3",
                    "network": {
                        "tx": 1434595272,
                        "rx": 530605511
                    }
                }
            }
        }
        """;

    public void testTsdbDataStream() throws Exception {
        assumeTrue(
            "Skipping version [" + UPGRADE_FROM_VERSION + "], because TSDB was GA-ed in 8.7.0",
            UPGRADE_FROM_VERSION.onOrAfter(Version.V_8_7_0)
        );
        String dataStreamName = "k8s";
        if (CLUSTER_TYPE == ClusterType.OLD) {
            final String INDEX_TEMPLATE = """
                {
                    "index_patterns": ["$PATTERN"],
                    "template": $TEMPLATE,
                    "data_stream": {
                    }
                }""";
            // Add composable index template
            String templateName = "1";
            var putIndexTemplateRequest = new Request("POST", "/_index_template/" + templateName);
            putIndexTemplateRequest.setJsonEntity(INDEX_TEMPLATE.replace("$TEMPLATE", TEMPLATE).replace("$PATTERN", dataStreamName));
            assertOK(client().performRequest(putIndexTemplateRequest));

            performOldClustertOperations(templateName, dataStreamName);
        } else if (CLUSTER_TYPE == ClusterType.MIXED) {
            performMixedClusterOperations(dataStreamName);
        } else if (CLUSTER_TYPE == ClusterType.UPGRADED) {
            performUpgradedClusterOperations(dataStreamName);
        }
    }

    // This causes rollover to fail with: backing index [.ds-test-with-component-template-2023.08.25-000001] with range
    // [-9999-01-01T00:00:00.000Z TO 9999-12-31T23:59:59.999Z] is overlapping with backing index [.ds-test-with-component-template-
    // 2023.08.25-000002] with range [2023-08-25T05:54:36.000Z TO 2023-08-25T09:54:36.000Z]"
    // (The fix is that upon upgrading the invalid tsdb data stream be downgraded)
    // @AwaitsFix(bugUrl = "https://github.com/elastic/elasticsearch/issues/98833")
    public void testTsdbDataStreamWithComponentTemplate() throws Exception {
        assumeTrue(
            "Skipping version [" + UPGRADE_FROM_VERSION + "], because TSDB was GA-ed in 8.7.0",
            UPGRADE_FROM_VERSION.onOrAfter(Version.V_8_7_0)
        );
        String dataStreamName = "test-with-component-template";
        if (CLUSTER_TYPE == ClusterType.OLD) {
            final String COMPONENT_TEMPLATE = """
                    {
                        "template": $TEMPLATE
                    }
                """;
            var putComponentTemplate = new Request("POST", "/_component_template/1");
            String template = TEMPLATE.replace("\"time_series\"", "\"time_series\", \"routing_path\": [\"k8s.pod.uid\"]");
            putComponentTemplate.setJsonEntity(COMPONENT_TEMPLATE.replace("$TEMPLATE", template));
            assertOK(client().performRequest(putComponentTemplate));
            final String INDEX_TEMPLATE = """
                {
                    "index_patterns": ["$PATTERN"],
                    "composed_of": ["1"],
                    "data_stream": {
                    }
                }""";
            // Add composable index template
            String templateName = "2";
            var putIndexTemplateRequest = new Request("POST", "/_index_template/" + templateName);
            putIndexTemplateRequest.setJsonEntity(INDEX_TEMPLATE.replace("$PATTERN", dataStreamName));
            assertOK(client().performRequest(putIndexTemplateRequest));

            performOldClustertOperations(templateName, dataStreamName);
        } else if (CLUSTER_TYPE == ClusterType.MIXED) {
            performMixedClusterOperations(dataStreamName);
        } else if (CLUSTER_TYPE == ClusterType.UPGRADED) {
            performUpgradedClusterOperations(dataStreamName);
        }
    }

    private void performUpgradedClusterOperations(String dataStreamName) throws IOException {
        ensureGreen(dataStreamName);
        var rolloverRequest = new Request("POST", "/" + dataStreamName + "/_rollover");
        assertOK(client().performRequest(rolloverRequest));

        var dataStreams = getDataStream(dataStreamName);
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.name"), equalTo(dataStreamName));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.generation"), equalTo(2));
        String secondBackingIndex = ObjectPath.evaluate(dataStreams, "data_streams.0.indices.1.index_name");
        assertThat(secondBackingIndex, backingIndexEqualTo(dataStreamName, 2));
        indexDoc(dataStreamName);
        assertSearch(dataStreamName, 10);
    }

    private static void performMixedClusterOperations(String dataStreamName) throws IOException {
        ensureHealth(dataStreamName, request -> request.addParameter("wait_for_status", "yellow"));
        if (FIRST_MIXED_ROUND) {
            indexDoc(dataStreamName);
        }
        assertSearch(dataStreamName, 9);
    }

    private static void performOldClustertOperations(String templateName, String dataStreamName) throws IOException {
        var bulkRequest = new Request("POST", "/" + dataStreamName + "/_bulk");
        bulkRequest.setJsonEntity(BULK.replace("$now", formatInstant(Instant.now())));
        bulkRequest.addParameter("refresh", "true");
        var response = client().performRequest(bulkRequest);
        assertOK(response);
        var responseBody = entityAsMap(response);
        assertThat("errors in response:\n " + responseBody, responseBody.get("errors"), equalTo(false));

        var dataStreams = getDataStream(dataStreamName);
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams"), hasSize(1));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.name"), equalTo(dataStreamName));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.generation"), equalTo(1));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.template"), equalTo(templateName));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.indices"), hasSize(1));
        String firstBackingIndex = ObjectPath.evaluate(dataStreams, "data_streams.0.indices.0.index_name");
        assertThat(firstBackingIndex, backingIndexEqualTo(dataStreamName, 1));
        assertSearch(dataStreamName, 8);
    }

    private static void indexDoc(String dataStreamName) throws IOException {
        var indexRequest = new Request("POST", "/" + dataStreamName + "/_doc");
        indexRequest.addParameter("refresh", "true");
        indexRequest.setJsonEntity(DOC.replace("$time", formatInstant(Instant.now())));
        var response = client().performRequest(indexRequest);
        assertOK(response);
    }

    private static void assertSearch(String dataStreamName, int expectedHitCount) throws IOException {
        var searchRequest = new Request("GET", dataStreamName + "/_search");
        var response = client().performRequest(searchRequest);
        assertOK(response);
        var responseBody = entityAsMap(response);
        assertThat(ObjectPath.evaluate(responseBody, "hits.total.value"), equalTo(expectedHitCount));
    }

    private static String formatInstant(Instant instant) {
        return DateFormatter.forPattern(FormatNames.STRICT_DATE_OPTIONAL_TIME.getName()).format(instant);
    }

    private static Map<String, Object> getDataStream(String dataStreamName) throws IOException {
        var getDataStreamsRequest = new Request("GET", "/_data_stream/" + dataStreamName);
        var response = client().performRequest(getDataStreamsRequest);
        assertOK(response);
        return entityAsMap(response);
    }

}
