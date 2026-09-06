package com.slayerprep;

import javax.annotation.Nullable;
import lombok.Value;

/**
 * The best owned option for one equipment slot, and the top-ranked entry the player does not own yet.
 */
@Value
public class SlotChoice
{
	/** Wiki entry name that was satisfied, or null if nothing is owned. */
	@Nullable
	String entryName;
	/** The actual owned item that satisfied the entry, or null if nothing is owned. */
	@Nullable
	String itemName;
	Ownership ownership;
	/** Highest-ranked entry the player does not own, or null if the top entry is owned. */
	@Nullable
	String nextUpgrade;
	/** Wiki footnote text for the chosen entry, or null. */
	@Nullable
	String note;
}
