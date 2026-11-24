package cloud.apocalipse.launcher.games.wow.patch;

record PatchFile(
        String name,
        String clientPath,
        String downloadUrl,
        String sha256,
        long size
) {
}
