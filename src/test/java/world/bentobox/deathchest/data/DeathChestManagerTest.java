package world.bentobox.deathchest.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableSet;

import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;

/**
 * Tests the death chest manager: creation, lookup, claiming and expiry.
 */
class DeathChestManagerTest extends CommonTestSetup {

    private static final int SURFACE_Y = 80;

    @Mock
    private DeathChest addon;
    @Mock
    private world.bentobox.bentobox.Settings pluginSettings;
    @Mock
    private Container container;
    @Mock
    private Inventory chestInventory;
    @Mock
    private Block chestBlock;

    private MockedStatic<DatabaseSetup> mockDb;
    private Settings settings;
    private DeathChestManager manager;
    private World gameWorld;

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        AbstractDatabaseHandler<Object> h = mock(AbstractDatabaseHandler.class);
        mockDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(h.loadObjects()).thenReturn(Collections.emptyList());
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getIslands()).thenReturn(im);
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getLogger()).thenReturn(java.util.logging.Logger.getLogger("DeathChestManagerTest"));

        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        when(gameWorld.getMinHeight()).thenReturn(-64);
        when(gameWorld.getMaxHeight()).thenReturn(320);
        Chunk chunk = mock(Chunk.class);
        when(gameWorld.getChunkAt(any(Location.class))).thenReturn(chunk);
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(gameWorld);
        mockedUtil.when(() -> Util.getWorld(any())).thenReturn(gameWorld);
        // Item serialization goes through the server's unsafe values, so hand back MockBukkit's
        // real implementations rather than the blanket Bukkit deep stubs.
        mockedBukkit.when(Bukkit::getItemFactory).thenReturn(server.getItemFactory());
        mockedBukkit.when(Bukkit::getUnsafe).thenReturn(server.getUnsafe());

        // A chest block that behaves like an empty container
        when(chestBlock.getState()).thenReturn(container);
        when(container.getInventory()).thenReturn(chestInventory);
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(chestInventory.getContents()).thenReturn(new ItemStack[27]);
        // Air, replaceable, standing on solid ground
        Block ground = mock(Block.class);
        when(ground.isSolid()).thenReturn(true);
        when(chestBlock.isReplaceable()).thenReturn(true);
        when(chestBlock.isLiquid()).thenReturn(false);
        when(chestBlock.getRelative(BlockFace.DOWN)).thenReturn(ground);
        // Open air above, so the spot is not treated as submerged
        Block above = mock(Block.class);
        when(above.isLiquid()).thenReturn(false);
        when(chestBlock.getRelative(BlockFace.UP)).thenReturn(above);
        when(chestBlock.getWorld()).thenReturn(gameWorld);
        when(chestBlock.getLocation()).thenReturn(new Location(gameWorld, 4, SURFACE_Y, 4));
        when(gameWorld.getBlockAt(any(Location.class))).thenReturn(chestBlock);

        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));
        when(island.onIsland(any(Location.class))).thenReturn(true);
        when(island.getUniqueId()).thenReturn("island-1");
        when(im.getProtectedIslandAt(any(Location.class))).thenReturn(Optional.of(island));
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(island);
        when(im.getHomeLocation(island)).thenReturn(new Location(gameWorld, 0, SURFACE_Y, 0));

        // The dying player
        when(mockPlayer.getLocation()).thenReturn(new Location(gameWorld, 4, SURFACE_Y, 4));
        when(mockPlayer.getWorld()).thenReturn(gameWorld);

        manager = new DeathChestManager(addon);
        manager.load();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockDb.closeOnDemand();
        super.tearDown();
        deleteAll(new File("database"));
    }

    @Test
    void testKeyIgnoresYawAndPitch() {
        Location a = new Location(gameWorld, 1, 2, 3, 45f, 12f);
        Location b = new Location(gameWorld, 1.9, 2.5, 3.2);
        assertEquals(DeathChestManager.key(a), DeathChestManager.key(b));
        assertEquals("bskyblock_world:1:2:3", DeathChestManager.key(a));
    }

    @Test
    void testCreateChestPlacesABlockAndIndexesIt() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 3)));

        DeathChestRecord record = manager.createChest(mockPlayer, drops, 25);

        assertNotNull(record);
        assertFalse(record.isVirtual(), "A player on solid ground should get a real chest");
        assertEquals(uuid, record.getOwnerUUID());
        assertEquals(25, record.getExperience());
        assertEquals("island-1", record.getIslandId());
        verify(chestBlock).setType(Material.CHEST, false);
        assertTrue(manager.getChestAt(record.getChestLoc()).isPresent());
    }

    /**
     * Regression: the chest was placed but always turned up empty. `getInventory()` on a placed
     * block state is the live tile entity inventory, so items added to it are already in the
     * world. Calling `update()` afterwards writes the snapshot captured by `getState()` - taken
     * when the chest was still empty - back over the block and wipes them.
     */
    @Test
    void testItemsAreNotWipedByABlockStateUpdate() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 3)));

        manager.createChest(mockPlayer, drops, 0);

        verify(chestInventory).addItem(any(ItemStack[].class));
        verify(container, never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());
        verify(container, never()).update(Mockito.anyBoolean());
        verify(container, never()).update();
    }

    @Test
    void testRefillDoesNotWipeTheChestWithAStaleSnapshot() {
        ItemStack overflow = new ItemStack(Material.COBBLESTONE, 64);
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0, overflow)));
        DeathChestRecord record = manager.createChest(mockPlayer,
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND), overflow)), 0);
        ItemStack[] contents = new ItemStack[27];
        contents[0] = new ItemStack(Material.DIAMOND);
        when(chestInventory.getContents()).thenReturn(contents);

        manager.refill(record);

        verify(container, never()).update(Mockito.anyBoolean(), Mockito.anyBoolean());
    }

    @Test
    void testCreateChestWithNoIslandStoresItemsInTheRecord() {
        when(im.getProtectedIslandAt(any(Location.class))).thenReturn(Optional.empty());
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(null);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 3)));

        DeathChestRecord record = manager.createChest(mockPlayer, drops, 0);

        assertTrue(record.isVirtual(), "With nowhere to build, items must be held not lost");
        assertEquals(1, manager.readItems(record).size());
        verify(chestBlock, never()).setType(any(Material.class), Mockito.anyBoolean());
    }

    @Test
    void testItemsThatDoNotFitAreHeldByTheAddon() {
        ItemStack overflow = new ItemStack(Material.COBBLESTONE, 64);
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0, overflow)));
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND), overflow));

        DeathChestRecord record = manager.createChest(mockPlayer, drops, 0);

        assertFalse(record.isVirtual());
        List<ItemStack> held = manager.readItems(record);
        assertEquals(1, held.size());
        assertEquals(Material.COBBLESTONE, held.get(0).getType());
    }

    @Test
    void testUnknownChestMaterialFallsBackToHoldingItems() {
        settings.setChestMaterial("NOT_A_REAL_BLOCK");
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND)));

        DeathChestRecord record = manager.createChest(mockPlayer, drops, 0);

        assertTrue(record.isVirtual());
        assertEquals(1, manager.readItems(record).size());
    }

    @Test
    void testGetChestsIsNewestFirstAndFiltersByOwner() {
        DeathChestRecord mine = manager.createChest(mockPlayer, new ArrayList<>(), 1);
        mine.setDeathTime(1000L);
        DeathChestRecord newer = manager.createChest(mockPlayer, new ArrayList<>(), 1);
        newer.setDeathTime(2000L);
        DeathChestRecord other = manager.createChest(mockPlayer, new ArrayList<>(), 1);
        other.setOwnerUUID(UUID.randomUUID());

        List<DeathChestRecord> chests = manager.getChests(uuid);

        assertEquals(2, chests.size());
        assertEquals(newer.getUniqueId(), chests.get(0).getUniqueId());
        assertEquals(mine.getUniqueId(), chests.get(1).getUniqueId());
    }

    @Test
    void testMaxChestsPerPlayerExpiresTheOldest() {
        settings.setMaxChestsPerPlayer(2);
        settings.setExpiryMinutes(0);
        DeathChestRecord first = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        first.setDeathTime(1000L);
        DeathChestRecord second = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        second.setDeathTime(2000L);
        DeathChestRecord third = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        third.setDeathTime(3000L);

        List<DeathChestRecord> chests = manager.getChests(uuid);
        assertEquals(2, chests.size());
        assertFalse(chests.stream().anyMatch(r -> r.getUniqueId().equals(first.getUniqueId())));
    }

    @Test
    void testUnlimitedChestsWhenMaxIsZero() {
        settings.setMaxChestsPerPlayer(0);
        for (int i = 0; i < 5; i++) {
            manager.createChest(mockPlayer, new ArrayList<>(), 0);
        }
        assertEquals(5, manager.getChests(uuid).size());
    }

    @Test
    void testCheckExpiryOnlyExpiresPastDueChests() {
        DeathChestRecord live = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        DeathChestRecord dead = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        dead.setExpiryTime(System.currentTimeMillis() - 1000L);

        assertEquals(1, manager.checkExpiry());
        assertEquals(1, manager.getAllChests().size());
        assertEquals(live.getUniqueId(), manager.getAllChests().get(0).getUniqueId());
    }

    @Test
    void testClaimGivesItemsAndExperience() {
        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(playerInv.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(mockPlayer.getInventory()).thenReturn(playerInv);
        when(im.getProtectedIslandAt(any(Location.class))).thenReturn(Optional.empty());
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(null);
        DeathChestRecord record = manager.createChest(mockPlayer,
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 2))), 30);

        int given = manager.claim(mockPlayer, record);

        assertEquals(1, given);
        verify(mockPlayer).giveExp(30);
        assertEquals(0, record.getExperience());
        assertTrue(manager.readItems(record).isEmpty());
    }

    @Test
    void testClaimTwiceGivesNothingTheSecondTime() {
        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(playerInv.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(mockPlayer.getInventory()).thenReturn(playerInv);
        when(im.getProtectedIslandAt(any(Location.class))).thenReturn(Optional.empty());
        when(im.getIsland(any(World.class), any(UUID.class))).thenReturn(null);
        DeathChestRecord record = manager.createChest(mockPlayer,
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND))), 10);

        manager.claim(mockPlayer, record);
        assertEquals(0, manager.claim(mockPlayer, record));
        verify(mockPlayer, times(1)).giveExp(anyInt());
    }

    @Test
    void testGiveExperienceOnlyPaysOutOnce() {
        DeathChestRecord record = manager.createChest(mockPlayer, new ArrayList<>(), 42);
        manager.giveExperience(mockPlayer, record);
        manager.giveExperience(mockPlayer, record);
        verify(mockPlayer, times(1)).giveExp(42);
    }

    @Test
    void testDeleteRemovesFromCacheAndIndex() {
        DeathChestRecord record = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        Location loc = record.getChestLoc();
        manager.delete(record);
        assertTrue(manager.getAllChests().isEmpty());
        assertTrue(manager.getChestAt(loc).isEmpty());
    }

    @Test
    void testRemoveIslandChestsClearsThatIslandOnly() {
        DeathChestRecord onIsland = manager.createChest(mockPlayer, new ArrayList<>(), 0);
        assertEquals("island-1", onIsland.getIslandId());

        manager.removeIslandChests(island);

        assertTrue(manager.getAllChests().isEmpty());
    }

    @Test
    void testGetChestAtNullIsEmpty() {
        assertTrue(manager.getChestAt(null).isEmpty());
    }

    @Test
    void testRefillEmptyChestRemovesTheBlockAndRecord() {
        DeathChestRecord record = manager.createChest(mockPlayer, new ArrayList<>(), 0);

        manager.refill(record);

        assertTrue(manager.getAllChests().isEmpty());
        verify(chestBlock).setType(Material.AIR, false);
    }

    @Test
    void testRefillMovesHeldItemsIntoTheChest() {
        ItemStack overflow = new ItemStack(Material.COBBLESTONE, 64);
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0, overflow)));
        DeathChestRecord record = manager.createChest(mockPlayer,
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND), overflow)), 0);
        assertEquals(1, manager.readItems(record).size());

        // This time everything fits, and the chest is no longer empty
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        ItemStack[] contents = new ItemStack[27];
        contents[0] = new ItemStack(Material.DIAMOND);
        when(chestInventory.getContents()).thenReturn(contents);
        manager.refill(record);

        assertTrue(manager.readItems(record).isEmpty());
        assertFalse(manager.getAllChests().isEmpty(), "The chest still holds items so it must stay");
    }

    @Test
    void testRefillWhenTheBlockHasGoneTurnsTheChestVirtual() {
        ItemStack overflow = new ItemStack(Material.COBBLESTONE, 64);
        when(chestInventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0, overflow)));
        DeathChestRecord record = manager.createChest(mockPlayer,
                new ArrayList<>(List.of(new ItemStack(Material.DIAMOND), overflow)), 0);
        // The block is no longer a container
        when(chestBlock.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));

        manager.refill(record);

        assertTrue(record.isVirtual());
        assertFalse(manager.getAllChests().isEmpty(), "Held items must survive the block going away");
    }
}
