package com.palordersoftworks.brokenstarsmpmod.mixins;

import com.palordersoftworks.brokenstarsmpmod.config.ServerRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayer_DeathMessageMixin {
    @Redirect(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;"
            )
    )
    private Component brokenstarsmpmod$hideInvisibleKiller(CombatTracker tracker) {
        Component original = tracker.getDeathMessage();
        if (!ServerRules.HIDE_INVISIBLE_KILLER_IN_DEATH_MESSAGES) {
            return original;
        }

        ServerPlayer victim = (ServerPlayer) (Object) this;
        LivingEntity killerCredit = victim.getKillCredit();
        // Only strip the killer's name when the killer is an invisible player.
        if (!(killerCredit instanceof ServerPlayer killer) || !killer.isInvisible()) {
            return original;
        }

        if (!(original.getContents() instanceof TranslatableContents translatable)) {
            return original;
        }
        Object[] args = translatable.getArgs();
        if (args.length == 0) {
            return original;
        }

        // Replace every argument that names the killer so multi-arg death
        // messages (e.g. death.attack.arrow.item) keep their remaining
        // placeholders intact instead of rendering a raw %s.
        String killerName = killer.getDisplayName().getString();
        Object[] rewrittenArgs = new Object[args.length];
        boolean replaced = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component arg && arg.getString().equals(killerName)) {
                rewrittenArgs[i] = Component.literal("someone");
                replaced = true;
            } else {
                rewrittenArgs[i] = args[i];
            }
        }
        if (!replaced) {
            return original;
        }

        MutableComponent rewritten = Component.translatable(
                translatable.getKey(),
                rewrittenArgs
        ).withStyle(original.getStyle());
        for (Component sibling : original.getSiblings()) {
            rewritten.append(sibling);
        }
        return rewritten;
    }
}
