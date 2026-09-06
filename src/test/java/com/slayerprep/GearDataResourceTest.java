package com.slayerprep;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GearDataResourceTest
{
	@Test
	public void bundledGearDataLoads() throws Exception
	{
		GearData data = GearData.load(new Gson());
		assertTrue(data.forTask("Gargoyles").isPresent());
		assertTrue(data.forTask("The Alchemical Hydra").isPresent());
		assertFalse(data.allItemNames().isEmpty());
	}

	@Test
	public void bundledAliasesResolveAgainstBundledGear() throws Exception
	{
		Gson gson = new Gson();
		Map<String, List<String>> aliases = GearData.loadAliases(gson);
		assertTrue(aliases.containsKey("God blessing"));
		GearAdvisor advisor = new GearAdvisor(aliases);
		ItemSnapshot s = ItemSnapshot.empty().withBank(Map.of("Unholy blessing", 1));
		Map<String, Map<String, SlotChoice>> sets = advisor.advise(GearData.load(gson).forTask("Gargoyles").get(), s);
		assertTrue(sets.get("Melee").get("ammo").getItemName().equals("Unholy blessing"));
	}
}
