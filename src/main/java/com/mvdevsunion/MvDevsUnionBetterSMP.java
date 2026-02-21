package com.mvdevsunion;

import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
			return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
		});

		// Block nether portal ignition and end portal activation
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockState blockState = world.getBlockState(hitResult.getBlockPos());
			if (CONFIG.disableNether && blockState.isOf(Blocks.OBSIDIAN) && player.getStackInHand(hand).isOf(Items.FLINT_AND_STEEL)) {
				player.sendMessage(Text.literal("§cNether is locked."), true);
				return ActionResult.FAIL;
			}
			if (CONFIG.disableEnd && blockState.isOf(Blocks.END_PORTAL_FRAME) && player.getStackInHand(hand).isOf(Items.ENDER_EYE)) {
				player.sendMessage(Text.literal("§cThe End is locked."), true);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		// Block villager interaction
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (CONFIG.disableVillagerInteraction && entity instanceof VillagerEntity) {
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		// Kill players in the nether/end or touching their portal blocks
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerWorld world : server.getWorlds()) {
				for (ServerPlayerEntity player : world.getPlayers()) {
					if ((CONFIG.disableNether && player.getWorld().getRegistryKey() == World.NETHER)
							|| (CONFIG.disableEnd && player.getWorld().getRegistryKey() == World.END)) {
						player.damage(world, world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
					}

					BlockState atFeet = world.getBlockState(player.getBlockPos());
					BlockState atBody = world.getBlockState(player.getBlockPos().up());
					if ((CONFIG.disableNether && (atFeet.isOf(Blocks.NETHER_PORTAL) || atBody.isOf(Blocks.NETHER_PORTAL)))
							|| (CONFIG.disableEnd && (atFeet.isOf(Blocks.END_PORTAL) || atBody.isOf(Blocks.END_PORTAL)))) {
						player.damage(world, world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
					}

					// Remove mending enchanted books from inventory
					if (CONFIG.disableMending) {
						for (int i = 0; i < player.getInventory().size(); i++) {
							ItemStack stack = player.getInventory().getStack(i);
							if (stack.isEmpty()) continue;
							ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
							if (stored == null) continue;
							for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
								if (entry.matchesKey(Enchantments.MENDING)) {
									player.getInventory().setStack(i, ItemStack.EMPTY);
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
			if (world.getRegistryKey() != World.OVERWORLD) return;
			diamondScanTick++;
			if (diamondScanTick % 20 != 0) return;

			for (ServerPlayerEntity player : world.getPlayers()) {
				int chunkX = player.getBlockPos().getX() >> 4;
				int chunkZ = player.getBlockPos().getZ() >> 4;

				for (int dx = -DIAMOND_SCAN_RADIUS; dx <= DIAMOND_SCAN_RADIUS; dx++) {
					for (int dz = -DIAMOND_SCAN_RADIUS; dz <= DIAMOND_SCAN_RADIUS; dz++) {
						int cx = chunkX + dx;
						int cz = chunkZ + dz;
						if (!world.isChunkLoaded(cx, cz)) continue;

						int startX = cx << 4;
						int startZ = cz << 4;
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								for (int y = -64; y < 16; y++) {
									BlockPos pos = new BlockPos(startX + x, y, startZ + z);
									BlockState state = world.getBlockState(pos);
									if (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)) {
										world.setBlockState(pos, Blocks.RED_WOOL.getDefaultState());
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
				CommandManager.literal("bettersmp")
					.requires(source -> source.hasPermissionLevel(2))
					.then(CommandManager.literal("reload")
						.executes(ctx -> {
							CONFIG = ConfigManager.load();
							ctx.getSource().sendFeedback(() -> Text.literal("§aBetterSMP config reloaded."), false);
							return 1;
						}))
					.then(CommandManager.literal("status")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(() -> Text.literal("§6--- BetterSMP Status ---"), false);
							ctx.getSource().sendFeedback(() -> status("Sleep disabled", CONFIG.disableSleep), false);
							ctx.getSource().sendFeedback(() -> status("Nether disabled", CONFIG.disableNether), false);
							ctx.getSource().sendFeedback(() -> status("End disabled", CONFIG.disableEnd), false);
							ctx.getSource().sendFeedback(() -> status("Diamonds disabled", CONFIG.disableDiamonds), false);
							ctx.getSource().sendFeedback(() -> status("Villager interaction disabled", CONFIG.disableVillagerInteraction), false);
							ctx.getSource().sendFeedback(() -> status("Mending books disabled", CONFIG.disableMending), false);
							return 1;
						}))
					.then(CommandManager.literal("enable")
						.then(CommandManager.argument("feature", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								FEATURES.forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> setFeature(ctx, StringArgumentType.getString(ctx, "feature"), false))))
					.then(CommandManager.literal("disable")
						.then(CommandManager.argument("feature", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								FEATURES.forEach(builder::suggest);
								return builder.buildFuture();
							})
							.executes(ctx -> setFeature(ctx, StringArgumentType.getString(ctx, "feature"), true))))
			)
		);

		LOGGER.info("Hello Fabric world!");
	}

	private static Text status(String label, boolean enabled) {
		String icon = enabled ? "§a✔" : "§c✘";
		String state = enabled ? "§aON" : "§cOFF";
		return Text.literal(icon + " §f" + label + ": " + state);
	}

	private static int setFeature(CommandContext<ServerCommandSource> ctx, String feature, boolean restricted) {
		switch (feature) {
			case "sleep"                -> CONFIG.disableSleep = restricted;
			case "nether"               -> CONFIG.disableNether = restricted;
			case "end"                  -> CONFIG.disableEnd = restricted;
			case "diamonds"             -> CONFIG.disableDiamonds = restricted;
			case "villagerInteraction"  -> CONFIG.disableVillagerInteraction = restricted;
			case "mending"              -> CONFIG.disableMending = restricted;
			default -> {
				ctx.getSource().sendFeedback(() -> Text.literal("§cUnknown feature: " + feature), false);
				return 0;
			}
		}
		ConfigManager.save(CONFIG);
		String action = restricted ? "§cdisabled" : "§aenabled";
		ctx.getSource().sendFeedback(() -> Text.literal("§f" + feature + " " + action + "§f."), false);
		return 1;
	}
}
