package world.bentobox.deathchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.bentobox.api.addons.Addon.State;
import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.managers.AddonsManager;
import world.bentobox.bentobox.managers.CommandsManager;
import world.bentobox.bentobox.managers.FlagsManager;

/**
 * Tests the DeathChest addon lifecycle and game mode hooking.
 */
class DeathChestTest extends CommonTestSetup {

    private DeathChest addon;
    private MockedStatic<DatabaseSetup> mockDb;

    /** Real backing maps, so command registration on the mocked parents is observable. */
    private final Map<String, CompositeCommand> playerSubCommands = new LinkedHashMap<>();
    private final Map<String, CompositeCommand> playerAliases = new LinkedHashMap<>();
    private final Map<String, CompositeCommand> adminSubCommands = new LinkedHashMap<>();
    private final Map<String, CompositeCommand> adminAliases = new LinkedHashMap<>();

    @Mock
    private FlagsManager flagsManager;
    @Mock
    private world.bentobox.bentobox.Settings pluginSettings;
    @Mock
    private AddonsManager am;
    @Mock
    private GameModeAddon gameMode;
    @Mock
    private CompositeCommand playerCommand;
    @Mock
    private CompositeCommand adminCommand;

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // Database
        AbstractDatabaseHandler<Object> h = mock(AbstractDatabaseHandler.class);
        mockDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(h.loadObjects()).thenReturn(Collections.emptyList());

        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);
        when(plugin.getFlagsManager()).thenReturn(flagsManager);
        when(flagsManager.getFlags()).thenReturn(Collections.emptyList());

        // A single game mode with both commands available
        AddonDescription gmDesc = new AddonDescription.Builder("bentobox", "BSkyBlock", "1.0.0").build();
        when(gameMode.getDescription()).thenReturn(gmDesc);
        when(gameMode.getPlayerCommand()).thenReturn(Optional.of(playerCommand));
        when(gameMode.getAdminCommand()).thenReturn(Optional.of(adminCommand));
        when(gameMode.inWorld(any(World.class))).thenReturn(true);
        when(playerCommand.getLabel()).thenReturn("is");
        when(playerCommand.getTopLabel()).thenReturn("ai");
        when(adminCommand.getTopLabel()).thenReturn("aiadmin");
        // The game mode's permission prefix is what addon sub-commands inherit
        when(playerCommand.getPermissionPrefix()).thenReturn("acidisland.");
        when(adminCommand.getPermissionPrefix()).thenReturn("acidisland.");
        when(playerCommand.getSubCommands()).thenReturn(playerSubCommands);
        when(playerCommand.getSubCommandAliases()).thenReturn(playerAliases);
        when(adminCommand.getSubCommands()).thenReturn(adminSubCommands);
        when(adminCommand.getSubCommandAliases()).thenReturn(adminAliases);
        when(plugin.getAddonsManager()).thenReturn(am);
        when(am.getGameModeAddons()).thenReturn(List.of(gameMode));

        addon = new DeathChest();
        File jFile = new File("addon.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jFile))) {
            Path fromPath = Paths.get("src/main/resources/config.yml");
            Path path = Paths.get("config.yml");
            Files.copy(fromPath, path);
            add(path, jos);
        }
        addon.setDataFolder(new File("addons/DeathChest"));
        addon.setFile(jFile);
        addon.setDescription(new AddonDescription.Builder("bentobox", "DeathChest", "1.0.0").description("test")
                .authors("tastybento").build());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockDb.closeOnDemand();
        super.tearDown();
        new File("config.yml").delete();
        new File("addon.jar").delete();
        deleteAll(new File("addons"));
    }

    private void add(Path path, JarOutputStream jos) throws IOException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            jos.putNextEntry(new JarEntry(path.toString()));
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                jos.write(buffer, 0, read);
            }
        }
    }

    @Test
    void testSettingsNullBeforeLoad() {
        assertNull(addon.getSettings());
        assertNull(addon.getManager());
    }

    @Test
    void testOnLoad() {
        addon.onLoad();
        assertNotNull(addon.getSettings());
        assertTrue(new File("addons/DeathChest", "config.yml").exists());
    }

    @Test
    void testOnLoadDefaults() {
        addon.onLoad();
        assertEquals("CHEST", addon.getSettings().getChestMaterial());
        assertEquals(60, addon.getSettings().getExpiryMinutes());
        assertEquals(3, addon.getSettings().getMaxChestsPerPlayer());
    }

    @Test
    void testOnEnableHooksGameMode() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        assertNotEquals(State.DISABLED, addon.getState());
        assertEquals(1, addon.getGameModes().size());
        assertNotNull(addon.getManager());
    }

    /**
     * The commands have to end up in the parent command's sub-command map, or they exist but
     * nothing can reach them. A plain Mockito mock hands back a throwaway empty map for every
     * getSubCommands() call, so the registration would silently go nowhere - the maps are
     * stubbed with real ones here so the wiring is actually observable.
     */
    @Test
    void testCommandsAreRegisteredUnderTheGameMode() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();

        assertTrue(playerSubCommands.containsKey("deathchest"),
                "The player command must be a sub-command of the game mode's player command");
        assertTrue(adminSubCommands.containsKey("deathchest"),
                "The admin command must be a sub-command of the game mode's admin command");
    }

    @Test
    void testCommandAliasesAreRegistered() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();

        assertTrue(playerAliases.containsKey("deaths"));
        assertTrue(playerAliases.containsKey("grave"));
        assertTrue(adminAliases.containsKey("deathchests"));
    }

    @Test
    void testRegisteredCommandsCarryTheGameModePermission() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();

        assertEquals("acidisland.deathchest", playerSubCommands.get("deathchest").getPermission());
        assertEquals("acidisland.admin.deathchest", adminSubCommands.get("deathchest").getPermission());
    }

    @Test
    void testDisabledGameModeIsNotHooked() {
        addon.onLoad();
        addon.getSettings().getDisabledGameModes().add("BSkyBlock");
        addon.setState(State.ENABLED);
        addon.onEnable();
        assertTrue(addon.getGameModes().isEmpty());
        // No game modes means no manager and nothing to do
        assertNull(addon.getManager());
    }

    @Test
    void testInGameWorld() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        assertTrue(addon.inGameWorld(world));
        assertTrue(!addon.inGameWorld(null));
    }

    @Test
    void testGetGameModeLabel() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        assertEquals("is", addon.getGameModeLabel(world));
    }

    @Test
    void testGetGameModeLabelFallsBackWhenNotHooked() {
        addon.onLoad();
        assertEquals("island", addon.getGameModeLabel(world));
    }

    @Test
    void testOnReload() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        addon.onReload();
        assertNotNull(addon.getSettings());
    }

    @Test
    void testOnDisable() {
        addon.onLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        addon.onDisable();
        assertNotNull(addon.getSettings());
    }

    @Test
    void testOnDisableBeforeEnableIsSafe() {
        addon.onDisable();
        assertNull(addon.getManager());
    }
}
