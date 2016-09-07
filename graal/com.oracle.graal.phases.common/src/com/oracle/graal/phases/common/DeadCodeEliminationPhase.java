/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.phases.common;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeFlood;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.phases.Phase;

public class DeadCodeEliminationPhase extends Phase {

    public static class Options {

        // @formatter:off
        @Option(help = "Disable optional dead code eliminations", type = OptionType.Debug)
        public static final OptionValue<Boolean> ReduceDCE = new OptionValue<>(true);
        // @formatter:on
    }

    private static final DebugCounter counterNodesRemoved = Debug.counter("NodesRemoved");

    public enum Optionality {
        Optional,
        Required;
    }

    /**
     * Creates a dead code elimination phase that will be run irrespective of
     * {@link Options#ReduceDCE}.
     */
    public DeadCodeEliminationPhase() {
        this(Optionality.Required);
    }

    /**
     * Creates a dead code elimination phase that will be run only if it is
     * {@linkplain Optionality#Required non-optional} or {@link Options#ReduceDCE} is false.
     */
    public DeadCodeEliminationPhase(Optionality optionality) {
        this.optional = optionality == Optionality.Optional;
    }

    private final boolean optional;

    @Override
    public void run(StructuredGraph graph) {
        if (optional && Options.ReduceDCE.getValue()) {
            return;
        }

        NodeFlood flood = graph.createNodeFlood();
        int totalNodeCount = graph.getNodeCount();
        flood.add(graph.start());
        iterateSuccessorsAndInputs(flood);
        int totalMarkedCount = flood.getTotalMarkedCount();
        if (totalNodeCount == totalMarkedCount) {
            // All nodes are live => nothing more to do.
            return;
        } else {
            // Some nodes are not marked alive and therefore dead => proceed.
            assert totalNodeCount > totalMarkedCount;
        }

        deleteNodes(flood, graph);
    }

    private static void iterateSuccessorsAndInputs(NodeFlood flood) {
        Node.EdgeVisitor consumer = new Node.EdgeVisitor() {
            @Override
            public Node apply(Node n, Node succOrInput) {
                assert succOrInput.isAlive() : "dead successor or input " + succOrInput + " in " + n;
                flood.add(succOrInput);
                return succOrInput;
            }
        };
        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
                flood.add(end.merge());
            } else {
                current.applySuccessors(consumer);
                current.applyInputs(consumer);
            }
        }
    }

    private static void deleteNodes(NodeFlood flood, StructuredGraph graph) {
        Node.EdgeVisitor consumer = new Node.EdgeVisitor() {
            @Override
            public Node apply(Node n, Node input) {
                if (input.isAlive() && flood.isMarked(input)) {
                    input.removeUsage(n);
                }
                return input;
            }
        };

        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.markDeleted();
                node.applyInputs(consumer);
                counterNodesRemoved.increment();
            }
        }
    }
}