/*
 * (C) Copyright 2016-2017, by Dimitrios Michail and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.alg.shortestpath;

import java.util.*;

import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;

/**
 * A bidirectional version of Dijkstra's algorithm.
 */
public final class BidirectionalDijkstraShortestPath<V, E>
    extends BaseShortestPathAlgorithm<V, E>
{
    private double radius;

    /**
     * Constructs a new instance for a specified graph.
     *
     * @param graph the input graph
     */
    public BidirectionalDijkstraShortestPath(Graph<V, E> graph)
    {
        this(graph, Double.POSITIVE_INFINITY);
    }

    /**
     * Constructs a new instance for a specified graph.
     *
     * @param graph the input graph
     * @param radius limit on path length, or Double.POSITIVE_INFINITY for unbounded search
     */
    public BidirectionalDijkstraShortestPath(Graph<V, E> graph, double radius)
    {
        super(graph);
        if (radius < 0.0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        this.radius = radius;
    }

    @Override
    public GraphPath<V, E> getPath(V source, V sink)
    {
        if (!graph.containsVertex(source)) {
            throw new IllegalArgumentException("graph must contain the source vertex");
        }
        if (!graph.containsVertex(sink)) {
            throw new IllegalArgumentException("graph must contain the sink vertex");
        }

        // handle special case if source equals target
        if (source.equals(sink)) {
            return createEmptyPath(source, sink);
        }

        // create frontiers
        SearchFrontier forwardFrontier = new SearchFrontier(graph);
        SearchFrontier backwardFrontier;
        if (graph instanceof DirectedGraph) {
            backwardFrontier =
                new SearchFrontier(new EdgeReversedGraph<>(((DirectedGraph<V, E>) graph)));
        } else {
            backwardFrontier = new SearchFrontier(graph);
        }

        assert !source.equals(sink);

        // initialize both frontiers
        forwardFrontier.updateDistance(source, null, 0d);
        backwardFrontier.updateDistance(sink, null, 0d);

        // initialize best path
        double bestPath = Double.POSITIVE_INFINITY;
        V bestPathCommonVertex = null;

        SearchFrontier frontier = forwardFrontier;
        SearchFrontier otherFrontier = backwardFrontier;

        while (true) {
            // stopping condition
            if (frontier.heap.isEmpty() || otherFrontier.heap.isEmpty()
                || frontier.heap.min().getKey() + otherFrontier.heap.min().getKey() >= bestPath)
            {
                break;
            }

            // frontier scan
            FibonacciHeapNode<QueueEntry> node = frontier.heap.removeMin();
            V v = node.getData().v;
            double vDistance = node.getKey();

            for (E e : frontier.specifics.edgesOf(v)) {

                V u = Graphs.getOppositeVertex(frontier.graph, e, v);

                double eWeight = frontier.graph.getEdgeWeight(e);

                frontier.updateDistance(u, e, vDistance + eWeight);

                // check path with u's distance from the other frontier
                double pathDistance = vDistance + eWeight + otherFrontier.getDistance(u);

                if (pathDistance < bestPath) {
                    bestPath = pathDistance;
                    bestPathCommonVertex = u;
                }

            }

            // swap frontiers
            SearchFrontier tmpFrontier = frontier;
            frontier = otherFrontier;
            otherFrontier = tmpFrontier;

        }

        // create path if found
        if (Double.isFinite(bestPath) && bestPath <= radius) {
            return createPath(
                forwardFrontier, backwardFrontier, bestPath, source, bestPathCommonVertex, sink);
        } else {
            return createEmptyPath(source, sink);
        }
    }

    /**
     * Find a path between two vertices. For a more advanced search (e.g. limited by radius), use
     * the constructor instead.
     * 
     * @param graph the graph to be searched
     * @param source the vertex at which the path should start
     * @param sink the vertex at which the path should end
     * 
     * @param <V> the graph vertex type
     * @param <E> the graph edge type
     *
     * @return a shortest path, or null if no path exists
     */
    public static <V, E> GraphPath<V, E> findPathBetween(Graph<V, E> graph, V source, V sink)
    {
        return new BidirectionalDijkstraShortestPath<>(graph).getPath(source, sink);
    }

    private GraphPath<V, E> createPath(
        SearchFrontier forwardFrontier, SearchFrontier backwardFrontier, double weight, V source,
        V commonVertex, V sink)
    {
        LinkedList<E> edgeList = new LinkedList<>();
        LinkedList<V> vertexList = new LinkedList<>();

        // add common vertex
        vertexList.add(commonVertex);

        // traverse forward path
        V v = commonVertex;
        while (true) {
            E e = forwardFrontier.getTreeEdge(v);

            if (e == null) {
                break;
            }

            edgeList.addFirst(e);
            v = Graphs.getOppositeVertex(forwardFrontier.graph, e, v);
            vertexList.addFirst(v);
        }

        // traverse reverse path
        v = commonVertex;
        while (true) {
            E e = backwardFrontier.getTreeEdge(v);

            if (e == null) {
                break;
            }

            edgeList.addLast(e);
            v = Graphs.getOppositeVertex(backwardFrontier.graph, e, v);
            vertexList.addLast(v);
        }

        return new GraphWalk<>(graph, source, sink, vertexList, edgeList, weight);
    }

    /**
     * Helper class to maintain the search frontier
     */
    class SearchFrontier
    {
        final Graph<V, E> graph;
        final Specifics specifics;

        final FibonacciHeap<QueueEntry> heap;
        final Map<V, FibonacciHeapNode<QueueEntry>> seen;

        public SearchFrontier(Graph<V, E> graph)
        {
            this.graph = graph;
            if (graph instanceof DirectedGraph) {
                this.specifics = new DirectedSpecifics((DirectedGraph<V, E>) graph);
            } else {
                this.specifics = new UndirectedSpecifics(graph);
            }
            this.heap = new FibonacciHeap<>();
            this.seen = new HashMap<>();
        }

        public void updateDistance(V v, E e, double distance)
        {
            FibonacciHeapNode<QueueEntry> node = seen.get(v);
            if (node == null) {
                node = new FibonacciHeapNode<>(new QueueEntry(e, v));
                heap.insert(node, distance);
                seen.put(v, node);
            } else {
                if (distance < node.getKey()) {
                    heap.decreaseKey(node, distance);
                    node.getData().e = e;
                }
            }
        }

        public double getDistance(V v)
        {
            FibonacciHeapNode<QueueEntry> node = seen.get(v);
            if (node == null) {
                return Double.POSITIVE_INFINITY;
            } else {
                return node.getKey();
            }
        }

        public E getTreeEdge(V v)
        {
            FibonacciHeapNode<QueueEntry> node = seen.get(v);
            if (node == null) {
                return null;
            } else {
                return node.getData().e;
            }
        }

    }

    abstract class Specifics
    {
        public abstract Set<? extends E> edgesOf(V vertex);
    }

    class DirectedSpecifics
        extends Specifics
    {

        private DirectedGraph<V, E> graph;

        public DirectedSpecifics(DirectedGraph<V, E> g)
        {
            graph = g;
        }

        @Override
        public Set<? extends E> edgesOf(V vertex)
        {
            return graph.outgoingEdgesOf(vertex);
        }
    }

    class UndirectedSpecifics
        extends Specifics
    {

        private Graph<V, E> graph;

        public UndirectedSpecifics(Graph<V, E> g)
        {
            graph = g;
        }

        @Override
        public Set<E> edgesOf(V vertex)
        {
            return graph.edgesOf(vertex);
        }
    }

    class QueueEntry
    {
        E e;
        V v;

        public QueueEntry(E e, V v)
        {
            this.e = e;
            this.v = v;
        }
    }

}

// End BidirectionalDijkstraShortestPath.java
