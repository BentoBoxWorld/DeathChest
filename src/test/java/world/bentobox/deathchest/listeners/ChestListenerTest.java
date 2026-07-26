package world.bentobox.deathchest.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.google.common.collect.ImmutableSet;

import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Tests death chest protection: who may open or break one, and that explosions leave them alone.
 */
class ChestListenerTest extends CommonTestSetup {

    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager manager;
    @Mock
    private Block chestBlock;

    private Settings settings;
    private ChestListener listener;
    private DeathChestRecord chest;
    private World gameWorld;
    private Location chestLocation;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getManager()).thenReturn(manager);
        when(addon.getIslands()).thenReturn(im);

        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        chestLocation = new Location(gameWorld, 5, 70, 5);
        when(chestBlock.getLocation()).thenReturn(chestLocation);
        when(chestBlock.getWorld()).thenReturn(gameWorld);
        when(chestBlock.getType()).thenReturn(Material.CHEST);

        chest = new DeathChestRecord();
        chest.setOwnerUUID(uuid);
        chest.setOwnerName("tastybento");
        when(manager.getChestAt(any(Location.class))).thenReturn(Optional.of(chest));

        when(island.getMemberSet()).thenReturn(ImmutableSet.of(uuid));
        when(im.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));

        listener = new ChestListener(addon);
    }

    private PlayerInteractEvent interact() {
        return new PlayerInteractEvent(mockPlayer, Action.RIGHT_CLICK_BLOCK, null, chestBlock, BlockFace.UP);
    }

    @Test
    void testOwnerMayOpenAndGetsTheExperience() {
        PlayerInteractEvent e = interact();

        listener.onInteract(e);

        assertFalse(e.isCancelled());
        verify(manager).giveExperience(mockPlayer, chest);
    }

    @Test
    void testStrangerIsBlocked() {
        chest.setOwnerUUID(UUID.randomUUID());
        when(island.getMemberSet()).thenReturn(ImmutableSet.of());
        PlayerInteractEvent e = interact();

        listener.onInteract(e);

        assertTrue(e.isCancelled());
        verify(manager, never()).giveExperience(any(), any());
    }

    @Test
    void testTeamMateMayOpenWhenTeamAccessIsOn() {
        UUID owner = UUID.randomUUID();
        chest.setOwnerUUID(owner);
        chest.setChestLoc(chestLocation);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(owner, uuid));
        PlayerInteractEvent e = interact();

        listener.onInteract(e);

        assertFalse(e.isCancelled());
    }

    @Test
    void testTeamMateIsBlockedWhenTeamAccessIsOff() {
        settings.setTeamAccess(false);
        UUID owner = UUID.randomUUID();
        chest.setOwnerUUID(owner);
        chest.setChestLoc(chestLocation);
        when(island.getMemberSet()).thenReturn(ImmutableSet.of(owner, uuid));
        PlayerInteractEvent e = interact();

        listener.onInteract(e);

        assertTrue(e.isCancelled());
    }

    @Test
    void testLeftClickIsIgnored() {
        PlayerInteractEvent e = new PlayerInteractEvent(mockPlayer, Action.LEFT_CLICK_BLOCK, null, chestBlock,
                BlockFace.UP);

        listener.onInteract(e);

        verify(manager, never()).giveExperience(any(), any());
    }

    @Test
    void testBlocksThatAreNotDeathChestsAreIgnored() {
        when(manager.getChestAt(any(Location.class))).thenReturn(Optional.empty());
        PlayerInteractEvent e = interact();

        listener.onInteract(e);

        assertFalse(e.isCancelled());
        verify(manager, never()).giveExperience(any(), any());
    }

    @Test
    void testOwnerMayBreakTheChest() {
        BlockBreakEvent e = new BlockBreakEvent(chestBlock, mockPlayer);

        listener.onBreak(e);

        assertFalse(e.isCancelled());
        verify(manager).claim(mockPlayer, chest);
        verify(manager).delete(chest);
    }

    @Test
    void testStrangerMayNotBreakTheChest() {
        chest.setOwnerUUID(UUID.randomUUID());
        when(island.getMemberSet()).thenReturn(ImmutableSet.of());
        BlockBreakEvent e = new BlockBreakEvent(chestBlock, mockPlayer);

        listener.onBreak(e);

        assertTrue(e.isCancelled());
        verify(manager, never()).delete(any());
    }

    @Test
    void testCloseRefillsTheChest() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(chestLocation);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getPlayer()).thenReturn(mockPlayer);
        InventoryCloseEvent e = new InventoryCloseEvent(view);

        listener.onClose(e);

        verify(manager).refill(chest);
    }

    @Test
    void testCloseOfAnInventoryWithNoLocationDoesNothing() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(null);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getPlayer()).thenReturn(mockPlayer);

        listener.onClose(new InventoryCloseEvent(view));

        verify(manager, never()).refill(any());
    }

    @Test
    void testExplosionSkipsDeathChests() {
        List<Block> blocks = new ArrayList<>(List.of(chestBlock));
        Entity entity = mock(Entity.class);

        listener.onEntityExplode(getExplodeEvent(entity, chestLocation, blocks));

        assertTrue(blocks.isEmpty(), "The death chest must be taken out of the explosion");
    }

    @Test
    void testExplosionProtectionCanBeTurnedOff() {
        settings.setProtectFromExplosions(false);
        List<Block> blocks = new ArrayList<>(List.of(chestBlock));
        Entity entity = mock(Entity.class);

        listener.onEntityExplode(getExplodeEvent(entity, chestLocation, blocks));

        assertEquals(1, blocks.size());
    }

    @Test
    void testChestWithNoOwnerIsOpenToAnyone() {
        chest.setOwner(null);
        assertTrue(listener.canAccess(mockPlayer, chest));
    }
}
