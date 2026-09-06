package com.slayerprep;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Recommended equipment per task and attack style, generated from the OSRS Wiki by tools/fetch_wiki_gear.py.
 */
public class GearData
{
	private static final String GEAR_RESOURCE = "gear.json";
	private static final String ALIASES_RESOURCE = "aliases.json";

	/** A wiki footnote attached to one or more table entries. */
	public static class Footnote
	{
		private String text;
		/** Item page names linked from the note text. */
		private List<String> items;

		public String getText()
		{
			return text == null ? "" : text;
		}

		public List<String> getItems()
		{
			return items == null ? Collections.emptyList() : items;
		}
	}

	/** One "Recommended equipment" table: an attack style with its slots and footnotes. */
	public static class StyleTable
	{
		/** slot name to ranked entries, each entry a list of equally good alternatives. */
		private Map<String, List<List<String>>> slots;
		/** item name to the names of the footnotes attached to it. */
		private Map<String, List<String>> notes;
		private Map<String, Footnote> footnotes;

		public Map<String, List<List<String>>> getSlots()
		{
			return slots == null ? Collections.emptyMap() : slots;
		}

		public List<String> notesFor(String item)
		{
			List<String> names = notes == null ? null : notes.get(item);
			return names == null ? Collections.emptyList() : names;
		}

		public Map<String, Footnote> getFootnotes()
		{
			return footnotes == null ? Collections.emptyMap() : footnotes;
		}
	}

	public static class GearTask
	{
		@Getter
		private String page;
		/** style name to its equipment table. */
		private Map<String, StyleTable> styles;
		/** Variant monsters that count for this task and have their own table, in display order. */
		private Map<String, GearTask> variants;

		public Map<String, StyleTable> getStyles()
		{
			return styles == null ? Collections.emptyMap() : styles;
		}

		public Map<String, GearTask> getVariants()
		{
			return variants == null ? Collections.emptyMap() : variants;
		}

		boolean hasStyles()
		{
			return !getStyles().isEmpty();
		}
	}

	private static class GearFile
	{
		private String generated;
		private String source;
		/** wiki item page name to item id, for icons. */
		private Map<String, Integer> items;
		private GearTask generic;
		private Map<String, GearTask> tasks;
		private List<String> missing;
	}

	public static final String GENERIC_LABEL = "Generic slayer gear";

	private final Map<String, GearTask> tasksByName;
	private final Map<String, Integer> itemIds;
	@Nullable
	private final GearTask generic;
	@Getter
	private final String generated;

	private GearData(GearFile file)
	{
		generated = file.generated;
		Map<String, Integer> ids = new HashMap<>();
		if (file.items != null)
		{
			file.items.forEach((name, id) ->
			{
				if (name != null && id != null)
				{
					ids.put(ItemMatcher.normalize(name), id);
				}
			});
		}
		itemIds = Collections.unmodifiableMap(ids);
		generic = file.generic != null && file.generic.hasStyles() ? file.generic : null;
		Map<String, GearTask> byName = new HashMap<>();
		if (file.tasks != null)
		{
			file.tasks.forEach((name, task) ->
			{
				if (task != null && (task.styles != null || task.variants != null))
				{
					byName.put(key(name), task);
				}
			});
		}
		tasksByName = Collections.unmodifiableMap(byName);
	}

	private static String key(String taskName)
	{
		return taskName.trim().toLowerCase(Locale.ROOT);
	}

	public static GearData parse(Gson gson, Reader reader)
	{
		GearFile file = gson.fromJson(reader, GearFile.class);
		if (file == null)
		{
			throw new JsonParseException("gear data is empty");
		}
		return new GearData(file);
	}

	public static GearData load(Gson gson) throws IOException
	{
		try (Reader reader = openResource(GEAR_RESOURCE))
		{
			return parse(gson, reader);
		}
	}

	public static Map<String, List<String>> parseAliases(Gson gson, Reader reader)
	{
		Map<String, List<String>> aliases = gson.fromJson(reader, new TypeToken<Map<String, List<String>>>()
		{
		}.getType());
		return aliases == null ? Collections.emptyMap() : aliases;
	}

	public static Map<String, List<String>> loadAliases(Gson gson) throws IOException
	{
		try (Reader reader = openResource(ALIASES_RESOURCE))
		{
			return parseAliases(gson, reader);
		}
	}

	private static Reader openResource(String name) throws IOException
	{
		InputStream in = GearData.class.getResourceAsStream(name);
		if (in == null)
		{
			throw new IOException("missing resource " + name);
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	/** Item id for a wiki item name, used for icons. */
	public Optional<Integer> itemId(String itemName)
	{
		return Optional.ofNullable(itemIds.get(ItemMatcher.normalize(itemName)));
	}

	/** The task's own gear table, if the wiki has one. */
	public Optional<GearTask> forTask(String taskName)
	{
		return Optional.ofNullable(tasksByName.get(key(taskName))).filter(GearTask::hasStyles);
	}

	/**
	 * Every gear table that applies to a task: the task's own table first, then variant monsters,
	 * then the generic slayer table. Empty only when the data file has none of these.
	 */
	public List<GearOption> optionsFor(String taskName)
	{
		List<GearOption> options = new ArrayList<>();
		GearTask task = tasksByName.get(key(taskName));
		if (task != null)
		{
			if (task.hasStyles())
			{
				options.add(new GearOption(taskName, GearOption.Kind.TASK, task));
			}
			task.getVariants().forEach((name, variant) ->
			{
				if (variant != null && variant.hasStyles())
				{
					options.add(new GearOption(name, GearOption.Kind.VARIANT, variant));
				}
			});
		}
		if (generic != null)
		{
			options.add(new GearOption(GENERIC_LABEL, GearOption.Kind.GENERIC, generic));
		}
		return options;
	}

	/** Every item name mentioned in any gear table, normalised. */
	public Set<String> allItemNames()
	{
		Set<String> names = new HashSet<>();
		for (GearTask task : tasksByName.values())
		{
			collectNames(task, names);
			task.getVariants().values().forEach(v -> collectNames(v, names));
		}
		if (generic != null)
		{
			collectNames(generic, names);
		}
		return names;
	}

	private static void collectNames(GearTask task, Set<String> names)
	{
		for (StyleTable style : task.getStyles().values())
		{
			for (List<List<String>> ranked : style.getSlots().values())
			{
				for (List<String> entry : ranked)
				{
					for (String name : entry)
					{
						names.add(ItemMatcher.normalize(name));
					}
				}
			}
		}
	}
}
