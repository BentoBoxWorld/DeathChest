package world.bentobox.deathchest;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.Pladdon;

/**
 * Plugin wrapper so DeathChest can be loaded as a Bukkit plugin as well as a BentoBox addon.
 *
 * @author tastybento
 */
public class DeathChestPladdon extends Pladdon {

    private Addon addon;

    @Override
    public Addon getAddon() {
        if (addon == null) {
            addon = new DeathChest();
        }
        return addon;
    }
}
