package com.slayerprep;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Case-insensitive item name matcher. Names are normalised before comparison so that
 * charge/dose states such as "(4)", "(uncharged)" or "(p++)" are ignored while real
 * variants such as "(i)", "(f)", "(or)", "(e)", "(t)" and "(g)" are kept.
 */
@Value
public class ItemMatcher
{
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern PAREN = Pattern.compile("\\s*\\(");
	private static final Pattern STATE_SUFFIX = Pattern.compile(
		"\\s*\\((?:\\d+|uncharged|empty|full|p|p\\+|p\\+\\+|unf|inactive)\\)$");
	private static final Pattern DEGRADE_SUFFIX = Pattern.compile(" \\d+$");
	private static final String SLAYER_HELMET = "slayer helmet";
	private static final String SLAYER_HELMET_IMBUED = "slayer helmet (i)";

	public enum Mode
	{
		EXACT,
		PREFIX,
		CONTAINS,
		/** Exact, but also accepts a trailing degrade/tier number ("Ahrim's robetop 100", "Ardougne cloak 4"). */
		ITEM
	}

	String name;
	String pattern;
	Mode mode;

	public static ItemMatcher exact(String name)
	{
		return new ItemMatcher(name, normalize(name), Mode.EXACT);
	}

	public static ItemMatcher prefix(String name)
	{
		return new ItemMatcher(name, normalize(name), Mode.PREFIX);
	}

	public static ItemMatcher contains(String name)
	{
		return new ItemMatcher(name, normalize(name), Mode.CONTAINS);
	}

	/** Any Slayer helmet variant: plain, imbued, or any recolour of either. */
	public static ItemMatcher slayerHelmet()
	{
		return new ItemMatcher("Slayer helmet", SLAYER_HELMET, Mode.CONTAINS);
	}

	/** Matcher for a wiki gear entry, which names the item page rather than a specific in-game variant. */
	public static ItemMatcher item(String name)
	{
		String normalized = normalize(name);
		if (normalized.endsWith(SLAYER_HELMET_IMBUED))
		{
			return new ItemMatcher(name, SLAYER_HELMET_IMBUED, Mode.CONTAINS);
		}
		if (normalized.endsWith(SLAYER_HELMET))
		{
			return new ItemMatcher(name, SLAYER_HELMET, Mode.CONTAINS);
		}
		return new ItemMatcher(name, normalized, Mode.ITEM);
	}

	public static String normalize(String raw)
	{
		String s = WHITESPACE.matcher(raw.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
		s = PAREN.matcher(s).replaceAll(" (");
		Matcher m = STATE_SUFFIX.matcher(s);
		while (m.find())
		{
			s = s.substring(0, m.start()).trim();
			m = STATE_SUFFIX.matcher(s);
		}
		return s.trim();
	}

	public boolean matches(String itemName)
	{
		String candidate = normalize(itemName);
		switch (mode)
		{
			case EXACT:
				return candidate.equals(pattern);
			case PREFIX:
				return candidate.startsWith(pattern);
			case CONTAINS:
				return candidate.contains(pattern);
			case ITEM:
				return candidate.equals(pattern)
					|| (candidate.startsWith(pattern + " ") && DEGRADE_SUFFIX.matcher(candidate.substring(pattern.length())).matches());
			default:
				return false;
		}
	}
}
