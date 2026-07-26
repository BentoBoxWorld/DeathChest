package world.bentobox.deathchest.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.DeathChest;

/**
 * Turns a location into something worth reading. Raw world names like
 * {@code acidisland_world} mean nothing to a player, so messages use the game mode's friendly
 * name, with the dimension appended only when it is not the overworld.
 *
 * @author tastybento
 */
public final class WorldName {

    private WorldName() {
        // Utility class
    }

    /**
     * Describe the world a location is in.
     *
     * @param addon addon, for the world manager
     * @param user  user the text is for, for translations
     * @param world world to describe, may be null
     * @return e.g. "AcidIsland", "AcidIsland Nether", "BSkyBlock The End"
     */
    public static String of(DeathChest addon, User user, @Nullable World world) {
        if (world == null) {
            return "";
        }
        // Colour codes in a configured friendly name would be parsed as MiniMessage tags by the
        // message this ends up inside, so strip them.
        String name = Util.stripColor(addon.getPlugin().getIWM().getFriendlyName(world));
        return switch (world.getEnvironment()) {
        case NETHER -> name + " " + user.getTranslation("deathchest.world.nether");
        case THE_END -> name + " " + user.getTranslation("deathchest.world.the-end");
        default -> name;
        };
    }

    /**
     * Describe a location as world plus block coordinates.
     *
     * @param addon    addon, for the world manager
     * @param user     user the text is for, for translations
     * @param location location to describe, may be null
     * @return e.g. "AcidIsland Nether 12, 64, -30", or "-" if there is no location
     */
    public static String describe(DeathChest addon, User user, @Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return "-";
        }
        return of(addon, user, location.getWorld()) + " " + location.getBlockX() + ", " + location.getBlockY()
                + ", " + location.getBlockZ();
    }
}
