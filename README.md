# VillagerTradingPlus

A library that replaces Minecraft's villager trading with a datapack-driven system.

It is not a content mod - it adds no villagers, blocks or items of its own. It provides the trade
engine that other mods and datapacks feed. [VillagersPlus](https://github.com/finallion/VillagersPlus_-FABRIC-)
is the reference consumer.

## What it does

- Add, delete or overwrite trades for **every** villager profession, vanilla ones included.
- Change the currency from emeralds to any item you like.
- Set amounts, max usages, villager experience and price behaviour per trade.
- Gate trades behind **conditions** - biome, dimension, weather, time of day, moon phase, gamerule,
  job site block or a config flag.
- Fourteen trade types, from a plain item sale to weighted pools and enchanted books drawn from a
  list you define.
- Merge **multiple datapacks** that modify the same villager instead of letting the last one win.

## Configuration

`config/villagertradingplus/villagertradingplus-1.0-config.json5`

These apply to every trade the library builds, no matter which mod or datapack defined it.

### Trades
| Option | Default | What it does |
| --- | --- | --- |
| `trade_offers_per_level` | 2 | Max new trades a villager gains per level. |
| `trade_offers_wandering_trader` | 5 | Max trades the wandering trader offers. |
| `trade_price_multiplier_scale` | 1.0 | Scales how strongly prices swing from demand and reputation. `0.0` freezes both. Does **not** change a trade's starting price. |
| `trade_cost_scale` | 1.0 | Multiplies the base cost of every trade (first input slot only). `2.0` doubles all prices. Result is clamped to 1 .. one stack. |
| `enable_conditional_trades` | true | Whether condition-gated trades are actually checked. `false` makes every trade always appear. |
| `enable_time_of_day_pricing` | false | Randomly nudges a trade's cost based on the in-game time it is unlocked. The price is locked in at unlock and does not keep changing. |
| `time_of_day_price_variance` | 0.15 | How far that nudge can go, as a fraction of the cost. Only used when the option above is on. |

### Trading screen controls
All three are **off by default**. When on, **any** player can use them - they are meant for creative
building and testing, not for survival servers.

| Option | Default | What it does |
| --- | --- | --- |
| `allow_trade_reroll` | false | Buttons to re-roll a villager's or wandering trader's trades, all at once or one level at a time. |
| `allow_set_villager_level` | false | A control to set a villager's level (1-5) and regenerate its trades. Wandering traders have no levels and ignore it. |
| `allow_view_all_trades` | false | A button that opens a read-only catalog panel listing every trade a villager could roll at a chosen level, with weights, chances and conditions. |

---

## Datapack Support

The library reads from `data/<any namespace>/`, so a datapack can use its own namespace. The
examples below use `villagersplus` because that is where the reference consumer's files live.

### Replacing the default trades of a mod's villagers
Create a datapack with this directory structure:

    data/<namespace>/default_villager_trades

Copy the JSON file of the villager whose trades you would like to modify into your datapack. The
default trade files of a consumer mod are located inside its jar under the same path. Modify the
trades as you please - your datapack JSON overwrites the default one.

### Replacing the default trades of Minecraft villagers
_Notice: the vanilla trades aren't in JSON form yet, so modifying those is a bit more work._

Create a datapack with this directory structure:

    data/<namespace>/default_villager_trades

Create a JSON named after the villager profession whose trades you would like to modify, for example
`butcher.json`. Below is the basic structure:

    {
        "profession": "minecraft:butcher",
        "trades": {
            "novice": [],
            "apprentice": [],
            "journeyman": [],
            "expert": [],
            "master": []
        }
    }

Add trades as you like.

### Adding trades to villagers
Create a datapack with this directory structure:

    data/<namespace>/villager_trades

Create a JSON named after the villager profession to whose trades you would like to add. Below is an
example for adding a sell trade. One diamond block is sold for 12 emeralds.

    {
        "profession": "villagersplus:horticulturist",
        "trades": {
            "novice": [
                {
                    "type": "villagertradingplus:sell_item",
                    "sell": { "item": "diamond_block", "count": 1 },
                    "priceIn": { "item": "emerald", "count": 12 },
                    "max_uses": 12,
                    "villager_experience": 4
                }
            ]
        }
    }

---

## Trade types

Every snippet below is a single trade object. It goes inside one of the five tier arrays
(`novice`, `apprentice`, `journeyman`, `expert`, `master`) of a profession file.

Trade types are namespaced `villagertradingplus:`. The old `villagersplus:` namespace is still
accepted for datapacks written before the split, but it logs a deprecation warning on load - write
new files against the new one.

These fields work on **every** type and are left out of the examples to keep them short:

| Field | Meaning |
| --- | --- |
| `max_uses` | How often the trade can be used before it locks. |
| `villager_experience` | Experience the villager gains per use. |
| `price_multiplier` | How strongly reputation and Hero of the Village move this trade's price. |
| `demand` | Seeds vanilla's demand pricing - the cost climbs with use and decays over time. |
| `conditions` / `logic` | See [Conditions](#conditions). |

### `sell_item`
The villager sells an item for a price.

    {
      "type": "villagertradingplus:sell_item",
      "sell": { "item": "minecraft:oxeye_daisy", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 2 }
    }

### `buy_item`
The villager buys an item and pays you.

    {
      "type": "villagertradingplus:buy_item",
      "buy": { "item": "minecraft:wheat_seeds", "count": 4 },
      "reward": { "item": "minecraft:emerald", "count": 1 }
    }

### `sell_tagged_item`
Sells **one random member** of an item tag. The member is chosen when the offer is generated, so
two villagers with this trade will usually sell different things. Vanilla trades match a concrete
item, so this is not an "any of" match.

    {
      "type": "villagertradingplus:sell_tagged_item",
      "sell": { "tag": "minecraft:saplings", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 3 }
    }

### `buy_tagged_item`
The same idea for buying.

    {
      "type": "villagertradingplus:buy_tagged_item",
      "buy": { "tag": "minecraft:flowers", "count": 8 },
      "reward": { "item": "minecraft:emerald", "count": 1 }
    }

### `process_item`
Takes `convertible` plus `priceIn` and returns `sell` - the classic "bring me raw material" trade.

    {
      "type": "villagertradingplus:process_item",
      "convertible": { "item": "minecraft:dead_tube_coral", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 5 },
      "sell": { "item": "minecraft:tube_coral", "count": 1 }
    }

### `multi_input`
Two inputs, one output. Vanilla has exactly two buy slots, so one of the two inputs **is** the currency.

    {
      "type": "villagertradingplus:multi_input",
      "input_a": { "item": "minecraft:emerald", "count": 5 },
      "input_b": { "item": "minecraft:book", "count": 1 },
      "sell": {
        "item": "minecraft:enchanted_book",
        "enchantments": [ { "id": "minecraft:mending", "lvl": 1 } ]
      }
    }

### `weighted_pool`
Picks one of several trades by weight when the offer is generated. Below, the daisy is ten times as
likely as the wither rose.

    {
      "type": "villagertradingplus:weighted_pool",
      "pool": [
        {
          "weight": 10,
          "trade": {
            "type": "villagertradingplus:sell_item",
            "sell": { "item": "minecraft:oxeye_daisy", "count": 1 },
            "priceIn": { "item": "minecraft:emerald", "count": 1 }
          }
        },
        {
          "weight": 1,
          "trade": {
            "type": "villagertradingplus:sell_item",
            "sell": { "item": "minecraft:wither_rose", "count": 1 },
            "priceIn": { "item": "minecraft:emerald", "count": 8 }
          }
        }
      ]
    }

### `sell_potion`
Takes `convertible` plus `priceIn` and returns `sell`, for potion-shaped trades.

    {
      "type": "villagertradingplus:sell_potion",
      "convertible": { "item": "minecraft:potion", "count": 3 },
      "priceIn": { "item": "minecraft:emerald", "count": 5 },
      "sell": { "item": "minecraft:splash_potion", "count": 7 }
    }

### `sell_map`
Sells a filled map pointing at a structure. `structure_id` is a structure tag.

    {
      "type": "villagertradingplus:sell_map",
      "structure_id": "minecraft:on_woodland_explorer_maps",
      "name": "filled_map",
      "buy": { "item": "minecraft:compass", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 2 },
      "price_multiplier": 0.2
    }

### `sell_enchanted_tool`
Sells a tool with a **random** enchantment, vanilla-style. The price scales with what was rolled.

    {
      "type": "villagertradingplus:sell_enchanted_tool",
      "sell": { "item": "minecraft:diamond_pickaxe", "count": 1 },
      "basePriceIn": { "item": "minecraft:emerald", "count": 2 }
    }

### `sell_specific_enchanted_tool`
Sells a tool with a **fixed** enchantment at a fixed level - a guaranteed result rather than a roll.
`enchantment` defaults to `minecraft:fortune`, `level` to 1.

    {
      "type": "villagertradingplus:sell_specific_enchanted_tool",
      "sell": { "item": "minecraft:diamond_pickaxe", "count": 1 },
      "basePriceIn": { "item": "minecraft:emerald", "count": 32 },
      "enchantment": "minecraft:efficiency",
      "level": 4
    }

### `sell_enchanted_book`
Sells a randomly enchanted book, vanilla-style.

    {
      "type": "villagertradingplus:sell_enchanted_book",
      "currency": { "item": "minecraft:emerald" },
      "price_multiplier": 0.2
    }

### `sell_specific_enchanted_book`
Sells a book with a **fixed** enchantment and level. There is no `sell` field - the item is always a
book. `enchantment` defaults to `minecraft:unbreaking`, `level` to 1.

    {
      "type": "villagertradingplus:sell_specific_enchanted_book",
      "basePriceIn": { "item": "minecraft:emerald", "count": 20 },
      "enchantment": "minecraft:mending",
      "level": 1
    }

### `sell_enchanted_book_from_list`
Sells an enchanted book drawn from a **weighted list** you define, so you control exactly which
enchantments a librarian can offer. Cost is `base_cost + cost_per_level * level`, multiplied by
`treasure_multiplier` for treasure enchantments.

    {
      "type": "villagertradingplus:sell_enchanted_book_from_list",
      "currency": { "item": "minecraft:emerald" },
      "enchantments": [
        { "id": "minecraft:sharpness",  "min_level": 1, "max_level": 3, "weight": 5 },
        { "id": "minecraft:unbreaking", "min_level": 1, "max_level": 3, "weight": 5 },
        { "id": "minecraft:mending",    "min_level": 1, "max_level": 1, "weight": 1 }
      ],
      "base_cost": 2,
      "cost_per_level": 3,
      "treasure_multiplier": 2,
      "price_multiplier": 0.2
    }

---

## Conditions

Add a `conditions` array to **any** trade to gate it. The default logic is AND; set `"logic": "or"`
on the trade to require only one of them.

**Timing matters.** Conditions are evaluated once, at the moment the offer is generated as the
villager levels up - not continuously. Location conditions (`biome`, `dimension`, `job_site_block`,
`config_flag`) are stable for a settled villager and therefore reliable. World-state conditions
(`weather`, `day_night`, `moon_phase`, `gamerule`) are a snapshot of that moment: a trade gated on
`night` stays available in broad daylight once it has been rolled.

Conditions can be switched off globally with `enable_conditional_trades`.

| Type | Fields | Example |
| --- | --- | --- |
| `biome` | `tag` (string) **or** `biomes` (array) | `{ "type": "biome", "tag": "minecraft:is_ocean" }` |
| `dimension` | `dimension` | `{ "type": "dimension", "dimension": "minecraft:overworld" }` |
| `weather` | `state`: `clear`, `rain`, `thunder` | `{ "type": "weather", "state": "rain" }` |
| `day_night` | `time`: `day` or `night` | `{ "type": "day_night", "time": "night" }` |
| `moon_phase` | `phases` (array of 0-7) | `{ "type": "moon_phase", "phases": [0] }` |
| `config_flag` | `field` (a boolean config field name), optional `value` | `{ "type": "config_flag", "field": "can_explode" }` |
| `gamerule` | `rule` (a boolean gamerule), optional `value` | `{ "type": "gamerule", "rule": "doInsomnia", "value": true }` |
| `job_site_block` | `blocks` (array) **or** `wood_variant` (string matched against the block id) | `{ "type": "job_site_block", "blocks": [ "villagersplus:oceanographer_table" ] }` |

Note that `biome`, `blocks` and `phases` take **arrays**, while `tag`, `dimension` and `wood_variant`
take a single string.

    {
      "type": "villagertradingplus:sell_item",
      "sell": { "item": "minecraft:heart_of_the_sea", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 30 },
      "logic": "or",
      "conditions": [
        { "type": "biome", "tag": "minecraft:is_ocean" },
        { "type": "day_night", "time": "night" }
      ]
    }

## Pricing

`price_multiplier` and `demand` work on any trade and feed vanilla's own price machinery:

- `price_multiplier` - how strongly reputation and Hero of the Village move this trade's price.
- `demand` - seeds vanilla's demand tracking. The cost climbs as the trade is used and decays again over time.

Both are scaled server-wide by `trade_price_multiplier_scale` (swing size) and `trade_cost_scale`
(base cost). Reputation and Hero of the Village discounts are applied by vanilla on top.

    {
      "type": "villagertradingplus:sell_item",
      "sell": { "item": "minecraft:diamond", "count": 1 },
      "priceIn": { "item": "minecraft:emerald", "count": 12 },
      "price_multiplier": 0.1,
      "demand": 10
    }

## Custom items

Any item stack in any trade type accepts this sugar - no special trade type needed:

| Field | Meaning |
| --- | --- |
| `name` | Display name. Plain string or a raw JSON text component. |
| `lore` | Array of lore lines. |
| `enchantments` | Array of `{ "id": ..., "lvl": ... }`. |
| `color` | Hex colour for dyeable armour. |
| `potion` | Potion id for potion items. |
| `skull_owner` | Player name for player heads. |
| `book` | `{ "title": ..., "author": ..., "pages": [...] }` for written books. |
| `nbt` | Raw SNBT, applied **last** as an escape hatch. |

    {
      "type": "villagertradingplus:sell_item",
      "sell": {
        "item": "minecraft:diamond_sword",
        "name": "{\"text\":\"Excalibur\",\"color\":\"gold\",\"italic\":false}",
        "lore": [ "A blade of legend" ],
        "enchantments": [ { "id": "minecraft:sharpness", "lvl": 5 } ],
        "nbt": "{Unbreakable:1b}"
      },
      "priceIn": { "item": "minecraft:emerald", "count": 40 }
    }

## Wandering trader trades

Create a datapack with this directory structure:

    data/<namespace>/wandering_trader_trades

Any file name works. Instead of the five villager tiers, the wandering trader has two:

- `common` - level 1. Several are picked, the count is set by `trade_offers_wandering_trader`.
- `rare` - level 2. Exactly one is picked.

Set `"replace": true` to drop the vanilla wandering-trader trades and use **only** yours. Omit it
(or set `false`) to add on top of vanilla. Every trade type, the conditions block and pricing all
work here exactly as they do on villagers.

    {
      "replace": false,
      "trades": {
        "common": [
          {
            "type": "villagertradingplus:sell_item",
            "sell": { "item": "minecraft:glowstone_dust", "count": 4 },
            "priceIn": { "item": "minecraft:emerald", "count": 2 },
            "conditions": [ { "type": "day_night", "time": "night" } ],
            "max_uses": 6,
            "villager_experience": 1
          }
        ],
        "rare": [
          {
            "type": "villagertradingplus:sell_item",
            "sell": {
              "item": "minecraft:trident",
              "name": "{\"text\":\"Traveler's Trident\",\"color\":\"aqua\",\"italic\":false}",
              "enchantments": [ { "id": "minecraft:loyalty", "lvl": 3 } ],
              "nbt": "{Unbreakable:1b}"
            },
            "priceIn": { "item": "minecraft:emerald", "count": 30 },
            "max_uses": 2,
            "villager_experience": 1
          }
        ]
      }
    }

## Ready-made example files

Inside the jar under `data/villagertradingplus/available_trade_examples` you will find complete,
copyable files for the cases a snippet cannot show well:

- `full_profession.json` - one profession across all five tiers, mixing many trade types.
- `conditions_and_pricing.json` - conditions and pricing on realistic trades.
- `wandering_trader.json` - a complete wandering trader file.
