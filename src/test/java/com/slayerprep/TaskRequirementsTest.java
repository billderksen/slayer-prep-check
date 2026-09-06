package com.slayerprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class TaskRequirementsTest
{
	@Test
	public void aberrantSpectresNeedNosePegOrSlayerHelmet()
	{
		List<Requirement> reqs = TaskRequirements.forTask("Aberrant spectres", false);
		assertEquals(1, reqs.size());
		Requirement r = reqs.get(0);
		assertTrue(r.isRequired());
		assertTrue(r.matchesAny("Nose peg"));
		assertTrue(r.matchesAny("Green slayer helmet (i)"));
		assertFalse(r.matchesAny("Facemask"));
	}

	@Test
	public void lookupIsCaseInsensitive()
	{
		assertEquals(1, TaskRequirements.forTask("GARGOYLES", false).size());
	}

	@Test
	public void unknownTaskHasNoRequirements()
	{
		assertTrue(TaskRequirements.forTask("Cows", false).isEmpty());
	}

	@Test
	public void hydraBootsAreSkippedWithKourendElite()
	{
		assertEquals(1, TaskRequirements.forTask("Hydras", false).size());
		assertTrue(TaskRequirements.forTask("Hydras", true).isEmpty());
	}

	@Test
	public void caveHorrorsHaveTwoRequiredGroups()
	{
		List<Requirement> reqs = TaskRequirements.forTask("Cave horrors", false);
		assertEquals(2, reqs.size());
		assertTrue(reqs.get(1).matchesAny("Bullseye lantern (lit)"));
	}

	@Test
	public void dragonsHaveRecommendedGroupsOnly()
	{
		List<Requirement> reqs = TaskRequirements.forTask("Blue dragons", false);
		assertEquals(2, reqs.size());
		assertFalse(reqs.get(0).isRequired());
		assertTrue(reqs.get(1).matchesAny("Extended antifire(4)"));
	}

	@Test
	public void zygomitesHaveRequiredSprayAndRecommendedFungicide()
	{
		List<Requirement> reqs = TaskRequirements.forTask("Mutated zygomites", false);
		assertEquals(2, reqs.size());
		assertTrue(reqs.get(0).isRequired());
		assertTrue(reqs.get(0).matchesAny("Fungicide spray 7"));
		assertFalse(reqs.get(1).isRequired());
		assertTrue(reqs.get(1).matchesAny("Fungicide"));
	}
}
