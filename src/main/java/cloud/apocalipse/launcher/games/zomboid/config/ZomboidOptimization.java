package cloud.apocalipse.launcher.games.zomboid.config;

public enum ZomboidOptimization {
    CLIENT_CHUNK_MAP("clientChunkMap", Group.HIGH_IMPACT, ".docs/features/client-chunk-map.md"),
    // The standalone agent guide is unpublished. Link to the published description of the ported algorithms.
    CLIENT_BUILDING_RECONCILIATION("clientBuildingReconciliation", Group.HIGH_IMPACT,
            "42.22.2-client/docs/features/2026-11-12-client-deferred-textures-and-async-translucency.md"
                    + "#building-reconciliation-algorithms"),
    JAVA_PATHFINDING("pathfinding.forceJava", Group.SIMULATION, ".docs/features/java-pathfinding.md"),
    CHARACTER_STATS("characterStats", Group.SIMULATION),
    ACTOR_ALLOCATIONS("actorUpdateAllocation", Group.SIMULATION),
    THERMAL_CLOTHING("thermalClothing", Group.SIMULATION),
    ANIMAL_GROWTH("animalGrowthGene", Group.SIMULATION),
    ENTITY_REMOVAL("entityRemoval", Group.SIMULATION,
            ".docs/features/server-simulation-hotspots.md#:~:text=The%20independent%20engine%20removal%20module"),
    LUA_DISPATCH("luaDispatch", Group.SCRIPTING),
    BLEND_DIRECTIONS("blendDirections", Group.WORLD),
    REGION_LOOKUP("regionCellLookup", Group.WORLD),
    ZOMBIE_PACKETS("zombiePacket", Group.NETWORK),
    ZOMBIE_CULLING("zombieCulling", Group.SERVER),
    PLAYER_NEAR_VEHICLE("playerNearVehicle", Group.SERVER),
    PARKED_VEHICLES("parkedVehicle", Group.SERVER, ".docs/features/parked-vehicle-cadence.md"),
    PERCEPTION_BUCKETS("perceptionBuckets", Group.SERVER),
    CHUNK_CONTAINERS("chunkContainers", Group.SERVER,
            ".docs/reports/2026-11-22-chunk-lifecycle.md#synchronous-changes:~:text=apocbr.optimization.chunkContainers.enabled"),
    CHUNK_ADJACENCY("chunkAdjacency", Group.SERVER,
            ".docs/reports/2026-11-22-chunk-lifecycle.md#synchronous-changes:~:text=apocbr.optimization.chunkAdjacency.enabled"),
    CELL_RELEVANCE("cellRelevance", Group.SERVER, ".docs/features/server-cell-streaming.md#frame-relevance-and-unload"),
    CELL_BORDER("cellBorder", Group.SERVER, ".docs/features/server-cell-streaming.md#server-cell-perimeter-lookups"),
    ENTITY_BUCKET_REMOVAL("entityBucketRemoval", Group.SERVER,
            ".docs/features/server-simulation-hotspots.md#on-demand-entity-bucket-removal-positions"),
    ENTITY_SIMULATION("entitySimulation", Group.SERVER, ".docs/features/server-simulation-hotspots.md#entity-catch-up-and-indexing"),
    SERVER_LOS("serverLos", Group.SERVER, ".docs/features/server-simulation-hotspots.md#server-los"),
    ZOMBIE_ASYNC_SEND("zombieAsyncSend", Group.SERVER, ".docs/features/server-simulation-hotspots.md#async-zombie-sends"),
    CHUNK_ASYNC_SEND("chunkAsyncSend", Group.SERVER, ".docs/features/chunk-async-send.md"),
    WORKSHOP_ASYNC("workshop.asyncCheck.enabled", Group.SERVER, ".docs/features/server-lag-diagnostics.md#workshop-checks");

    public enum Group { HIGH_IMPACT, SIMULATION, SCRIPTING, WORLD, NETWORK, SERVER }

    private final String property;
    private final Group group;
    private final String documentationPath;

    ZomboidOptimization(String key, Group group) {
        this(key, group, ".docs/features/server-optimizations.md#:~:text=apocbr.optimization." + key + ".enabled");
    }

    ZomboidOptimization(String key, Group group, String documentationPath) {
        this.property = "apocbr." + (key.contains(".") ? key : "optimization." + key + ".enabled");
        this.group = group;
        this.documentationPath = documentationPath;
    }

    public String property() {
        return property;
    }

    public Group group() {
        return group;
    }

    public String documentationUrl() {
        return "https://github.com/rbm4/apocalipsebr-zomboid-patches/blob/master/" + documentationPath;
    }

    public boolean availableOnClient() {
        return group != Group.SERVER;
    }

    public String vmArgument(boolean enabled) {
        return "-D" + property + "=" + (availableOnClient() && enabled);
    }
}
