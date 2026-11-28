package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher.WorkshopProgress;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher.WorkshopProgress.Status;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidLaunchMode;
import cloud.apocalipse.launcher.games.zomboid.config.ZomboidWorkshopItem;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class ViewpointWorkshop {
    private static final String STAGE_NAME = "Apocalipse Launcher - Viewpoint";
    private static final String MANIFESTS = "launcher-workshop-manifests.txt";
    private static final String ZOMBIE_BUDDY_ID = "ZombieBuddy";
    private static final String ZOMBIE_BUDDY_NAME = AgentReleaseCache.Agent.ZOMBIE_BUDDY.displayName();
    private final Path gameCache;
    private final Downloader downloader;

    @FunctionalInterface
    interface Downloader {
        Path download(List<String> ids, BiConsumer<String, SteamWorkshopDownloader.Status> progress)
                throws IOException, InterruptedException;
    }

    ViewpointWorkshop(Path gameCache, Downloader downloader) {
        this.gameCache = gameCache.toAbsolutePath().normalize();
        this.downloader = downloader;
    }

    Set<String> synchronize(Consumer<WorkshopProgress> progress, ZomboidLaunchMode mode) throws IOException, InterruptedException {
        var items = mode.properties().carriedWorkshopItems();
        if (items.isEmpty()) return Set.of();
        Files.createDirectories(gameCache);
        try (FileChannel channel = FileChannel.open(gameCache.resolve("apocalipse-viewpoint.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("Another launcher is updating the Project Zomboid Workshop stage.");
            }
            progress.accept(new WorkshopProgress(Status.PREPARING, mode.displayName(), 0, items.size()));
            Path content = downloader.download(items.stream().map(ZomboidWorkshopItem::id).toList(), (id, status) -> {
                for (int index = 0; index < items.size(); index++) {
                    var item = items.get(index);
                    if (item.id().equals(id)) {
                        Status phase = switch (status) {
                            case CHECKING -> Status.CHECKING;
                            case VERIFIED -> Status.VERIFIED;
                        };
                        String name = ZOMBIE_BUDDY_ID.equals(item.modId()) ? ZOMBIE_BUDDY_NAME : item.modId();
                        progress.accept(new WorkshopProgress(phase, name, index + 1, items.size()));
                        break;
                    }
                }
            });
            String metadata = Files.readString(content.getParent().getParent().resolve("appworkshop_108600.acf"));
            StringBuilder manifests = new StringBuilder();
            Set<String> javaApprovals = new LinkedHashSet<>();
            for (var item : items) {
                manifests.append(item.id()).append('=').append(item.manifest(metadata)).append('\n');
                javaApprovals.addAll(approvals(item, sourceMod(content, item)));
            }
            stage(content, manifests.toString(), javaApprovals, progress, mode);
            return Set.copyOf(javaApprovals);
        } catch (OverlappingFileLockException ex) {
            throw new IOException("Another launcher is updating the Project Zomboid Workshop stage.", ex);
        }
    }

    private void stage(Path content, String manifests, Set<String> javaApprovals, Consumer<WorkshopProgress> progress, ZomboidLaunchMode mode)
            throws IOException {
        Path workshop = Files.createDirectories(gameCache.resolve("Workshop"));
        Path destination = workshop.resolve(STAGE_NAME);
        Path previous = gameCache.resolve("apocalipse-viewpoint-previous");
        requireManaged(destination);
        requireManaged(previous);
        var items = mode.properties().carriedWorkshopItems();
        if (Files.exists(previous) && Files.notExists(destination)) {
            Files.move(previous, destination);
        }
        if (Files.exists(destination) && manifests.equals(Files.readString(destination.resolve(MANIFESTS)))
                && hasStagedMods(destination, javaApprovals, items)) {
            deleteTree(previous);
            return;
        }
        deleteTree(previous);
        progress.accept(new WorkshopProgress(Status.STAGING, mode.displayName(), items.size(), items.size()));
        Path staging = Files.createTempDirectory(gameCache, "apocalipse-viewpoint-");
        try {
            Path mods = Files.createDirectories(staging.resolve("Contents/mods"));
            for (var item : items) {
                Path source = sourceMod(content, item);
                Path target = mods.resolve(item.modId());
                copyTree(source, target);
                Files.writeString(target.resolve("workshop.txt"), "id=" + item.id() + "\n");
                if (ZOMBIE_BUDDY_ID.equals(item.modId())) {
                    brandZombieBuddy(target);
                }
            }
            if (!hasStagedMods(staging, javaApprovals, items)) {
                throw new IOException(
                        "Os arquivos preparados não correspondem aos verificados na Oficina Steam.");
            }
            Files.writeString(staging.resolve(MANIFESTS), manifests);
            if (Files.exists(destination)) {
                Files.move(destination, previous);
            }
            try {
                Files.move(staging, destination);
            } catch (IOException failure) {
                if (Files.exists(previous)) {
                    try {
                        Files.move(previous, destination);
                    } catch (IOException rollback) {
                        failure.addSuppressed(rollback);
                    }
                }
                throw failure;
            }
            deleteTree(previous);
        } finally {
            deleteTree(staging);
        }
    }

    private static Path sourceMod(Path content, ZomboidWorkshopItem item) {
        return content.resolve(item.id()).resolve("mods").resolve(item.modId());
    }

    private static List<String> approvals(ZomboidWorkshopItem item, Path mod) throws IOException {
        return item.javaHashes(mod).stream()
                .map(hash -> item.modId() + ":" + item.id() + ":" + hash).toList();
    }

    private static void requireManaged(Path stage) throws IOException {
        if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(stage.resolve(MANIFESTS), LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("The launcher does not own the Workshop directory " + stage);
        }
    }

    private static boolean hasStagedMods(Path stage, Set<String> javaApprovals, List<ZomboidWorkshopItem> items) {
        try {
            Set<String> stagedApprovals = new LinkedHashSet<>();
            for (var item : items) {
                Path mod = stage.resolve("Contents/mods").resolve(item.modId());
                stagedApprovals.addAll(approvals(item, mod));
                if (!("id=" + item.id() + "\n").equals(Files.readString(mod.resolve("workshop.txt")))) {
                    return false;
                }
                if (ZOMBIE_BUDDY_ID.equals(item.modId()) && !hasZombieBuddyBrand(mod)) {
                    return false;
                }
            }
            return stagedApprovals.equals(javaApprovals);
        } catch (IOException ex) {
            return false;
        }
    }

    private static void brandZombieBuddy(Path mod) throws IOException {
        for (Path info : versionMetadata(mod)) {
            var lines = Files.readAllLines(info).stream()
                    .map(line -> line.startsWith("name=") ? "name=" + ZOMBIE_BUDDY_NAME : line)
                    .toList();
            if (lines.stream().noneMatch(line -> line.startsWith("name="))) {
                Files.writeString(info, "name=" + ZOMBIE_BUDDY_NAME + "\n" + Files.readString(info));
            } else {
                Files.write(info, lines);
            }
        }
    }

    private static boolean hasZombieBuddyBrand(Path mod) throws IOException {
        List<Path> metadata = versionMetadata(mod);
        for (Path info : metadata) {
            if (!Files.readAllLines(info).contains("name=" + ZOMBIE_BUDDY_NAME)) {
                return false;
            }
        }
        return !metadata.isEmpty();
    }

    private static List<Path> versionMetadata(Path mod) throws IOException {
        try (var directories = Files.list(mod)) {
            return directories.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.resolve("mod.info"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var files = Files.walk(source)) {
            for (Path path : (Iterable<Path>) files::iterator) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(target);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.copy(path, target);
                } else {
                    throw new IOException("Unsupported file in Workshop package: " + path);
                }
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var files = Files.walk(path)) {
            for (Path child : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(child);
            }
        }
    }
}
