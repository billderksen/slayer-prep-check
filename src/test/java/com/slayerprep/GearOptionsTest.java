package com.slayerprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class GearOptionsTest
{
	private GearData gearData;

	@Before
	public void setUp() throws Exception
	{
		try (Reader gear = new InputStreamReader(GearOptionsTest.class.getResourceAsStream("test-gear.json"), StandardCharsets.UTF_8))
		{
			gearData = GearData.parse(new Gson(), gear);
		}
	}

	private static List<String> labels(List<GearOption> options)
	{
		return options.stream().map(GearOption::getLabel).collect(Collectors.toList());
	}

	@Test
	public void taskWithOwnTableComesFirstThenGeneric()
	{
		List<GearOption> options = gearData.optionsFor("Gargoyles");
		assertEquals(List.of("Gargoyles", "Generic slayer gear"), labels(options));
		assertEquals(GearOption.Kind.TASK, options.get(0).getKind());
		assertEquals(GearOption.Kind.GENERIC, options.get(1).getKind());
	}

	@Test
	public void taskWithoutOwnTableOffersVariantsThenGeneric()
	{
		List<GearOption> options = gearData.optionsFor("Greater demons");
		assertEquals(List.of("Tormented demon", "Generic slayer gear"), labels(options));
		assertEquals(GearOption.Kind.VARIANT, options.get(0).getKind());
		assertTrue(options.get(0).getTask().getStyles().containsKey("Melee"));
	}

	@Test
	public void unknownTaskOnlyGetsGeneric()
	{
		assertEquals(List.of("Generic slayer gear"), labels(gearData.optionsFor("Cows")));
	}

	@Test
	public void itemIdsAreLookedUpByNormalisedName()
	{
		assertEquals(Integer.valueOf(4151), gearData.itemId("abyssal WHIP").orElse(null));
		assertEquals(Integer.valueOf(11865), gearData.itemId("Slayer helmet (i)").orElse(null));
		assertTrue(gearData.itemId("Fire cape").isEmpty());
	}

	@Test
	public void taskWithoutOwnTableIsNotATaskOption()
	{
		assertTrue(gearData.forTask("Greater demons").isEmpty());
	}
}
