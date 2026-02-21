package com.mvdevsunion;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MvDevsUnionBetterSMP implements ModInitializer {
	public static final String MOD_ID = "mvdevsunionbettersmp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static ModConfig CONFIG;

	private int diamondScanTick = 0;
	private static final int DIAMOND_SCAN_RADIUS = 2;
	private static final List<String> FEATURES = List.of(
			"sleep", "nether", "end", "diamonds", "villagerInteraction", "mending");

	@Override
	public void onInitialize() {
		CONFIG = ConfigManager.load();

		// Prevent players from sleeping
		EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
			if (!CONFIG.disableSleep) return null;
			return Player.BedSleepingProblem.OTHER_PROBLEM;
		});

		// Block nether portal ignition and end portal activation
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockState blockState = world.getBlockState(hitResult.getBlockPos());
			if (CONFIG.disableNether && blockState.is(Blocks.OBSIDIAN) && player.getItemInHand(hand).is(Items.FLINT_AND_STEEL)) {
				player.displayClientMessage(Component.literal("§cNether is locked."), true);
				return InteractionResult.FAIL;
			}
			if (CONFIG.disableEnd && blockState.is(Blocks.END_PORTAL_FRAME) && player.getItemInHand(hand).is(Items.ENDER_EYE)) {
				player.displayClientMessage(Component.literal("§cThe End is locked."), true);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// Block villager interaction
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (CONFIG.disableVillagerInteraction && entity.getType() == EntityType.VILLAGER) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// Kill players in the nether/end or touching their portal blocks
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel world : server.getAllLevels()) {
				for (ServerPlayer player : world.players()) {
					if ((CONFIG.disableNether && player.level().dimension() == Level.NETHER)
							|| (CONFIG.disableEnd && player.level().dimension() == Level.END)) {
						player.hurt(world.damageSources().genericKill(), Float.MAX_VALUE);
					}

					BlockState atFeet = world.getBlockState(player.blockPosition());
					BlockState atBody = world.getBlockState(player.blockPosition().above());
					if ((CONFIG.disableNether && (atFeet.is(Blocks.NETHER_PORTAL) || atBody.is(Blocks.NETHER_PORTAL)))
							|| (CONFIG.disableEnd && (atFeet.is(Blocks.END_PORTAL) || atBody.is(Blocks.END_PORTAL)))) {
						player.hurt(world.damageSources().genericKill(), Float.MAX_VALUE);
					}

					// Remove mending enchanted books from inventory
					if (CONFIG.disableMending) {
						for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
							ItemStack stack = player.getInventory().getItem(i);
							if (stack.isEmpty()) continue;
							ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
							if (stored == null) continue;
							for (Holder<Enchantment> holder : stored.keySet()) {
								if (holder.is(Enchantments.MENDING)) {
									player.getInventory().setItem(i, ItemStack.EMPTY);
									break;
								}
							}
						}
					}
				}
			}
		});

		// Replace diamond ore with red wool in loaded overworld chunks
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (!CONFIG.disableDiamonds) return;
			if (world.dimension() != Level.OVERWORLD) return;
			diamondScanTick++;
			if (diamondScanTick % 20 != 0) return;

			for (ServerPlayer player : world.players()) {
				int chunkX = player.blockPosition().getX() >> 4;
				int chunkZ = player.blockPosition().getZ() >> 4;

				for (int dx = -DIAMOND_SCAN_RADIUS; dx <= DIAMOND_SCAN_RADIUS; dx++) {
					for (int dz = -DIAMOND_SCAN_RADIUS; dz <= DIAMOND_SCAN_RADIUS; dz++) {
						int cx = chunkX + dx;
						int cz = chunkZ + dz;
						if (!world.hasChunk(cx, cz)) continue;

						int startX = cx << 4;
						int startZ = cz << 4;
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								for (int y = -64; y < 16; y++) {
									BlockPos pos = new BlockPos(startX + x, y, startZ + z);
									BlockState state = world.getBlockState(pos);
									if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
										world.setBlock(pos, Blocks.RED_WOOL.defaultBlockState(), 3);
									}
								}
							}
						}
					}
				}
			}
		});

		// Admin commands: /bettersmp reload | status | enable | disable
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(
				Commands.literal("bettersmp")
					.requires(source -> {
						if (source.getEntity() instanceof ServerPlayer player) {
							return source.getServer().getPlayerList().isOp(player);
						}
						return true; // console / RCON always has permission
					})
					.then(Commands.literal("reload")
						.executes(ctx -> {
							CONFIG = ConfigManager.load();
							ctx.getSource().sendSuccess(() -> Component.literal("§aBetterSMP config reloaded."), false);
							return 1;
						}))
					.then(Commands.literal("status")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(() -> Component.literal("§6--- BetterSMP Status ---"), false);
							ctx.getSource().sendSuccess(() -> status("Sleep disabled", CONFIG.disableSleep), false);
							ctx.getSource().sendSuccess(() -> status("Nether disabled", CONFIG.disableNether), false);
							ctx.getSource().sendSuccess(() -> status("End disabled", CONFIG.disableEnd), false);
							ctx.getSource().sendSuccess(() -> status("Diamonds disabled", CONFIG.disableDiamonds), false);
							ctx.getSource().sendSuccess(() -> status("Villager interaction disabled", CONFIG.disableVillagerInteraction), false);
							ctx.getSource().sendSuccess(() -> status("Mending books disabled", CONFIG.disableMending), false);
							return 1;
						}))
					.then(Commands.literal("enable")
						.then(Commands.argument("feature", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								FEATURES.forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> setFeature(ctx, StringArgumentType.getString(ctx, "feature"), false))))
					.then(Commands.literal("disable")
						.then(Commands.argument("feature", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								FEATURES.forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> setFeature(ctx, StringArgumentType.getString(ctx, "feature"), true))))
			)
		);

		LOGGER.info("Hello Fabric world!");
	}

	private static Component status(String label, boolean enabled) {
		String icon = enabled ? "§a✔" : "§c✘";
		String state = enabled ? "§aON" : "§cOFF";
		return Component.literal(icon + " §f" + label + ": " + state);
	}

	private static int setFeature(CommandContext<CommandSourceStack> ctx, String feature, boolean restricted) {
		switch (feature) {
			case "sleep"                -> CONFIG.disableSleep = restricted;
			case "nether"               -> CONFIG.disableNether = restricted;
			case "end"                  -> CONFIG.disableEnd = restricted;
			case "diamonds"             -> CONFIG.disableDiamonds = restricted;
			case "villagerInteraction"  -> CONFIG.disableVillagerInteraction = restricted;
			case "mending"              -> CONFIG.disableMending = restricted;
			default -> {
				ctx.getSource().sendFailure(Component.literal("§cUnknown feature: " + feature));
				return 0;
			}
		}
		ConfigManager.save(CONFIG);
		String action = restricted ? "§cdisabled" : "§aenabled";
		ctx.getSource().sendSuccess(() -> Component.literal("§f" + feature + " " + action + "§f."), false);
		return 1;
	}
}
