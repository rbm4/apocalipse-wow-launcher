package cloud.apocalipse.launcher.games.zomboid.config;

import java.util.List;
import java.util.Set;

public enum ZomboidLaunchMode {
    STANDARD("Project Zomboid",
            Set.of(),
            List.of("ZombieBuddy", "APOCALISEBR_PACK"),
            List.of(new ZomboidWorkshopItem("3619862853", "ZombieBuddy", false))),
    VIEWPOINT("Viewpoint",
            Set.of(ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH),
            List.of("ZombieBuddy", "APOCALISEBR_PACK", "Viewpoint", "PZVoxelStudioViewpoint"),
            List.of(
                    new ZomboidWorkshopItem("3619862853", "ZombieBuddy", false),
                    new ZomboidWorkshopItem("3809306528", "Viewpoint", true),
                    new ZomboidWorkshopItem("3810302175", "PZVoxelStudioViewpoint", false))),
    VIEWPOINT_VISIBLE_LOOT("Viewpoint Visible Loot",
            Set.of(ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH),
            List.of("ZombieBuddy", "APOCALISEBR_PACK", "Viewpoint", "ViewpointVisibleLoot", "PZVoxelStudioViewpoint"),
            List.of(
                    new ZomboidWorkshopItem("3619862853", "ZombieBuddy", false),
                    new ZomboidWorkshopItem("3809306528", "Viewpoint", true),
                    new ZomboidWorkshopItem("3818189653", "ViewpointVisibleLoot", false),
                    new ZomboidWorkshopItem("3810302175", "PZVoxelStudioViewpoint", false))),
    VIEWPOINT_RIGHT_CLICK("Viewpoint Visible Loot + Right Click Fix",
            Set.of(ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH),
            List.of("ZombieBuddy", "APOCALISEBR_PACK", "Viewpoint", "ViewpointVisibleLoot",
                    "ViewpointRightClick", "PZVoxelStudioViewpoint"),
            List.of(
                    new ZomboidWorkshopItem("3619862853", "ZombieBuddy", false),
                    new ZomboidWorkshopItem("3809306528", "Viewpoint", true),
                    new ZomboidWorkshopItem("3818189653", "ViewpointVisibleLoot", false),
                    new ZomboidWorkshopItem("3819018807", "ViewpointRightClick", false),
                    new ZomboidWorkshopItem("3810302175", "PZVoxelStudioViewpoint", false))),
    VIEWPOINT_TREE_FLICKER_PATCH("Viewpoint + Tree Flicker Patch",
            Set.of(ZomboidOptimization.CHARACTER_STATS, ZomboidOptimization.LUA_DISPATCH),
            List.of("ZombieBuddy", "APOCALISEBR_PACK", "Viewpoint", "ViewpointVisibleLoot",
                    "ViewpointRightClick", "ViewpointDisableIsometricUpkeep", "PZVoxelStudioViewpoint"),
            List.of(
                    new ZomboidWorkshopItem("3619862853", "ZombieBuddy", false),
                    new ZomboidWorkshopItem("3809306528", "Viewpoint", true),
                    new ZomboidWorkshopItem("3818189653", "ViewpointVisibleLoot", false),
                    new ZomboidWorkshopItem("3819018807", "ViewpointRightClick", false),
                    new ZomboidWorkshopItem("3842290180", "ViewpointDisableIsometricUpkeep", true),
                    new ZomboidWorkshopItem("3810302175", "PZVoxelStudioViewpoint", false)));

    private final String displayName;
    private final ZomboidLaunchModeProperties properties;

    ZomboidLaunchMode(String displayName, Set<ZomboidOptimization> disabledOptimizations, List<String> carriedMods,
            List<ZomboidWorkshopItem> carriedWorkshopItems) {
        this.displayName = displayName;
        this.properties = new ZomboidLaunchModeProperties(disabledOptimizations, carriedMods, carriedWorkshopItems);
    }

    public String displayName() {
        return displayName;
    }

    public ZomboidLaunchModeProperties properties() {
        return properties;
    }

    public Set<ZomboidOptimization> disabledOptimizations() {
        return this.properties.disabledOptimizations();
    }

    public boolean allowsOptimization(ZomboidOptimization optimization) {
        return optimization.availableOnClient() && !properties.disabledOptimizations().contains(optimization);
    }
}
