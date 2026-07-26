package world.bentobox.deathchest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.util.ChestPlacer.Placement;

/**
 * Tests the placement rules that make DeathChest work in void worlds: a chest goes where the
 * player died only if there is solid ground there, and lands on their own island otherwise.
 */
class ChestPlacerTest extends CommonTestSetup {

    /** Void worlds have no floor between the build limit and the island. */
    private static final int WORLD_MIN = -64;
    private static final int WORLD_MAX = 320;
    /** Where the test island's surface is. */
    private static final int ISLAND_SURFACE_Y = 80;

    @Mock
    private DeathChest addon;

    private Settings settings;
    private ChestPlacer placer;
    private World gameWorld;
    /** Blocks that are solid, keyed by "x:y:z". Everything else is air. */
    private final Map<String, Boolean> solid = new HashMap<>();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslands()).thenReturn(im);

        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        when(gameWorld.getMinHeight()).thenReturn(WORLD_MIN);
        when(gameWorld.getMaxHeight()).thenReturn(WORLD_MAX);
        when(gameWorld.getBlockAt(any(Location.class))).thenAnswer(i -> block(i.getArgument(0, Location.class)));
        Chunk chunk = mock(Chunk.class);
        when(chunk.load(true)).thenReturn(true);
        when(gameWorld.getChunkAt(any(Location.class))).thenReturn(chunk);
        mockedUtil.when(() -> Util.getWorld(any())).thenReturn(gameWorld);

        // One island, owned by the test player, that covers everywhere the tests look.
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));
        when(island.onIsland(any(Location.class))).thenReturn(true);
        when(island.getUniqueId()).thenReturn("island-1");
        when(im.getIsland(any(World.class), any(java.util.UUID.class))).thenReturn(island);
        when(im.getHomeLocation(island)).thenReturn(new Location(gameWorld, 0, ISLAND_SURFACE_Y + 1, 0));
        // Island surface, so the home has something to stand on.
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                solid.put(key(x, ISLAND_SURFACE_Y, z), true);
            }
        }

        placer = new ChestPlacer(addon);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    /**
     * Build a block mock on demand for a location, solid or not according to the test's map.
     */
    private Block block(Location loc) {
        Block b = mock(Block.class);
        boolean isSolid = solid.getOrDefault(key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), false);
        when(b.isSolid()).thenReturn(isSolid);
        when(b.isReplaceable()).thenReturn(!isSolid);
        when(b.isLiquid()).thenReturn(false);
        when(b.getRelative(BlockFace.DOWN))
                .thenAnswer(i -> block(new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY() - 1,
                        loc.getBlockZ())));
        return b;
    }

    @Test
    void testDeathOnOwnIslandPlacesChestThere() {
        // Standing on the island surface
        Location death = new Location(gameWorld, 2, ISLAND_SURFACE_Y + 1, 2);
        when(im.getIslandAt(death)).thenReturn(Optional.of(island));

        Optional<Placement> result = placer.findSpot(uuid, death);

        assertTrue(result.isPresent());
        assertTrue(result.get().atDeathSite());
        assertEquals(ISLAND_SURFACE_Y + 1, result.get().location().getBlockY());
        assertEquals(2, result.get().location().getBlockX());
    }

    @Test
    void testVoidDeathFallsBackToTheIsland() {
        // Fell off the island: below the world floor, nothing solid in the whole column
        Location death = new Location(gameWorld, 200, -120, 200);
        when(im.getIslandAt(death)).thenReturn(Optional.of(island));

        Optional<Placement> result = placer.findSpot(uuid, death);

        assertTrue(result.isPresent(), "A void death must still produce a chest");
        assertFalse(result.get().atDeathSite(), "The chest must not be left hanging in the void");
        assertEquals(ISLAND_SURFACE_Y + 1, result.get().location().getBlockY());
        assertEquals(gameWorld, result.get().location().getWorld());
    }

    @Test
    void testDeathOnSomeoneElsesIslandFallsBackToOwnIsland() {
        Location death = new Location(gameWorld, 500, ISLAND_SURFACE_Y + 1, 500);
        // The island there does not have the dead player as a member
        var otherIsland = mock(world.bentobox.bentobox.database.objects.Island.class);
        when(otherIsland.getMemberSet()).thenReturn(ImmutableSet.of(java.util.UUID.randomUUID()));
        when(im.getIslandAt(death)).thenReturn(Optional.of(otherIsland));

        Optional<Placement> result = placer.findSpot(uuid, death);

        assertTrue(result.isPresent());
        assertFalse(result.get().atDeathSite());
        assertEquals("island-1", result.get().island().getUniqueId());
    }

    @Test
    void testPlaceAtDeathLocationDisabledAlwaysUsesTheIsland() {
        settings.setPlaceAtDeathLocation(false);
        Location death = new Location(gameWorld, 2, ISLAND_SURFACE_Y + 1, 2);
        when(im.getIslandAt(death)).thenReturn(Optional.of(island));

        Optional<Placement> result = placer.findSpot(uuid, death);

        assertTrue(result.isPresent());
        assertFalse(result.get().atDeathSite());
        assertEquals(0, result.get().location().getBlockX());
    }

    @Test
    void testNoIslandMeansNoSpot() {
        Location death = new Location(gameWorld, 200, -120, 200);
        when(im.getIslandAt(death)).thenReturn(Optional.empty());
        when(im.getIsland(any(World.class), any(java.util.UUID.class))).thenReturn(null);

        assertTrue(placer.findSpot(uuid, death).isEmpty());
    }

    @Test
    void testHomeSpotOccupiedMovesTheChestAside() {
        settings.setPlaceAtDeathLocation(false);
        // Fill the block the home sits on top of, so the obvious spot is taken
        solid.put(key(0, ISLAND_SURFACE_Y + 1, 0), true);
        Location death = new Location(gameWorld, 2, ISLAND_SURFACE_Y + 1, 2);
        when(im.getIslandAt(death)).thenReturn(Optional.empty());

        Optional<Placement> result = placer.findSpot(uuid, death);

        assertTrue(result.isPresent());
        Location spot = result.get().location();
        assertFalse(solid.getOrDefault(key(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ()), false),
                "The chest must not replace an existing block");
    }

    @Test
    void testClampToWorldPullsVoidDeathsInsideBuildLimits() {
        Location clamped = placer.clampToWorld(new Location(gameWorld, 0, -300, 0));
        assertEquals(WORLD_MIN + 1, clamped.getBlockY());

        clamped = placer.clampToWorld(new Location(gameWorld, 0, 5000, 0));
        assertEquals(WORLD_MAX - 2, clamped.getBlockY());
    }
}
