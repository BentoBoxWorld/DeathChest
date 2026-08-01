package world.bentobox.deathchest.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.managers.CommandsManager;
import world.bentobox.bentobox.managers.PlayersManager;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Tests the admin death chest command.
 */
class AdminDeathChestCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand parent;
    @Mock
    private User user;
    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager manager;
    @Mock
    private PlayersManager pm;

    private AdminDeathChestCommand command;
    private DeathChestRecord chest;
    private final UUID target = UUID.randomUUID();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);
        when(plugin.getPlayers()).thenReturn(pm);
        when(addon.getManager()).thenReturn(manager);
        when(addon.getPlugin()).thenReturn(plugin);
        when(iwm.getFriendlyName(any(org.bukkit.World.class))).thenReturn("AcidIsland");

        World gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        when(gameWorld.getEnvironment()).thenReturn(World.Environment.NORMAL);
        mockedBukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(gameWorld);

        when(user.isOp()).thenReturn(true);
        when(user.getPermissionValue(anyString(), anyInt())).thenReturn(4);
        when(user.getWorld()).thenReturn(world);
        when(user.getUniqueId()).thenReturn(uuid);
        when(user.getTranslation(any())).thenAnswer(i -> i.getArgument(0, String.class));
        User.setPlugin(plugin);

        when(parent.getSubCommandAliases()).thenReturn(new HashMap<>());
        when(parent.getWorld()).thenReturn(world);

        chest = new DeathChestRecord();
        chest.setOwnerUUID(target);
        chest.setOwnerName("someone");
        chest.setChestLoc(new Location(gameWorld, 3, 64, 9));
        chest.setDeathLoc(new Location(gameWorld, 3, -120, 9));

        command = new AdminDeathChestCommand(addon, parent);
    }

    @Test
    void testNoArgsShowsASummary() {
        when(manager.getAllChests()).thenReturn(List.of(chest));

        assertTrue(command.execute(user, "deathchest", List.of()));

        verify(user).sendMessage("deathchest.commands.admin.summary", "[number]", "1");
    }

    @Test
    void testPurgeExpiresChests() {
        when(manager.checkExpiry()).thenReturn(4);

        assertTrue(command.execute(user, "deathchest", List.of("purge")));

        verify(manager).checkExpiry();
        verify(user).sendMessage("deathchest.commands.admin.purged", "[number]", "4");
    }

    @Test
    void testDebugTogglesAndSaves() {
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);

        assertTrue(command.execute(user, "deathchest", List.of("debug")));
        assertTrue(settings.isDebug());
        verify(user).sendMessage("deathchest.commands.admin.debug-on");

        assertTrue(command.execute(user, "deathchest", List.of("debug")));
        assertFalse(settings.isDebug());
        verify(user).sendMessage("deathchest.commands.admin.debug-off");
        verify(addon, times(2)).saveSettings();
    }

    @Test
    void testListsAPlayersChests() {
        when(pm.getUUID("someone")).thenReturn(target);
        when(manager.getChests(target)).thenReturn(List.of(chest));

        assertTrue(command.execute(user, "deathchest", List.of("someone")));

        verify(user).sendMessage("deathchest.commands.admin.header", "[name]", "someone", "[number]", "1");
        verify(user).sendMessage("deathchest.commands.admin.entry", "[number]", "1", "[description]",
                "AcidIsland 3, 64, 9", "[death]", "AcidIsland 3, -120, 9");
    }

    @Test
    void testPlayerWithNoChests() {
        when(pm.getUUID("someone")).thenReturn(target);
        when(manager.getChests(target)).thenReturn(List.of());

        assertTrue(command.execute(user, "deathchest", List.of("someone")));

        verify(user).sendMessage("deathchest.commands.admin.none", "[name]", "someone");
    }

    @Test
    void testUnknownPlayer() {
        when(pm.getUUID("nobody")).thenReturn(null);

        assertFalse(command.execute(user, "deathchest", List.of("nobody")));

        verify(user).sendMessage("general.errors.unknown-player", "[name]", "nobody");
    }

    @Test
    void testTooManyArgumentsShowsHelp() {
        assertFalse(command.execute(user, "deathchest", List.of("a", "b")));
    }

    @Test
    void testCommandIdentity() {
        assertTrue(command.getLabel().equals("deathchest"));
        assertFalse(command.isOnlyPlayer());
    }
}
