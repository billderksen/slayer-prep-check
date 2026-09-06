package com.slayerprep;

import com.slayerprep.GearData.Footnote;
import com.slayerprep.GearData.StyleTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Picks, per attack style and slot, the best wiki-ranked entry the player owns, honouring the wiki's footnotes:
 * <ul>
 * <li>a footnote shared by entries in two or more armour/weapon slots that speaks of a "full" set (Void, crystal +
 * bow of Faerdhinen, ...) marks a <em>set</em> that is used as a whole or not at all; the set is used when it scores
 * better than the loose picks;</li>
 * <li>a footnote that links other items of the table ("If bringing a twisted bow") makes its entries depend on one
 * of those items being chosen; a note mentioning a crossbow depends on a crossbow being chosen;</li>
 * <li>any other footnote shared across slots is treated as a set too (Inquisitor's "with a crush weapon");
 * the rest is informational and shown with the choice.</li>
 * </ul>
 */
@Slf4j
public class GearAdvisor
{
	static final List<String> SLOT_ORDER = List.of(
		"head", "neck", "cape", "body", "legs", "weapon", "2h", "shield", "ammo", "hands", "feet", "ring", "special");
	/** Slots whose shared footnotes describe a set; "special" is a spec weapon, not part of an outfit. */
	private static final Set<String> SET_SLOTS = Set.of(
		"head", "neck", "cape", "body", "legs", "weapon", "2h", "shield", "ammo", "hands", "feet", "ring");
	private static final String CROSSBOW = "crossbow";
	/** Wiki wording for a set that must be worn as a whole: "With full Elite Void", "full crystal armour". */
	private static final String FULL_SET = "full";

	private final Map<String, List<String>> aliases;

	public GearAdvisor(Map<String, List<String>> aliases)
	{
		Map<String, List<String>> normalized = new HashMap<>();
		aliases.forEach((name, items) -> normalized.put(ItemMatcher.normalize(name), List.copyOf(items)));
		this.aliases = normalized;
	}

	public Map<String, Map<String, SlotChoice>> advise(GearData.GearTask task, ItemSnapshot snapshot)
	{
		Map<String, Map<String, SlotChoice>> result = new LinkedHashMap<>();
		task.getStyles().forEach((style, table) -> result.put(style, adviseStyle(table, snapshot)));
		return result;
	}

	private Map<String, SlotChoice> adviseStyle(StyleTable table, ItemSnapshot snapshot)
	{
		Table t = new Table(table);
		Assignment best = assign(t, snapshot, null);
		for (String set : t.sets.keySet())
		{
			if (!setCompletable(t, snapshot, set))
			{
				continue;
			}
			Assignment withSet = assign(t, snapshot, set);
			if (withSet.score < best.score)
			{
				best = withSet;
			}
		}
		return best.choices;
	}

	/** All slots of a set have an owned member. */
	private boolean setCompletable(Table t, ItemSnapshot snapshot, String set)
	{
		for (String slot : t.sets.get(set))
		{
			boolean owned = false;
			for (List<String> entry : t.table.getSlots().get(slot))
			{
				for (String alternative : entry)
				{
					if (t.table.notesFor(alternative).contains(set) && owned(slot, alternative, snapshot).isPresent())
					{
						owned = true;
					}
				}
			}
			if (!owned)
			{
				return false;
			}
		}
		return true;
	}

	private Assignment assign(Table t, ItemSnapshot snapshot, @Nullable String activeSet)
	{
		Assignment assignment = new Assignment();
		for (String slot : SLOT_ORDER)
		{
			List<List<String>> ranked = t.table.getSlots().get(slot);
			if (ranked == null || ranked.isEmpty())
			{
				continue;
			}
			SlotChoice choice = adviseSlot(t, slot, ranked, snapshot, activeSet, assignment);
			assignment.choices.put(slot, choice);
			assignment.score += choice.getItemName() == null ? ranked.size() + 1 : assignment.rankOfLast;
		}
		return assignment;
	}

	private SlotChoice adviseSlot(Table t, String slot, List<List<String>> ranked, ItemSnapshot snapshot,
		@Nullable String activeSet, Assignment assignment)
	{
		String topEntry = null;
		for (int rank = 0; rank < ranked.size(); rank++)
		{
			List<String> labels = new ArrayList<>();
			for (String alternative : ranked.get(rank))
			{
				if (!eligible(t, slot, alternative, activeSet, assignment))
				{
					continue;
				}
				if (!resolve(slot, alternative).isEmpty())
				{
					labels.add(alternative);
				}
				Optional<Owned> owned = owned(slot, alternative, snapshot);
				if (owned.isPresent())
				{
					assignment.rankOfLast = rank;
					assignment.chosenNames.add(ItemMatcher.normalize(alternative));
					return new SlotChoice(alternative, owned.get().name, owned.get().ownership, topEntry, noteFor(t, alternative));
				}
			}
			if (topEntry == null && !labels.isEmpty())
			{
				topEntry = String.join(" / ", labels);
			}
		}
		return new SlotChoice(null, null, Ownership.MISSING, topEntry, null);
	}

	private boolean eligible(Table t, String slot, String alternative, @Nullable String activeSet, Assignment assignment)
	{
		List<String> notes = t.table.notesFor(alternative);
		if (activeSet != null && t.sets.get(activeSet).contains(slot) && !notes.contains(activeSet))
		{
			return false; // a set slot only takes members of the active set
		}
		for (String note : notes)
		{
			if (t.sets.containsKey(note))
			{
				if (!note.equals(activeSet))
				{
					return false; // member of a set that is not in use
				}
				continue;
			}
			Set<String> dependencies = t.dependencies.getOrDefault(note, Set.of());
			if (!dependencies.isEmpty() && dependencies.stream().noneMatch(assignment.chosenNames::contains))
			{
				return false;
			}
			if (t.crossbowNotes.contains(note) && assignment.chosenNames.stream().noneMatch(n -> n.contains(CROSSBOW)))
			{
				return false;
			}
		}
		return true;
	}

	@Nullable
	private static String noteFor(Table t, String alternative)
	{
		List<String> texts = new ArrayList<>();
		for (String note : t.table.notesFor(alternative))
		{
			Footnote footnote = t.table.getFootnotes().get(note);
			if (footnote != null && !footnote.getText().isEmpty())
			{
				texts.add(footnote.getText());
			}
		}
		return texts.isEmpty() ? null : String.join(" ", texts);
	}

	private Optional<Owned> owned(String slot, String alternative, ItemSnapshot snapshot)
	{
		for (String candidate : resolve(slot, alternative))
		{
			ItemMatcher matcher = ItemMatcher.item(candidate);
			Optional<String> name = snapshot.findName(matcher);
			if (name.isPresent())
			{
				return Optional.of(new Owned(name.get(), snapshot.find(matcher)));
			}
		}
		return Optional.empty();
	}

	/** Expand a wiki entry name to in-game item names; unknown group names resolve to nothing. */
	private List<String> resolve(String slot, String name)
	{
		String key = ItemMatcher.normalize(name);
		List<String> slotSpecific = aliases.get(key + "#" + slot);
		if (slotSpecific != null)
		{
			return slotSpecific;
		}
		List<String> generic = aliases.get(key);
		if (generic != null)
		{
			return generic;
		}
		if (key.contains("#"))
		{
			log.debug("Skipping unknown wiki group name {}", name);
			return List.of();
		}
		return List.of(name);
	}

	/** Footnote analysis of one table: which notes are sets, and what the other notes depend on. */
	private static class Table
	{
		private final StyleTable table;
		/** set note name to the slots it spans. */
		private final Map<String, Set<String>> sets = new LinkedHashMap<>();
		/** dependency note name to normalised names of table items it requires (any one of them). */
		private final Map<String, Set<String>> dependencies = new HashMap<>();
		private final Set<String> crossbowNotes = new HashSet<>();

		Table(StyleTable table)
		{
			this.table = table;
			Map<String, String> slotOfItem = new HashMap<>();
			Map<String, Set<String>> slotsOfNote = new LinkedHashMap<>();
			table.getSlots().forEach((slot, ranked) -> ranked.forEach(entry -> entry.forEach(alternative ->
			{
				slotOfItem.putIfAbsent(ItemMatcher.normalize(alternative), slot);
				for (String note : table.notesFor(alternative))
				{
					slotsOfNote.computeIfAbsent(note, k -> new HashSet<>()).add(slot);
				}
			})));
			slotsOfNote.forEach((note, slots) ->
			{
				Footnote footnote = table.getFootnotes().get(note);
				String text = footnote == null ? "" : footnote.getText().toLowerCase(Locale.ROOT);
				Set<String> setSlots = slots.stream().filter(SET_SLOTS::contains).collect(Collectors.toSet());
				if (setSlots.size() >= 2 && text.contains(FULL_SET))
				{
					sets.put(note, setSlots);
					return;
				}
				Set<String> required = new HashSet<>();
				if (footnote != null)
				{
					for (String item : footnote.getItems())
					{
						String key = ItemMatcher.normalize(item);
						String itemSlot = slotOfItem.get(key);
						if (itemSlot != null && !slots.contains(itemSlot))
						{
							required.add(key);
						}
					}
				}
				if (!required.isEmpty())
				{
					dependencies.put(note, required);
				}
				else if (text.contains(CROSSBOW))
				{
					crossbowNotes.add(note);
				}
				else if (setSlots.size() >= 2)
				{
					sets.put(note, setSlots);
				}
			});
		}
	}

	private static class Assignment
	{
		private final Map<String, SlotChoice> choices = new LinkedHashMap<>();
		private final Set<String> chosenNames = new HashSet<>();
		private int score;
		private int rankOfLast;
	}

	private static class Owned
	{
		private final String name;
		private final Ownership ownership;

		Owned(String name, Ownership ownership)
		{
			this.name = name;
			this.ownership = ownership;
		}
	}

	/**
	 * Order style names by a comma-separated preference list ("Melee, Ranged, Magic"); a style matches a
	 * preference when it contains it, with an exact match ranked first within its group. Unmatched styles
	 * go last. Remaining ties are broken alphabetically.
	 */
	public static List<String> orderStyles(Collection<String> styles, String preference)
	{
		List<String> prefs = Arrays.stream(preference.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.toList());
		Comparator<String> byPreference = Comparator.comparingInt(style ->
		{
			String lower = style.toLowerCase(Locale.ROOT);
			for (int i = 0; i < prefs.size(); i++)
			{
				if (lower.contains(prefs.get(i)))
				{
					return i;
				}
			}
			return prefs.size();
		});
		Comparator<String> exactFirst = Comparator.comparingInt(style -> prefs.contains(style.toLowerCase(Locale.ROOT)) ? 0 : 1);
		return styles.stream()
			.sorted(byPreference.thenComparing(exactFirst).thenComparing(String.CASE_INSENSITIVE_ORDER))
			.collect(Collectors.toList());
	}
}
