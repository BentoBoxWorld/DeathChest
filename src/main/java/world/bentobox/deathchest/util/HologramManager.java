package world.bentobox.deathchest.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.eclipse.jdt.annotation.NonNull;

import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Puts a floating text hologram above each death chest so it can be spotted from a distance.
 * <p>
 * The text comes from the {@code deathchest.hologram} locale entry, which is MiniMessage
 * formatted, may span several lines, and supports a {@code [player]} placeholder for the name
 * of the player who died. Setting the entry to an empty string turns holograms off.
 * <p>
 * The {@link TextDisplay} entities are deliberately not persistent: they are never written to
 * the world save, so a hologram can never be left behind by a crash or an externally deleted
 * record. Instead each one is respawned from its record whenever its chunk loads, and removed
 * when its record goes away.
 *
 * @author tastybento
 */
public class HologramManager implements Listener {

    /** Locale reference for the hologram text. */
    static final String HOLOGRAM_REF = "deathchest.hologram";

    /** How far above the base of the chest block the text floats. */
    private static final double Y_OFFSET = 1.25;

    private final DeathChest addon;

    /** Record unique id to the entity id of its hologram, live entities only. */
    private final Map<String, UUID> holograms = new HashMap<>();

    public HologramManager(DeathChest addon) {
        this.addon = addon;
    }

    /**
     * Spawn the hologram for a chest, replacing any existing one. Does nothing for a virtual
     * chest, an unloaded chunk - the chunk load handler picks that up later - or when the
     * locale entry is blank.
     *
     * @param chest chest record to show a hologram for
     */
    public void spawn(@NonNull DeathChestRecord chest) {
        remove(chest);
        Location location = chest.getChestLoc();
        UUID owner = chest.getOwnerUUID();
        if (location == null || location.getWorld() == null || owner == null) {
            return;
        }
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        // The owner's language, or the server default if they are offline. Blank means the
        // admin has switched holograms off.
        String raw = User.getInstance(owner).getTranslationOrNothing(HOLOGRAM_REF, "[player]",
                String.valueOf(chest.getOwnerName()));
        if (raw.isBlank()) {
            return;
        }
        Component text = Util.parseMiniMessageOrLegacy(raw);
        // Block coordinates, not a plain +0.5: getChestLoc() is already block-centred.
        Location spot = new Location(world, location.getBlockX() + 0.5, location.getBlockY() + Y_OFFSET,
                location.getBlockZ() + 0.5);
        TextDisplay display = world.spawn(spot, TextDisplay.class);
        display.text(text);
        display.setBillboard(Billboard.CENTER);
        display.setPersistent(false);
        holograms.put(chest.getUniqueId(), display.getUniqueId());
    }

    /**
     * Remove a chest's hologram, if it has a live one.
     *
     * @param chest chest record whose hologram should go
     */
    public void remove(@NonNull DeathChestRecord chest) {
        UUID id = holograms.remove(chest.getUniqueId());
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /**
     * Spawn holograms for every chest whose chunk is currently loaded. Used at startup and
     * after a reload, when the locale text may have changed.
     */
    public void spawnAll() {
        addon.getManager().getAllChests().forEach(this::spawn);
    }

    /**
     * Remove every live hologram. Used when the addon shuts down.
     */
    public void removeAll() {
        holograms.values().forEach(id -> {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        });
        holograms.clear();
    }

    /**
     * Respawn the holograms for any chests in a chunk that has just loaded. The spawn is
     * deferred a tick because adding entities from inside the chunk load event is unsafe.
     *
     * @param e chunk load event
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        List<DeathChestRecord> inChunk = addon.getManager().getAllChests().stream()
                .filter(chest -> isInChunk(chest, e)).toList();
        if (!inChunk.isEmpty()) {
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> inChunk.forEach(this::spawn));
        }
    }

    private boolean isInChunk(@NonNull DeathChestRecord chest, @NonNull ChunkLoadEvent e) {
        Location location = chest.getChestLoc();
        return location != null && e.getWorld().equals(location.getWorld())
                && location.getBlockX() >> 4 == e.getChunk().getX()
                && location.getBlockZ() >> 4 == e.getChunk().getZ();
    }
}
