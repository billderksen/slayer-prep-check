package com.slayerprep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ItemMatcherTest
{
	@Test
	public void normalizeStripsDoseWithoutSpace()
	{
		assertEquals("amulet of glory", ItemMatcher.normalize("Amulet of glory(4)"));
	}

	@Test
	public void normalizeStripsChargeStates()
	{
		assertEquals("serpentine helm", ItemMatcher.normalize("Serpentine helm (uncharged)"));
		assertEquals("toxic blowpipe", ItemMatcher.normalize("Toxic blowpipe (empty)"));
		assertEquals("trident of the seas", ItemMatcher.normalize("Trident of the seas (full)"));
		assertEquals("dragon dagger", ItemMatcher.normalize("Dragon dagger(p++)"));
		assertEquals("grimy ranarr weed", ItemMatcher.normalize("Grimy ranarr weed (unf)"));
		assertEquals("antidote++", ItemMatcher.normalize("Antidote++(4)"));
	}

	@Test
	public void normalizeKeepsVariantSuffixes()
	{
		assertEquals("slayer helmet (i)", ItemMatcher.normalize("Slayer helmet (i)"));
		assertEquals("masori body (f)", ItemMatcher.normalize("Masori body (f)"));
		assertEquals("slayer's staff (e)", ItemMatcher.normalize("Slayer's staff (e)"));
		assertEquals("rune platebody (g)", ItemMatcher.normalize("Rune platebody (g)"));
		assertEquals("ranging cape (t)", ItemMatcher.normalize("Ranging cape(t)"));
		assertEquals("dragon platebody (or)", ItemMatcher.normalize("Dragon platebody (or)"));
	}

	@Test
	public void exactMatchIsCaseInsensitiveAndWhole()
	{
		ItemMatcher m = ItemMatcher.exact("Nose peg");
		assertTrue(m.matches("nose PEG"));
		assertFalse(m.matches("Nose pegs"));
	}

	@Test
	public void prefixMatchesTrailingText()
	{
		assertTrue(ItemMatcher.prefix("Fungicide spray").matches("Fungicide spray 10"));
		assertTrue(ItemMatcher.prefix("Antifire potion").matches("Antifire potion(3)"));
		assertTrue(ItemMatcher.prefix("Bullseye lantern").matches("Bullseye lantern (lit)"));
		assertFalse(ItemMatcher.prefix("Fungicide spray").matches("Fungicide"));
	}

	@Test
	public void slayerHelmetMatchesEveryVariant()
	{
		ItemMatcher m = ItemMatcher.slayerHelmet();
		assertTrue(m.matches("Slayer helmet"));
		assertTrue(m.matches("Slayer helmet (i)"));
		assertTrue(m.matches("Black slayer helmet (i)"));
		assertTrue(m.matches("Twisted slayer helmet"));
		assertFalse(m.matches("Helm of neitiznot"));
	}

	@Test
	public void gearItemMatchesDegradedAndNumberedVariants()
	{
		assertTrue(ItemMatcher.item("Ahrim's robetop").matches("Ahrim's robetop 100"));
		assertTrue(ItemMatcher.item("Ardougne cloak").matches("Ardougne cloak 4"));
		assertTrue(ItemMatcher.item("Dragon dagger").matches("Dragon dagger(p++)"));
		assertTrue(ItemMatcher.item("Helm of Neitiznot").matches("Helm of neitiznot"));
		assertFalse(ItemMatcher.item("Rune platebody").matches("Rune platebody (g)"));
		assertFalse(ItemMatcher.item("Fire cape").matches("Infernal cape"));
	}

	@Test
	public void gearSlayerHelmetEntriesAcceptRecolours()
	{
		assertTrue(ItemMatcher.item("Slayer helmet (i)").matches("Black slayer helmet (i)"));
		assertFalse(ItemMatcher.item("Slayer helmet (i)").matches("Slayer helmet"));
		assertTrue(ItemMatcher.item("Slayer helmet").matches("Twisted slayer helmet"));
	}
}
