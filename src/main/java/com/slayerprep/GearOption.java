package com.slayerprep;

import lombok.Value;

/**
 * One selectable source of gear advice for a task: the task's own wiki table, a variant monster's table,
 * or the generic slayer table.
 */
@Value
public class GearOption
{
	public enum Kind
	{
		TASK,
		VARIANT,
		GENERIC
	}

	String label;
	Kind kind;
	GearData.GearTask task;
}
