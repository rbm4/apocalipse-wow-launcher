package cloud.apocalipse.launcher;

import java.util.Optional;

public enum LauncherGame {
    WORLD_OF_WARCRAFT("wow"),
    PROJECT_ZOMBOID("project-zomboid");

    private final String id;

    LauncherGame(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LauncherGame> fromId(String id) {
        for (LauncherGame game : values()) {
            if (game.id.equals(id)) {
                return Optional.of(game);
            }
        }
        return Optional.empty();
    }
}
