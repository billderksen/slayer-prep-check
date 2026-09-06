package com.slayerprep;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * One group of items for a task; owning any one of the alternatives satisfies the group.
 */
@Value
public class Requirement
{
	List<ItemMatcher> alternatives;
	boolean required;

	public static Requirement required(ItemMatcher... alternatives)
	{
		return new Requirement(List.of(alternatives), true);
	}

	public static Requirement recommended(ItemMatcher... alternatives)
	{
		return new Requirement(List.of(alternatives), false);
	}

	public boolean matchesAny(String itemName)
	{
		return alternatives.stream().anyMatch(m -> m.matches(itemName));
	}

	public String getLabel()
	{
		return alternatives.stream().map(ItemMatcher::getName).collect(Collectors.joining(" / "));
	}
}
