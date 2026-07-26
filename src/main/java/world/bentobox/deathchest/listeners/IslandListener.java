package world.bentobox.deathchest.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import world.bentobox.bentobox.api.events.island.IslandDeleteEvent;
import world.bentobox.bentobox.api.events.island.IslandPreclearEvent;
import world.bentobox.deathchest.DeathChest;

/**
 * Drops death chest records when the island under them is about to be wiped, so that a reset
 * island does not leave behind records pointing at blocks that no longer exist.
 *
 * @author tastybento
 */
public class IslandListener implements Listener {

    private final DeathChest addon;

    public IslandListener(DeathChest addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDelete(IslandDeleteEvent e) {
        if (e.getIsland() != null) {
            addon.getManager().removeIslandChests(e.getIsland());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandPreclear(IslandPreclearEvent e) {
        if (e.getOldIsland() != null) {
            addon.getManager().removeIslandChests(e.getOldIsland());
        }
    }
}
