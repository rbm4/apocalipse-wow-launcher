package cloud.apocalipse.launcher.games.zomboid.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ZomboidJvmSettings {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT)
            .setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final Pattern HEAP_SIZE = Pattern.compile("[0-9]{1,19}[kKmMgG]?");
    private static final String DEFAULT_MAXIMUM = "3072m";
    private static final String MAX_PREFIX = "-Xmx";
    private static final String INITIAL_PREFIX = "-Xms";

    private final Path file;

    public ZomboidJvmSettings(Path installation) {
        file = installation.resolve("ProjectZomboid64.json");
    }

    public static boolean isHeapSize(String value) {
        if (!HEAP_SIZE.matcher(value).matches()) {
            return false;
        }
        BigInteger bytes = heapBytes(value);
        return bytes.signum() > 0 && bytes.bitLength() <= 63;
    }

    public Optional<String> maximumHeap() throws IOException {
        return lastHeapSize(vmArgs(read()), MAX_PREFIX);
    }

    public List<String> heapArguments() throws IOException {
        if (Files.notExists(file)) {
            return List.of(MAX_PREFIX + DEFAULT_MAXIMUM);
        }
        JsonArray arguments = vmArgs(read());
        String maximum = MAX_PREFIX + lastHeapSize(arguments, MAX_PREFIX)
                .filter(ZomboidJvmSettings::isHeapSize).orElse(DEFAULT_MAXIMUM);
        return lastHeapSize(arguments, INITIAL_PREFIX).filter(ZomboidJvmSettings::isHeapSize)
                .map(initial -> List.of(INITIAL_PREFIX + initial, maximum))
                .orElseGet(() -> List.of(maximum));
    }

    public void setMaximumHeap(String value) throws IOException {
        if (!isHeapSize(value)) {
            throw new IllegalArgumentException("Invalid maximum heap size.");
        }
        JsonObject config = read();
        JsonArray arguments = vmArgs(config);
        Optional<String> initial = lastHeapSize(arguments, INITIAL_PREFIX).filter(ZomboidJvmSettings::isHeapSize);
        if (initial.isPresent() && heapBytes(value).compareTo(heapBytes(initial.get())) < 0) {
            throw new IllegalArgumentException("Maximum heap size is below the configured initial heap size.");
        }

        boolean replaced = false;
        for (int i = 0; i < arguments.size(); i++) {
            if (heapSize(arguments, i, MAX_PREFIX).isPresent()) {
                arguments.set(i, new JsonPrimitive(MAX_PREFIX + value));
                replaced = true;
            }
        }
        if (!replaced) {
            arguments.add(MAX_PREFIX + value);
        }
        config.add("vmArgs", arguments);
        write(config);
    }

    private JsonObject read() throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject config = JSON.fromJson(reader, JsonObject.class);
            if (config == null) {
                throw new IOException("Missing Project Zomboid JVM settings in " + file);
            }
            return config;
        } catch (JsonParseException ex) {
            throw new IOException("Could not parse Project Zomboid JVM settings in " + file, ex);
        }
    }

    private JsonArray vmArgs(JsonObject config) throws IOException {
        if (!config.has("vmArgs")) {
            return new JsonArray();
        }
        if (!config.get("vmArgs").isJsonArray()) {
            throw new IOException("Expected a vmArgs array in " + file);
        }
        return config.getAsJsonArray("vmArgs");
    }

    private static Optional<String> lastHeapSize(JsonArray arguments, String prefix) {
        for (int i = arguments.size() - 1; i >= 0; i--) {
            Optional<String> value = heapSize(arguments, i, prefix);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> heapSize(JsonArray arguments, int index, String prefix) {
        var value = arguments.get(index);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String argument = value.getAsString();
            if (argument.startsWith(prefix)) {
                return Optional.of(argument.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    private static BigInteger heapBytes(String size) {
        int shift = switch (Character.toLowerCase(size.charAt(size.length() - 1))) {
            case 'k' -> 10;
            case 'm' -> 20;
            case 'g' -> 30;
            default -> 0;
        };
        return new BigInteger(shift == 0 ? size : size.substring(0, size.length() - 1)).shiftLeft(shift);
    }

    private void write(JsonObject config) throws IOException {
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "ProjectZomboid64-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(config) + System.lineSeparator());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
