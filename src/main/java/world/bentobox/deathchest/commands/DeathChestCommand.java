package world.bentobox.deathchest.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * {@code /<gamemode> deathchest} - list your death chests, claim items the addon is holding
 * for you, or teleport to a chest.
 *
 * @author tastybento
 */
public class DeathChestCommand extends CompositeCommand {

    private static final String CLAIM = "claim";
    private static final String TELEPORT = "tp";

    private final DeathChest addon;

    public DeathChestCommand(DeathChest addon, CompositeCommand parent) {
        super(addon, parent, "deathchest", "deaths", "grave");
        this.addon = addon;
    }

    @Override
    public void setup() {
        setPermission("deathchest");
        setDescription("deathchest.commands.player.description");
        setParametersHelp("deathchest.commands.player.parameters");
        setOnlyPlayer(true);
    }

    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (!Util.sameWorld(getWorld(), user.getWorld())) {
            user.sendMessage("general.errors.wrong-world");
            return false;
        }
        return true;
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        List<DeathChestRecord> chests = addon.getManager().getChests(user.getUniqueId());
        if (chests.isEmpty()) {
            user.sendMessage("deathchest.commands.player.none");
            return true;
        }
        if (args.isEmpty()) {
            listChests(user, chests);
            return true;
        }
        if (args.size() != 2) {
            showHelp(this, user);
            return false;
        }
        Optional<DeathChestRecord> chest = byNumber(chests, args.get(1));
        if (chest.isEmpty()) {
            user.sendMessage("deathchest.commands.player.unknown-number", TextVariables.NUMBER, args.get(1));
            return false;
        }
        return switch (args.get(0).toLowerCase(java.util.Locale.ENGLISH)) {
        case CLAIM -> claim(user, chest.get());
        case TELEPORT -> teleport(user, chest.get());
        default -> {
            showHelp(this, user);
            yield false;
        }
        };
    }

    /**
     * Show the player a numbered list of their chests.
     *
     * @param user   player
     * @param chests their chests, newest first
     */
    private void listChests(User user, List<DeathChestRecord> chests) {
        user.sendMessage("deathchest.commands.player.header", TextVariables.NUMBER,
                String.valueOf(chests.size()));
        for (int i = 0; i < chests.size(); i++) {
            DeathChestRecord record = chests.get(i);
            Location loc = record.getChestLoc();
            String where = loc == null ? user.getTranslation("deathchest.commands.player.held-by-addon")
                    : loc.getWorld().getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                            + loc.getBlockZ();
            user.sendMessage("deathchest.commands.player.entry", TextVariables.NUMBER, String.valueOf(i + 1),
                    TextVariables.DESCRIPTION, where, "[time]", timeLeft(user, record));
        }
        user.sendMessage("deathchest.commands.player.footer", TextVariables.LABEL, getTopLabel());
    }

    /**
     * @param user   player, for translations
     * @param record chest record
     * @return a human readable time until expiry
     */
    private String timeLeft(User user, DeathChestRecord record) {
        if (record.getExpiryTime() == 0) {
            return user.getTranslation("deathchest.commands.player.never-expires");
        }
        long minutes = Math.max(0, (record.getExpiryTime() - System.currentTimeMillis()) / 60_000L);
        return user.getTranslation("deathchest.commands.player.minutes-left", TextVariables.NUMBER,
                String.valueOf(minutes));
    }

    /**
     * Hand over whatever the addon is holding for this chest.
     *
     * @param user   player
     * @param record chest record
     * @return true
     */
    private boolean claim(User user, DeathChestRecord record) {
        int given = addon.getManager().claim(user.getPlayer(), record);
        if (given == 0) {
            user.sendMessage("deathchest.commands.player.nothing-to-claim");
        } else {
            user.sendMessage("deathchest.commands.player.claimed", TextVariables.NUMBER, String.valueOf(given));
        }
        // A chest with no block behind it is finished once it has been emptied.
        if (record.isVirtual()) {
            addon.getManager().delete(record);
        }
        return true;
    }

    /**
     * Teleport the player to a chest that exists in the world.
     *
     * @param user   player
     * @param record chest record
     * @return true if the teleport was started
     */
    private boolean teleport(User user, DeathChestRecord record) {
        if (!addon.getSettings().isAllowTeleport() || !user.hasPermission(getPermissionPrefix() + "deathchest.teleport")) {
            user.sendMessage("general.errors.no-permission");
            return false;
        }
        Location loc = record.getChestLoc();
        if (loc == null) {
            user.sendMessage("deathchest.commands.player.no-block-to-visit");
            return false;
        }
        Util.teleportAsync(user.getPlayer(), loc.clone().add(0.5, 1, 0.5))
                .thenRun(() -> user.sendMessage("deathchest.commands.player.teleported"));
        return true;
    }

    /**
     * @param chests the player's chests
     * @param arg    a 1-based index as typed by the player
     * @return the chest, or empty if the number is not valid
     */
    private Optional<DeathChestRecord> byNumber(List<DeathChestRecord> chests, String arg) {
        if (!Util.isInteger(arg, true)) {
            return Optional.empty();
        }
        int index = Integer.parseInt(arg) - 1;
        return index < 0 || index >= chests.size() ? Optional.empty() : Optional.of(chests.get(index));
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        if (args.size() == 2) {
            return Optional.of(List.of(CLAIM, TELEPORT));
        }
        if (args.size() == 3) {
            int count = addon.getManager().getChests(user.getUniqueId()).size();
            List<String> numbers = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                numbers.add(String.valueOf(i));
            }
            return Optional.of(numbers);
        }
        return Optional.empty();
    }
}
