package com.slayerprep;

import static com.slayerprep.ItemMatcher.exact;
import static com.slayerprep.ItemMatcher.prefix;
import static com.slayerprep.ItemMatcher.slayerHelmet;
import static com.slayerprep.Requirement.recommended;
import static com.slayerprep.Requirement.required;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Required and recommended items per Slayer task, keyed by the task name the game's DB table uses.
 */
public final class TaskRequirements
{
	private static final Map<String, List<Requirement>> BY_TASK = new HashMap<>();

	/** Boots that are only needed on Karuulm tasks without the Kourend &amp; Kebos Elite diary. */
	private static final Requirement KARUULM_BOOTS = required(exact("Boots of stone"), exact("Granite boots"), exact("Brimstone boots"));

	static
	{
		add(required(exact("Nose peg"), slayerHelmet()), "Aberrant spectres");
		add(required(exact("Earmuffs"), slayerHelmet()), "Banshees");
		add(required(exact("Facemask"), slayerHelmet()), "Dust devils", "Smoke devils", "The Thermonuclear Smoke Devil");
		add(required(exact("Spiny helmet"), slayerHelmet()), "Wall beasts");
		add(required(exact("Reinforced goggles"), slayerHelmet()), "Sourhogs");
		add(required(exact("Mirror shield"), exact("V's shield")), "Basilisks", "Cockatrice");
		add(required(exact("Ice cooler")), "Lizards");
		add(required(exact("Bag of salt")), "Rockslugs");
		add(required(exact("Rock hammer"), exact("Granite hammer"), exact("Rock thrownhammer")), "Gargoyles", "The Grotesque Guardians");
		add(required(prefix("Fungicide spray")), "Mutated zygomites");
		add(recommended(exact("Fungicide")), "Mutated zygomites");
		add(required(exact("Leaf-bladed spear"), exact("Leaf-bladed sword"), exact("Leaf-bladed battleaxe"),
			exact("Broad arrows"), exact("Broad bolts"), exact("Amethyst broad bolts"),
			exact("Slayer's staff"), exact("Slayer's staff (e)")), "Kurask", "Turoth");
		add(required(exact("Witchwood icon")), "Cave horrors");
		add(required(prefix("Bullseye lantern"), prefix("Kandarin headgear"), exact("Bruma torch"), prefix("Firemaking cape")), "Cave horrors");
		add(required(exact("Slayer gloves")), "Fever spiders");
		add(required(exact("Bug lantern"), exact("Lit bug lantern")), "Harpie bug swarms");
		add(required(exact("Insulated boots")), "Killerwatts");
		add(required(exact("Slayer bell")), "Molanisks");
		add(required(exact("Fishing explosive")), "Mogres");
		add(required(exact("Spade")), "Brine rats");
		add(KARUULM_BOOTS, "Hydras", "The Alchemical Hydra", "Drakes", "Wyrms");
		add(required(exact("Elemental shield"), exact("Mind shield"), exact("Dragonfire shield"), exact("Dragonfire ward"), exact("Ancient wyvern shield")),
			"Fossil island wyverns", "Skeletal wyverns");

		String[] dragons = {"Black dragons", "Blue dragons", "Green dragons", "Red dragons", "Metal dragons",
			"Lava Dragons", "Frost dragons", "The King Black Dragon", "Vorkath"};
		add(recommended(exact("Anti-dragon shield"), exact("Dragonfire shield"), exact("Dragonfire ward"), exact("Ancient wyvern shield")), dragons);
		add(recommended(prefix("Antifire potion"), prefix("Extended antifire"), prefix("Super antifire potion"), prefix("Extended super antifire")), dragons);

		add(recommended(prefix("Antidote+"), prefix("Antidote++"), prefix("Anti-venom"), prefix("Anti-venom+"), prefix("Sanfew serum")),
			"Cave slimes", "Lizardmen", "Araxytes");
		add(recommended(prefix("Shayzien")), "Lizardmen");
		add(recommended(exact("Wolfbane")), "Werewolves");
		add(recommended(exact("Ivandis flail"), exact("Blisterwood flail"), exact("Blisterwood sickle"), exact("Rod of ivandis")), "Vampyres");
	}

	private TaskRequirements()
	{
	}

	private static void add(Requirement requirement, String... tasks)
	{
		for (String task : tasks)
		{
			BY_TASK.computeIfAbsent(key(task), k -> new ArrayList<>()).add(requirement);
		}
	}

	private static String key(String task)
	{
		return task.trim().toLowerCase(Locale.ROOT);
	}

	public static List<Requirement> forTask(String taskName, boolean kourendElite)
	{
		List<Requirement> reqs = BY_TASK.getOrDefault(key(taskName), Collections.emptyList());
		if (!kourendElite)
		{
			return reqs;
		}
		return reqs.stream().filter(r -> r != KARUULM_BOOTS).collect(Collectors.toUnmodifiableList());
	}

	/** Every matcher of every task, used to decide which bank items are worth remembering. */
	public static List<ItemMatcher> allMatchers()
	{
		return BY_TASK.values().stream()
			.flatMap(List::stream)
			.flatMap(r -> r.getAlternatives().stream())
			.collect(Collectors.toUnmodifiableList());
	}
}
