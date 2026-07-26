package world.bentobox.deathchest;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.deathchest.commands.AdminDeathChestCommand;
import world.bentobox.deathchest.commands.DeathChestCommand;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.listeners.ChestListener;
import world.bentobox.deathchest.listeners.DeathListener;
import world.bentobox.deathchest.listeners.IslandListener;

/**
 * DeathChest addon entry point.
 * <p>
 * When a player dies in a game mode world their items go into a chest instead of onto the
 * ground. The chest goes where they died if that is somewhere they can get back to, and on
 * their own island if it is not - which is what makes this work in void worlds, where a
 * conventional death chest plugin has no block to build on and simply loses the items.
 *
 * @author tastybento
 */
public class DeathChest extends Addon {

    private Settings settings;
    private final Config<Settings> config = new Config<>(this, Settings.class);
    private final @NonNull List<GameModeAddon> gameModes = new ArrayList<>();
    private DeathChestManager manager;
    private BukkitTask expiryTask;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        loadSettings();
    }

    @Override
    public void onEnable() {
        if (getState() == State.DISABLED) {
            return;
        }
        gameModes.clear();
        getPlugin().getAddonsManager().getGameModeAddons().stream()
                .filter(gm -> !settings.getDisabledGameModes().contains(gm.getDescription().getName()))
                .forEach(gm -> {
                    gameModes.add(gm);
                    log("DeathChest hooking into " + gm.getDescription().getName());
                    gm.getPlayerCommand().ifPresent(c -> new DeathChestCommand(this, c));
                    gm.getAdminCommand().ifPresent(c -> new AdminDeathChestCommand(this, c));
                });

        if (gameModes.isEmpty()) {
            logWarning("DeathChest is not hooked into any game mode, so it will do nothing.");
            return;
        }

        manager = new DeathChestManager(this);
        manager.load();

        registerListener(new DeathListener(this));
        registerListener(new ChestListener(this));
        registerListener(new IslandListener(this));

        startExpiryTask();
    }

    @Override
    public void onReload() {
        loadSettings();
        if (settings == null) {
            // The config is broken and loadSettings has already disabled the addon. Leave the
            // task stopped rather than running on with settings that are no longer there.
            stopExpiryTask();
            return;
        }
        if (manager != null) {
            startExpiryTask();
        }
    }

    @Override
    public void onDisable() {
        stopExpiryTask();
        if (manager != null) {
            manager.close();
        }
    }

    /**
     * Start, or restart, the repeating task that expires old chests.
     */
    private void startExpiryTask() {
        stopExpiryTask();
        long period = settings.getExpiryCheckSeconds() * 20L;
        expiryTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> manager.checkExpiry(), period, period);
    }

    private void stopExpiryTask() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private void loadSettings() {
        settings = config.loadConfigObject();
        if (settings == null) {
            logError("DeathChest settings could not load! Addon disabled.");
            setState(State.DISABLED);
            return;
        }
        config.saveConfigObject(settings);
    }

    /**
     * @return the addon settings, or null if they have not loaded yet
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the death chest manager, or null if the addon is not enabled
     */
    public DeathChestManager getManager() {
        return manager;
    }

    /**
     * @return the game modes this addon is hooked into
     */
    public List<GameModeAddon> getGameModes() {
        return gameModes;
    }

    /**
     * @param world world to check
     * @return true if DeathChest is active in this world
     */
    public boolean inGameWorld(World world) {
        return world != null && gameModes.stream().anyMatch(gm -> gm.inWorld(world));
    }

    /**
     * The label players type to reach the game mode's commands in this world, for use in
     * messages that tell them what to run next.
     *
     * @param world world the player is in
     * @return the game mode's player command label, or "island" if it cannot be worked out
     */
    public String getGameModeLabel(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst()
                .flatMap(GameModeAddon::getPlayerCommand).map(c -> c.getLabel()).orElse("island");
    }
}
