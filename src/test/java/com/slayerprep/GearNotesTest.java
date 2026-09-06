package com.slayerprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** Wiki footnotes: set notes are all-or-nothing, item-linked notes depend on the chosen weapon. */
public class GearNotesTest
{
	private GearData.GearTask tormented;
	private GearAdvisor advisor;

	@Before
	public void setUp() throws Exception
	{
		try (Reader gear = new InputStreamReader(GearNotesTest.class.getResourceAsStream("test-gear.json"), StandardCharsets.UTF_8))
		{
			GearData data = GearData.parse(new Gson(), gear);
			tormented = data.optionsFor("Greater demons").get(0).getTask();
		}
		advisor = new GearAdvisor(Collections.emptyMap());
	}

	private Map<String, SlotChoice> ranged(String... bankItems)
	{
		Map<String, Integer> bank = new HashMap<>();
		for (String item : bankItems)
		{
			bank.put(item, 1);
		}
		return advisor.advise(tormented, ItemSnapshot.empty().withBank(bank)).get("Ranged");
	}

	@Test
	public void partialVoidIsNeverMixedWithOtherArmour()
	{
		Map<String, SlotChoice> set = ranged("Slayer helmet (i)", "Elite void top", "Elite void robe",
			"Black d'hide body", "Black d'hide chaps", "Toxic blowpipe");
		assertEquals("Slayer helmet (i)", set.get("head").getItemName());
		assertEquals("Black d'hide body", set.get("body").getItemName());
		assertEquals("Black d'hide chaps", set.get("legs").getItemName());
	}

	@Test
	public void fullVoidIsChosenAsAWholeSet()
	{
		Map<String, SlotChoice> set = ranged("Slayer helmet (i)", "Void ranger helm", "Elite void top", "Elite void robe",
			"Void knight gloves", "Toxic blowpipe");
		assertEquals("Void ranger helm", set.get("head").getItemName());
		assertEquals("Elite void top", set.get("body").getItemName());
		assertEquals("Elite void robe", set.get("legs").getItemName());
		assertEquals("Void knight gloves", set.get("hands").getItemName());
		assertEquals("With full Elite Void.", set.get("body").getNote());
	}

	@Test
	public void bowOfFaerdhinenNeedsFullCrystal()
	{
		Map<String, SlotChoice> without = ranged("Slayer helmet (i)", "Bow of Faerdhinen", "Rune crossbow");
		assertEquals("Rune crossbow", without.get("weapon").getItemName());

		Map<String, SlotChoice> with = ranged("Slayer helmet (i)", "Bow of Faerdhinen", "Rune crossbow",
			"Crystal helm", "Crystal body", "Crystal legs");
		assertEquals("Bow of Faerdhinen", with.get("weapon").getItemName());
		assertEquals("Crystal helm", with.get("head").getItemName());
	}

	@Test
	public void crossbowOnlyItemsNeedACrossbow()
	{
		assertNull(ranged("Twisted bow", "Twisted buckler").get("shield").getItemName());
		assertEquals("Twisted buckler", ranged("Rune crossbow", "Twisted buckler").get("shield").getItemName());
	}

	@Test
	public void ammoFollowsTheChosenWeapon()
	{
		Map<String, SlotChoice> set = ranged("Toxic blowpipe", "Dragon arrow", "Rada's blessing 4");
		assertEquals("Rada's blessing 4", set.get("ammo").getItemName());
		assertEquals("Only if using a bow of Faerdhinen or toxic blowpipe.", set.get("ammo").getNote());
		assertEquals("Dragon arrow", ranged("Twisted bow", "Dragon arrow", "Rada's blessing 4").get("ammo").getItemName());
	}

	@Test
	public void upgradeHintSkipsIneligibleEntries()
	{
		// nothing owned for the weapon: the hint is the top entry, and the blowpipe (rank 2) is not the hint
		assertEquals("Twisted bow", ranged("Slayer helmet (i)").get("weapon").getNextUpgrade());
		// a bow user should not be told to upgrade to a crossbow-only shield
		assertNull(ranged("Twisted bow").get("shield").getNextUpgrade());
	}
}
