package cloud.apocalipse.launcher.games.wow.patch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class FileHashes {
    private FileHashes() {
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[1024 * 1024];
        try (InputStream in = Files.newInputStream(path);
             DigestInputStream digestInputStream = new DigestInputStream(in, digest)) {
            while (digestInputStream.read(buffer) != -1) {
                // DigestInputStream updates the digest as bytes are consumed.
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-256 is not available", exc);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02x", value));
        }
        return out.toString();
    }
}
