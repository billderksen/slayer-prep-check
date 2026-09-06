package com.slayerprep;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PrepResultTest
{
	private static final SlayerTask TASK = new SlayerTask("Gargoyles", 100, null);
	private static final Requirement HAMMER = Requirement.required(ItemMatcher.exact("Rock hammer"));

	private static PrepResult result(List<RequirementStatus> requirements, Map<String, Map<String, SlotChoice>> gear, String style)
	{
		return new PrepResult(TASK, requirements, gear, Collections.emptyList(), null, null, Collections.emptyList(),
			Collections.emptyMap(), style, true);
	}

	private static SlotChoice choice(String item, Ownership ownership)
	{
		return new SlotChoice(item, item, ownership, null, null);
	}

	@Test
	public void readyWhenRequiredItemsAndSelectedStyleGearAreCarried()
	{
		PrepResult r = result(
			List.of(new RequirementStatus(HAMMER, Ownership.CARRIED, "Rock hammer")),
			Map.of("Melee", Map.of("weapon", choice("Abyssal whip", Ownership.CARRIED), "ring", new SlotChoice(null, null, Ownership.MISSING, "Ultor ring", null))),
			"Melee");
		assertTrue(r.isReady());
	}

	@Test
	public void notReadyWhileARequiredItemIsInTheBank()
	{
		PrepResult r = result(
			List.of(new RequirementStatus(HAMMER, Ownership.IN_BANK, "Rock hammer")),
			Map.of("Melee", Map.of("weapon", choice("Abyssal whip", Ownership.CARRIED))),
			"Melee");
		assertFalse(r.isReady());
	}

	@Test
	public void notReadyWhileSelectedStyleGearIsInTheBank()
	{
		PrepResult r = result(
			List.of(new RequirementStatus(HAMMER, Ownership.CARRIED, "Rock hammer")),
			Map.of("Melee", Map.of("weapon", choice("Abyssal whip", Ownership.IN_BANK)),
				"Ranged", Map.of("weapon", choice("Toxic blowpipe", Ownership.CARRIED))),
			"Melee");
		assertFalse(r.isReady());
		assertTrue(result(List.of(), r.getGear(), "Ranged").isReady());
	}

	@Test
	public void recommendedItemsDoNotBlockReadiness()
	{
		Requirement optional = Requirement.recommended(ItemMatcher.exact("Antifire potion"));
		PrepResult r = result(List.of(new RequirementStatus(optional, Ownership.MISSING, null)), Collections.emptyMap(), null);
		assertTrue(r.isReady());
	}

	@Test
	public void gearStillInBankIsCounted()
	{
		PrepResult r = result(List.of(),
			Map.of("Melee", Map.of("weapon", choice("Abyssal whip", Ownership.IN_BANK), "head", choice("Slayer helmet (i)", Ownership.IN_BANK),
				"cape", choice("Fire cape", Ownership.CARRIED))),
			"Melee");
		assertTrue(r.gearInBank().containsAll(List.of("Abyssal whip", "Slayer helmet (i)")));
		assertFalse(r.gearInBank().contains("Fire cape"));
	}
}
