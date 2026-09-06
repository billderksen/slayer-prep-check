package com.slayerprep;

import static org.junit.Assert.assertEquals;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class ItemSnapshotTest
{
	private static final ItemMatcher NOSE_PEG = ItemMatcher.exact("Nose peg");

	@Test
	public void inventoryItemIsCarried()
	{
		ItemSnapshot s = ItemSnapshot.empty().withInventory(Map.of("Nose peg", 1));
		assertEquals(Ownership.CARRIED, s.find(NOSE_PEG));
	}

	@Test
	public void wornItemIsCarried()
	{
		ItemSnapshot s = ItemSnapshot.empty().withWorn(Map.of("Nose peg", 1));
		assertEquals(Ownership.CARRIED, s.find(NOSE_PEG));
	}

	@Test
	public void bankItemIsInBank()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Nose peg", 3));
		assertEquals(Ownership.IN_BANK, s.find(NOSE_PEG));
	}

	@Test
	public void carriedBeatsBank()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Nose peg", 3)).withInventory(Map.of("Nose peg", 1));
		assertEquals(Ownership.CARRIED, s.find(NOSE_PEG));
	}

	@Test
	public void groupStorageItemIsInGroupBank()
	{
		ItemSnapshot s = ItemSnapshot.empty().withGroupBank(Map.of("Nose peg", 1));
		assertEquals(Ownership.IN_GROUP_BANK, s.find(NOSE_PEG));
		assertEquals(Ownership.IN_BANK, s.withBank(Map.of("Nose peg", 1)).find(NOSE_PEG));
	}

	@Test
	public void absentItemIsMissing()
	{
		assertEquals(Ownership.MISSING, ItemSnapshot.empty().find(NOSE_PEG));
	}

	@Test
	public void findReturnsMatchingItemName()
	{
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Black slayer helmet (i)", 1));
		assertEquals("Black slayer helmet (i)", s.findName(ItemMatcher.slayerHelmet()).orElse(null));
		assertEquals(Collections.emptyMap(), s.getInventory());
	}
}
