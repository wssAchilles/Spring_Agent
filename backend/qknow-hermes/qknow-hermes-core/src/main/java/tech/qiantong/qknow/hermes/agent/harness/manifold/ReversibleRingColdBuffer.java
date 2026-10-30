package tech.qiantong.qknow.hermes.agent.harness.manifold;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded active windows and archive with non-destructive rejection at capacity. */
public final class ReversibleRingColdBuffer<T> {
    public static final int DEFAULT_MAX_ARCHIVE_CAPACITY = 256;

    public record Episode<T>(String id, T payload, double[] embedding) {
        public Episode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(payload, "payload");
            embedding = Objects.requireNonNull(embedding, "embedding").clone();
        }

        @Override
        public double[] embedding() {
            return embedding.clone();
        }
    }

    public record RestorationToken(String value) {
        public RestorationToken {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Restoration token is required");
            }
        }
    }

    public record Statistics(int hot, int warm, int cold, int archived, int archivedCapacity,
                             long softDeleted, long restored, long rejectedArchive) {
        public Statistics(int hot, int warm, int cold, int archived, long softDeleted, long restored) {
            this(hot, warm, cold, archived, DEFAULT_MAX_ARCHIVE_CAPACITY, softDeleted, restored, 0L);
        }
    }

    private record Stored<T>(RestorationToken token, Episode<T> episode) {}

    private final int hotCapacity;
    private final int warmCapacity;
    private final int coldCapacity;
    private final int maxArchiveCapacity;
    private final HypersphericalManifoldProjector projector;
    private final Deque<Episode<T>> hot = new ArrayDeque<>();
    private final Deque<double[]> warm = new ArrayDeque<>();
    private final LinkedHashMap<String, Stored<T>> cold = new LinkedHashMap<>();
    private final Map<String, Stored<T>> archive = new LinkedHashMap<>();
    private long softDeleted;
    private long restored;
    private long rejectedArchiveCount;

    public ReversibleRingColdBuffer(int hotCapacity, int warmCapacity, int coldCapacity,
                                    HypersphericalManifoldProjector projector) {
        this(hotCapacity, warmCapacity, coldCapacity, DEFAULT_MAX_ARCHIVE_CAPACITY, projector);
    }

    public ReversibleRingColdBuffer(int hotCapacity, int warmCapacity, int coldCapacity,
                                    int maxArchiveCapacity, HypersphericalManifoldProjector projector) {
        if (hotCapacity < 1 || warmCapacity < 1 || coldCapacity < 1) {
            throw new IllegalArgumentException("All capacities must be positive");
        }
        if (maxArchiveCapacity < 1) {
            throw new IllegalArgumentException("maxArchiveCapacity must be >= 1");
        }
        this.hotCapacity = hotCapacity;
        this.warmCapacity = warmCapacity;
        this.coldCapacity = coldCapacity;
        this.maxArchiveCapacity = maxArchiveCapacity;
        this.projector = Objects.requireNonNull(projector, "projector");
    }

    public int maxArchiveCapacity() {
        return maxArchiveCapacity;
    }

    public synchronized boolean canArchive() {
        return archive.size() < maxArchiveCapacity;
    }

    public synchronized Optional<RestorationToken> add(Episode<T> episode) {
        Objects.requireNonNull(episode, "episode");
        Episode<T> normalized = new Episode<>(episode.id(), episode.payload(),
                projector.normalize(episode.embedding()));
        if (wouldOverflowArchiveOnAdd()) {
            rejectedArchiveCount++;
            throw new IllegalStateException("Archive capacity reached; episode was not added");
        }
        if (hot.size() < hotCapacity) {
            hot.addLast(normalized);
            return Optional.empty();
        }
        Episode<T> evicted = hot.removeFirst();
        hot.addLast(normalized);
        warm.addLast(evicted.embedding());
        if (warm.size() > warmCapacity) {
            warm.removeFirst();
        }
        RestorationToken token = new RestorationToken(UUID.randomUUID().toString());
        cold.put(token.value(), new Stored<>(token, evicted));
        if (cold.size() > coldCapacity) {
            Iterator<Map.Entry<String, Stored<T>>> oldest = cold.entrySet().iterator();
            Map.Entry<String, Stored<T>> entry = oldest.next();
            archive.put(entry.getKey(), entry.getValue());
            oldest.remove();
            softDeleted++;
        }
        return Optional.of(token);
    }

    public synchronized Optional<Episode<T>> restore(RestorationToken token) {
        Objects.requireNonNull(token, "token");
        Stored<T> stored = cold.get(token.value());
        if (stored == null) {
            stored = archive.get(token.value());
        }
        if (stored == null) {
            return Optional.empty();
        }
        String episodeId = stored.episode().id();
        boolean alreadyHot = hot.stream().anyMatch(item -> item.id().equals(episodeId));
        // A full archive blocks hot promotion, but never blocks reading a retained episode.
        if (!alreadyHot && !wouldOverflowArchiveOnAdd()) {
            add(stored.episode());
        }
        restored++;
        return Optional.of(stored.episode());
    }

    public synchronized Optional<Episode<T>> restoreNearest(double[] query, double maxRadians) {
        if (!Double.isFinite(maxRadians) || maxRadians < 0.0 || maxRadians > Math.PI) {
            throw new IllegalArgumentException("maxRadians must be within [0, pi]");
        }
        double[] unitQuery = projector.normalize(query);
        Stored<T> nearest = null;
        double minDistance = Double.POSITIVE_INFINITY;
        for (Stored<T> candidate : allStored()) {
            double distance = projector.geodesic(unitQuery, candidate.episode().embedding());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = candidate;
            }
        }
        return nearest != null && minDistance <= maxRadians
                ? restore(nearest.token()) : Optional.empty();
    }

    public synchronized boolean softDelete(RestorationToken token) {
        Objects.requireNonNull(token, "token");
        Stored<T> stored = cold.get(token.value());
        if (stored == null || !MessageDigest.isEqual(
                stored.token().value().getBytes(StandardCharsets.UTF_8),
                token.value().getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        if (!canArchive()) {
            rejectedArchiveCount++;
            return false;
        }
        cold.remove(token.value());
        archive.put(token.value(), stored);
        softDeleted++;
        return true;
    }

    public synchronized Optional<HypersphericalManifoldProjector.Centroid> warmCentroid() {
        if (warm.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(projector.extrinsicCentroid(new ArrayList<>(warm)));
    }

    public synchronized Statistics statistics() {
        return new Statistics(hot.size(), warm.size(), cold.size(), archive.size(), maxArchiveCapacity,
                softDeleted, restored, rejectedArchiveCount);
    }

    private List<Stored<T>> allStored() {
        List<Stored<T>> stored = new ArrayList<>(cold.values());
        stored.addAll(archive.values());
        return stored;
    }

    private boolean wouldOverflowArchiveOnAdd() {
        return hot.size() == hotCapacity && cold.size() == coldCapacity && !canArchive();
    }
}
