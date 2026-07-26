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
    private static final Pattern ITEM_OBJECT = Pattern.compile("\\{([^{}]*)\\}");
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
        Matcher matcher = ITEM_OBJECT.matcher(itemsArray(json));
        while (matcher.find() && posts.size() < limit) {
            String object = matcher.group(1);
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

    private static String itemsArray(String json) {
        int key = json.indexOf("\"items\"");
        if (key < 0) {
            return "";
        }
        int start = json.indexOf('[', key);
        int end = json.indexOf(']', start);
        if (start < 0 || end < 0 || end <= start) {
            return "";
        }
        return json.substring(start + 1, end);
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
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
