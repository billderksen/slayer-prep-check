package com.slayerprep;

import javax.annotation.Nullable;
import lombok.Value;

@Value
public class RequirementStatus
{
	Requirement requirement;
	Ownership ownership;
	/** The owned item that satisfies the requirement, or null when missing. */
	@Nullable
	String itemName;

	public boolean isRequired()
	{
		return requirement.isRequired();
	}

	public String getLabel()
	{
		return itemName != null ? itemName : requirement.getLabel();
	}
}
