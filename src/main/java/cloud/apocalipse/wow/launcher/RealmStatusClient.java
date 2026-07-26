package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RealmStatusClient {
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+|null)");
    private static final Pattern BOOL_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(true|false|null)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")");

    private final HttpClient httpClient;

    RealmStatusClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    RealmStatus fetch(String realmStatusUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(realmStatusUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Realm status request failed with HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    private RealmStatus parse(String json) {
        return new RealmStatus(
                stringField(json, "status", "offline"),
                boolField(json, "online", false),
                stringField(json, "realm_name", "Apocalipse"),
                intField(json, "players_online"),
                intField(json, "total_accounts"),
                stringField(json, "error", null)
        );
    }

    private static Integer intField(String json, String name) {
        Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean boolField(String json, String name, boolean defaultValue) {
        Matcher matcher = Pattern.compile(BOOL_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return defaultValue;
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static String stringField(String json, String name, String defaultValue) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return defaultValue;
        }
        return unescapeJsonString(matcher.group(2));
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
