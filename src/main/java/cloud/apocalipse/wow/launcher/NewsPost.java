package cloud.apocalipse.wow.launcher;

record NewsPost(
        int id,
        String title,
        String subtitle,
        String url,
        String imageUrl,
        String author,
        String publishedAt
) {
}
