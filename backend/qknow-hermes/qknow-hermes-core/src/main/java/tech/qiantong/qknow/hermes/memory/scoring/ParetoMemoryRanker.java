package tech.qiantong.qknow.hermes.memory.scoring;

import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Fast non-dominated sorting and crowding-distance selection for memory candidates. */
public final class ParetoMemoryRanker {

    private ParetoMemoryRanker() {
    }

    /** Immutable objective payload for the episodic MemoryNode path. */
    public record MemoryObjectiveVector(
            MemoryNode node,
            double recency,
            double relevance,
            double importance,
            double crowdingDistance,
            int paretoRank) {
        public MemoryObjectiveVector {
            Objects.requireNonNull(node, "node");
            recency = unit(recency);
            relevance = unit(relevance);
            importance = unit(importance);
            crowdingDistance = crowding(crowdingDistance);
            paretoRank = Math.max(0, paretoRank);
        }

        private ObjectiveVector<MemoryObjectiveVector> objectiveVector() {
            return new ObjectiveVector<>(this, List.of(recency, relevance, importance), relevance,
                    crowdingDistance, paretoRank);
        }
    }

    /** Generic payload lets the live VectorStore path reuse the same sorting implementation. */
    public record ObjectiveVector<T>(
            T item,
            List<Double> objectives,
            double tieBreaker,
            double crowdingDistance,
            int paretoRank) {
        public ObjectiveVector {
            Objects.requireNonNull(item, "item");
            if (objectives == null || objectives.isEmpty()) {
                throw new IllegalArgumentException("At least one objective is required");
            }
            List<Double> normalized = new ArrayList<>(objectives.size());
            for (Double objective : objectives) {
                normalized.add(unit(objective == null ? 0.0 : objective));
            }
            objectives = List.copyOf(normalized);
            tieBreaker = unit(tieBreaker);
            crowdingDistance = crowding(crowdingDistance);
            paretoRank = Math.max(0, paretoRank);
        }

        private ObjectiveVector<T> withCrowdingDistance(double value) {
            return new ObjectiveVector<>(item, objectives, tieBreaker, value, paretoRank);
        }

        private ObjectiveVector<T> withParetoRank(int value) {
            return new ObjectiveVector<>(item, objectives, tieBreaker, crowdingDistance, value);
        }
    }

    public static boolean dominates(MemoryObjectiveVector a, MemoryObjectiveVector b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return dominates(a.objectiveVector(), b.objectiveVector());
    }

    public static <T> boolean dominates(ObjectiveVector<T> a, ObjectiveVector<T> b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.objectives().size() != b.objectives().size()) {
            throw new IllegalArgumentException("Objective vectors must have equal dimensions");
        }
        boolean strictlyBetter = false;
        for (int i = 0; i < a.objectives().size(); i++) {
            double left = a.objectives().get(i);
            double right = b.objectives().get(i);
            if (left < right) {
                return false;
            }
            strictlyBetter |= left > right;
        }
        return strictlyBetter;
    }

    public static List<List<MemoryObjectiveVector>> computeParetoFronts(List<MemoryObjectiveVector> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ObjectiveVector<MemoryObjectiveVector>> wrapped = new ArrayList<>(candidates.size());
        for (MemoryObjectiveVector candidate : candidates) {
            wrapped.add(new ObjectiveVector<>(Objects.requireNonNull(candidate, "candidate"),
                    List.of(candidate.recency(), candidate.relevance(), candidate.importance()),
                    candidate.relevance(), candidate.crowdingDistance(), candidate.paretoRank()));
        }
        List<List<ObjectiveVector<MemoryObjectiveVector>>> ranked = computeObjectiveFronts(wrapped);
        List<List<MemoryObjectiveVector>> result = new ArrayList<>(ranked.size());
        for (List<ObjectiveVector<MemoryObjectiveVector>> front : ranked) {
            List<MemoryObjectiveVector> converted = new ArrayList<>(front.size());
            for (ObjectiveVector<MemoryObjectiveVector> item : front) {
                MemoryObjectiveVector original = item.item();
                converted.add(new MemoryObjectiveVector(original.node(), original.recency(),
                        original.relevance(), original.importance(), item.crowdingDistance(), item.paretoRank()));
            }
            result.add(List.copyOf(converted));
        }
        return List.copyOf(result);
    }

    public static List<MemoryObjectiveVector> assignCrowdingDistance(List<MemoryObjectiveVector> front) {
        if (front == null || front.isEmpty()) {
            return List.of();
        }
        List<ObjectiveVector<MemoryObjectiveVector>> wrapped = new ArrayList<>(front.size());
        for (MemoryObjectiveVector candidate : front) {
            Objects.requireNonNull(candidate, "candidate");
            wrapped.add(candidate.objectiveVector());
        }
        List<ObjectiveVector<MemoryObjectiveVector>> assigned = assignObjectiveCrowdingDistance(wrapped);
        List<MemoryObjectiveVector> result = new ArrayList<>(assigned.size());
        for (ObjectiveVector<MemoryObjectiveVector> item : assigned) {
            MemoryObjectiveVector original = item.item();
            result.add(new MemoryObjectiveVector(original.node(), original.recency(), original.relevance(),
                    original.importance(), item.crowdingDistance(), item.paretoRank()));
        }
        return List.copyOf(result);
    }

    public static <T> List<List<ObjectiveVector<T>>> computeObjectiveFronts(List<ObjectiveVector<T>> candidates) {
        return computeObjectiveFronts(candidates, Integer.MAX_VALUE);
    }

    private static <T> List<List<ObjectiveVector<T>>> computeObjectiveFronts(
            List<ObjectiveVector<T>> candidates, int stopAfterRankedCandidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int size = candidates.size();
        int[] dominatedByCount = new int[size];
        List<List<Integer>> dominates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dominates.add(new ArrayList<>());
        }
        int dimensions = candidates.get(0).objectives().size();
        double[][] objectiveValues = new double[size][dimensions];
        for (int i = 0; i < size; i++) {
            ObjectiveVector<T> candidate = candidates.get(i);
            Objects.requireNonNull(candidate, "candidate");
            if (candidate.objectives().size() != dimensions) {
                throw new IllegalArgumentException("All candidates must have equal objective dimensions");
            }
            for (int d = 0; d < dimensions; d++) {
                objectiveValues[i][d] = candidate.objectives().get(d);
            }
        }

        List<Integer> firstFront = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                int dominance = dominanceOrder(objectiveValues[i], objectiveValues[j]);
                if (dominance > 0) {
                    dominates.get(i).add(j);
                    dominatedByCount[j]++;
                } else if (dominance < 0) {
                    dominates.get(j).add(i);
                    dominatedByCount[i]++;
                }
            }
            if (dominatedByCount[i] == 0) {
                firstFront.add(i);
            }
        }

        List<List<ObjectiveVector<T>>> fronts = new ArrayList<>();
        List<Integer> currentFront = firstFront;
        int rank = 1;
        int rankedCandidates = 0;
        while (!currentFront.isEmpty()) {
            List<ObjectiveVector<T>> front = new ArrayList<>(currentFront.size());
            List<Integer> nextFront = new ArrayList<>();
            for (int index : currentFront) {
                front.add(candidates.get(index).withParetoRank(rank));
                for (int dominatedIndex : dominates.get(index)) {
                    dominatedByCount[dominatedIndex]--;
                    if (dominatedByCount[dominatedIndex] == 0) {
                        nextFront.add(dominatedIndex);
                    }
                }
            }
            fronts.add(List.copyOf(front));
            rankedCandidates += currentFront.size();
            if (rankedCandidates >= stopAfterRankedCandidates) {
                break;
            }
            currentFront = nextFront;
            rank++;
        }
        return List.copyOf(fronts);
    }

    public static <T> List<ObjectiveVector<T>> assignObjectiveCrowdingDistance(List<ObjectiveVector<T>> front) {
        if (front == null || front.isEmpty()) {
            return List.of();
        }
        int size = front.size();
        int dimensions = front.get(0).objectives().size();
        double[] distances = new double[size];
        for (ObjectiveVector<T> candidate : front) {
            if (candidate.objectives().size() != dimensions) {
                throw new IllegalArgumentException("All candidates must have equal objective dimensions");
            }
        }

        for (int dimension = 0; dimension < dimensions; dimension++) {
            final int objectiveIndex = dimension;
            List<Integer> sorted = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                sorted.add(i);
            }
            sorted.sort(Comparator.comparingDouble((Integer i) -> front.get(i).objectives().get(objectiveIndex))
                    .thenComparingInt(Integer::intValue));
            double minimum = front.get(sorted.get(0)).objectives().get(dimension);
            double maximum = front.get(sorted.get(size - 1)).objectives().get(dimension);
            if (maximum <= minimum) {
                continue;
            }
            distances[sorted.get(0)] = Double.POSITIVE_INFINITY;
            distances[sorted.get(size - 1)] = Double.POSITIVE_INFINITY;
            for (int position = 1; position < size - 1; position++) {
                int index = sorted.get(position);
                if (!Double.isInfinite(distances[index])) {
                    double previous = front.get(sorted.get(position - 1)).objectives().get(dimension);
                    double next = front.get(sorted.get(position + 1)).objectives().get(dimension);
                    distances[index] += (next - previous) / (maximum - minimum);
                }
            }
        }

        List<ObjectiveVector<T>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(front.get(i).withCrowdingDistance(distances[i]));
        }
        return List.copyOf(result);
    }

    public static <T> List<T> rankObjectivesTopK(List<ObjectiveVector<T>> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<ObjectiveVector<T>> firstFront = computeFirstObjectiveFront(candidates);
        if (firstFront.size() >= topK) {
            List<ObjectiveVector<T>> diversified = new ArrayList<>(assignObjectiveCrowdingDistance(firstFront));
            diversified.sort(Comparator.comparingDouble(ObjectiveVector<T>::crowdingDistance).reversed()
                    .thenComparing(Comparator.comparingDouble(ObjectiveVector<T>::tieBreaker).reversed()));
            List<T> result = new ArrayList<>(topK);
            for (int i = 0; i < topK; i++) {
                result.add(diversified.get(i).item());
            }
            return List.copyOf(result);
        }
        List<T> result = new ArrayList<>(Math.min(topK, candidates.size()));
        for (List<ObjectiveVector<T>> front : computeObjectiveFronts(candidates, topK)) {
            int remaining = topK - result.size();
            if (front.size() <= remaining) {
                List<ObjectiveVector<T>> ordered = new ArrayList<>(front);
                ordered.sort(Comparator.comparingDouble(ObjectiveVector<T>::tieBreaker).reversed());
                for (ObjectiveVector<T> candidate : ordered) {
                    result.add(candidate.item());
                }
                continue;
            }
            List<ObjectiveVector<T>> diversified = new ArrayList<>(assignObjectiveCrowdingDistance(front));
            diversified.sort(Comparator.comparingDouble(ObjectiveVector<T>::crowdingDistance).reversed()
                    .thenComparing(Comparator.comparingDouble(ObjectiveVector<T>::tieBreaker).reversed()));
            for (int i = 0; i < Math.min(remaining, diversified.size()); i++) {
                result.add(diversified.get(i).item());
            }
            break;
        }
        return List.copyOf(result);
    }

    private static <T> List<ObjectiveVector<T>> computeFirstObjectiveFront(List<ObjectiveVector<T>> candidates) {
        int size = candidates.size();
        int dimensions = candidates.get(0).objectives().size();
        double[][] values = new double[size][dimensions];
        boolean[] dominated = new boolean[size];
        for (int i = 0; i < size; i++) {
            ObjectiveVector<T> candidate = Objects.requireNonNull(candidates.get(i), "candidate");
            if (candidate.objectives().size() != dimensions) {
                throw new IllegalArgumentException("All candidates must have equal objective dimensions");
            }
            for (int dimension = 0; dimension < dimensions; dimension++) {
                values[i][dimension] = candidate.objectives().get(dimension);
            }
        }
        if (dimensions == 3) {
            List<double[]> points = new ArrayList<>(size);
            for (double[] value : values) {
                points.add(value);
            }
            List<ObjectiveVector<T>> firstFront = new ArrayList<>();
            for (int index : firstFrontIndices(points)) {
                firstFront.add(candidates.get(index).withParetoRank(1));
            }
            return firstFront;
        }
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                int dominance = dominanceOrder(values[i], values[j]);
                if (dominance > 0) {
                    dominated[j] = true;
                } else if (dominance < 0) {
                    dominated[i] = true;
                }
            }
        }
        List<ObjectiveVector<T>> firstFront = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (!dominated[i]) {
                firstFront.add(candidates.get(i).withParetoRank(1));
            }
        }
        return firstFront;
    }

    public static List<MemoryNode> rankTopK(List<MemoryNode> candidates, float[] queryEmbedding, int topK,
                                            long nowTimestamp, MemoryScoringService scoringService) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        Objects.requireNonNull(scoringService, "scoringService");
        List<MemoryNode> nodes = new ArrayList<>(candidates.size());
        List<double[]> objectives = new ArrayList<>(candidates.size());
        for (MemoryNode node : candidates) {
            if (node == null) {
                continue;
            }
            float[] embedding = node.getEmbedding();
            if (embedding != null && queryEmbedding != null && embedding.length != queryEmbedding.length) {
                continue;
            }
            double recency = scoringService.computeRecency(node, nowTimestamp);
            double relevance = unit(scoringService.computeCosineSimilarity(node.getEmbedding(), queryEmbedding));
            double importance = unit(node.getImportance());
            nodes.add(node);
            objectives.add(new double[]{unit(recency), relevance, importance});
        }
        if (nodes.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(topK, nodes.size());
        int[] firstFront = firstFrontIndices(objectives);
        if (firstFront.length >= limit) {
            return selectMemoryFront(nodes, objectives, firstFront, limit);
        }
        List<ObjectiveVector<MemoryNode>> vectors = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            double[] values = objectives.get(i);
            vectors.add(new ObjectiveVector<>(nodes.get(i), List.of(values[0], values[1], values[2]), values[1], 0.0, 0));
        }
        return rankObjectivesTopK(vectors, limit);
    }

    private static int[] firstFrontIndices(List<double[]> objectives) {
        int size = objectives.size();
        Integer[] sorted = new Integer[size];
        double[] relevanceCoordinates = new double[size];
        for (int i = 0; i < size; i++) {
            sorted[i] = i;
            relevanceCoordinates[i] = objectives.get(i)[1];
        }
        Arrays.sort(sorted, Comparator.<Integer>comparingDouble(i -> objectives.get(i)[0]).reversed()
                .thenComparing(Comparator.comparingDouble((Integer i) -> objectives.get(i)[1]).reversed())
                .thenComparing(Comparator.comparingDouble((Integer i) -> objectives.get(i)[2]).reversed())
                .thenComparingInt(Integer::intValue));
        Arrays.sort(relevanceCoordinates);

        // A Fenwick prefix maximum answers whether any point with higher recency,
        // or equal recency and higher relevance, also has sufficient importance.
        double[] maximumImportanceTree = new double[size + 1];
        Arrays.fill(maximumImportanceTree, Double.NEGATIVE_INFINITY);
        boolean[] dominated = new boolean[size];
        for (int groupStart = 0; groupStart < size;) {
            int groupEnd = groupStart + 1;
            double groupRecency = objectives.get(sorted[groupStart])[0];
            while (groupEnd < size && objectives.get(sorted[groupEnd])[0] == groupRecency) {
                groupEnd++;
            }

            // Equal-recency points enter the tree by descending relevance. Query
            // before inserting an equal-relevance block to preserve strictness.
            for (int blockStart = groupStart; blockStart < groupEnd;) {
                int blockEnd = blockStart + 1;
                double blockRelevance = objectives.get(sorted[blockStart])[1];
                while (blockEnd < groupEnd
                        && objectives.get(sorted[blockEnd])[1] == blockRelevance) {
                    blockEnd++;
                }
                int coordinate = size - lowerBound(relevanceCoordinates, blockRelevance);
                double higherRelevanceMaximum = fenwickMaximum(maximumImportanceTree, coordinate);
                double sameRelevanceMaximum = Double.NEGATIVE_INFINITY;
                for (int position = blockStart; position < blockEnd; position++) {
                    sameRelevanceMaximum = Math.max(sameRelevanceMaximum,
                            objectives.get(sorted[position])[2]);
                }
                for (int position = blockStart; position < blockEnd; position++) {
                    double importance = objectives.get(sorted[position])[2];
                    if (higherRelevanceMaximum >= importance || sameRelevanceMaximum > importance) {
                        dominated[sorted[position]] = true;
                    }
                }
                for (int position = blockStart; position < blockEnd; position++) {
                    fenwickUpdate(maximumImportanceTree, coordinate,
                            objectives.get(sorted[position])[2]);
                }
                blockStart = blockEnd;
            }
            groupStart = groupEnd;
        }
        int count = 0;
        for (boolean isDominated : dominated) {
            if (!isDominated) {
                count++;
            }
        }
        int[] front = new int[count];
        for (int i = 0, position = 0; i < size; i++) {
            if (!dominated[i]) {
                front[position++] = i;
            }
        }
        return front;
    }

    private static int lowerBound(double[] sorted, double value) {
        int low = 0;
        int high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static double fenwickMaximum(double[] tree, int index) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int cursor = index; cursor > 0; cursor -= cursor & -cursor) {
            maximum = Math.max(maximum, tree[cursor]);
        }
        return maximum;
    }

    private static void fenwickUpdate(double[] tree, int index, double value) {
        for (int cursor = index; cursor < tree.length; cursor += cursor & -cursor) {
            tree[cursor] = Math.max(tree[cursor], value);
        }
    }

    private static List<MemoryNode> selectMemoryFront(List<MemoryNode> nodes, List<double[]> objectives,
                                                       int[] front, int topK) {
        double[] distances = new double[nodes.size()];
        for (int dimension = 0; dimension < 3; dimension++) {
            final int objectiveIndex = dimension;
            Integer[] sorted = new Integer[front.length];
            for (int i = 0; i < front.length; i++) {
                sorted[i] = front[i];
            }
            java.util.Arrays.sort(sorted, Comparator.<Integer>comparingDouble(i -> objectives.get(i)[objectiveIndex])
                    .thenComparingInt(Integer::intValue));
            double minimum = objectives.get(sorted[0])[dimension];
            double maximum = objectives.get(sorted[sorted.length - 1])[dimension];
            if (maximum <= minimum) {
                continue;
            }
            distances[sorted[0]] = Double.POSITIVE_INFINITY;
            distances[sorted[sorted.length - 1]] = Double.POSITIVE_INFINITY;
            for (int position = 1; position < sorted.length - 1; position++) {
                int index = sorted[position];
                if (!Double.isInfinite(distances[index])) {
                    double previous = objectives.get(sorted[position - 1])[dimension];
                    double next = objectives.get(sorted[position + 1])[dimension];
                    distances[index] += (next - previous) / (maximum - minimum);
                }
            }
        }
        Integer[] selected = new Integer[front.length];
        for (int i = 0; i < front.length; i++) {
            selected[i] = front[i];
        }
        java.util.Arrays.sort(selected, Comparator.<Integer>comparingDouble(i -> distances[i]).reversed()
                .thenComparing(Comparator.comparingDouble((Integer i) -> objectives.get(i)[1]).reversed()));
        List<MemoryNode> result = new ArrayList<>(topK);
        for (int i = 0; i < topK; i++) {
            result.add(nodes.get(selected[i]));
        }
        return List.copyOf(result);
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    private static int dominanceOrder(double[] a, double[] b) {
        if (a.length == 3) {
            boolean aNotWorse = a[0] >= b[0] && a[1] >= b[1] && a[2] >= b[2];
            if (aNotWorse) {
                return a[0] > b[0] || a[1] > b[1] || a[2] > b[2] ? 1 : 0;
            }
            boolean bNotWorse = b[0] >= a[0] && b[1] >= a[1] && b[2] >= a[2];
            return bNotWorse ? -1 : 0;
        }
        boolean aBetter = false;
        boolean bBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                aBetter = true;
            } else if (a[i] < b[i]) {
                bBetter = true;
            }
            if (aBetter && bBetter) {
                return 0;
            }
        }
        return aBetter ? 1 : bBetter ? -1 : 0;
    }

    private static double crowding(double value) {
        return Double.isNaN(value) || value < 0.0 ? 0.0 : value;
    }
}
