# Transmog Unlock System

All transmog unlocks are controlled via LuckPerms permission nodes. Players with the appropriate permission will have the transmog available in the Transmog Table.

## Patron Tier Transmogs

Granted automatically on login when the player has the corresponding patron permission. Models are discovered into `DiscoveredModelsData` and persist across sessions. Revoked when the permission is removed (even across server restarts).

Each tier includes all models from lower tiers.

| Permission Node | Tier | Models |
|---|---|---|
| `masucraftfixes.patron.dweller` | Vault Dweller | Dweller Sword, Dweller Axe |
| `masucraftfixes.patron.cheeser` | Vault Cheeser | Dark Cheese Warrior armor set (helmet, chestplate, leggings, boots) |
| `masucraftfixes.patron.goblin` | Vault Goblin | Goblin armor set (helmet, chestplate, leggings, boots) |
| `masucraftfixes.patron.champion` | Vault Champion | God Axe, Champion armor set (helmet, chestplate, leggings, boots) |
| `masucraftfixes.patron.legend` | Vault Legend | No additional models (includes all previous tiers) |

**Additional patron features:**
- `/donatordisplay` command for emblem, colour, and display tier settings
- Patron emblem and colour enabled by default when tier is granted
- Display tier can be set to any tier at or below the granted tier

## Developer Transmogs

Gated by the `IskalliaDevs.isDeveloper` check. The mixin extends this check to also accept a LuckPerms permission.

| Permission Node | Models |
|---|---|
| `masucraftfixes.developer` | Developer Shield, Developer Pyjamas, Pink Developer Pyjamas, Developer Ducky (axe), I Love Java Mug (focus) |

**Note:** This permission also grants all patron tiers and bypasses all reward checks (via `PatreonManager` and `Reward.hasModel` developer checks).

## Reward Transmogs

Normally locked behind the external `rewards.vaulthunters.gg` API. The `RewardMixin` hooks into `Reward.hasModel()` to check LuckPerms permissions when the API check fails.

Use `masucraftfixes.rewards.*` as a wildcard to grant all reward transmogs.

### Forsaken Set

**Permission:** `masucraftfixes.rewards.forsaken`

| Type | Name | Model ID |
|---|---|---|
| Armor | Forsaken | `gear/armor/forsaken` |
| Axe | Forsaken Scythe | `gear/axe/forsaken` |
| Sword | Forsaken Sword | `gear/sword/forsaken` |

### Spring Set

**Permission:** `masucraftfixes.rewards.spring`

| Type | Name | Model ID |
|---|---|---|
| Armor | Flowery Madness | `gear/armor/flowery_madness` |
| Axe | Spring Axe | `gear/axe/spring_axe` |
| Axe | Petal Splitter | `gear/axe/petal_splitter` |
| Sword | Spring Sword | `gear/sword/spring_sword` |
| Shield | Spring Shield | `gear/shield/spring_shield` |
| Focus | Spring Focus | `gear/focus/spring_focus` |
| Wand | Spring Wand | `gear/wand/spring_wand` |
| Magnet | Spring Magnet | `gear/magnets/spring_magnet` |

### Christmas Set

**Permission:** `masucraftfixes.rewards.christmas`

| Type | Name | Model ID |
|---|---|---|
| Armor | Green Christmas Hat | `gear/armor/green_christmas_hat` |
| Armor | Red Christmas Hat | `gear/armor/red_christmas_hat` |
| Armor | Snowman | `gear/armor/snowman` |
| Armor | Gingerbread | `gear/armor/gingerbread` |
| Shield | Christmas Fireplace | `gear/shield/fireplace` |
| Sword | Lost Christmas | `gear/sword/lost_christmas` |
| Axe | Lost Christmas | `gear/axe/lost_christmas_axe` |
| Wand | Nutcracker Wand | `gear/wand/nutcracker_wand` |

### Red Dragon Set

**Permission:** `masucraftfixes.rewards.reddragon`

| Type | Name | Model ID |
|---|---|---|
| Axe | Red Dragon | `gear/axe/reddragon_axe` |
| Sword | Red Dragon | `gear/sword/reddragon_sword` |

### Dylan's Set

**Permission:** `masucraftfixes.rewards.dylans`

| Type | Name | Model ID |
|---|---|---|
| Armor | Dylan's Suit | `gear/armor/dylans_suit` |
| Axe | Dylan's Cleaver | `gear/axe/dylans_cleaver` |
| Sword | Dylan's Blade | `gear/sword/dylans_blade` |
| Sword | Dylan's Haunted Dagger | `gear/sword/haunted_dagger` |
| Shield | Dylan's Protector | `gear/shield/dylans_protector` |
| Focus | Dylan's Book | `gear/focus/dylans_book` |
| Wand | Dylan's Magic Stick | `gear/wand/dylans_magic_stick` |

### Competition Rewards

**Permission:** `masucraftfixes.rewards.competition`

| Type | Name | Model ID |
|---|---|---|
| Armor | Royale Crown | `gear/armor/royale_crown` |
| Armor | Five In A Row Guard | `gear/armor/five_in_a_row_guard` |
| Armor | Combat | `gear/armor/combat` |
| Armor | Companion Party Leader | `gear/armor/companion10` |
| Armor | Falcon | `gear/armor/falcon` |

### Special Rewards

**Permission:** `masucraftfixes.rewards.special`

| Type | Name | Model ID |
|---|---|---|
| Armor | Sculk | `gear/armor/sculk` |
| Armor | Retirement Outfit | `gear/armor/retirement` |
| Shield | Golden Kappa | `gear/shield/golden_kappa` |
| Shield | Molten | `gear/shield/molten` |
| Shield | Mattress | `gear/shield/mattress` |
| Shield | Never Ever Shield | `gear/shield/never_ever` |
| Shield | Nocturn Shield | `gear/shield/nocturn` |
| Shield | Lamenting Mirror | `gear/shield/lamenting_mirror` |
| Sword | Twin Blade | `gear/sword/twin_blade` |
| Sword | Buster Blade | `gear/sword/buster_sword` |
| Sword | Jubilant Blade | `gear/sword/jubilant_blade` |
| Sword | Centennial Blade | `gear/sword/centennial_blade` |
| Sword | Doomslasher | `gear/sword/doomslasher` |
| Axe | Pick-axe | `gear/axe/pick_axe` |
| Axe | Arry | `gear/axe/arry` |
| Focus | Ancient Scroll | `gear/focus/ancient_scroll` |
| Focus | Tiny Treasure Train | `gear/focus/tiny_treasure_train` |
| Focus | Essence of Iskallium | `gear/focus/essence_of_iskallium` |
| Wand | Stormcaller | `gear/wand/stormcallers_wand` |
| Wand | Hypefuse Scepter | `gear/wand/hypefuse_scepter` |
| Wand | Hytale | `gear/wand/hytale` |
| Wand | Midnight Thorn | `gear/wand/midnight_thorn` |

## Permission Summary

| Node | Description |
|---|---|
| `masucraftfixes.patron.dweller` | Vault Dweller patron tier + transmogs |
| `masucraftfixes.patron.cheeser` | Vault Cheeser patron tier + transmogs |
| `masucraftfixes.patron.goblin` | Vault Goblin patron tier + transmogs |
| `masucraftfixes.patron.champion` | Vault Champion patron tier + transmogs |
| `masucraftfixes.patron.legend` | Vault Legend patron tier (all lower tier transmogs) |
| `masucraftfixes.developer` | Developer transmogs + all patron tiers + all rewards bypass |
| `masucraftfixes.rewards.forsaken` | Forsaken set (3 items) |
| `masucraftfixes.rewards.spring` | Spring set (8 items) |
| `masucraftfixes.rewards.christmas` | Christmas set (8 items) |
| `masucraftfixes.rewards.reddragon` | Red Dragon set (2 items) |
| `masucraftfixes.rewards.dylans` | Dylan's set (7 items) |
| `masucraftfixes.rewards.competition` | Competition rewards (5 items) |
| `masucraftfixes.rewards.special` | Special/misc rewards (22 items) |
| `masucraftfixes.rewards.*` | All reward transmogs |
