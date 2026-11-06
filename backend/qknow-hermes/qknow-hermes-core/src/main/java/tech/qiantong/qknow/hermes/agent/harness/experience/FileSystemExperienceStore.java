package tech.qiantong.qknow.hermes.agent.harness.experience;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class FileSystemExperienceStore implements ExperienceStore {
    private final Path root;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FileSystemExperienceStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public ExperienceEntry save(ExperienceEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ReentrantLock lock = lock(entry.domain());
        lock.lock();
        try {
            checkNotSymlink(root);
            Path domainPath = domainPath(entry.domain());
            Files.createDirectories(domainPath);
            checkNotSymlink(domainPath);
            verifyDomain(domainPath, entry.domain());
            Path entryPath = entryPath(domainPath, entry.entryId());
            Files.createDirectories(entryPath);
            checkNotSymlink(domainPath.resolve("entries"));
            checkNotSymlink(entryPath);
            Path json = entryPath.resolve("ENTRY.json");
            Path markdown = entryPath.resolve("EXPERIENCE.md");
            if (Files.exists(json) || Files.exists(markdown)) {
                if (!Files.isRegularFile(json) || !Files.isRegularFile(markdown)) {
                    throw new IOException("Incomplete current experience entry");
                }
                ExperienceEntry previous = readEntry(json, entry.domain());
                if (entry.version() != previous.version() + 1) {
                    throw new IllegalArgumentException("experience version must advance by one");
                }
                archive(entryPath, previous.version(), json, markdown);
            } else if (entry.version() != 1) {
                throw new IllegalArgumentException("new experience must start at version 1");
            }
            atomicWrite(json, JSON.toJSONString(entry));
            atomicWrite(markdown, render(entry));
            rebuildIndex(entry.domain(), domainPath);
            return entry;
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot save experience " + entry.entryId(), error);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<ExperienceEntry> find(ExperienceDomain domain, String entryId) {
        Objects.requireNonNull(domain, "domain");
        validateEntryId(entryId);
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            checkNotSymlink(root);
            Path domainPath = domainPath(domain);
            if (!Files.exists(domainPath)) {
                return Optional.empty();
            }
            checkNotSymlink(domainPath);
            verifyDomain(domainPath, domain);
            Path entryPath = entryPath(domainPath, entryId);
            if (!Files.exists(entryPath)) {
                return Optional.empty();
            }
            checkNotSymlink(domainPath.resolve("entries"));
            checkNotSymlink(entryPath);
            Path json = entryPath.resolve("ENTRY.json");
            return Files.isRegularFile(json) ? Optional.of(readEntry(json, domain)) : Optional.empty();
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read experience " + entryId, error);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<ExperienceEntry> findVersion(ExperienceDomain domain, String entryId, int version) {
        Objects.requireNonNull(domain, "domain");
        validateEntryId(entryId);
        if (version < 1) {
            return Optional.empty();
        }
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            checkNotSymlink(root);
            Path domainPath = domainPath(domain);
            if (!Files.exists(domainPath)) {
                return Optional.empty();
            }
            checkNotSymlink(domainPath);
            verifyDomain(domainPath, domain);
            Path entryPath = entryPath(domainPath, entryId);
            Path archivedJson = entryPath.resolve("versions").resolve(Integer.toString(version)).resolve("ENTRY.json");
            if (!Files.exists(archivedJson)) {
                return Optional.empty();
            }
            checkNotSymlink(entryPath);
            checkNotSymlink(entryPath.resolve("versions"));
            checkNotSymlink(archivedJson.getParent());
            checkNotSymlink(archivedJson);
            return Files.isRegularFile(archivedJson)
                    ? Optional.of(readEntry(archivedJson, domain)) : Optional.empty();
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot read experience version " + version, error);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ExperienceEntry> list(ExperienceDomain domain) {
        Objects.requireNonNull(domain, "domain");
        ReentrantLock lock = lock(domain);
        lock.lock();
        try {
            checkNotSymlink(root);
            Path domainPath = domainPath(domain);
            if (!Files.exists(domainPath)) {
                return List.of();
            }
            checkNotSymlink(domainPath);
            verifyDomain(domainPath, domain);
            Path entriesPath = domainPath.resolve("entries");
            if (!Files.exists(entriesPath)) {
                return List.of();
            }
            checkNotSymlink(entriesPath);
            List<ExperienceEntry> entries = new ArrayList<>();
            try (var children = Files.list(entriesPath)) {
                for (Path child : children.sorted().toList()) {
                    checkNotSymlink(child);
                    Path json = child.resolve("ENTRY.json");
                    if (Files.isDirectory(child) && Files.isRegularFile(json)) {
                        entries.add(readEntry(json, domain));
                    }
                }
            }
            return List.copyOf(entries);
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot list experiences for " + domain.toSlug(), error);
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lock(ExperienceDomain domain) {
        return locks.computeIfAbsent(domain.toSlug(), ignored -> new ReentrantLock());
    }

    private Path domainPath(ExperienceDomain domain) {
        Path path = root.resolve(domain.toSlug()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("domain escaped experience root");
        }
        return path;
    }

    private static Path entryPath(Path domainPath, String entryId) {
        validateEntryId(entryId);
        return domainPath.resolve("entries").resolve(entryId);
    }

    private static void validateEntryId(String entryId) {
        if (entryId == null || !entryId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || entryId.equals("..")) {
            throw new IllegalArgumentException("safe entryId is required");
        }
    }

    private static void checkNotSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Symlink is not allowed in experience storage: " + path);
        }
    }

    private static void verifyDomain(Path path, ExperienceDomain domain) throws IOException {
        Path marker = path.resolve("DOMAIN.json");
        if (!Files.exists(marker)) {
            try (var children = Files.list(path)) {
                if (children.findAny().isPresent()) {
                    throw new IOException("Domain marker is missing for nonempty directory");
                }
            }
            atomicWrite(marker, JSON.toJSONString(domain));
            return;
        }
        checkNotSymlink(marker);
        ExperienceDomain existing = JSON.parseObject(Files.readString(marker, StandardCharsets.UTF_8),
                ExperienceDomain.class);
        if (!domain.equals(existing)) {
            throw new IOException("Domain slug collision: " + domain.toSlug());
        }
    }

    private static ExperienceEntry readEntry(Path json, ExperienceDomain domain) throws IOException {
        checkNotSymlink(json);
        ExperienceEntry entry = JSON.parseObject(Files.readString(json, StandardCharsets.UTF_8),
                ExperienceEntry.class);
        if (entry == null || !domain.equals(entry.domain())) {
            throw new IOException("Entry domain does not match its directory");
        }
        return entry;
    }

    private static void archive(Path entryPath, int version, Path json, Path markdown) throws IOException {
        Path versions = entryPath.resolve("versions");
        Files.createDirectories(versions);
        checkNotSymlink(versions);
        Path target = versions.resolve(Integer.toString(version));
        if (Files.exists(target)) {
            throw new IOException("Experience version is already archived");
        }
        Path temporary = versions.resolve(".tmp_" + UUID.randomUUID());
        Files.createDirectory(temporary);
        try {
            checkNotSymlink(json);
            checkNotSymlink(markdown);
            Files.writeString(temporary.resolve("ENTRY.json"),
                    Files.readString(json, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            Files.writeString(temporary.resolve("EXPERIENCE.md"),
                    Files.readString(markdown, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            Files.deleteIfExists(temporary.resolve("ENTRY.json"));
            Files.deleteIfExists(temporary.resolve("EXPERIENCE.md"));
            Files.deleteIfExists(temporary);
            throw error;
        }
    }

    private void rebuildIndex(ExperienceDomain domain, Path domainPath) throws IOException {
        List<ExperienceEntry> entries = list(domain);
        StringBuilder content = new StringBuilder("# Experience Index: ")
                .append(domain.toSlug()).append("\n\nUpdated: ").append(Instant.now()).append("\n\n");
        entries.stream().filter(entry -> !entry.retired())
                .sorted(Comparator.comparing(ExperienceEntry::entryId))
                .forEach(entry -> appendIndexLine(content, entry));
        content.append("\n## Retired Entries\n");
        entries.stream().filter(ExperienceEntry::retired)
                .sorted(Comparator.comparing(ExperienceEntry::entryId))
                .forEach(entry -> appendIndexLine(content, entry));
        atomicWrite(domainPath.resolve("INDEX.md"), content.toString());
    }

    private static void appendIndexLine(StringBuilder content, ExperienceEntry entry) {
        content.append("- ").append(entry.entryId()).append(" | ")
                .append(entry.disposition()).append(" | ")
                .append(entry.confidence()).append(" | ")
                .append(entry.cue().replace('\n', ' ')).append("\n");
    }

    private static String render(ExperienceEntry entry) {
        StringBuilder content = new StringBuilder("# Experience: ").append(entry.entryId()).append("\n\n");
        if (entry.retired()) {
            content.append("> [!WARNING] RETIRED: ").append(entry.retirementReason()).append("\n\n");
        }
        return content.append("Disposition: ").append(entry.disposition())
                .append("\nAttribution: ").append(entry.attributionStatus())
                .append("\nFailure observed: ").append(entry.failureObserved())
                .append("\nEvidence: ").append(entry.evidenceRef().evidenceId())
                .append("\n\n## Cue\n").append(entry.cue())
                .append("\n\n## Lesson\n").append(entry.lesson())
                .append("\n\n## Evidence Summary\n").append(entry.evidenceSummary())
                .append("\n").toString();
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".tmp_", "");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            throw new IOException("ATOMIC_MOVE is required for experience storage", error);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
