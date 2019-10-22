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
package org.elasticsearch.cluster.coordination;

import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cluster.metadata.Manifest;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;

import java.io.IOException;
import java.nio.file.Path;

public class ReadMetaDataCommand extends ElasticsearchNodeCommand {

    public ReadMetaDataCommand() {
        super("reads the metadata on disk");
    }

    @Override protected void processNodePaths(
        Terminal terminal,
        Path[] dataPaths,
        Environment env, NamedXContentRegistry namedXContentRegistry) throws IOException {

        final Tuple<Manifest, MetaData> manifestMetaDataTuple = loadMetaData(terminal, dataPaths, namedXContentRegistry);
        terminal.println("metadata successfully read for cluster state version " + manifestMetaDataTuple.v1().getClusterStateVersion());
    }
}
