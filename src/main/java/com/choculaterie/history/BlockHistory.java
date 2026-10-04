package com.choculaterie.history;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Prediction;

public final class BlockHistory {
	private static final int QUIET_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
	private static final int MAX_DEPTH = 16;
	private static final int HISTORY_HARD_CAP = 5000;

	private static final Deque<Action> undoStack = new ArrayDeque<>();
	private static final Deque<Action> redoStack = new ArrayDeque<>();
	private static final Map<Long, Action> actionsById = new HashMap<>();
	private static final Deque<Action> savedActions = new LinkedList<>();
	private static final Deque<Integer> savedDepths = new ArrayDeque<>();
	private static final Deque<Cause> causeStack = new ArrayDeque<>();

	private static Action activeAction;
	private static long nextActionId = 1;
	private static int depth = 0;
	private static boolean applying = false;
	private static Cause currentCause = Cause.COMMAND;

	private BlockHistory() {}

	public static boolean isTracking() {
		return activeAction != null && !applying;
	}

	public static long currentActionId() {
		return activeAction != null ? activeAction.id : 0;
	}

	public static void begin(Cause cause) {
		if (depth == 0 || depth > MAX_DEPTH) {
			resetDepth();
			causeStack.push(currentCause);
			currentCause = cause;
			activeAction = new Action();
			activeAction.id = nextActionId++;
			activeAction.cause = cause;
			actionsById.put(activeAction.id, activeAction);
			log("begin: new step");
		}
		depth++;
	}

	public static void pushAction(long id, Cause resumeCause) {
		savedActions.push(activeAction);
		savedDepths.push(depth);
		causeStack.push(currentCause);
		activeAction = id != 0 ? actionsById.get(id) : null;
		currentCause = resumeCause != null ? resumeCause : (activeAction != null ? activeAction.cause : currentCause);
		depth = activeAction != null ? 1 : 0;
	}

	public static void popAction() {
		if (activeAction != null) {
			depth--;
			if (depth == 0) {
				pushIfNotEmpty(activeAction);
			}
		}
		activeAction = savedActions.pop();
		depth = savedDepths.pop();
		currentCause = causeStack.pop();
	}

	private static void resetDepth() {
		if (depth > MAX_DEPTH) {
			com.choculaterie.CtrlZ.LOGGER.error("Ctrl-Z step nesting exceeded {}, resetting", MAX_DEPTH);
		}
		depth = 0;
	}

	public static void end() {
		if (depth == 0) {
			return;
		}
		depth--;
		if (depth == 0) {
			currentCause = causeStack.pop();
			pushIfNotEmpty(activeAction);
			activeAction = null;
		}
	}

	private static void pushIfNotEmpty(Action action) {
		if (action.isEmpty()) {
			return;
		}
		if (!action.inUndoStack) {
			undoStack.push(action);
			action.inUndoStack = true;
			log("step pushed (" + action.size() + " changes)");
		}
		redoStack.clear();
		trimHistory();
	}

	private static void trimHistory() {
		int cap = HistorySettings.isTimeBased() ? HISTORY_HARD_CAP : HistorySettings.getValue();
		while (undoStack.size() > cap) {
			Action discarded = undoStack.removeLast();
			actionsById.remove(discarded.id);
		}
	}

	private static void log(String message) {
		com.choculaterie.CtrlZ.LOGGER.info("Ctrl-Z: {}", message);
	}

	public static void clear() {
		undoStack.clear();
		redoStack.clear();
		actionsById.clear();
	}

	public static void markActor(ServerPlayer player) {
		if (isTracking() && activeAction.actorUuid == null) {
			activeAction.actorUuid = player.getUUID();
			activeAction.actorSurvival = !player.isCreative() && !player.isSpectator();
		}
	}

	public static void recordToolDurability(ServerPlayer player, Item item, int oldDamage, int newDamage, boolean broke) {
		if (!isTracking() || !UndoCategorySettings.isTrackInventory() || !CauseSettings.isEnabled(CauseSettings.Category.INVENTORY, currentCause)
			|| player.isSpectator()) {
			return;
		}
		activeAction.toolDurability.add(new ToolDurabilityChange(player.getUUID(), item, oldDamage, newDamage, broke));
	}

	public static void recordHeldItemChange(ServerPlayer player, ItemStack before, ItemStack after) {
		if (!isTracking() || !UndoCategorySettings.isTrackInventory() || !CauseSettings.isEnabled(CauseSettings.Category.INVENTORY, currentCause)
			|| player.isSpectator() || before.isEmpty()) {
			return;
		}
		Item consumedItem = before.getItem();
		boolean changed = after.isEmpty() || after.getCount() < before.getCount() || !after.is(consumedItem);
		if (!changed) {
			return;
		}
		Item remainderItem = !after.isEmpty() && !after.is(consumedItem) ? after.getItem() : null;
		activeAction.heldItems.add(new HeldItemChange(player.getUUID(), consumedItem, remainderItem));
	}

	public static int reserveBlock(ServerLevel level, BlockPos pos, BlockState oldState, CompoundTag oldNbt, BlockState newState) {
		if (!isTracking() || !UndoCategorySettings.isTrackBlocks() || !CauseSettings.isEnabled(CauseSettings.Category.BLOCKS, currentCause)) {
			return -1;
		}
		try {
			BlockPos key = pos.immutable();
			Integer existingSlot = activeAction.blockSlotByPos.get(key);
			if (existingSlot != null) {
				BlockChange original = activeAction.blocks.get(existingSlot);
				activeAction.blocks.set(existingSlot, new BlockChange(level.dimension(), key, original.oldState(), original.oldNbt(), newState, null));
				return existingSlot;
			}
			int slot = activeAction.blocks.size();
			activeAction.blocks.add(new BlockChange(level.dimension(), key, oldState, oldNbt, newState, null));
			activeAction.blockSlotByPos.put(key, slot);
			return slot;
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to reserve block change at {}", pos, t);
			return -1;
		}
	}

	public static void finalizeBlock(int slot, CompoundTag newNbt) {
		if (slot < 0 || activeAction == null || slot >= activeAction.blocks.size()) {
			return;
		}
		try {
			BlockChange pending = activeAction.blocks.get(slot);
			activeAction.blocks.set(slot, new BlockChange(pending.dimension(), pending.pos(), pending.oldState(), pending.oldNbt(), pending.newState(), newNbt));
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to finalize block change", t);
		}
	}

	public static void cancelBlock(int slot, BlockState revertState, CompoundTag revertNbt) {
		if (slot < 0 || activeAction == null || slot >= activeAction.blocks.size()) {
			return;
		}
		try {
			BlockChange pending = activeAction.blocks.get(slot);
			activeAction.blocks.set(slot, new BlockChange(pending.dimension(), pending.pos(), pending.oldState(), pending.oldNbt(), revertState, revertNbt));
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to cancel block change", t);
		}
	}

	public static void recordEntityAdded(ServerLevel level, Entity entity) {
		if (!isTracking()) {
			return;
		}
		try {
			CompoundTag nbt = snapshotEntity(entity);
			if (nbt != null) {
				if (UndoCategorySettings.isTrackEntities() && CauseSettings.isEnabled(CauseSettings.Category.ENTITIES, currentCause)) {
					activeAction.entities.add(new EntityChange(level.dimension(), entity.getUUID(), null, nbt));
				}
				((TaggedAction) entity).ctrlz$setActionId(activeAction.id);
			}
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to record entity add", t);
		}
	}

	public static void recordEntityRemoved(ServerLevel level, Entity entity) {
		if (!isTracking() || !UndoCategorySettings.isTrackEntities() || !CauseSettings.isEnabled(CauseSettings.Category.ENTITIES, currentCause)) {
			return;
		}
		try {
			CompoundTag nbt = snapshotEntity(entity);
			if (nbt != null) {
				activeAction.entities.add(new EntityChange(level.dimension(), entity.getUUID(), nbt, null));
			}
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to record entity removal", t);
		}
	}

	public static void recordEntityModified(ServerLevel level, UUID entityId, CompoundTag oldNbt, CompoundTag newNbt) {
		if (!isTracking() || !UndoCategorySettings.isTrackEntities() || !CauseSettings.isEnabled(CauseSettings.Category.ENTITIES, currentCause)
			|| oldNbt == null || newNbt == null || oldNbt.equals(newNbt)) {
			return;
		}
		try {
			activeAction.entities.add(new EntityChange(level.dimension(), entityId, oldNbt, newNbt));
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to record entity modification", t);
		}
	}

	public static CompoundTag snapshotEntity(Entity entity) {
		try {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
			if (id == null) {
				return null;
			}
			try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), com.choculaterie.CtrlZ.LOGGER)) {
				TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.level().registryAccess());
				output.putString("id", id.toString());
				entity.saveWithoutId(output);
				return output.buildResult();
			}
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to snapshot entity {}", entity, t);
			return null;
		}
	}

	public static CompoundTag snapshotBlockEntity(Level level, BlockPos pos) {
		try {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			return blockEntity != null ? blockEntity.saveWithFullMetadata(level.registryAccess()) : null;
		} catch (Throwable t) {
			com.choculaterie.CtrlZ.LOGGER.error("Failed to snapshot block entity at {}", pos, t);
			return null;
		}
	}

	public static void undo(ServerPlayer requester) {
		apply(requester, undoStack, redoStack, false);
	}

	public static void redo(ServerPlayer requester) {
		apply(requester, redoStack, undoStack, true);
	}

	private static void apply(ServerPlayer requester, Deque<Action> from, Deque<Action> to, boolean forward) {
		if (from.isEmpty()) {
			requester.sendSystemMessage(Component.literal(forward ? "Nothing to redo" : "Nothing to undo"), true);
			return;
		}

		List<Action> batch = new ArrayList<>();
		if (HistorySettings.isTimeBased() && !forward) {
			long cutoff = System.currentTimeMillis() - HistorySettings.getWindowMillis();
			while (!from.isEmpty() && from.peek().createdAt >= cutoff) {
				batch.add(from.pop());
			}
			if (batch.isEmpty()) {
				requester.sendSystemMessage(Component.literal("Nothing to undo"), true);
				return;
			}
		} else if (HistorySettings.isTimeBased()) {
			while (!from.isEmpty()) {
				batch.add(from.pop());
			}
		} else {
			batch.add(from.pop());
		}

		if (!forward) {
			for (Action action : batch) {
				actionsById.remove(action.id);
			}
		}

		applying = true;
		int totalChanges = 0;
		try {
			for (Action action : batch) {
				List<BlockChange> blocks = action.blocks;
				if (forward) {
					for (BlockChange change : blocks) {
						applyBlockChange(requester, action, change, true);
					}
				} else {
					for (int i = blocks.size() - 1; i >= 0; i--) {
						applyBlockChange(requester, action, blocks.get(i), false);
					}
				}

				List<EntityChange> entities = action.entities;
				if (forward) {
					for (EntityChange change : entities) {
						applyEntityChange(requester, action, change, true);
					}
				} else {
					for (int i = entities.size() - 1; i >= 0; i--) {
						applyEntityChange(requester, action, entities.get(i), false);
					}
				}

				for (ToolDurabilityChange change : action.toolDurability) {
					applyToolDurability(requester, change, forward);
				}
				for (HeldItemChange change : action.heldItems) {
					applyHeldItemChange(requester, change, forward);
				}
				totalChanges += action.size();
			}
		} finally {
			applying = false;
		}

		for (Action action : batch) {
			to.push(action);
		}
		requester.sendSystemMessage(Component.literal((forward ? "Redid " : "Undid ") + totalChanges + " change" + (totalChanges == 1 ? "" : "s")), true);
	}

	private static void applyBlockChange(ServerPlayer requester, Action action, BlockChange change, boolean forward) {
		MinecraftServer server = requester.level().getServer();
		ServerLevel level = server.getLevel(change.dimension());
		if (level == null) {
			return;
		}

		BlockState targetState = forward ? change.newState() : change.oldState();
		CompoundTag targetNbt = forward ? change.newNbt() : change.oldNbt();

		boolean wasPlacement = change.oldState().isAir() && !change.newState().isAir();
		if (wasPlacement && action.actorSurvival) {
			ServerPlayer placer = server.getPlayerList().getPlayer(action.actorUuid);
			if (placer != null) {
				Item item = change.newState().getBlock().asItem();
				if (item != Items.AIR) {
					if (forward) {
						consumeOneMatching(placer, item);
					} else {
						giveItem(placer, item);
					}
				}
			}
		}

		level.setBlock(change.pos(), targetState, QUIET_UPDATE_FLAGS);
		if (targetNbt != null) {
			BlockEntity restored = BlockEntity.loadStatic(change.pos(), targetState, targetNbt, level.registryAccess());
			if (restored != null) {
				level.setBlockEntity(restored);
			}
		}
	}

	private static void giveItem(ServerPlayer player, Item item) {
		giveItem(player, new ItemStack(item));
	}

	private static void giveItem(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static void consumeOneMatching(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && stack.is(item)) {
				stack.shrink(1);
				return;
			}
		}
	}

	private static void applyToolDurability(ServerPlayer requester, ToolDurabilityChange change, boolean forward) {
		MinecraftServer server = requester.level().getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(change.playerUuid());
		if (player == null) {
			return;
		}

		if (change.broke()) {
			if (forward) {
				consumeOneMatching(player, change.item());
			} else {
				ItemStack restored = new ItemStack(change.item());
				restored.setDamageValue(change.oldDamage());
				giveItem(player, restored);
			}
			return;
		}

		int targetDamage = forward ? change.newDamage() : change.oldDamage();
		ItemStack mainhand = player.getMainHandItem();
		if (mainhand.is(change.item()) && mainhand.isDamageableItem()) {
			mainhand.setDamageValue(targetDamage);
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && stack.is(change.item()) && stack.isDamageableItem()) {
				stack.setDamageValue(targetDamage);
				return;
			}
		}
	}

	private static void applyHeldItemChange(ServerPlayer requester, HeldItemChange change, boolean forward) {
		MinecraftServer server = requester.level().getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(change.playerUuid());
		if (player == null) {
			return;
		}

		if (forward) {
			consumeOneMatching(player, change.consumedItem());
			if (change.remainderItem() != null) {
				giveItem(player, change.remainderItem());
			}
		} else {
			if (change.remainderItem() != null) {
				consumeOneMatching(player, change.remainderItem());
			}
			giveItem(player, change.consumedItem());
		}
	}

	private static void applyEntityChange(ServerPlayer requester, Action action, EntityChange change, boolean forward) {
		MinecraftServer server = requester.level().getServer();
		ServerLevel level = server.getLevel(change.dimension());
		if (level == null) {
			return;
		}

		Entity existing = level.getEntity(change.entityId());
		CompoundTag target = forward ? change.newNbt() : change.oldNbt();

		if (existing instanceof ServerPlayer player) {
			if (target != null) {
				try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), com.choculaterie.CtrlZ.LOGGER)) {
					player.load(TagValueInput.create(reporter, level.registryAccess(), target));
					player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
					if (player.getHealth() <= 0.0F) {
						player.die(player.damageSources().genericKill());
					}
				} catch (Throwable t) {
					com.choculaterie.CtrlZ.LOGGER.error("Failed to restore player state", t);
				}
			}
			return;
		}

		if (existing != null) {
			existing.remove(Entity.RemovalReason.DISCARDED);
		} else if (target == null) {
			reclaimIfAlreadyGone(server, level, action, change);
		}
		if (target != null) {
			Entity entity = EntityType.loadEntityRecursive(
				target, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), EntityProcessor.NOP
			);
			if (entity != null) {
				if (entity instanceof PrimedTnt tnt && tnt.getFuse() <= 0) {
					tnt.setFuse(80);
				}
				level.addFreshEntity(entity);
			}
		}
	}

	private static void reclaimIfAlreadyGone(MinecraftServer server, ServerLevel level, Action action, EntityChange change) {
		if (!action.actorSurvival || action.actorUuid == null) {
			return;
		}
		CompoundTag original = change.oldNbt() != null ? change.oldNbt() : change.newNbt();
		if (original == null) {
			return;
		}
		ServerPlayer actor = server.getPlayerList().getPlayer(action.actorUuid);
		if (actor == null) {
			return;
		}
		Entity gone = EntityType.loadEntityRecursive(
			original, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), EntityProcessor.NOP
		);
		if (gone instanceof ItemEntity itemEntity) {
			removeMatching(actor, itemEntity.getItem());
		}
	}

	private static void removeMatching(ServerPlayer player, ItemStack wanted) {
		int remaining = wanted.getCount();
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, wanted)) {
				int take = Math.min(remaining, stack.getCount());
				stack.shrink(take);
				remaining -= take;
			}
		}
	}

	private static final class Action {
		final long createdAt = System.currentTimeMillis();
		final List<BlockChange> blocks = new ArrayList<>();
		final List<EntityChange> entities = new ArrayList<>();
		final List<ToolDurabilityChange> toolDurability = new ArrayList<>();
		final List<HeldItemChange> heldItems = new ArrayList<>();
		final Map<BlockPos, Integer> blockSlotByPos = new HashMap<>();
		long id;
		Cause cause;
		boolean inUndoStack;
		UUID actorUuid;
		boolean actorSurvival;

		boolean isEmpty() {
			return blocks.isEmpty() && entities.isEmpty() && toolDurability.isEmpty() && heldItems.isEmpty();
		}

		int size() {
			return blocks.size() + entities.size() + toolDurability.size() + heldItems.size();
		}
	}
}
