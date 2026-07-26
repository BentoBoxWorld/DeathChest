# DeathChest

A death chest addon for [BentoBox](https://bentobox.world) that works in void worlds.

## The problem

Generic death chest plugins assume the player dies somewhere a chest can be placed and later
reached. On a skyblock-style server neither assumption holds:

* **The void.** A player who falls off their island dies at Y -100 in empty space. There is no
  block to place a grave on, so the plugin either drops the chest into the void, gives up, or
  places it at bedrock level where nobody can get to it.
* **Other people's islands.** A player who dies while visiting cannot build there, cannot break
  the chest, and often cannot open it either. The items are effectively gone.
* **The wild.** The space between islands is not protected and not reachable without flying.

Which is why the usual advice ends up being "just turn on keepInventory" - and with that, dying
stops mattering at all.

## What this addon does

DeathChest decides *where* the chest goes using BentoBox's island data:

1. **The death site**, if the player died inside the *protected* part of an island they belong
   to **and** there is reachable ground within the search depth. This is the normal case - you
   died on your island, your stuff is where you died.
2. **Your own island** otherwise. Void deaths, lava deaths, deaths on someone else's island and
   deaths in the wild all put the chest next to your island home, where you respawn anyway.
3. **Held for you** if you have no island at all. The items sit in the addon's database and you
   get them back with `/<gamemode> deathchest claim 1`.

The "reachable ground required at the death site" rule is the important one. A spot only counts
if it has something solid under it, is within `chest.search-depth` blocks below the player, and
is not submerged. Failing any of those is exactly what routes a chest to your island rather than
leaving it hanging in the void, resting on the sea bed, or buried in terrain a hundred blocks
down.

Everything else is ordinary death chest behaviour: a timer, experience storage, protection from
other players and from explosions, and a command to list where your chests are.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/<gamemode> deathchest` | `[gamemode].deathchest` | List your death chests, with location and time left |
| `/<gamemode> deathchest claim <n>` | `[gamemode].deathchest` | Take the items the addon is holding for chest `n` |
| `/<gamemode> deathchest tp <n>` | `[gamemode].deathchest.teleport` | Teleport to chest `n` |
| `/<gamemode>admin deathchest` | `[gamemode].admin.deathchest` | How many chests are stored |
| `/<gamemode>admin deathchest <player>` | `[gamemode].admin.deathchest` | List a player's chests |
| `/<gamemode>admin deathchest purge` | `[gamemode].admin.deathchest` | Expire everything that is past due now |

Aliases: `deaths`, `grave`.

## Configuration

See `config.yml`. The settings worth knowing about:

| Setting | Default | Notes |
|---|---|---|
| `chest.material` | `CHEST` | `BARREL` opens even with a block above it, which suits cramped islands |
| `chest.place-at-death-location` | `true` | Set false to always send chests to the island home |
| `chest.search-radius` | `8` | How far to look sideways for a free block |
| `chest.search-depth` | `16` | How far to look down for ground. Beyond this the chest goes to the island |
| `chest.expiry-minutes` | `60` | `0` never expires |
| `chest.expiry-action` | `DROP` | `DROP` spills the contents, `DELETE` destroys them |
| `chest.max-per-player` | `3` | Older chests expire early past this, `0` is unlimited |
| `chest.team-access` | `true` | Island team members can open each other's chests |
| `experience.percent` | `100` | Lower it to keep some cost in dying |

## How items are stored

The chest block holds the items. A player's inventory (41 slots including armour and offhand)
does not fit in one chest (27 slots), so anything that does not fit is kept in the addon's
database and moved into the chest automatically each time the player closes it. Empty the chest
and it disappears along with its record.

If the chest block goes away for a reason the addon did not cause - a world edit, a rollback -
the record survives and turns into a held chest that can be claimed with the command.

## Building

Requires JDK 25 to build (Paper 26.1.2 and BentoBox 3.18.1 ship Java 25 bytecode); the addon's
own sources are compiled at release 21.

```bash
mvn clean package
```

The jar lands in `target/`.

## Translations

Ships with 19 locales: `en-US` plus `cs`, `de`, `es`, `fr`, `hr`, `hu`, `id`, `it`, `ja`, `ko`, `lv`,
`pl`, `ro`, `ru`, `tr`, `uk`, `vi`, `zh-CN`. The non-English files were machine translated and have not
yet been reviewed by native speakers - corrections are very welcome.

## Not in this version

Deliberately left out of the first cut, in rough order of how likely they are to be wanted:

* Holograms over the chest showing the owner and countdown.
* A GUI panel instead of a chat list for `/deathchest`.
* A `DEATH_CHEST_ACCESS` island protection flag, so owners can choose who may loot their graves.
* PvP looting rules - letting the killer open the chest.
* Hopper protection. A hopper under a death chest will drain it.
* Per-permission chest limits and expiry times.
