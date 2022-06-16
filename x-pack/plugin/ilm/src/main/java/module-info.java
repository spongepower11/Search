/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
module org.elasticsearch.ilm {
    requires org.apache.lucene.core;
    requires org.elasticsearch.server;
    requires org.elasticsearch.base;
    requires org.elasticsearch.xcore;
    requires org.elasticsearch.xcontent;
    requires org.apache.logging.log4j;

    exports org.elasticsearch.xpack.ilm.action to org.elasticsearch.server;
    exports org.elasticsearch.xpack.ilm to org.elasticsearch.server;
    exports org.elasticsearch.xpack.slm.action to org.elasticsearch.server;
    exports org.elasticsearch.xpack.slm to org.elasticsearch.server;
}
