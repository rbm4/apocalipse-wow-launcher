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

final class NewsClient {
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+|null)");
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")");

    private final HttpClient httpClient;

    NewsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    List<NewsPost> fetchLatest(String newsUrl, int limit) throws IOException, InterruptedException {
        String separator = newsUrl.contains("?") ? "&" : "?";
        URI uri = URI.create(newsUrl + separator + "limit=" + Math.max(1, limit));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("News request failed with HTTP " + response.statusCode());
        }
        return parse(response.body(), limit);
    }

    private List<NewsPost> parse(String json, int limit) {
        List<NewsPost> posts = new ArrayList<>();
        for (String object : itemObjects(json)) {
            if (posts.size() >= limit) {
                break;
            }
            String title = stringField(object, "title", "");
            if (title.isBlank()) {
                continue;
            }
            posts.add(new NewsPost(
                    intField(object, "id", 0),
                    title,
                    stringField(object, "subtitle", ""),
                    stringField(object, "url", ""),
                    stringField(object, "image_url", ""),
                    stringField(object, "author", ""),
                    stringField(object, "published_at", "")
            ));
        }
        return posts;
    }

    private static List<String> itemObjects(String json) {
        List<String> objects = new ArrayList<>();
        int key = json.indexOf("\"items\"");
        if (key < 0) {
            return objects;
        }
        int start = json.indexOf('[', key);
        if (start < 0) {
            return objects;
        }

        boolean inString = false;
        boolean escaped = false;
        int objectDepth = 0;
        int objectStart = -1;
        for (int i = start + 1; i < json.length(); i++) {
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
            if (ch == '{') {
                if (objectDepth == 0) {
                    objectStart = i;
                }
                objectDepth++;
                continue;
            }
            if (ch == '}') {
                objectDepth--;
                if (objectDepth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
                continue;
            }
            if (ch == ']' && objectDepth == 0) {
                break;
            }
        }
        return objects;
    }

    private static int intField(String json, String name, int defaultValue) {
        Matcher matcher = Pattern.compile(INT_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return defaultValue;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String stringField(String json, String name, String defaultValue) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(name))).matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) {
            return defaultValue;
        }
        return unescapeJsonString(matcher.group(2));
    }

    private static String unescapeJsonString(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                out.append(ch);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException exc) {
                            out.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(escaped);
            }
        }
        return out.toString();
    }
}
