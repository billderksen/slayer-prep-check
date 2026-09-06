package com.slayerprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class GearAdvisorTest
{
	private GearData gearData;
	private GearAdvisor advisor;

	@Before
	public void setUp() throws Exception
	{
		Gson gson = new Gson();
		try (Reader gear = open("test-gear.json"); Reader aliases = open("test-aliases.json"))
		{
			gearData = GearData.parse(gson, gear);
			advisor = new GearAdvisor(GearData.parseAliases(gson, aliases));
		}
	}

	private static Reader open(String name)
	{
		return new InputStreamReader(GearAdvisorTest.class.getResourceAsStream(name), StandardCharsets.UTF_8);
	}

	@Test
	public void malformedTaskEntryIsSkippedNotFatal()
	{
		assertTrue(gearData.forTask("Broken").isEmpty());
		assertTrue(gearData.allItemNames().contains("fire cape"));
	}

	@Test
	public void gearDataLookupIsCaseInsensitive()
	{
		assertTrue(gearData.forTask("GARGOYLES").isPresent());
		assertTrue(gearData.forTask("Cows").isEmpty());
	}

	@Test
	public void picksHighestRankedOwnedEntry()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Abyssal whip", 1, "Fire cape", 1));
		Map<String, Map<String, SlotChoice>> sets = advisor.advise(gearData.forTask("Gargoyles").get(), s);

		SlotChoice weapon = sets.get("Melee").get("weapon");
		assertEquals("Abyssal whip", weapon.getItemName());
		assertEquals(Ownership.IN_BANK, weapon.getOwnership());
		assertEquals("Scythe of Vitur", weapon.getNextUpgrade());
	}

	@Test
	public void ownedTopEntryHasNoUpgrade()
	{
		ItemSnapshot s = ItemSnapshot.empty().withWorn(Map.of("Black slayer helmet (i)", 1));
		SlotChoice head = advisor.advise(gearData.forTask("Gargoyles").get(), s).get("Melee").get("head");
		assertEquals("Slayer helmet (i)", head.getEntryName());
		assertEquals("Black slayer helmet (i)", head.getItemName());
		assertEquals(Ownership.CARRIED, head.getOwnership());
		assertNull(head.getNextUpgrade());
	}

	@Test
	public void nothingOwnedIsMissingWithTopEntryAsUpgrade()
	{
		SlotChoice ring = advisor.advise(gearData.forTask("Gargoyles").get(), ItemSnapshot.empty()).get("Melee").get("ring");
		assertNull(ring.getItemName());
		assertEquals(Ownership.MISSING, ring.getOwnership());
		assertEquals("Berserker ring (i)", ring.getNextUpgrade());
	}

	@Test
	public void aliasResolvesGroupNameToOwnedItem()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Zamorak cape", 1));
		SlotChoice cape = advisor.advise(gearData.forTask("Gargoyles").get(), s).get("Melee").get("cape");
		assertEquals("God capes", cape.getEntryName());
		assertEquals("Zamorak cape", cape.getItemName());
		assertEquals("Infernal cape", cape.getNextUpgrade());
	}

	@Test
	public void unknownGroupNamesAreSkipped()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Fire cape", 1));
		SlotChoice cape = advisor.advise(gearData.forTask("Gargoyles").get(), s).get("Melee").get("cape");
		assertEquals("Fire cape", cape.getItemName());
		assertEquals("Infernal cape", cape.getNextUpgrade());
	}

	@Test
	public void slotsComeOutInEquipmentOrder()
	{
		Map<String, SlotChoice> melee = advisor.advise(gearData.forTask("Gargoyles").get(), ItemSnapshot.empty()).get("Melee");
		assertEquals(Arrays.asList("head", "cape", "weapon", "ring"), List.copyOf(melee.keySet()));
	}

	@Test
	public void stylesAreOrderedByPreferenceThenAlphabetically()
	{
		List<String> ordered = GearAdvisor.orderStyles(
			Arrays.asList("Magic", "Ranged", "Melee (Low level)", "High level melee", "Melee", "Phase 1 Magic", "Pure"), "Melee, Ranged, Magic");
		assertEquals(Arrays.asList("Melee", "High level melee", "Melee (Low level)", "Ranged", "Magic", "Phase 1 Magic", "Pure"), ordered);
	}
}
