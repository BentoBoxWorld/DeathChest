package world.bentobox.deathchest.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import world.bentobox.bentobox.util.Util;
import world.bentobox.deathchest.CommonTestSetup;
import world.bentobox.deathchest.DeathChest;
import world.bentobox.deathchest.Settings;
import world.bentobox.deathchest.data.DeathChestManager;
import world.bentobox.deathchest.data.DeathChestRecord;

/**
 * Tests the player-facing death chest command.
 */
class DeathChestCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand parent;
    @Mock
    private User user;
    @Mock
    private DeathChest addon;
    @Mock
    private DeathChestManager manager;

    private DeathChestCommand command;
    private Settings settings;
    private World gameWorld;
    private DeathChestRecord physical;
    private DeathChestRecord virtual;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);

        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getManager()).thenReturn(manager);

        gameWorld = mock(World.class);
        when(gameWorld.getName()).thenReturn("bskyblock_world");
        mockedBukkit.when(() -> org.bukkit.Bukkit.getWorld(anyString())).thenReturn(gameWorld);

        when(user.isOp()).thenReturn(false);
        when(user.getPermissionValue(anyString(), anyInt())).thenReturn(4);
        when(user.hasPermission(anyString())).thenReturn(true);
        when(user.getWorld()).thenReturn(world);
        when(user.getUniqueId()).thenReturn(uuid);
        when(user.getPlayer()).thenReturn(mockPlayer);
        when(user.getName()).thenReturn("tastybento");
        when(user.getTranslation(any())).thenAnswer(i -> i.getArgument(0, String.class));
        when(user.getTranslation(anyString(), any(String[].class))).thenAnswer(i -> i.getArgument(0, String.class));
        User.setPlugin(plugin);

        when(parent.getSubCommandAliases()).thenReturn(new HashMap<>());
        when(parent.getWorld()).thenReturn(world);
        when(parent.getTopLabel()).thenReturn("is");

        physical = new DeathChestRecord();
        physical.setOwnerUUID(uuid);
        physical.setDeathTime(2000L);
        physical.setChestLoc(new Location(gameWorld, 1, 70, 2));
        virtual = new DeathChestRecord();
        virtual.setOwnerUUID(uuid);
        virtual.setDeathTime(1000L);

        command = new DeathChestCommand(addon, parent);
    }

    @Test
    void testNoChestsTellsThePlayer() {
        when(manager.getChests(uuid)).thenReturn(List.of());

        assertTrue(command.execute(user, "deathchest", List.of()));

        verify(user).sendMessage("deathchest.commands.player.none");
    }

    @Test
    void testListShowsEveryChest() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical, virtual));

        assertTrue(command.execute(user, "deathchest", List.of()));

        // One "entry" line per chest, plus the header and footer
        verify(user, org.mockito.Mockito.times(2)).sendMessage(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(user).sendMessage("deathchest.commands.player.header", "[number]", "2");
    }

    @Test
    void testClaimHandsOverHeldItems() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical, virtual));
        when(manager.claim(mockPlayer, virtual)).thenReturn(2);

        assertTrue(command.execute(user, "deathchest", List.of("claim", "2")));

        verify(manager).claim(mockPlayer, virtual);
        // A virtual chest is finished once claimed
        verify(manager).delete(virtual);
    }

    @Test
    void testClaimOfAPhysicalChestDoesNotDeleteIt() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical));
        when(manager.claim(mockPlayer, physical)).thenReturn(0);

        assertTrue(command.execute(user, "deathchest", List.of("claim", "1")));

        verify(manager, never()).delete(any());
        verify(user).sendMessage("deathchest.commands.player.nothing-to-claim");
    }

    @Test
    void testBadNumbersAreRejected() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical));

        assertFalse(command.execute(user, "deathchest", List.of("claim", "0")));
        assertFalse(command.execute(user, "deathchest", List.of("claim", "9")));
        assertFalse(command.execute(user, "deathchest", List.of("claim", "banana")));
        verify(manager, never()).claim(any(), any());
    }

    @Test
    void testTeleportToAVirtualChestIsRefused() {
        when(manager.getChests(uuid)).thenReturn(List.of(virtual));

        assertFalse(command.execute(user, "deathchest", List.of("tp", "1")));

        verify(user).sendMessage("deathchest.commands.player.no-block-to-visit");
    }

    @Test
    void testTeleportIsRefusedWhenTurnedOffInConfig() {
        settings.setAllowTeleport(false);
        when(manager.getChests(uuid)).thenReturn(List.of(physical));

        assertFalse(command.execute(user, "deathchest", List.of("tp", "1")));

        verify(user).sendMessage("general.errors.no-permission");
    }

    @Test
    void testUnknownSubCommandShowsHelp() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical));

        assertFalse(command.execute(user, "deathchest", List.of("explode", "1")));
    }

    @Test
    void testWrongWorldIsRejected() {
        mockedUtil.when(() -> Util.sameWorld(any(), any())).thenReturn(false);

        assertFalse(command.canExecute(user, "deathchest", List.of()));

        verify(user).sendMessage("general.errors.wrong-world");
    }

    @Test
    void testTabCompleteOffersSubCommands() {
        when(manager.getChests(uuid)).thenReturn(List.of(physical, virtual));

        assertTrue(command.tabComplete(user, "deathchest", List.of("deathchest", "")).orElseThrow()
                .containsAll(List.of("claim", "tp")));
        assertTrue(command.tabComplete(user, "deathchest", List.of("deathchest", "claim", "")).orElseThrow()
                .containsAll(List.of("1", "2")));
    }

    @Test
    void testTabCompleteIsEmptyBeyondTheArguments() {
        assertTrue(command.tabComplete(user, "deathchest", List.of("deathchest", "claim", "1", "")).isEmpty());
    }

    @Test
    void testCommandIdentity() {
        assertTrue(command.getLabel().equals("deathchest"));
        assertTrue(command.isOnlyPlayer());
        assertTrue(command.getAliases().containsAll(List.of("deaths", "grave")));
    }

    @Test
    void testOwnerOfChestsIsTheCaller() {
        UUID other = UUID.randomUUID();
        when(user.getUniqueId()).thenReturn(other);
        when(manager.getChests(other)).thenReturn(List.of());

        assertTrue(command.execute(user, "deathchest", List.of()));

        verify(manager).getChests(other);
    }
}
