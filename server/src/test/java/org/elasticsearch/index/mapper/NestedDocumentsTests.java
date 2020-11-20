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

package org.elasticsearch.index.mapper;

import org.apache.lucene.search.join.QueryBitSetProducer;
import org.elasticsearch.search.LeafNestedDocuments;
import org.elasticsearch.search.NestedDocuments;
import org.elasticsearch.search.SearchHit;

import java.io.IOException;

public class NestedDocumentsTests extends MapperServiceTestCase {

    public void testSimpleNestedHierarchy() throws IOException {

        MapperService mapperService = createMapperService(mapping(b -> {
            b.startObject("name").field("type", "keyword").endObject();
            b.startObject("children");
            {
                b.field("type", "nested");
                b.startObject("properties");
                {
                    b.startObject("name").field("type", "keyword").endObject();
                }
                b.endObject();
            }
            b.endObject();
        }));

        ParsedDocument doc = mapperService.documentMapper().parse(source(b -> {
            b.field("name", "top1");
            b.startArray("children");
            {
                b.startObject().field("name", "child1").endObject();
                b.startObject().field("name", "child2").endObject();
                b.startObject().field("name", "child3").endObject();
            }
            b.endArray();
        }));

        withLuceneIndex(mapperService, iw -> iw.addDocuments(doc.docs()), reader -> {
            NestedDocuments nested = new NestedDocuments(mapperService.snapshot().documentMapper(), QueryBitSetProducer::new);
            LeafNestedDocuments leaf = nested.getLeafNestedDocuments(reader.leaves().get(0));

            assertNotNull(leaf.advance(0));
            assertEquals(0, leaf.doc());
            assertEquals(3, leaf.rootDoc());
            assertEquals(new SearchHit.NestedIdentity("children", 0, null), leaf.nestedIdentity());

            assertNotNull(leaf.advance(1));
            assertEquals(1, leaf.doc());
            assertEquals(3, leaf.rootDoc());
            assertEquals(new SearchHit.NestedIdentity("children", 1, null), leaf.nestedIdentity());

            assertNotNull(leaf.advance(2));
            assertEquals(2, leaf.doc());
            assertEquals(3, leaf.rootDoc());
            assertEquals(new SearchHit.NestedIdentity("children", 2, null), leaf.nestedIdentity());

            assertNull(leaf.advance(3));
            assertEquals(3, leaf.doc());
            assertEquals(3, leaf.rootDoc());
            assertNull(leaf.nestedIdentity());
        });

    }

    public void testMultiLevelNestedHierarchy() throws IOException {

        MapperService mapperService = createMapperService(mapping(b -> {
            b.startObject("name").field("type", "keyword").endObject();
            b.startObject("children");
            {
                b.field("type", "nested");
                b.startObject("properties");
                {
                    b.startObject("name").field("type", "keyword").endObject();
                    b.startObject("grandchildren");
                    {
                        b.field("type", "nested");
                        b.startObject("properties");
                        {
                            b.startObject("name").field("type", "keyword").endObject();
                        }
                        b.endObject();
                    }
                    b.endObject();
                }
                b.endObject();
            }
            b.endObject();
        }));

        ParsedDocument doc = mapperService.documentMapper().parse(source(b -> {
            b.field("name", "top1");
            b.startArray("children");
            {
                b.startObject();
                {
                    b.field("name", "child1");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild1").endObject();
                        b.startObject().field("name", "grandchild2").endObject();
                        b.startObject().field("name", "grandchild3").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
                b.startObject();
                {
                    b.field("name", "child2");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild21").endObject();
                        b.startObject().field("name", "grandchild22").endObject();
                        b.startObject().field("name", "grandchild23").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
                b.startObject();
                {
                    b.field("name", "child3");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild31").endObject();
                        b.startObject().field("name", "grandchild32").endObject();
                        b.startObject().field("name", "grandchild33").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
            }
            b.endArray();
        }));

        withLuceneIndex(mapperService, iw -> iw.addDocuments(doc.docs()), reader -> {
            NestedDocuments nested = new NestedDocuments(mapperService.snapshot().documentMapper(), QueryBitSetProducer::new);
            LeafNestedDocuments leaf = nested.getLeafNestedDocuments(reader.leaves().get(0));

            assertNotNull(leaf.advance(0));
            assertEquals(0, leaf.doc());
            assertEquals(12, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children", 0, new SearchHit.NestedIdentity("grandchildren", 0, null)),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(2));
            assertEquals(2, leaf.doc());
            assertEquals(12, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children", 0, new SearchHit.NestedIdentity("grandchildren", 2, null)),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(3));
            assertEquals(3, leaf.doc());
            assertEquals(12, leaf.rootDoc());
            assertEquals(new SearchHit.NestedIdentity("children", 0, null), leaf.nestedIdentity());

            assertNotNull(leaf.advance(4));
            assertEquals(4, leaf.doc());
            assertEquals(12, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children", 1, new SearchHit.NestedIdentity("grandchildren", 0, null)),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(5));
            assertEquals(5, leaf.doc());
            assertEquals(12, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children", 1, new SearchHit.NestedIdentity("grandchildren", 1, null)),
                leaf.nestedIdentity());

            assertNull(leaf.advance(12));
            assertNull(leaf.nestedIdentity());
        });
    }

    public void testNestedObjectWithinNonNestedObject() throws IOException {
        MapperService mapperService = createMapperService(mapping(b -> {
            b.startObject("name").field("type", "keyword").endObject();
            b.startObject("children");
            {
                b.field("type", "object");
                b.startObject("properties");
                {
                    b.startObject("name").field("type", "keyword").endObject();
                    b.startObject("grandchildren");
                    {
                        b.field("type", "nested");
                        b.startObject("properties");
                        {
                            b.startObject("name").field("type", "keyword").endObject();
                        }
                        b.endObject();
                    }
                    b.endObject();
                }
                b.endObject();
            }
            b.endObject();
        }));

        ParsedDocument doc = mapperService.documentMapper().parse(source(b -> {
            b.field("name", "top1");
            b.startArray("children");
            {
                b.startObject();
                {
                    b.field("name", "child1");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild1").endObject();
                        b.startObject().field("name", "grandchild2").endObject();
                        b.startObject().field("name", "grandchild3").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
                b.startObject();
                {
                    b.field("name", "child2");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild21").endObject();
                        b.startObject().field("name", "grandchild22").endObject();
                        b.startObject().field("name", "grandchild23").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
                b.startObject();
                {
                    b.field("name", "child3");
                    b.startArray("grandchildren");
                    {
                        b.startObject().field("name", "grandchild31").endObject();
                        b.startObject().field("name", "grandchild32").endObject();
                        b.startObject().field("name", "grandchild33").endObject();
                    }
                    b.endArray();
                }
                b.endObject();
            }
            b.endArray();
        }));

        withLuceneIndex(mapperService, iw -> iw.addDocuments(doc.docs()), reader -> {
            NestedDocuments nested = new NestedDocuments(mapperService.snapshot().documentMapper(), QueryBitSetProducer::new);
            LeafNestedDocuments leaf = nested.getLeafNestedDocuments(reader.leaves().get(0));

            assertNotNull(leaf.advance(0));
            assertEquals(0, leaf.doc());
            assertEquals(9, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children.grandchildren", 0, null),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(2));
            assertEquals(2, leaf.doc());
            assertEquals(9, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children.grandchildren", 2, null),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(3));
            assertEquals(3, leaf.doc());
            assertEquals(9, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children.grandchildren", 3, null),
                leaf.nestedIdentity());

            assertNotNull(leaf.advance(5));
            assertEquals(5, leaf.doc());
            assertEquals(9, leaf.rootDoc());
            assertEquals(
                new SearchHit.NestedIdentity("children.grandchildren", 5, null),
                leaf.nestedIdentity());

            assertNull(leaf.advance(9));
            assertNull(leaf.nestedIdentity());
        });
    }

}
