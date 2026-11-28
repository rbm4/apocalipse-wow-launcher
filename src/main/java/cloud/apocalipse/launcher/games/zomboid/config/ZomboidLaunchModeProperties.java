package cloud.apocalipse.launcher.games.zomboid.config;

import java.util.List;
import java.util.Set;

public record ZomboidLaunchModeProperties(Set<ZomboidOptimization> disabledOptimizations,
                                          List<String> carriedMods,
                                          List<ZomboidWorkshopItem> carriedWorkshopItems) {
}
