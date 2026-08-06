package com.lion.villagertradingplus.tradeoffers.conditions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.villagertradingplus.VillagerTradingPlus;
import com.lion.villagertradingplus.tradeoffers.util.JsonFields;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Registry and parser for the cross-cutting {@code "conditions"} block that any trade may carry.
 * A condition is JSON {@code {"type": "...", ...params}}. {@link #parse} returns the combined list,
 * evaluated with AND semantics by default or OR when the array is preceded by a {@code logic}
 * marker (see {@link #parse}). An unknown condition type fails closed (the trade is withheld) with
 * a loud log so typos are visible instead of silently disabling gating.
 *
 * <p>Each condition type registers a <em>describer</em> alongside its parser. The predicate alone is
 * an opaque lambda, so without one the trade catalogue could tell a player a trade was gated but
 * never say by what.
 */
public final class TradeConditions {

    private static final Map<String, ConditionType> REGISTRY = new HashMap<>();
    private static final Map<String, GameRules.Key<GameRules.BooleanRule>> BOOLEAN_RULES = new HashMap<>();

    /** How many entries of a list-valued condition to name before collapsing the rest into a count. */
    private static final int DESCRIBE_LIST_LIMIT = 3;

    /** A condition type: how to build its predicate, and how to phrase it for a player. */
    private record ConditionType(Function<JsonObject, TradeCondition> parser,
                                 Function<JsonObject, Text> describer) {
    }

    static {
        // accept() stopped being static in 1.21.6 because game rules are feature-gated now. A
        // throwaway instance over the default feature set enumerates every rule a vanilla world can
        // have, which is all this map is for: turning a rule name from JSON into a typed Key.
        new GameRules(FeatureFlags.DEFAULT_ENABLED_FEATURES).accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
            }

            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                BOOLEAN_RULES.put(key.getName(), key);
            }
        });
        registerBuiltins();
    }

    private TradeConditions() {
    }

    /** Registers a condition type with a generic description. Prefer the three-arg overload. */
    public static void register(String type, Function<JsonObject, TradeCondition> factory) {
        register(type, factory, json -> Text.translatable("condition.villagertradingplus.generic", type));
    }

    public static void register(String type, Function<JsonObject, TradeCondition> factory,
                                Function<JsonObject, Text> describer) {
        REGISTRY.put(type, new ConditionType(factory, describer));
    }

    /**
     * Parses the {@code conditions} array into a single combined {@link TradeCondition}. Default
     * logic is AND; pass {@code "logic": "or"} on the owning trade object to switch (read separately
     * — see {@link #parse(JsonArray, boolean)}).
     */
    public static ParsedConditions parse(JsonArray conditions) {
        return parse(conditions, false);
    }

    public static ParsedConditions parse(JsonArray conditions, boolean orLogic) {
        List<ParsedConditions.Entry> parsed = new ArrayList<>();
        for (JsonElement element : conditions) {
            JsonObject obj = JsonFields.asObject(element, "\"conditions\"");
            String type = stripNamespace(JsonFields.requireString(obj, "trade condition", "type"));
            ConditionType conditionType = REGISTRY.get(type);
            if (conditionType == null) {
                // Withheld rather than skipped: the type may come from a mod that is not installed,
                // and offering the trade ungated would be the more harmful guess.
                VillagerTradingPlus.LOGGER.error("Unknown trade condition type: " + type + " -- this trade will be withheld.");
                parsed.add(new ParsedConditions.Entry(
                        Text.translatable("condition.villagertradingplus.unknown", type),
                        villager -> false)); // fail closed
                continue;
            }
            // Parsed before it is described, so a malformed condition fails with one clean message
            // instead of first logging that its describer choked on the same missing field.
            TradeCondition condition = conditionType.parser().apply(obj);
            parsed.add(new ParsedConditions.Entry(describeSafely(conditionType, obj, type), condition));
        }

        return new ParsedConditions(List.copyOf(parsed), orLogic);
    }

    /** A malformed describer must never take down trade loading, so fall back to the bare type name. */
    private static Text describeSafely(ConditionType type, JsonObject json, String typeName) {
        try {
            return type.describer().apply(json);
        } catch (RuntimeException e) {
            VillagerTradingPlus.LOGGER.warn("Could not describe trade condition of type " + typeName, e);
            return Text.translatable("condition.villagertradingplus.generic", typeName);
        }
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon >= 0 ? type.substring(colon + 1) : type;
    }

    private static void registerBuiltins() {
        register("biome", TradeConditions::parseBiome, TradeConditions::describeBiome);
        register("dimension", TradeConditions::parseDimension, TradeConditions::describeDimension);
        register("weather", TradeConditions::parseWeather, TradeConditions::describeWeather);
        register("day_night", TradeConditions::parseDayNight, TradeConditions::describeDayNight);
        register("moon_phase", TradeConditions::parseMoonPhase, TradeConditions::describeMoonPhase);
        register("config_flag", TradeConditions::parseConfigFlag, TradeConditions::describeConfigFlag);
        register("gamerule", TradeConditions::parseGamerule, TradeConditions::describeGamerule);
        register("job_site_block", TradeConditions::parseJobSite, TradeConditions::describeJobSite);
    }

    // --- Built-in condition parsers ---------------------------------------------------------

    private static TradeCondition parseBiome(JsonObject json) {
        if (json.has("tag")) {
            TagKey<net.minecraft.world.biome.Biome> tag = TagKey.of(RegistryKeys.BIOME,
                    Identifier.tryParse(JsonFields.requireString(json, "\"biome\" condition", "tag")));
            return villager -> villager.getWorld().getBiome(villager.getBlockPos()).isIn(tag);
        }
        Set<String> biomes = toStringSet(JsonFields.requireArray(json, "\"biome\" condition", "biomes"));
        return villager -> villager.getWorld().getBiome(villager.getBlockPos()).getKey()
                .map(key -> biomes.contains(key.getValue().toString()))
                .orElse(false);
    }

    private static TradeCondition parseDimension(JsonObject json) {
        String dimension = JsonFields.requireString(json, "\"dimension\" condition", "dimension");
        return villager -> villager.getWorld().getRegistryKey().getValue().toString().equals(dimension);
    }

    private static TradeCondition parseWeather(JsonObject json) {
        String state = JsonFields.requireString(json, "\"weather\" condition", "state");
        return villager -> {
            World world = villager.getWorld();
            return switch (state) {
                case "thunder" -> world.isThundering();
                case "rain" -> world.isRaining();
                default -> !world.isRaining(); // "clear"
            };
        };
    }

    private static TradeCondition parseDayNight(JsonObject json) {
        boolean wantDay = "day".equals(JsonFields.requireString(json, "\"day_night\" condition", "time"));
        return villager -> villager.getWorld().isDay() == wantDay;
    }

    /**
     * Matches the lunar day, and by default only while the moon is actually up.
     *
     * <p>{@code getMoonPhase()} is {@code getLunarTime() / 24000 % 8}: it names the lunar day and
     * says nothing about the time within it, so on its own it holds all through the following
     * daylight hours too - a "full moon" trade would sit there unlocked at noon. Set
     * {@code "require_night": false} to gate on the bare lunar day anyway.
     */
    private static TradeCondition parseMoonPhase(JsonObject json) {
        Set<Integer> phases = new HashSet<>();
        for (JsonElement element : JsonFields.requireArray(json, "\"moon_phase\" condition", "phases")) {
            phases.add(element.getAsInt());
        }
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return villager -> phases.contains(villager.getWorld().getMoonPhase())
                && (!requireNight || !villager.getWorld().isDay());
    }

    private static TradeCondition parseConfigFlag(JsonObject json) {
        String fieldName = JsonFields.requireString(json, "\"config_flag\" condition", "field");
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return villager -> {
            try {
                Field field = VillagerTradingPlus.CONFIG.getClass().getField(fieldName);
                return field.getBoolean(VillagerTradingPlus.CONFIG) == expected;
            } catch (ReflectiveOperationException e) {
                VillagerTradingPlus.LOGGER.error("config_flag condition references unknown boolean field: " + fieldName, e);
                return false;
            }
        };
    }

    private static TradeCondition parseGamerule(JsonObject json) {
        String ruleName = JsonFields.requireString(json, "\"gamerule\" condition", "rule");
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        GameRules.Key<GameRules.BooleanRule> key = BOOLEAN_RULES.get(ruleName);
        if (key == null) {
            VillagerTradingPlus.LOGGER.error("gamerule condition references unknown boolean rule: " + ruleName);
            return villager -> false;
        }
        // getGameRules() moved off World onto ServerWorld. Trades are only ever generated
        // server-side, so a client-side world simply fails the condition rather than guessing.
        return villager -> villager.getWorld() instanceof ServerWorld serverWorld
                && serverWorld.getGameRules().getBoolean(key) == expected;
    }

    private static TradeCondition parseJobSite(JsonObject json) {
        if (json.has("wood_variant")) {
            String variant = JsonFields.requireString(json, "\"job_site_block\" condition", "wood_variant");
            return villager -> jobSiteBlockId(villager)
                    .map(id -> id.getPath().contains(variant))
                    .orElse(false);
        }
        Set<String> blocks = toStringSet(JsonFields.requireArray(json, "\"job_site_block\" condition", "blocks"));
        return villager -> jobSiteBlockId(villager)
                .map(id -> blocks.contains(id.toString()))
                .orElse(false);
    }

    // --- Built-in condition describers ------------------------------------------------------

    private static Text describeBiome(JsonObject json) {
        if (json.has("tag")) {
            return Text.translatable("condition.villagertradingplus.biome_tag", prettify(json.get("tag").getAsString()));
        }
        return Text.translatable("condition.villagertradingplus.biome", joinArray(json.getAsJsonArray("biomes")));
    }

    private static Text describeDimension(JsonObject json) {
        return Text.translatable("condition.villagertradingplus.dimension", prettify(json.get("dimension").getAsString()));
    }

    private static Text describeWeather(JsonObject json) {
        String state = json.get("state").getAsString();
        String key = switch (state) {
            case "thunder", "rain" -> state;
            default -> "clear";
        };
        return Text.translatable("condition.villagertradingplus.weather",
                Text.translatable("condition.villagertradingplus.weather." + key));
    }

    private static Text describeDayNight(JsonObject json) {
        String time = "day".equals(json.get("time").getAsString()) ? "day" : "night";
        return Text.translatable("condition.villagertradingplus.day_night",
                Text.translatable("condition.villagertradingplus.day_night." + time));
    }

    private static Text describeMoonPhase(JsonObject json) {
        boolean requireNight = !json.has("require_night") || json.get("require_night").getAsBoolean();
        return Text.translatable(
                requireNight ? "condition.villagertradingplus.moon_phase_night" : "condition.villagertradingplus.moon_phase",
                joinArray(json.getAsJsonArray("phases")));
    }

    private static Text describeConfigFlag(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Text.translatable("condition.villagertradingplus.config_flag",
                json.get("field").getAsString(), String.valueOf(expected));
    }

    private static Text describeGamerule(JsonObject json) {
        boolean expected = !json.has("value") || json.get("value").getAsBoolean();
        return Text.translatable("condition.villagertradingplus.gamerule",
                json.get("rule").getAsString(), String.valueOf(expected));
    }

    private static Text describeJobSite(JsonObject json) {
        if (json.has("wood_variant")) {
            return Text.translatable("condition.villagertradingplus.job_site_wood", json.get("wood_variant").getAsString());
        }
        return Text.translatable("condition.villagertradingplus.job_site_block", joinArray(json.getAsJsonArray("blocks")));
    }

    /** Comma-joins a JSON array, naming at most {@link #DESCRIBE_LIST_LIMIT} entries. */
    private static String joinArray(JsonArray array) {
        StringBuilder joined = new StringBuilder();
        int shown = Math.min(array.size(), DESCRIBE_LIST_LIMIT);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(prettify(array.get(i).getAsString()));
        }
        if (array.size() > shown) {
            joined.append(" +").append(array.size() - shown);
        }
        return joined.toString();
    }

    /** Drops the {@code minecraft:} namespace so descriptions stay readable in a tooltip. */
    private static String prettify(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static Optional<Identifier> jobSiteBlockId(Entity villager) {
        if (!(villager instanceof VillagerEntity villagerEntity)) {
            return Optional.empty();
        }
        Optional<GlobalPos> jobSite = villagerEntity.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isEmpty()) {
            return Optional.empty();
        }
        GlobalPos pos = jobSite.get();
        if (!pos.dimension().equals(villager.getWorld().getRegistryKey())) {
            return Optional.empty();
        }
        return Optional.of(net.minecraft.registry.Registries.BLOCK.getId(
                villager.getWorld().getBlockState(pos.pos()).getBlock()));
    }

    private static Set<String> toStringSet(JsonArray array) {
        Set<String> set = new HashSet<>();
        for (JsonElement element : array) {
            set.add(element.getAsString());
        }
        return set;
    }
}
