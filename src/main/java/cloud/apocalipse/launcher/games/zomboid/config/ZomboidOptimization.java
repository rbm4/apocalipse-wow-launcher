package cloud.apocalipse.launcher.games.zomboid.config;

public enum ZomboidOptimization {
    CLIENT_CHUNK_MAP("clientChunkMap", Group.HIGH_IMPACT),
    JAVA_PATHFINDING("pathfinding.forceJava", Group.SIMULATION),
    CHARACTER_STATS("characterStats", Group.SIMULATION),
    ACTOR_ALLOCATIONS("actorUpdateAllocation", Group.SIMULATION),
    THERMAL_CLOTHING("thermalClothing", Group.SIMULATION),
    ANIMAL_GROWTH("animalGrowthGene", Group.SIMULATION),
    ENTITY_REMOVAL("entityRemoval", Group.SIMULATION),
    LUA_DISPATCH("luaDispatch", Group.SCRIPTING),
    BLEND_DIRECTIONS("blendDirections", Group.WORLD),
    REGION_LOOKUP("regionCellLookup", Group.WORLD),
    ZOMBIE_PACKETS("zombiePacket", Group.NETWORK),
    ZOMBIE_CULLING("zombieCulling", Group.SERVER),
    PLAYER_NEAR_VEHICLE("playerNearVehicle", Group.SERVER),
    PARKED_VEHICLES("parkedVehicle", Group.SERVER),
    PERCEPTION_BUCKETS("perceptionBuckets", Group.SERVER),
    CHUNK_CONTAINERS("chunkContainers", Group.SERVER),
    CHUNK_ADJACENCY("chunkAdjacency", Group.SERVER),
    CELL_RELEVANCE("cellRelevance", Group.SERVER),
    CELL_BORDER("cellBorder", Group.SERVER),
    ENTITY_BUCKET_REMOVAL("entityBucketRemoval", Group.SERVER),
    ENTITY_SIMULATION("entitySimulation", Group.SERVER),
    SERVER_LOS("serverLos", Group.SERVER),
    ZOMBIE_ASYNC_SEND("zombieAsyncSend", Group.SERVER),
    CHUNK_ASYNC_SEND("chunkAsyncSend", Group.SERVER),
    WORKSHOP_ASYNC("workshop.asyncCheck.enabled", Group.SERVER);

    public enum Group { HIGH_IMPACT, SIMULATION, SCRIPTING, WORLD, NETWORK, SERVER }

    private final String property;
    private final Group group;

    ZomboidOptimization(String key, Group group) {
        this.property = "apocbr." + (key.contains(".") ? key : "optimization." + key + ".enabled");
        this.group = group;
    }

    public String property() {
        return property;
    }

    public Group group() {
        return group;
    }

    public boolean availableOnClient() {
        return group != Group.SERVER;
    }

    public String vmArgument(boolean enabled) {
        return "-D" + property + "=" + (availableOnClient() && enabled);
    }
}
