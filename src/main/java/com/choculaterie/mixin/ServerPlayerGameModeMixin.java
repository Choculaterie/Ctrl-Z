package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

	@Shadow
	protected ServerPlayer player;

	@Unique
	private Item ctrlz$tool;

	@Unique
	private int ctrlz$toolDamageBefore;

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void ctrlz$beginDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		BlockHistory.begin(Cause.BREAK_BLOCK);
		BlockHistory.markActor(this.player);

		ItemStack tool = this.player.getMainHandItem();
		if (tool.isDamageableItem()) {
			ctrlz$tool = tool.getItem();
			ctrlz$toolDamageBefore = tool.getDamageValue();
		} else {
			ctrlz$tool = null;
		}
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void ctrlz$endDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (ctrlz$tool != null) {
			ItemStack after = this.player.getMainHandItem();
			if (after.is(ctrlz$tool) && after.isDamageableItem()) {
				if (after.getDamageValue() != ctrlz$toolDamageBefore) {
					BlockHistory.recordToolDurability(this.player, ctrlz$tool, ctrlz$toolDamageBefore, after.getDamageValue(), false);
				}
			} else {
				BlockHistory.recordToolDurability(this.player, ctrlz$tool, ctrlz$toolDamageBefore, ctrlz$toolDamageBefore, true);
			}
			ctrlz$tool = null;
		}
		BlockHistory.end();
	}

	@Unique
	private ItemStack ctrlz$heldBefore;

	@Unique
	private InteractionHand ctrlz$heldHand;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void ctrlz$beginUseItemOn(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		BlockHistory.begin(Cause.USE_BLOCK);
		BlockHistory.markActor(player);

		if (!(itemStack.getItem() instanceof BlockItem)) {
			ctrlz$heldHand = hand;
			ctrlz$heldBefore = itemStack.copy();
		} else {
			ctrlz$heldBefore = null;
		}
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void ctrlz$endUseItemOn(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		if (ctrlz$heldBefore != null) {
			BlockHistory.recordHeldItemChange(player, ctrlz$heldBefore, player.getItemInHand(ctrlz$heldHand));
			ctrlz$heldBefore = null;
		}
		BlockHistory.end();
	}

	@Unique
	private ItemStack ctrlz$useItemHeldBefore;

	@Unique
	private InteractionHand ctrlz$useItemHand;

	@Inject(method = "useItem", at = @At("HEAD"))
	private void ctrlz$beginUseItem(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		BlockHistory.begin(Cause.USE_ITEM);
		BlockHistory.markActor(player);
		ctrlz$useItemHand = hand;
		ctrlz$useItemHeldBefore = itemStack.copy();
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void ctrlz$endUseItem(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (ctrlz$useItemHeldBefore != null) {
			BlockHistory.recordHeldItemChange(player, ctrlz$useItemHeldBefore, player.getItemInHand(ctrlz$useItemHand));
			ctrlz$useItemHeldBefore = null;
		}
		BlockHistory.end();
	}
}
