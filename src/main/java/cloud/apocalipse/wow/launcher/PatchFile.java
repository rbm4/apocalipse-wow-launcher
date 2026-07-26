package cloud.apocalipse.wow.launcher;

record PatchFile(
        String name,
        String clientPath,
        String downloadUrl,
        String sha256,
        long size
) {
}
