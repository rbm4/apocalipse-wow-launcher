package cloud.apocalipse.wow.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.CookieStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

final class PersistentCookieStore implements CookieStore {
    private static final String COUNT = "count";
    private static final String EXPIRES_AT = "expiresAt";

    private final Path path;
    private final List<StoredCookie> cookies = new ArrayList<>();

    PersistentCookieStore(Path path) {
        this.path = path;
        load();
    }

    @Override
    public synchronized void add(URI uri, HttpCookie cookie) {
        if (cookie == null) {
            return;
        }
        remove(uri, cookie);
        if (cookie.getMaxAge() == 0) {
            save();
            return;
        }
        cookies.add(new StoredCookie(cookie, expiresAt(cookie)));
        save();
    }

    @Override
    public synchronized List<HttpCookie> get(URI uri) {
        purgeExpired();
        List<HttpCookie> matches = new ArrayList<>();
        for (StoredCookie stored : cookies) {
            HttpCookie cookie = stored.cookie();
            if (uri == null || matches(cookie, uri)) {
                refreshMaxAge(stored);
                matches.add(cookie);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public synchronized List<HttpCookie> getCookies() {
        purgeExpired();
        cookies.forEach(this::refreshMaxAge);
        return cookies.stream().map(StoredCookie::cookie).toList();
    }

    @Override
    public synchronized List<URI> getURIs() {
        purgeExpired();
        return cookies.stream()
                .map(StoredCookie::cookie)
                .map(HttpCookie::getDomain)
                .filter(Objects::nonNull)
                .distinct()
                .map(PersistentCookieStore::domainUri)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public synchronized boolean remove(URI uri, HttpCookie cookie) {
        boolean removed = cookies.removeIf(stored -> sameCookie(stored.cookie(), cookie));
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public synchronized boolean removeAll() {
        boolean hadCookies = !cookies.isEmpty();
        cookies.clear();
        save();
        return hadCookies;
    }

    private boolean matches(HttpCookie cookie, URI uri) {
        if (cookie.getSecure() && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || !domainMatches(cookie, host)) {
            return false;
        }
        String requestPath = uri.getPath();
        if (requestPath == null || requestPath.isBlank()) {
            requestPath = "/";
        }
        return requestPath.startsWith(nullToRoot(cookie.getPath()));
    }

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            int count = Integer.parseInt(properties.getProperty(COUNT, "0"));
            for (int index = 0; index < count; index++) {
                HttpCookie cookie = readCookie(properties, index);
                long expiresAt = Long.parseLong(properties.getProperty(key(index, EXPIRES_AT), "-1"));
                if (cookie != null && !isExpired(expiresAt)) {
                    refreshMaxAge(cookie, expiresAt);
                    cookies.add(new StoredCookie(cookie, expiresAt));
                }
            }
        } catch (Exception ignored) {
            cookies.clear();
        }
    }

    private void save() {
        try {
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(COUNT, Integer.toString(cookies.size()));
            for (int index = 0; index < cookies.size(); index++) {
                writeCookie(properties, index, cookies.get(index));
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Apocalipse WoW Launcher website cookies");
            }
        } catch (IOException ignored) {
            // Cookie persistence should never prevent the website tab from working.
        }
    }

    private void purgeExpired() {
        if (cookies.removeIf(stored -> isExpired(stored.expiresAtMillis()))) {
            save();
        }
    }

    private static HttpCookie readCookie(Properties properties, int index) {
        String name = properties.getProperty(key(index, "name"));
        String value = properties.getProperty(key(index, "value"));
        if (name == null || value == null) {
            return null;
        }
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setComment(properties.getProperty(key(index, "comment")));
        cookie.setCommentURL(properties.getProperty(key(index, "commentUrl")));
        cookie.setDiscard(Boolean.parseBoolean(properties.getProperty(key(index, "discard"), "false")));
        cookie.setDomain(properties.getProperty(key(index, "domain")));
        cookie.setHttpOnly(Boolean.parseBoolean(properties.getProperty(key(index, "httpOnly"), "false")));
        cookie.setPath(properties.getProperty(key(index, "path")));
        cookie.setPortlist(properties.getProperty(key(index, "portList")));
        cookie.setSecure(Boolean.parseBoolean(properties.getProperty(key(index, "secure"), "false")));
        cookie.setVersion(Integer.parseInt(properties.getProperty(key(index, "version"), "1")));
        return cookie;
    }

    private static void writeCookie(Properties properties, int index, StoredCookie stored) {
        HttpCookie cookie = stored.cookie();
        properties.setProperty(key(index, "name"), cookie.getName());
        properties.setProperty(key(index, "value"), cookie.getValue());
        setNullable(properties, index, "comment", cookie.getComment());
        setNullable(properties, index, "commentUrl", cookie.getCommentURL());
        setNullable(properties, index, "domain", cookie.getDomain());
        setNullable(properties, index, "path", cookie.getPath());
        setNullable(properties, index, "portList", cookie.getPortlist());
        properties.setProperty(key(index, "discard"), Boolean.toString(cookie.getDiscard()));
        properties.setProperty(key(index, "httpOnly"), Boolean.toString(cookie.isHttpOnly()));
        properties.setProperty(key(index, "secure"), Boolean.toString(cookie.getSecure()));
        properties.setProperty(key(index, "version"), Integer.toString(cookie.getVersion()));
        properties.setProperty(key(index, EXPIRES_AT), Long.toString(stored.expiresAtMillis()));
    }

    private static void setNullable(Properties properties, int index, String field, String value) {
        if (value != null) {
            properties.setProperty(key(index, field), value);
        }
    }

    private static long expiresAt(HttpCookie cookie) {
        long maxAge = cookie.getMaxAge();
        if (maxAge < 0) {
            return -1;
        }
        return System.currentTimeMillis() + maxAge * 1000L;
    }

    private void refreshMaxAge(StoredCookie stored) {
        refreshMaxAge(stored.cookie(), stored.expiresAtMillis());
    }

    private static void refreshMaxAge(HttpCookie cookie, long expiresAtMillis) {
        if (expiresAtMillis < 0) {
            cookie.setMaxAge(-1);
            return;
        }
        cookie.setMaxAge(Math.max(0, (expiresAtMillis - System.currentTimeMillis()) / 1000L));
    }

    private static boolean isExpired(long expiresAtMillis) {
        return expiresAtMillis >= 0 && expiresAtMillis <= System.currentTimeMillis();
    }

    private static boolean sameCookie(HttpCookie left, HttpCookie right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getName().equalsIgnoreCase(right.getName())
                && Objects.equals(normalize(left.getDomain()), normalize(right.getDomain()))
                && Objects.equals(nullToRoot(left.getPath()), nullToRoot(right.getPath()));
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private static String nullToRoot(String value) {
        return value == null ? "/" : value;
    }

    private static boolean domainMatches(HttpCookie cookie, String host) {
        String domain = cookie.getDomain();
        if (domain == null || domain.isBlank()) {
            return true;
        }
        return HttpCookie.domainMatches(domain, host);
    }

    private static URI domainUri(String domain) {
        try {
            return new URI("https://" + domain.replaceFirst("^\\.", ""));
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String key(int index, String field) {
        return "cookie." + index + "." + field;
    }

    private record StoredCookie(HttpCookie cookie, long expiresAtMillis) {
    }
}
