package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ManifestClient {
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private final HttpClient httpClient;

    ManifestClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    PatchManifest fetch(String manifestUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(manifestUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Manifest request failed with HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    private PatchManifest parse(String json) {
        int version = intField(json, "version", 0);
        String format = stringField(json, "format", "");
        List<PatchFile> files = parseFiles(json);
        return new PatchManifest(version, format, files);
    }

    private List<PatchFile> parseFiles(String json) {
        List<PatchFile> files = new ArrayList<>();
        String filesArray = extractArray(json, "files");
        if (filesArray.isBlank()) {
            return files;
        }
        for (String object : splitObjects(filesArray)) {
            files.add(new PatchFile(
                    stringField(object, "name", ""),
                    stringField(object, "client_path", ""),
                    stringField(object, "download_url", ""),
                    stringField(object, "sha256", ""),
                    longField(object, "size", 0)
            ));
        }
        return files;
    }

    private static int intField(String json, String name, int defaultValue) {
        Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static long longField(String json, String name, long defaultValue) {
        Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String stringField(String json, String name, String defaultValue) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find()) {
            return defaultValue;
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static String extractArray(String json, String name) {
        String marker = "\"" + name + "\"";
        int nameIndex = json.indexOf(marker);
        if (nameIndex < 0) {
            return "";
        }
        int arrayStart = json.indexOf('[', nameIndex + marker.length());
        if (arrayStart < 0) {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = arrayStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(arrayStart + 1, i);
                }
            }
        }
        return "";
    }

    private static List<String> splitObjects(String arrayJson) {
        List<String> objects = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < arrayJson.length(); i++) {
            char ch = arrayJson.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(arrayJson.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
