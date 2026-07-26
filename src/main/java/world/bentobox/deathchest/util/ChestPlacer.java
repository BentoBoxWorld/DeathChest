package world.bentobox.deathchest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.DeathChest;

/**
 * Works out where a death chest should go.
 * <p>
 * This is the part that generic death chest plugins get wrong on island servers. A player
 * who dies in the void has no block to put a chest on, and a player who dies on someone
 * else's island cannot reach a chest placed there. So the search runs in two stages:
 * <ol>
 * <li>the death site, but only if the player is a member of the island there and a
 * reachable free block can be found nearby;</li>
 * <li>otherwise the player's own island, next to their home location.</li>
 * </ol>
 * If both fail the caller falls back to storing the items in the database.
 *
 * @author tastybento
 */
public class ChestPlacer {

    /**
     * A chosen spot for a death chest.
     *
     * @param location    block location to place the chest at
     * @param island      island the spot is on, never null - chests are only ever placed on islands
     * @param atDeathSite true if this is where the player actually died, false if it is the
     *                    fallback spot on their own island
     */
    public record Placement(@NonNull Location location, @NonNull Island island, boolean atDeathSite) {
    }

    private final DeathChest addon;

    public ChestPlacer(DeathChest addon) {
        this.addon = addon;
    }

    /**
     * Find a spot for a player's death chest.
     *
     * @param playerUUID    player who died
     * @param deathLocation where they died
     * @return the placement, or empty if nowhere suitable could be found
     */
    public Optional<Placement> findSpot(@NonNull UUID playerUUID, @NonNull Location deathLocation) {
        int radius = addon.getSettings().getSearchRadius();
        int depth = addon.getSettings().getSearchDepth();

        // Stage 1: the death site, if the player died inside the protected part of an island
        // they belong to. Protected, not just "in island space" - on an ocean game mode the
        // island's grid square is mostly open sea, which is not somewhere to leave a chest.
        if (addon.getSettings().isPlaceAtDeathLocation()) {
            Island deathIsland = addon.getIslands().getProtectedIslandAt(deathLocation).orElse(null);
            if (deathIsland != null && deathIsland.getMemberSet().contains(playerUUID)) {
                // Solid ground within reach is required here. A player who fell into the void, or
                // drowned at sea, is inside their island's column with nothing usable under them,
                // and failing this check is exactly what sends the chest to the island instead.
                Location spot = search(clampToWorld(deathLocation), deathIsland, radius, depth, true);
                if (spot != null) {
                    return Optional.of(new Placement(spot, deathIsland, true));
                }
            }
        }

        // Stage 2: the player's own island. Its overworld is the one BentoBox keys islands by,
        // so a nether or void death still resolves to the island the player can walk around on.
        World overworld = Util.getWorld(deathLocation.getWorld());
        if (overworld == null) {
            return Optional.empty();
        }
        Island own = addon.getIslands().getIsland(overworld, playerUUID);
        if (own == null) {
            return Optional.empty();
        }
        Location home = addon.getIslands().getHomeLocation(own);
        if (home == null || home.getWorld() == null) {
            home = own.getProtectionCenter();
        }
        if (home == null || home.getWorld() == null) {
            return Optional.empty();
        }
        // The island may be far from any player, so make sure the chunk is there to build in.
        home.getWorld().getChunkAt(home).load(true);
        Location spot = search(clampToWorld(home), own, radius, depth, false);
        return spot == null ? Optional.empty() : Optional.of(new Placement(spot, own, false));
    }

    /**
     * Pull a location back inside the world's build limits. A player who dies in the void is
     * below the world floor, which is not somewhere a block can exist.
     *
     * @param location location to clamp
     * @return a copy of the location with a legal Y
     */
    @NonNull
    Location clampToWorld(@NonNull Location location) {
        World world = location.getWorld();
        Location clamped = location.clone();
        clamped.setY(Math.clamp(location.getBlockY(), world.getMinHeight() + 1d, world.getMaxHeight() - 2d));
        return clamped;
    }

    /**
     * Look for a free block to put a chest in.
     *
     * @param centre        where to search from
     * @param island        island the spot must stay inside
     * @param radius        how far outwards to search
     * @param depth         how far down to search for ground
     * @param requireGround if true, only accept a spot with a solid block under it
     * @return a block location, or null if nothing suitable was found
     */
    @Nullable
    Location search(@NonNull Location centre, @NonNull Island island, int radius, int depth, boolean requireGround) {
        // First pass: a free block sitting on something solid, scanning downwards because that is
        // the way the items would have fallen.
        for (Location candidate : grounded(centre, radius, depth)) {
            if (isUsable(candidate, island) && isGrounded(candidate)) {
                return candidate;
            }
        }
        if (requireGround) {
            return null;
        }
        // Second pass: any free block close by. A floating chest is ugly but it is not lost items.
        for (Location candidate : nearby(centre, radius)) {
            if (isUsable(candidate, island)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Candidates for the "standing on something" pass: straight down first, then outwards ring
     * by ring, each ring also scanning down.
     * <p>
     * The downward scan is bounded by {@code depth} rather than running to the world floor. An
     * unbounded scan will happily find ground a hundred blocks below the player - the sea bed of
     * an ocean world, or a pocket inside generated terrain - and a chest there is as good as lost
     * even though it is technically on the island.
     *
     * @param centre where to search from
     * @param radius how far outwards to search
     * @param depth  how far down to search
     * @return candidate block locations in preference order
     */
    private List<Location> grounded(@NonNull Location centre, int radius, int depth) {
        World world = centre.getWorld();
        int cx = centre.getBlockX();
        int cy = centre.getBlockY();
        int cz = centre.getBlockZ();
        int lowest = Math.max(world.getMinHeight() + 1, cy - depth);

        List<Location> list = new ArrayList<>();
        for (int y = cy; y >= lowest; y--) {
            list.add(new Location(world, cx, y, cz));
        }
        forEachRing(radius, (dx, dz) -> {
            for (int y = cy; y >= lowest; y--) {
                list.add(new Location(world, cx + dx, y, cz + dz));
            }
        });
        return list;
    }

    /**
     * Candidates for the "anywhere free" pass, kept within the search radius so a chest never
     * ends up hundreds of blocks below where the player died.
     *
     * @param centre where to search from
     * @param radius how far to search
     * @return candidate block locations in preference order
     */
    private List<Location> nearby(@NonNull Location centre, int radius) {
        World world = centre.getWorld();
        int cx = centre.getBlockX();
        int cy = centre.getBlockY();
        int cz = centre.getBlockZ();
        int min = world.getMinHeight();
        int max = world.getMaxHeight() - 1;

        List<Location> list = new ArrayList<>();
        addColumn(list, world, cx, cy, cz, radius, min, max);
        forEachRing(radius, (dx, dz) -> addColumn(list, world, cx + dx, cy, cz + dz, radius, min, max));
        return list;
    }

    private void addColumn(List<Location> list, World world, int x, int cy, int z, int radius, int min, int max) {
        for (int dy = 0; dy <= radius; dy++) {
            if (cy - dy >= min) {
                list.add(new Location(world, x, cy - dy, z));
            }
            if (dy > 0 && cy + dy <= max) {
                list.add(new Location(world, x, cy + dy, z));
            }
        }
    }

    /**
     * Walk the edge of each square ring outwards from the centre, closest ring first.
     *
     * @param radius how many rings
     * @param action called with each ring offset
     */
    private void forEachRing(int radius, java.util.function.BiConsumer<Integer, Integer> action) {
        for (int ring = 1; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Only the edge of each ring - the inside was covered by a smaller ring.
                    if (Math.abs(dx) == ring || Math.abs(dz) == ring) {
                        action.accept(dx, dz);
                    }
                }
            }
        }
    }

    /**
     * @param location candidate location
     * @param island   island the location must be inside
     * @return true if a chest can be put here without destroying anything, and a player can get
     *         to it
     */
    private boolean isUsable(@NonNull Location location, @NonNull Island island) {
        if (!island.onIsland(location)) {
            return false;
        }
        Block block = location.getBlock();
        // Liquids are replaceable but a chest in lava is not somewhere a player wants to swim.
        if (!block.isReplaceable() || block.isLiquid()) {
            return false;
        }
        // Nor is a chest on the sea bed. A liquid directly above means this spot is submerged,
        // which on an ocean game mode is most of the world.
        return !block.getRelative(BlockFace.UP).isLiquid();
    }

    /**
     * @param location candidate location
     * @return true if the block below this one is solid
     */
    private boolean isGrounded(@NonNull Location location) {
        if (location.getBlockY() <= location.getWorld().getMinHeight()) {
            return false;
        }
        return location.getBlock().getRelative(BlockFace.DOWN).isSolid();
    }
}
