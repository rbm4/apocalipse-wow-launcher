package cloud.apocalipse.launcher.games.wow.community;

public record NewsPost(
        int id,
        String title,
        String subtitle,
        String url,
        String imageUrl,
        String author,
        String publishedAt
) {
}
