package world.bentobox.deathchest.listeners;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Guards death chest blocks and keeps their records in step with what is in the world.
 *
 * @author tastybento
 */
public class ChestListener implements Listener {

    private final DeathChest addon;

    public ChestListener(DeathChest addon) {
        this.addon = addon;
    }

    private DeathChestManager manager() {
        return addon.getManager();
    }

    /**
     * Stop the wrong player opening a death chest, and hand over its experience to the right one.
     *
     * @param e interact event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Optional<DeathChestRecord> found = manager().getChestAt(e.getClickedBlock().getLocation());
        if (found.isEmpty()) {
            return;
        }
        DeathChestRecord chest = found.get();
        if (!canAccess(e.getPlayer(), chest)) {
            e.setCancelled(true);
            User.getInstance(e.getPlayer()).sendMessage("deathchest.errors.not-your-chest", TextVariables.NAME,
                    String.valueOf(chest.getOwnerName()));
            return;
        }
        manager().giveExperience(e.getPlayer(), chest);
    }

    /**
     * When a death chest is closed, top it up from anything the addon is still holding, and
     * clear it away if it is empty.
     *
     * @param e close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        Location location = e.getInventory().getLocation();
        if (location == null) {
            return;
        }
        manager().getChestAt(location).ifPresent(chest -> manager().refill(chest));
    }

    /**
     * Only someone who could open the chest may break it. Breaking it drops the contents, so
     * the record goes with it.
     *
     * @param e break event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Optional<DeathChestRecord> found = manager().getChestAt(e.getBlock().getLocation());
        if (found.isEmpty()) {
            return;
        }
        DeathChestRecord chest = found.get();
        if (!canAccess(e.getPlayer(), chest)) {
            e.setCancelled(true);
            User.getInstance(e.getPlayer()).sendMessage("deathchest.errors.not-your-chest", TextVariables.NAME,
                    String.valueOf(chest.getOwnerName()));
            return;
        }
        // The block's own contents drop with it. Whatever the addon is still holding has to be
        // handed over explicitly or it would be lost with the record.
        manager().claim(e.getPlayer(), chest);
        manager().delete(chest);
    }

    /**
     * Keep death chests out of explosion block lists.
     *
     * @param e explosion event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (addon.getSettings().isProtectFromExplosions()) {
            e.blockList().removeIf(b -> manager().getChestAt(b.getLocation()).isPresent());
        }
    }

    /**
     * Keep death chests out of block explosion lists, such as beds in the nether.
     *
     * @param e explosion event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (addon.getSettings().isProtectFromExplosions()) {
            e.blockList().removeIf(b -> manager().getChestAt(b.getLocation()).isPresent());
        }
    }

    /**
     * @param player player trying to get at a chest
     * @param chest  the chest record
     * @return true if this player may open or break the chest
     */
    boolean canAccess(Player player, DeathChestRecord chest) {
        UUID owner = chest.getOwnerUUID();
        if (owner == null || owner.equals(player.getUniqueId())) {
            return true;
        }
        if (!addon.getSettings().isTeamAccess()) {
            return false;
        }
        // Team access means the island the chest sits on, so a chest that landed on the
        // owner's island is shared with that island's team and no one else.
        Location chestLocation = chest.getChestLoc();
        return chestLocation != null && addon.getIslands().getIslandAt(chestLocation)
                .map(island -> island.getMemberSet().contains(player.getUniqueId())
                        && island.getMemberSet().contains(owner))
                .orElse(false);
    }
}
