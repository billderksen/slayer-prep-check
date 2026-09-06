package com.slayerprep;

import javax.annotation.Nullable;
import lombok.Value;

@Value
public class SlayerTask
{
	String name;
	int amount;
	@Nullable
	String location;
}
