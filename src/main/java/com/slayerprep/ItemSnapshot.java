package com.slayerprep;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.Value;

/**
 * Immutable view of what the player owns: item name to quantity for inventory, worn equipment, bank and
 * (for group ironmen) the shared group storage.
 */
@Value
public class ItemSnapshot
{
	private static final ItemSnapshot EMPTY = new ItemSnapshot(
		Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

	Map<String, Integer> inventory;
	Map<String, Integer> worn;
	Map<String, Integer> bank;
	Map<String, Integer> groupBank;

	public static ItemSnapshot empty()
	{
		return EMPTY;
	}

	public ItemSnapshot withInventory(Map<String, Integer> items)
	{
		return new ItemSnapshot(Map.copyOf(items), worn, bank, groupBank);
	}

	public ItemSnapshot withWorn(Map<String, Integer> items)
	{
		return new ItemSnapshot(inventory, Map.copyOf(items), bank, groupBank);
	}

	public ItemSnapshot withBank(Map<String, Integer> items)
	{
		return new ItemSnapshot(inventory, worn, Map.copyOf(items), groupBank);
	}

	public ItemSnapshot withGroupBank(Map<String, Integer> items)
	{
		return new ItemSnapshot(inventory, worn, bank, Map.copyOf(items));
	}

	public Ownership find(ItemMatcher matcher)
	{
		if (findIn(inventory, matcher).isPresent() || findIn(worn, matcher).isPresent())
		{
			return Ownership.CARRIED;
		}
		if (findIn(bank, matcher).isPresent())
		{
			return Ownership.IN_BANK;
		}
		if (findIn(groupBank, matcher).isPresent())
		{
			return Ownership.IN_GROUP_BANK;
		}
		return Ownership.MISSING;
	}

	/** The name of the first owned item that matches, preferring carried items, then bank, then group storage. */
	public Optional<String> findName(ItemMatcher matcher)
	{
		Optional<String> name = findIn(worn, matcher);
		if (name.isEmpty())
		{
			name = findIn(inventory, matcher);
		}
		if (name.isEmpty())
		{
			name = findIn(bank, matcher);
		}
		if (name.isEmpty())
		{
			name = findIn(groupBank, matcher);
		}
		return name;
	}

	private static Optional<String> findIn(Map<String, Integer> items, ItemMatcher matcher)
	{
		return items.entrySet().stream()
			.filter(e -> e.getValue() > 0 && matcher.matches(e.getKey()))
			.map(Map.Entry::getKey)
			.findFirst();
	}
}
