package world.bentobox.deathchest.listeners;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Turns a player's drops into a death chest.
 *
 * @author tastybento
 */
public class DeathListener implements Listener {

    private final DeathChest addon;

    public DeathListener(DeathChest addon) {
        this.addon = addon;
    }

    /**
     * Runs at MONITOR-adjacent priority so that any other plugin that wants to modify the
     * drops has already done so, but still early enough to take them.
     *
     * @param e death event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        if (!addon.inGameWorld(player.getWorld())) {
            return;
        }
        // If the server or another plugin is keeping the inventory there is nothing to store.
        if (e.getKeepInventory()) {
            return;
        }
        Settings settings = addon.getSettings();
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        drops.removeIf(item -> item == null || item.getType().isAir());

        int xp = 0;
        if (settings.isStoreExperience()) {
            xp = e.getDroppedExp() * settings.getExperiencePercent() / 100;
        }
        if (drops.isEmpty() && xp == 0) {
            return;
        }

        e.getDrops().clear();
        if (settings.isStoreExperience()) {
            e.setDroppedExp(0);
        }

        DeathChestRecord record = addon.getManager().createChest(player, drops, xp);
        if (settings.isNotifyOnDeath()) {
            notifyPlayer(User.getInstance(player), record, addon.getGameModeLabel(player.getWorld()));
        }
    }

    /**
     * Tell the player where their things went.
     *
     * @param user   player who died
     * @param record their new death chest
     * @param label  the game mode's command label, for the "run this next" hint
     */
    private void notifyPlayer(User user, DeathChestRecord record, String label) {
        if (user == null || !user.isPlayer()) {
            return;
        }
        Location chest = record.getChestLoc();
        if (chest == null) {
            user.sendMessage("deathchest.death.stored-virtual", TextVariables.LABEL, label);
        } else {
            user.sendMessage("deathchest.death.chest-placed", "[world]", chest.getWorld().getName(),
                    TextVariables.NUMBER, String.valueOf(chest.getBlockX()), "[y]",
                    String.valueOf(chest.getBlockY()), "[z]", String.valueOf(chest.getBlockZ()));
            if (!addon.getManager().readItems(record).isEmpty()) {
                user.sendMessage("deathchest.death.overflow");
            }
        }
        if (record.getExpiryTime() > 0) {
            user.sendMessage("deathchest.death.expires", TextVariables.NUMBER,
                    String.valueOf(addon.getSettings().getExpiryMinutes()));
        }
    }
}
