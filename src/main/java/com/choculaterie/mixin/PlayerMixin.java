package com.choculaterie.mixin;

import com.choculaterie.history.BlockHistory;
import com.choculaterie.history.Cause;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

	@Unique
	private Item ctrlz$weapon;

	@Unique
	private int ctrlz$weaponDamageBefore;

	@Inject(method = "attack", at = @At("HEAD"))
	private void ctrlz$beginAttack(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!self.level().isClientSide()) {
			BlockHistory.begin(Cause.ATTACK_ENTITY);
			ItemStack weapon = self.getMainHandItem();
			if (weapon.isDamageableItem()) {
				ctrlz$weapon = weapon.getItem();
				ctrlz$weaponDamageBefore = weapon.getDamageValue();
			} else {
				ctrlz$weapon = null;
			}
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void ctrlz$endAttack(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (!self.level().isClientSide()) {
			if (ctrlz$weapon != null && self instanceof ServerPlayer serverPlayer) {
				ItemStack after = self.getMainHandItem();
				if (after.is(ctrlz$weapon) && after.isDamageableItem()) {
					if (after.getDamageValue() != ctrlz$weaponDamageBefore) {
						BlockHistory.recordToolDurability(serverPlayer, ctrlz$weapon, ctrlz$weaponDamageBefore, after.getDamageValue(), false);
					}
				} else if (!after.is(ctrlz$weapon)) {
					BlockHistory.recordToolDurability(serverPlayer, ctrlz$weapon, ctrlz$weaponDamageBefore, ctrlz$weaponDamageBefore, true);
				}
				ctrlz$weapon = null;
			}
			BlockHistory.end();
		}
	}
}
