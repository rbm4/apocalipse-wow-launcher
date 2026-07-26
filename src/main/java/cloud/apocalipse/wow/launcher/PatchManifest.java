package cloud.apocalipse.wow.launcher;

import java.util.List;

record PatchManifest(int version, String format, List<PatchFile> files) {
}
