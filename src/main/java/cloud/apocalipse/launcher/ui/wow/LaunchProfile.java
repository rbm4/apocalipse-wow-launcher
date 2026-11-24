package cloud.apocalipse.launcher.ui.wow;

import java.util.List;

enum LaunchProfile {
    DEFAULT("default", "profile.default", List.of()),
    WINDOWED("windowed", "profile.windowed", List.of("-windowed")),
    FULLSCREEN("fullscreen", "profile.fullscreen", List.of("-fullscreen")),
    NO_SOUND("nosound", "profile.noSound", List.of("-nosound")),
    CONSOLE("console", "profile.console", List.of("-console")),
    WINDOWED_CONSOLE("windowed-console", "profile.windowedConsole", List.of("-windowed", "-console"));

    private final String id;
    private final String labelKey;
    private final List<String> arguments;

    LaunchProfile(String id, String labelKey, List<String> arguments) {
        this.id = id;
        this.labelKey = labelKey;
        this.arguments = arguments;
    }

    String id() {
        return id;
    }

    String label(AppLanguage language) {
        return language.text(labelKey);
    }

    List<String> arguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return AppLanguage.PORTUGUESE.text(labelKey);
    }

    static LaunchProfile fromId(String id) {
        for (LaunchProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return DEFAULT;
    }
}
