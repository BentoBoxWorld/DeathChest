package world.bentobox.deathchest.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Tests the hologram above a death chest: MiniMessage text with the [player] placeholder,
 * and its lifecycle against chunk loads and record removal.
 */
class HologramManagerTest extends CommonTestSetup {

    private static final String HOLOGRAM_TEXT = "<gold><bold>Death Chest</bold></gold>\n<yellow>[player]</yellow>";

    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager dcm;
    @Mock
    private TextDisplay display;

    private HologramManager holograms;
    private World gameWorld;
    private DeathChestRecord chest;
    private final UUID displayId = UUID.randomUUID();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getPlugin()).thenReturn(plugin);
        when(addon.getManager()).thenReturn(dcm);

        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        when(gameWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(gameWorld.spawn(any(Location.class), eq(TextDisplay.class))).thenReturn(display);
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(gameWorld);

        when(display.getUniqueId()).thenReturn(displayId);

        // The locale entry: MiniMessage, two lines, with the [player] placeholder
        when(lm.get(any(), eq(HologramManager.HOLOGRAM_REF))).thenReturn(HOLOGRAM_TEXT);

        chest = new DeathChestRecord();
        chest.setOwnerUUID(uuid);
        chest.setOwnerName("tastybento");
        chest.setChestLoc(new Location(gameWorld, 4, 80, 4));

        holograms = new HologramManager(addon);
    }

    @Test
    void testSpawnSetsMiniMessageTextWithPlayerName() {
        holograms.spawn(chest);

        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(display).text(text.capture());
        String plain = PlainTextComponentSerializer.plainText().serialize(text.getValue());
        assertTrue(plain.contains("Death Chest"), "Hologram should carry the locale text: " + plain);
        assertTrue(plain.contains("tastybento"), "[player] should be replaced with the owner's name: " + plain);
        assertTrue(plain.contains("\n"), "The locale's second line should survive as a new line: " + plain);
        verify(display).setBillboard(Billboard.CENTER);
        verify(display).setPersistent(false);
    }

    @Test
    void testSpawnGoesAboveTheChestBlock() {
        holograms.spawn(chest);

        ArgumentCaptor<Location> at = ArgumentCaptor.forClass(Location.class);
        verify(gameWorld).spawn(at.capture(), eq(TextDisplay.class));
        assertTrue(at.getValue().getX() == 4.5 && at.getValue().getZ() == 4.5,
                "Hologram should be centred on the block");
        assertTrue(at.getValue().getY() > 80 && at.getValue().getY() < 82,
                "Hologram should float just above the chest");
    }

    @Test
    void testSpawnAgainReplacesTheOldHologram() {
        mockedBukkit.when(() -> Bukkit.getEntity(displayId)).thenReturn(display);

        holograms.spawn(chest);
        holograms.spawn(chest);

        verify(display).remove();
        verify(gameWorld, times(2)).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testNoHologramWhenTheLocaleEntryIsMissingOrBlank() {
        // The default test locale stub answers every reference with itself, which
        // getTranslationOrNothing turns into "" - the same as an admin blanking the entry.
        when(lm.get(any(), eq(HologramManager.HOLOGRAM_REF))).thenReturn(null);

        holograms.spawn(chest);

        verify(gameWorld, never()).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testNoHologramForAVirtualChest() {
        chest.setChestLoc(null);
        holograms.spawn(chest);
        verify(gameWorld, never()).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testNoHologramWhenTheChunkIsNotLoaded() {
        when(gameWorld.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        holograms.spawn(chest);
        verify(gameWorld, never()).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testRemoveTakesTheEntityDown() {
        mockedBukkit.when(() -> Bukkit.getEntity(displayId)).thenReturn(display);

        holograms.spawn(chest);
        holograms.remove(chest);

        verify(display).remove();
    }

    @Test
    void testRemoveIsSafeWhenTheEntityIsAlreadyGone() {
        holograms.spawn(chest);
        mockedBukkit.when(() -> Bukkit.getEntity(displayId)).thenReturn(null);
        holograms.remove(chest);
        verify(display, never()).remove();
    }

    @Test
    void testRemoveAllClearsEverything() {
        mockedBukkit.when(() -> Bukkit.getEntity(displayId)).thenReturn(display);

        holograms.spawn(chest);
        holograms.removeAll();

        verify(display).remove();
    }

    @Test
    void testSpawnAllCoversEveryChest() {
        when(dcm.getAllChests()).thenReturn(List.of(chest));
        holograms.spawnAll();
        verify(gameWorld).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testChunkLoadRespawnsTheHologramNextTick() {
        when(dcm.getAllChests()).thenReturn(List.of(chest));
        when(sch.runTask(any(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(gameWorld);
        // Chest at 4,4 sits in chunk 0,0

        holograms.onChunkLoad(new ChunkLoadEvent(chunk, false));

        verify(gameWorld).spawn(any(Location.class), eq(TextDisplay.class));
    }

    @Test
    void testChunkLoadElsewhereDoesNothing() {
        when(dcm.getAllChests()).thenReturn(List.of(chest));
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(gameWorld);
        when(chunk.getX()).thenReturn(7);

        holograms.onChunkLoad(new ChunkLoadEvent(chunk, false));

        verify(sch, never()).runTask(any(), any(Runnable.class));
        verify(gameWorld, never()).spawn(any(Location.class), eq(TextDisplay.class));
    }
}
