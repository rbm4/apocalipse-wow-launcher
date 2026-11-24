package cloud.apocalipse.launcher.games.wow.patch;

import java.util.List;

public record PatchManifest(int version, String format, List<PatchFile> files) {
}
