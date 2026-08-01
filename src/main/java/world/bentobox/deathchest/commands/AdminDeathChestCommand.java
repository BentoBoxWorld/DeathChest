package world.bentobox.deathchest.commands;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestRecord;
import world.bentobox.deathchest.listeners.DeathDebugListener;
import world.bentobox.deathchest.util.WorldName;

/**
 * {@code /<gamemode>admin deathchest} - look at, and clean up, players' death chests.
 *
 * @author tastybento
 */
public class AdminDeathChestCommand extends CompositeCommand {

    private static final String PURGE = "purge";

    private static final String DEBUG = "debug";

    private final DeathChest addon;

    public AdminDeathChestCommand(DeathChest addon, CompositeCommand parent) {
        super(addon, parent, "deathchest", "deathchests");
        this.addon = addon;
    }

    @Override
    public void setup() {
        setPermission("admin.deathchest");
        setDescription("deathchest.commands.admin.description");
        setParametersHelp("deathchest.commands.admin.parameters");
        setOnlyPlayer(false);
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.isEmpty()) {
            user.sendMessage("deathchest.commands.admin.summary", TextVariables.NUMBER,
                    String.valueOf(addon.getManager().getAllChests().size()));
            return true;
        }
        if (args.size() != 1) {
            showHelp(this, user);
            return false;
        }
        if (DEBUG.equalsIgnoreCase(args.get(0))) {
            boolean on = !addon.getSettings().isDebug();
            addon.getSettings().setDebug(on);
            addon.saveSettings();
            if (on) {
                // Report the state now: it is often enough on its own to show what is wrong.
                DeathDebugListener.dumpState(addon);
            }
            user.sendMessage(on ? "deathchest.commands.admin.debug-on" : "deathchest.commands.admin.debug-off");
            return true;
        }
        if (PURGE.equalsIgnoreCase(args.get(0))) {
            int purged = addon.getManager().checkExpiry();
            user.sendMessage("deathchest.commands.admin.purged", TextVariables.NUMBER, String.valueOf(purged));
            return true;
        }
        UUID target = getPlayers().getUUID(args.get(0));
        if (target == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        List<DeathChestRecord> chests = addon.getManager().getChests(target);
        if (chests.isEmpty()) {
            user.sendMessage("deathchest.commands.admin.none", TextVariables.NAME, args.get(0));
            return true;
        }
        user.sendMessage("deathchest.commands.admin.header", TextVariables.NAME, args.get(0),
                TextVariables.NUMBER, String.valueOf(chests.size()));
        for (int i = 0; i < chests.size(); i++) {
            DeathChestRecord chest = chests.get(i);
            Location loc = chest.getChestLoc();
            Location death = chest.getDeathLoc();
            user.sendMessage("deathchest.commands.admin.entry", TextVariables.NUMBER, String.valueOf(i + 1),
                    TextVariables.DESCRIPTION, WorldName.describe(addon, user, loc), "[death]",
                    WorldName.describe(addon, user, death));
        }
        return true;
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (args.size() == 2) {
            List<String> options = new java.util.ArrayList<>(Util.getOnlinePlayerList(user));
            options.add(PURGE);
            options.add(DEBUG);
            return Optional.of(Util.tabLimit(options, args.get(1).toLowerCase(Locale.ENGLISH)));
        }
        return Optional.empty();
    }
}
