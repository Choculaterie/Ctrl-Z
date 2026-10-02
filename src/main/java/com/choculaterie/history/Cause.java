package com.choculaterie.history;

public enum Cause {
	BREAK_BLOCK("Break Block"),
	USE_BLOCK("Place / Use Block"),
	USE_ITEM("Use Item"),
	ATTACK_ENTITY("Attack Entity"),
	ENTITY_HURT("Entity Hurt"),
	EXPLOSION("Explosion / TNT"),
	PISTON("Piston Movement"),
	REDSTONE_TICK("Redstone / Scheduled Tick"),
	DROP_ITEM("Drop Item"),
	COMMAND("Command");

	private final String displayName;

	Cause(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
