package cloud.apocalipse.launcher.games.zomboid.config;

import java.util.Set;

public enum ZomboidLaunchMode {
    STANDARD(Set.of()),
    VIEWPOINT(Set.of(ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH));

    private final Set<ZomboidOptimization> disabledOptimizations;

    ZomboidLaunchMode(Set<ZomboidOptimization> disabledOptimizations) {
        this.disabledOptimizations = disabledOptimizations;
    }

    public Set<ZomboidOptimization> disabledOptimizations() {
        return disabledOptimizations;
    }

    public boolean allowsOptimization(ZomboidOptimization optimization) {
        return optimization.availableOnClient() && !disabledOptimizations.contains(optimization);
    }
}
