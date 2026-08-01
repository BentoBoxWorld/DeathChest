package world.bentobox.deathchest;

import java.util.HashSet;
import java.util.Set;

import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.ConfigObject;
import world.bentobox.bentobox.api.configuration.StoreAt;

/**
 * DeathChest addon settings, saved to and loaded from {@code addons/DeathChest/config.yml}.
 *
 * @author tastybento
 */
@StoreAt(filename = "config.yml", path = "addons/DeathChest")
public class Settings implements ConfigObject {

    /**
     * What happens to a death chest when its timer runs out.
     */
    public enum ExpiryAction {
        /** Break the chest so its contents drop on the ground as items. */
        DROP,
        /** Remove the chest and delete the items entirely. */
        DELETE
    }

    @ConfigComment("DeathChest addon configuration file")
    @ConfigComment("See the documentation at https://docs.bentobox.world/en/latest/addons/DeathChest/")
    @ConfigComment("")
    @ConfigComment("DeathChest saves a player's items into a chest when they die instead of")
    @ConfigComment("dropping them. Unlike generic death chest plugins, it understands islands:")
    @ConfigComment("if the player dies in the void, in lava, or somewhere they cannot build,")
    @ConfigComment("the chest is placed on their own island instead of being lost.")
    @ConfigComment("")
    @ConfigComment("Game modes listed here are ignored by DeathChest. Example:")
    @ConfigComment("disabled-gamemodes:")
    @ConfigComment("  - BSkyBlock")
    @ConfigEntry(path = "disabled-gamemodes")
    private Set<String> disabledGameModes = new HashSet<>();

    @ConfigComment("")
    @ConfigComment("The block used for the death chest.")
    @ConfigComment("CHEST is the classic look. BARREL is a good choice for cramped islands")
    @ConfigComment("because it can be opened even with a block directly above it.")
    @ConfigEntry(path = "chest.material")
    private String chestMaterial = "CHEST";

    @ConfigComment("")
    @ConfigComment("Place the chest where the player died, if that is a spot they can reach")
    @ConfigComment("and build in. If false, the chest is always placed at the player's island home.")
    @ConfigComment("Deaths in the void, outside a build-allowed area, or in an unreachable spot")
    @ConfigComment("always fall back to the island home regardless of this setting.")
    @ConfigEntry(path = "chest.place-at-death-location")
    private boolean placeAtDeathLocation = true;

    @ConfigComment("")
    @ConfigComment("How far to look sideways, in blocks, for a free spot to put the chest.")
    @ConfigComment("Range 1 to 32.")
    @ConfigEntry(path = "chest.search-radius")
    private int searchRadius = 8;

    @ConfigComment("")
    @ConfigComment("How far to look downwards, in blocks, for ground to stand the chest on.")
    @ConfigComment("This is what stops a chest ending up far below the player - on the sea bed")
    @ConfigComment("under an ocean world, or buried in terrain. If no ground is found within")
    @ConfigComment("this distance the chest goes to the player's island instead, which is")
    @ConfigComment("usually what you want. Range 1 to 64.")
    @ConfigEntry(path = "chest.search-depth")
    private int searchDepth = 16;

    @ConfigComment("")
    @ConfigComment("How many minutes a death chest lasts before it expires. 0 means never.")
    @ConfigEntry(path = "chest.expiry-minutes")
    private int expiryMinutes = 60;

    @ConfigComment("")
    @ConfigComment("What to do when a death chest expires.")
    @ConfigComment("DROP   - break the chest and let the items drop on the ground")
    @ConfigComment("DELETE - remove the chest and its contents")
    @ConfigEntry(path = "chest.expiry-action")
    private ExpiryAction expiryAction = ExpiryAction.DROP;

    @ConfigComment("")
    @ConfigComment("How often, in seconds, to check for expired chests.")
    @ConfigEntry(path = "chest.expiry-check-seconds")
    private int expiryCheckSeconds = 60;

    @ConfigComment("")
    @ConfigComment("Maximum number of death chests a player may have at once.")
    @ConfigComment("When exceeded, the oldest chest is expired early. 0 means unlimited.")
    @ConfigEntry(path = "chest.max-per-player")
    private int maxChestsPerPlayer = 3;

    @ConfigComment("")
    @ConfigComment("Let island team members open each other's death chests.")
    @ConfigComment("If false, only the owner can open their own chest.")
    @ConfigEntry(path = "chest.team-access")
    private boolean teamAccess = true;

    @ConfigComment("")
    @ConfigComment("Stop death chests being blown up by creepers, TNT and so on.")
    @ConfigEntry(path = "chest.protect-from-explosions")
    private boolean protectFromExplosions = true;

    @ConfigComment("")
    @ConfigComment("Store the player's experience in the chest and give it back when it is opened.")
    @ConfigEntry(path = "experience.store")
    private boolean storeExperience = true;

    @ConfigComment("")
    @ConfigComment("Percentage of the player's experience to store. 0 to 100.")
    @ConfigComment("The remainder is lost, which keeps some sting in dying.")
    @ConfigEntry(path = "experience.percent")
    private int experiencePercent = 100;

    @ConfigComment("")
    @ConfigComment("Tell the player where their death chest is when they die.")
    @ConfigEntry(path = "notify.on-death")
    private boolean notifyOnDeath = true;

    @ConfigComment("")
    @ConfigComment("Allow '/[label] deathchest tp <number>' to teleport the player to their chest.")
    @ConfigComment("Players still need the [gamemode].deathchest.teleport permission.")
    @ConfigEntry(path = "commands.allow-teleport")
    private boolean allowTeleport = true;

    @ConfigComment("")
    @ConfigComment("Write a detailed report of every death to the server console: which world it")
    @ConfigComment("was in, whether another plugin kept the inventory or took the drops, and where")
    @ConfigComment("the chest ended up. Turn this on if death chests are not appearing and you")
    @ConfigComment("need to find out why. It can also be toggled in game, without a restart, with")
    @ConfigComment("'/<gamemode>admin deathchest debug'.")
    @ConfigEntry(path = "debug")
    private boolean debug = false;

    // ------------------------------------------------------------------------
    // Getters and setters
    // ------------------------------------------------------------------------

    public Set<String> getDisabledGameModes() {
        return disabledGameModes;
    }

    public void setDisabledGameModes(Set<String> disabledGameModes) {
        this.disabledGameModes = disabledGameModes;
    }

    public String getChestMaterial() {
        return chestMaterial;
    }

    public void setChestMaterial(String chestMaterial) {
        this.chestMaterial = chestMaterial;
    }

    public boolean isPlaceAtDeathLocation() {
        return placeAtDeathLocation;
    }

    public void setPlaceAtDeathLocation(boolean placeAtDeathLocation) {
        this.placeAtDeathLocation = placeAtDeathLocation;
    }

    /**
     * @return the search radius, clamped to a sane 1 to 32 blocks
     */
    public int getSearchRadius() {
        return Math.clamp(searchRadius, 1, 32);
    }

    public void setSearchRadius(int searchRadius) {
        this.searchRadius = searchRadius;
    }

    /**
     * @return the downwards search distance, clamped to a sane 1 to 64 blocks
     */
    public int getSearchDepth() {
        return Math.clamp(searchDepth, 1, 64);
    }

    public void setSearchDepth(int searchDepth) {
        this.searchDepth = searchDepth;
    }

    public int getExpiryMinutes() {
        return Math.max(0, expiryMinutes);
    }

    public void setExpiryMinutes(int expiryMinutes) {
        this.expiryMinutes = expiryMinutes;
    }

    public ExpiryAction getExpiryAction() {
        return expiryAction == null ? ExpiryAction.DROP : expiryAction;
    }

    public void setExpiryAction(ExpiryAction expiryAction) {
        this.expiryAction = expiryAction;
    }

    /**
     * @return the expiry check period in seconds, never less than 5
     */
    public int getExpiryCheckSeconds() {
        return Math.max(5, expiryCheckSeconds);
    }

    public void setExpiryCheckSeconds(int expiryCheckSeconds) {
        this.expiryCheckSeconds = expiryCheckSeconds;
    }

    public int getMaxChestsPerPlayer() {
        return Math.max(0, maxChestsPerPlayer);
    }

    public void setMaxChestsPerPlayer(int maxChestsPerPlayer) {
        this.maxChestsPerPlayer = maxChestsPerPlayer;
    }

    public boolean isTeamAccess() {
        return teamAccess;
    }

    public void setTeamAccess(boolean teamAccess) {
        this.teamAccess = teamAccess;
    }

    public boolean isProtectFromExplosions() {
        return protectFromExplosions;
    }

    public void setProtectFromExplosions(boolean protectFromExplosions) {
        this.protectFromExplosions = protectFromExplosions;
    }

    public boolean isStoreExperience() {
        return storeExperience;
    }

    public void setStoreExperience(boolean storeExperience) {
        this.storeExperience = storeExperience;
    }

    /**
     * @return the percentage of experience to store, clamped to 0 to 100
     */
    public int getExperiencePercent() {
        return Math.clamp(experiencePercent, 0, 100);
    }

    public void setExperiencePercent(int experiencePercent) {
        this.experiencePercent = experiencePercent;
    }

    public boolean isNotifyOnDeath() {
        return notifyOnDeath;
    }

    public void setNotifyOnDeath(boolean notifyOnDeath) {
        this.notifyOnDeath = notifyOnDeath;
    }

    public boolean isAllowTeleport() {
        return allowTeleport;
    }

    public void setAllowTeleport(boolean allowTeleport) {
        this.allowTeleport = allowTeleport;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
