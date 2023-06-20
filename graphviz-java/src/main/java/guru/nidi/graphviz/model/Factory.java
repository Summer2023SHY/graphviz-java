/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.graphviz.model;

import guru.nidi.graphviz.attribute.*;

/**
 * Provides factory methods for creating various Graphviz elements.
 */
public final class Factory {
    private Factory() {
    }

    /**
     * Creates a new immutable graph.
     * @return an immutable graph
     */
    public static Graph graph() {
        return graph("");
    }

    /**
     * Creates a new immutable graph with the specified name.
     * 
     * @param name the name for the graph
     * @return an immutable graph with the specified name
     */
    public static Graph graph(String name) {
        return new ImmutableGraph().named(name);
    }

    /**
     * Creates a new immutable node with the specified name.
     * 
     * @param name the name for the node
     * @return an immutable node with the specified name
     */
    public static Node node(String name) {
        return node(Label.of(name));
    }

    /**
     * Creates a new immutable node with the specified name.
     * 
     * @param name the name for the node
     * @return an immutable node with the specified name
     */
    public static Node node(Label name) {
        return CreationContext.createNode(name);
    }

    public static Port port(String record) {
        return new Port(record, null);
    }

    public static Port port(Compass compass) {
        return new Port(null, compass);
    }

    public static Port port(String record, Compass compass) {
        return new Port(record, compass);
    }


    /**
     * Creates a new mutable graph.
     * @return a mutable graph
     */
    public static MutableGraph mutGraph() {
        return CreationContext.createMutGraph();
    }

    /**
     * Creates a new mutable graph with the specified name.
     * 
     * @param name the name for the graph
     * @return a mutable graph with the specified name
     */
    public static MutableGraph mutGraph(String name) {
        return mutGraph().setName(name);
    }

    /**
     * Creates a new mutable node with the specified name.
     * 
     * @param name the name for the node
     * @return an mutable node with the specified name
     */
    public static MutableNode mutNode(String name) {
        return mutNode(name, false);
    }

    /**
     * Creates a new mutable node with the specified name.
     * 
     * @param name the name for the node
     * @return an mutable node with the specified name
     */
    public static MutableNode mutNode(String name, boolean raw) {
        return mutNode(raw ? Label.raw(name) : Label.of(name));
    }

    public static MutableNode mutNode(Label name) {
        return CreationContext.createMutNode(name);
    }


    public static Link to(Node node) {
        return Link.to(node);
    }

    public static Link to(LinkTarget node) {
        return Link.to(node);
    }

    public static Link between(Port port, LinkTarget to) {
        return Link.between(port, to);
    }


    public static MutableAttributed<?, ForNode> nodeAttrs() {
        return CreationContext.get().nodeAttrs();
    }

    public static MutableAttributed<?, ForLink> linkAttrs() {
        return CreationContext.get().linkAttrs();
    }

    public static MutableAttributed<?, ForGraph> graphAttrs() {
        return CreationContext.get().graphAttrs();
    }

}
