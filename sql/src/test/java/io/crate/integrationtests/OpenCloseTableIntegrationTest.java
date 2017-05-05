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
import org.elasticsearch.action.admin.indices.close.CloseIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.open.OpenIndexRequest;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.is;

@UseJdbc
public class OpenCloseTableIntegrationTest extends SQLTransportIntegrationTest {

    @Test
    public void testOpenTable() throws Exception {
        execute("create table t (i int)");
        ensureYellow();

        execute("select closed from information_schema.tables where table_name = 't'");

        assertEquals(1, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
    }

    @Test
    public void testCloseTable() throws Exception {
        execute("create table t (i int)");
        ensureYellow();

        // TODO: replace with `alter table t close` once supported
        String[] indices = client().admin().indices().getIndex(new GetIndexRequest()).actionGet().getIndices();
        client().admin().indices().close(new CloseIndexRequest(indices[0])).actionGet();

        execute("select closed from information_schema.tables where table_name = 't'");

        assertEquals(1, response.rowCount());
        assertEquals(true, response.rows()[0][0]);
    }

    @Test
    public void testClosePartition() throws Exception {
        execute("create table t (i int) partitioned by (i)");
        ensureYellow();

        execute("insert into t values (1)");
        ensureYellow();
        String partitionIdent = client().admin().indices().getIndex(new GetIndexRequest()).actionGet().getIndices()[0];

        execute("insert into t values (2), (3)");
        ensureYellow();

        execute("select closed from information_schema.table_partitions where table_name = 't'");

        assertEquals(3, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
        assertEquals(false, response.rows()[1][0]);
        assertEquals(false, response.rows()[2][0]);

        // TODO: replace with `alter table t close` once supported
        client().admin().indices().close(new CloseIndexRequest(partitionIdent)).actionGet();

        execute("select partition_ident, values from information_schema.table_partitions" +
                " where table_name = 't' and closed = true");

        assertEquals(1, response.rowCount());

        HashMap values = (HashMap) response.rows()[0][1];
        assertEquals(1, values.get("i"));
        assertTrue(partitionIdent.endsWith((String) response.rows()[0][0]));

        execute("select partition_ident from information_schema.table_partitions" +
                " where table_name = 't' and closed = false");


        assertEquals(2, response.rowCount());
    }

    @Test
    public void testReopenTable() throws Exception {
        execute("create table t (i int)");
        ensureYellow();

        // TODO: replace with `alter table t close` once supported
        String[] indices = client().admin().indices().getIndex(new GetIndexRequest()).actionGet().getIndices();
        client().admin().indices().close(new CloseIndexRequest(indices[0])).actionGet();

        execute("select closed from information_schema.tables where table_name = 't'");
        assertEquals(1, response.rowCount());
        assertEquals(true, response.rows()[0][0]);

        // TODO: replace with `alter table t partition (i = 1) close` once supported
        client().admin().indices().open(new OpenIndexRequest(indices[0])).actionGet();

        execute("select closed from information_schema.tables where table_name = 't'");
        assertEquals(1, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
    }

    @Test
    public void testReopenPartition() throws Exception {
        execute("create table t (i int) partitioned by (i)");
        ensureYellow();

        execute("insert into t values (1)");
        ensureYellow();
        String partitionIdent = client().admin().indices().getIndex(new GetIndexRequest()).actionGet().getIndices()[0];

        execute("insert into t values (2), (3)");
        ensureYellow();

        execute("select closed from information_schema.table_partitions where table_name = 't'");

        assertEquals(3, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
        assertEquals(false, response.rows()[1][0]);
        assertEquals(false, response.rows()[2][0]);

        // TODO: replace with `alter table t partition (i = 1) close` once supported
        client().admin().indices().close(new CloseIndexRequest(partitionIdent)).actionGet();

        execute("select partition_ident, values from information_schema.table_partitions" +
                " where table_name = 't' and closed = true");

        assertEquals(1, response.rowCount());

        HashMap values = (HashMap) response.rows()[0][1];
        assertEquals(1, values.get("i"));
        assertTrue(partitionIdent.endsWith((String) response.rows()[0][0]));

        // TODO: replace with `alter table t partition (i = 1) open` once supported
        client().admin().indices().open(new OpenIndexRequest(partitionIdent)).actionGet();

        execute("select closed from information_schema.table_partitions");

        assertEquals(3, response.rowCount());
        assertEquals(false, response.rows()[0][0]);
        assertEquals(false, response.rows()[1][0]);
        assertEquals(false, response.rows()[2][0]);
    }
}
