package world.bentobox.deathchest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Round trips items through the Base64 store. Runs against a real MockBukkit server because
 * item serialization needs the item registry.
 */
class ItemSerializerTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testRoundTrip() {
        List<ItemStack> items = List.of(new ItemStack(Material.DIAMOND, 5),
                new ItemStack(Material.OAK_LOG, 64), new ItemStack(Material.IRON_PICKAXE));

        List<ItemStack> back = ItemSerializer.fromBase64(ItemSerializer.toBase64(items));

        assertEquals(3, back.size());
        assertEquals(Material.DIAMOND, back.get(0).getType());
        assertEquals(5, back.get(0).getAmount());
        assertEquals(Material.OAK_LOG, back.get(1).getType());
        assertEquals(64, back.get(1).getAmount());
        assertEquals(Material.IRON_PICKAXE, back.get(2).getType());
    }

    @Test
    void testNullsAndAirAreDropped() {
        List<ItemStack> items = new ArrayList<>(
                Arrays.asList(new ItemStack(Material.DIAMOND), null, new ItemStack(Material.AIR)));

        List<ItemStack> back = ItemSerializer.fromBase64(ItemSerializer.toBase64(items));

        assertEquals(1, back.size());
        assertEquals(Material.DIAMOND, back.get(0).getType());
    }

    @Test
    void testEmptyInputs() {
        assertEquals("", ItemSerializer.toBase64(null));
        assertEquals("", ItemSerializer.toBase64(List.of()));
        assertTrue(ItemSerializer.fromBase64(null).isEmpty());
        assertTrue(ItemSerializer.fromBase64("").isEmpty());
    }

    @Test
    void testReturnedListIsMutable() {
        List<ItemStack> back = ItemSerializer.fromBase64(ItemSerializer.toBase64(List.of(new ItemStack(Material.DIRT))));
        back.add(new ItemStack(Material.STONE));
        assertEquals(2, back.size());
    }

    @Test
    void testGarbageInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> ItemSerializer.fromBase64("not base64 at all!!"));
    }
}
