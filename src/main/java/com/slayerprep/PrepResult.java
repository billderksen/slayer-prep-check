package com.slayerprep;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Everything the panel, overlay and bank tab show for the current task.
 */
@Value
public class PrepResult
{
	public static final PrepResult NO_TASK = new PrepResult(null, Collections.emptyList(), Collections.emptyMap(),
		Collections.emptyList(), null, null, Collections.emptyList(), Collections.emptyMap(), null, false);

	@Nullable
	SlayerTask task;
	List<RequirementStatus> requirements;
	/** style name to slot name to choice, in display order. */
	Map<String, Map<String, SlotChoice>> gear;
	/** Labels of the selectable gear sources for this task, in display order. */
	List<String> gearOptions;
	@Nullable
	String selectedGearOption;
	/** Explains where the gear advice comes from when it is not the task's own table. */
	@Nullable
	String gearNote;
	List<String> notes;
	/** Normalised item name to item id, for every item the panel shows that has a known id. */
	Map<String, Integer> icons;
	/** The style the player looks at in the panel; the readiness check and the bank tab option use it. */
	@Nullable
	String selectedStyle;
	boolean bankKnown;

	@Nullable
	public Integer iconFor(@Nullable String itemName)
	{
		return itemName == null ? null : icons.get(ItemMatcher.normalize(itemName));
	}

	/** Required items the player is not carrying right now. */
	public List<RequirementStatus> requiredNotCarried()
	{
		return requirements.stream()
			.filter(r -> r.isRequired() && r.getOwnership() != Ownership.CARRIED)
			.collect(Collectors.toList());
	}

	/** Labels of required items the player does not own at all. */
	public List<String> requiredMissing()
	{
		return requirements.stream()
			.filter(r -> r.isRequired() && r.getOwnership() == Ownership.MISSING)
			.map(RequirementStatus::getLabel)
			.collect(Collectors.toList());
	}

	/** Chosen items of the selected style that are not carried yet. */
	public List<String> gearInBank()
	{
		Map<String, SlotChoice> slots = selectedStyle == null ? null : gear.get(selectedStyle);
		if (slots == null)
		{
			return Collections.emptyList();
		}
		return slots.values().stream()
			.filter(c -> c.getItemName() != null && c.getOwnership() != Ownership.CARRIED)
			.map(SlotChoice::getItemName)
			.collect(Collectors.toList());
	}

	/** True when every required item and every chosen item of the selected style is carried. */
	public boolean isReady()
	{
		return requiredNotCarried().isEmpty() && gearInBank().isEmpty();
	}
}
