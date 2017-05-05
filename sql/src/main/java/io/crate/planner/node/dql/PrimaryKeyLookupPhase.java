/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.planner.node.dql;

import com.google.common.collect.ImmutableSet;
import io.crate.analyze.OrderBy;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.Symbols;
import io.crate.analyze.where.DocKeys;
import io.crate.collections.Lists2;
import io.crate.metadata.Routing;
import io.crate.metadata.RowGranularity;
import io.crate.planner.distribution.DistributionInfo;
import io.crate.planner.node.ExecutionPhaseVisitor;
import io.crate.planner.projection.Projection;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A plan node which collects data.
 */
public class PrimaryKeyLookupPhase extends AbstractProjectionsPhase implements CollectPhase {

    public static final ExecutionPhaseFactory<PrimaryKeyLookupPhase> FACTORY = PrimaryKeyLookupPhase::new;

    private Routing routing;
    private List<Symbol> toCollect;
    private DistributionInfo distributionInfo;
    private DocKeys docKeys;
    private RowGranularity maxRowGranularity = RowGranularity.CLUSTER;

    private boolean isPartitioned = false;

    @Nullable
    private Integer nodePageSizeHint = null;

    @Nullable
    private OrderBy orderBy = null;

    protected PrimaryKeyLookupPhase() {
        super();
    }

    public PrimaryKeyLookupPhase(UUID jobId,
                                 int executionNodeId,
                                 String name,
                                 Routing routing,
                                 RowGranularity maxRowGranularity,
                                 List<Symbol> toCollect,
                                 List<Projection> projections,
                                 DocKeys docKeys,
                                 DistributionInfo distributionInfo) {
        super(jobId, executionNodeId, name, projections);
        this.docKeys = docKeys;
        this.routing = routing;
        this.maxRowGranularity = maxRowGranularity;
        this.toCollect = toCollect;
        this.distributionInfo = distributionInfo;
        this.outputTypes = extractOutputTypes(toCollect, projections);
    }

    @Override
    public void replaceSymbols(Function<Symbol, Symbol> replaceFunction) {
        super.replaceSymbols(replaceFunction);
        Lists2.replaceItems(toCollect, replaceFunction);
        if (orderBy != null) {
            orderBy.replace(replaceFunction);
        }
    }

    @Override
    public Type type() {
        return Type.PRIMARY_KEY_LOOKUP;
    }

    /**
     * @return a set of node ids where this collect operation is executed,
     */
    @Override
    public Set<String> nodeIds() {
        if (routing == null) {
            return ImmutableSet.of();
        }
        return routing.nodes();
    }

    @Override
    public DistributionInfo distributionInfo() {
        return distributionInfo;
    }

    @Override
    public void distributionInfo(DistributionInfo distributionInfo) {
        this.distributionInfo = distributionInfo;
    }

    @Nullable
    public OrderBy orderBy() {
        return orderBy;
    }

    public void orderBy(@Nullable OrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public DocKeys docKeys() {
        return docKeys;
    }

    public Routing routing() {
        return routing;
    }

    public List<Symbol> toCollect() {
        return toCollect;
    }

    public RowGranularity maxRowGranularity() {
        return maxRowGranularity;
    }

    @Override
    public <C, R> R accept(ExecutionPhaseVisitor<C, R> visitor, C context) {
        return visitor.visitPrimaryKeyLookupPhase(this, context);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);

        distributionInfo = DistributionInfo.fromStream(in);

        toCollect = Symbols.listFromStream(in);
        maxRowGranularity = RowGranularity.fromStream(in);

        if (in.readBoolean()) {
            routing = Routing.fromStream(in);
        }

        if (in.readBoolean()) {
            nodePageSizeHint = in.readVInt();
        }

        if (in.readBoolean()) {
            orderBy = OrderBy.fromStream(in);
        }
        isPartitioned = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        distributionInfo.writeTo(out);

        Symbols.toStream(toCollect, out);
        RowGranularity.toStream(maxRowGranularity, out);

        if (routing != null) {
            out.writeBoolean(true);
            routing.writeTo(out);
        } else {
            out.writeBoolean(false);
        }

        if (nodePageSizeHint != null) {
            out.writeBoolean(true);
            out.writeVInt(nodePageSizeHint);
        } else {
            out.writeBoolean(false);
        }
        if (orderBy != null) {
            out.writeBoolean(true);
            OrderBy.toStream(orderBy, out);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(isPartitioned);
    }
}

