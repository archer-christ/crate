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

package io.crate.planner.consumer;


import io.crate.analyze.OrderBy;
import io.crate.analyze.QuerySpec;
import io.crate.analyze.relations.QueriedDocTable;
import io.crate.analyze.symbol.InputColumn;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.where.DocKeys;
import io.crate.collections.Lists2;
import io.crate.exceptions.VersionInvalidException;
import io.crate.metadata.Routing;
import io.crate.metadata.doc.DocTableInfo;
import io.crate.planner.*;
import io.crate.planner.distribution.DistributionInfo;
import io.crate.planner.node.dql.PrimaryKeyLookupPhase;
import io.crate.planner.node.dql.PrimaryKeyLookupPlan;
import io.crate.planner.projection.Projection;
import io.crate.planner.projection.TopNProjection;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ESGetStatementPlanner {

    public static Plan convert(QueriedDocTable table, Planner.Context context) {
        QuerySpec querySpec = table.querySpec();
        Optional<DocKeys> optKeys = querySpec.where().docKeys();
        assert !querySpec.hasAggregates() : "Can't create ESGet plan for queries with aggregates";
        assert !querySpec.groupBy().isPresent() : "Can't create ESGet plan for queries with group by";
        assert optKeys.isPresent() : "Can't create ESGet without docKeys";

        DocTableInfo tableInfo = table.tableRelation().tableInfo();
        DocKeys docKeys = optKeys.get();
        if (docKeys.withVersions()){
            throw new VersionInvalidException();
        }

        Optional<OrderBy> optOrderBy = querySpec.orderBy();
        List<Symbol> qsOutputs = querySpec.outputs();
        List<Symbol> toCollect = getToCollectSymbols(qsOutputs, optOrderBy);
        Limits limits = context.getLimits(querySpec);
        if (limits.hasLimit() && limits.finalLimit() == 0) {
            return new NoopPlan(context.jobId());
        }
        table.tableRelation().validateOrderBy(querySpec.orderBy());

        Routing routing = context.allocateRouting(tableInfo, querySpec.where(), null);
        PrimaryKeyLookupPhase primaryKeyLookupPhase = new PrimaryKeyLookupPhase(
            context.jobId(),
            context.nextExecutionPhaseId(),
            "primary-key-lookup",
            routing,
            tableInfo.rowGranularity(),
            toCollect,
            topNOrEmptyProjections(toCollect, limits),
            docKeys,
            DistributionInfo.DEFAULT_BROADCAST
        );

        PrimaryKeyLookupPlan plan = new PrimaryKeyLookupPlan(
            primaryKeyLookupPhase,
            limits.finalLimit(),
            limits.offset(),
            qsOutputs.size(),
            limits.limitAndOffset(),
            PositionalOrderBy.of(optOrderBy.orElse(null), toCollect)
        );

        return plan;
    }

    /**
     * @return qsOutputs + symbols from orderBy which are not already within qsOutputs (if orderBy is present)
     */
    private static List<Symbol> getToCollectSymbols(List<Symbol> qsOutputs, Optional<OrderBy> optOrderBy) {
        if (optOrderBy.isPresent()) {
            return Lists2.concatUnique(qsOutputs, optOrderBy.get().orderBySymbols());
        }
        return qsOutputs;
    }

    private static List<Projection> topNOrEmptyProjections(List<Symbol> toCollect, Limits limits) {
        if (limits.hasLimit()) {
            return Collections.singletonList(new TopNProjection(
                limits.limitAndOffset(),
                0,
                InputColumn.fromSymbols(toCollect)
            ));
        }
        return Collections.emptyList();
    }
}
