package world.bentobox.deathchest.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import net.kyori.adventure.text.Component;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Tests that deaths are turned into chests, and that the listener keeps out of the way when it
 * should.
 */
class DeathListenerTest extends CommonTestSetup {

    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager manager;

    private Settings settings;
    private DeathListener listener;
    private DeathChestRecord chest;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        settings = new Settings();
        settings.setNotifyOnDeath(false);
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getManager()).thenReturn(manager);
        when(addon.getPlugin()).thenReturn(plugin);
        when(iwm.getFriendlyName(any(org.bukkit.World.class))).thenReturn("AcidIsland");
        when(addon.inGameWorld(any(World.class))).thenReturn(true);
        when(addon.getGameModeLabel(any(World.class))).thenReturn("is");
        // Real item behaviour: the blanket Bukkit deep stubs do not give working ItemStacks
        mockedBukkit.when(org.bukkit.Bukkit::getItemFactory).thenReturn(server.getItemFactory());
        mockedBukkit.when(org.bukkit.Bukkit::getUnsafe).thenReturn(server.getUnsafe());
        chest = new DeathChestRecord();
        when(manager.createChest(any(), anyList(), anyInt())).thenReturn(chest);
        when(manager.readItems(any())).thenReturn(new ArrayList<>());
        listener = new DeathListener(addon);
    }

    private PlayerDeathEvent deathEvent(List<ItemStack> drops, int droppedExp) {
        return new PlayerDeathEvent(mockPlayer,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.OUT_OF_WORLD).build(), drops,
                droppedExp, Component.text("died"), false);
    }

    @Test
    void testDropsAreTakenAndAChestIsMade() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 2)));
        PlayerDeathEvent e = deathEvent(drops, 40);

        listener.onPlayerDeath(e);

        assertTrue(e.getDrops().isEmpty(), "Drops must be removed so they do not also hit the ground");
        assertEquals(0, e.getDroppedExp());
        ArgumentCaptor<List<ItemStack>> captor = ArgumentCaptor.captor();
        verify(manager).createChest(any(), captor.capture(), anyInt());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void testExperiencePercentIsApplied() {
        settings.setExperiencePercent(50);
        PlayerDeathEvent e = deathEvent(new ArrayList<>(List.of(new ItemStack(Material.DIRT))), 40);

        listener.onPlayerDeath(e);

        verify(manager).createChest(any(), anyList(), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void testExperienceLeftAloneWhenStoringIsOff() {
        settings.setStoreExperience(false);
        PlayerDeathEvent e = deathEvent(new ArrayList<>(List.of(new ItemStack(Material.DIRT))), 40);

        listener.onPlayerDeath(e);

        assertEquals(40, e.getDroppedExp(), "Vanilla should still drop the orbs");
        verify(manager).createChest(any(), anyList(), org.mockito.ArgumentMatchers.eq(0));
    }

    @Test
    void testOtherWorldsAreIgnored() {
        when(addon.inGameWorld(any(World.class))).thenReturn(false);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND)));
        PlayerDeathEvent e = deathEvent(drops, 40);

        listener.onPlayerDeath(e);

        assertEquals(1, e.getDrops().size());
        verify(manager, never()).createChest(any(), anyList(), anyInt());
    }

    @Test
    void testKeepInventoryIsRespected() {
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND)));
        PlayerDeathEvent e = deathEvent(drops, 40);
        e.setKeepInventory(true);

        listener.onPlayerDeath(e);

        assertEquals(1, e.getDrops().size());
        verify(manager, never()).createChest(any(), anyList(), anyInt());
    }

    @Test
    void testNothingToStoreMeansNoChest() {
        settings.setStoreExperience(false);
        PlayerDeathEvent e = deathEvent(new ArrayList<>(), 0);

        listener.onPlayerDeath(e);

        verify(manager, never()).createChest(any(), anyList(), anyInt());
    }

    @Test
    void testNullAndAirDropsAreFilteredOut() {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Material.AIR));
        drops.add(new ItemStack(Material.DIAMOND));
        PlayerDeathEvent e = deathEvent(drops, 0);

        listener.onPlayerDeath(e);

        ArgumentCaptor<List<ItemStack>> captor = ArgumentCaptor.captor();
        verify(manager).createChest(any(), captor.capture(), anyInt());
        assertEquals(1, captor.getValue().size());
        assertEquals(Material.DIAMOND, captor.getValue().get(0).getType());
    }

    @Test
    void testNotifyOnDeathSendsMessages() {
        settings.setNotifyOnDeath(true);
        World gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        when(gameWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        chest.setChestLoc(new Location(gameWorld, 10, 70, 20));
        mockedBukkit.when(() -> org.bukkit.Bukkit.getWorld(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(gameWorld);
        PlayerDeathEvent e = deathEvent(new ArrayList<>(List.of(new ItemStack(Material.DIAMOND))), 0);

        listener.onPlayerDeath(e);

        verify(mockPlayer, org.mockito.Mockito.atLeastOnce()).sendMessage(any(Component.class));
    }
}
