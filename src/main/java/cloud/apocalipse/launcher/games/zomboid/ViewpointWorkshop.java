package cloud.apocalipse.launcher.games.zomboid;

import cloud.apocalipse.launcher.core.FileHashes;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher.WorkshopProgress;
import cloud.apocalipse.launcher.games.zomboid.ProjectZomboidLauncher.WorkshopProgress.Status;

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
import java.util.regex.Pattern;

final class ViewpointWorkshop {
    private static final String STAGE_NAME = "Apocalipse Launcher - Viewpoint";
    private static final String MANIFESTS = "launcher-workshop-manifests.txt";
    private static final String ZOMBIE_BUDDY_ID = "ZombieBuddy";
    private static final String ZOMBIE_BUDDY_NAME = AgentReleaseCache.Agent.ZOMBIE_BUDDY.displayName();
    private static final Pattern MANIFEST_VALUE = Pattern.compile("\"manifest\"\\s+\"([0-9]+)\"");
    private static final Pattern JAVA_MOD = Pattern.compile("javaJarFile[0-9]*=(.+)", Pattern.CASE_INSENSITIVE);
    private static final List<Item> ITEMS = List.of(new Item("3619862853", ZOMBIE_BUDDY_ID, false),
            new Item("3809306528", "Viewpoint", true), new Item("3810302175", "PZVoxelStudioViewpoint", false));
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

    Set<String> synchronize(Consumer<WorkshopProgress> progress) throws IOException, InterruptedException {
        Files.createDirectories(gameCache);
        try (FileChannel channel = FileChannel.open(gameCache.resolve("apocalipse-viewpoint.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("Another launcher is updating the Viewpoint template.");
            }
            progress.accept(new WorkshopProgress(Status.PREPARING, "Viewpoint", 0, ITEMS.size()));
            Path content = downloader.download(ITEMS.stream().map(Item::id).toList(), (id, status) -> {
                for (int index = 0; index < ITEMS.size(); index++) {
                    Item item = ITEMS.get(index);
                    if (item.id().equals(id)) {
                        Status phase = switch (status) {
                            case CHECKING -> Status.CHECKING;
                            case VERIFIED -> Status.VERIFIED;
                        };
                        progress.accept(new WorkshopProgress(phase, item.displayName(), index + 1, ITEMS.size()));
                        break;
                    }
                }
            });
            String metadata = Files.readString(content.getParent().getParent().resolve("appworkshop_108600.acf"));
            StringBuilder manifests = new StringBuilder();
            Set<String> javaHashes = new LinkedHashSet<>();
            for (Item item : ITEMS) {
                manifests.append(item.id()).append('=').append(item.manifest(metadata)).append('\n');
                javaHashes.addAll(item.javaHashes(content.resolve(item.id()).resolve("mods").resolve(item.modId())));
            }
            stage(content, manifests.toString(), javaHashes, progress);
            return Set.copyOf(javaHashes);
        } catch (OverlappingFileLockException ex) {
            throw new IOException("Another launcher is updating the Viewpoint template.", ex);
        }
    }

    private void stage(Path content, String manifests, Set<String> javaHashes, Consumer<WorkshopProgress> progress)
            throws IOException {
        Path workshop = Files.createDirectories(gameCache.resolve("Workshop"));
        Path destination = workshop.resolve(STAGE_NAME);
        Path previous = gameCache.resolve("apocalipse-viewpoint-previous");
        requireManaged(destination);
        requireManaged(previous);
        if (Files.exists(previous) && Files.notExists(destination)) {
            Files.move(previous, destination);
        }
        if (Files.exists(destination) && manifests.equals(Files.readString(destination.resolve(MANIFESTS)))
                && hasStagedMods(destination, javaHashes)) {
            deleteTree(previous);
            return;
        }
        deleteTree(previous);

        progress.accept(new WorkshopProgress(Status.STAGING, "Viewpoint", ITEMS.size(), ITEMS.size()));
        Path staging = Files.createTempDirectory(gameCache, "apocalipse-viewpoint-");
        try {
            Path mods = Files.createDirectories(staging.resolve("Contents/mods"));
            for (Item item : ITEMS) {
                Path source = content.resolve(item.id()).resolve("mods").resolve(item.modId());
                Path target = mods.resolve(item.modId());
                copyTree(source, target);
                Files.writeString(target.resolve("workshop.txt"), "id=" + item.id() + "\n");
                if (ZOMBIE_BUDDY_ID.equals(item.modId())) {
                    brandZombieBuddy(target);
                }
            }
            if (!hasStagedMods(staging, javaHashes)) {
                throw new IOException("Os arquivos preparados do Viewpoint não correspondem aos verificados na Oficina Steam.");
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

    private static void requireManaged(Path stage) throws IOException {
        if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(stage.resolve(MANIFESTS), LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("The launcher does not own the Viewpoint directory " + stage);
        }
    }

    private static boolean hasStagedMods(Path stage, Set<String> javaHashes) {
        try {
            Set<String> stagedHashes = new LinkedHashSet<>();
            for (Item item : ITEMS) {
                Path mod = stage.resolve("Contents/mods").resolve(item.modId());
                stagedHashes.addAll(item.javaHashes(mod));
                if (!("id=" + item.id() + "\n").equals(Files.readString(mod.resolve("workshop.txt")))) {
                    return false;
                }
                if (ZOMBIE_BUDDY_ID.equals(item.modId()) && !hasZombieBuddyBrand(mod)) {
                    return false;
                }
            }
            return stagedHashes.equals(javaHashes);
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

    private record Item(String id, String modId, boolean javaMod) {
        String displayName() {
            return ZOMBIE_BUDDY_ID.equals(modId) ? ZOMBIE_BUDDY_NAME : modId;
        }

        String manifest(String metadata) throws IOException {
            var item = Pattern.compile("\"" + id + "\"\\s*\\{([^{}]*)}").matcher(metadata);
            if (item.find()) {
                var value = MANIFEST_VALUE.matcher(item.group(1));
                if (value.find() && !"0".equals(value.group(1))) {
                    return value.group(1);
                }
            }
            throw new IOException("SteamCMD did not record a Workshop manifest for item " + id + ".");
        }

        Set<String> javaHashes(Path mod) throws IOException {
            List<Path> directories;
            try (var files = Files.list(mod)) {
                directories = files.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList();
            }
            boolean foundMetadata = false;
            var hashes = new LinkedHashSet<String>();
            Path realMod = mod.toRealPath();
            for (Path directory : directories) {
                Path info = directory.resolve("mod.info");
                if (!Files.isRegularFile(info, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                List<String> lines = Files.readAllLines(info).stream().map(String::strip).toList();
                if (!lines.contains("id=" + modId)) {
                    continue;
                }
                foundMetadata = true;
                if (!javaMod) {
                    return Set.of();
                }
                collectJavaHashes(mod, realMod, directories, lines, hashes);
            }
            if (!hashes.isEmpty()) {
                return hashes;
            }
            String missing = foundMetadata ? " its declared Java mod JAR." : " mod.info for " + modId + ".";
            throw new IOException("Workshop item " + id + " is missing" + missing);
        }

        private static void collectJavaHashes(Path mod, Path realMod, List<Path> directories,
                                              List<String> lines, Set<String> hashes) throws IOException {
            for (String line : lines) {
                var jar = JAVA_MOD.matcher(line);
                if (jar.matches()) {
                    for (Path directory : directories) {
                        Path candidate = directory.resolve(jar.group(1).strip()).normalize();
                        if (candidate.startsWith(mod) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                            Path realJar = candidate.toRealPath();
                            if (realJar.startsWith(realMod)) {
                                hashes.add(FileHashes.sha256(realJar));
                            }
                        }
                    }
                }
            }
        }
    }
}
