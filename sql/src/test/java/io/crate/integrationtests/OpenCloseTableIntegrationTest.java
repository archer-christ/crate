/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.integrationtests;

import io.crate.testing.UseJdbc;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.is;

@UseJdbc
public class OpenCloseTableIntegrationTest extends SQLTransportIntegrationTest {

    @Test
    public void testOpenCloseTable() throws Exception {
        execute("create table t (i int)");
        ensureYellow();

        execute("alter table t close");

        execute("select closed from information_schema.tables where table_name = 't'");
        assertEquals(1, response.rowCount());
        assertEquals(true, response.rows()[0][0]);

        IndexMetaData indexMetaData = client().admin().cluster().prepareState().execute().actionGet().getState().metaData()
            .indices().get("doc.t");
        assertEquals(IndexMetaData.State.CLOSE, indexMetaData.getState());

        execute("alter table t open");

        indexMetaData = client().admin().cluster().prepareState().execute().actionGet().getState().metaData()
            .indices().get("doc.t");

        execute("select closed from information_schema.tables where table_name = 't'");
        assertEquals(1, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
        assertEquals(IndexMetaData.State.OPEN, indexMetaData.getState());
    }


}
