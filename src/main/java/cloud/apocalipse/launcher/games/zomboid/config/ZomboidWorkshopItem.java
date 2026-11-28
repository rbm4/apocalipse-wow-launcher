package cloud.apocalipse.launcher.games.zomboid.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import cloud.apocalipse.launcher.core.FileHashes;

public record ZomboidWorkshopItem(String id, String modId, boolean isJava) {
    private static final Pattern MANIFEST_VALUE = Pattern.compile("\"manifest\"\\s+\"([0-9]+)\"");
    private static final Pattern JAVA_MOD = Pattern.compile("javaJarFile[0-9]*=(.+)", Pattern.CASE_INSENSITIVE);

    public String manifest(String metadata) throws IOException {
        var item = Pattern.compile("\"" + id + "\"\\s*\\{([^{}]*)}").matcher(metadata);
        if (item.find()) {
            var value = MANIFEST_VALUE.matcher(item.group(1));
            if (value.find() && !"0".equals(value.group(1))) {
                return value.group(1);
            }
        }
        throw new IOException("SteamCMD did not record a Workshop manifest for item " + id + ".");
    }

    public Set<String> javaHashes(Path mod) throws IOException {
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
            if (!isJava) {
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
