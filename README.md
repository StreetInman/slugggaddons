# SlugggAddons

A client-side Fabric mod for Hypixel SkyBlock (formerly *SkyBlock Profile Viewer*). Type
`/pv <player>` to open a window showing that player's stats and gear without leaving the game,
browse every item and recipe with `/sbitems`, and tweak how things look from `/slugggaddons`.
Builds are available for Minecraft **26.1.2, 26.2 and 26.3**.

> **Status: early development (v0.4.0).** It works with your own Hypixel developer key. A
> public release with no key setup needed is planned (see [Roadmap](#roadmap)).

## Features

| Tab | Shows |
|---|---|
| Stats | SkyBlock level, **networth** (hover for a breakdown), magical power, selected power, tuning, active pet, purse, bank, fairy souls, skill average, Catacombs, class average, trophy fish rank, all slayer levels |
| Skills | Every skill with level and progress to the next level |
| Dungeons | Catacombs and class levels, secrets and **secrets per run**, and an **M7 calculator** (below). Page 2 lists every floor, Entrance to F7 and M1 to M7, with runs, **PB, PB S, PB S+** and best score |
| Inv | Armor, equipment (necklace, cloak, belt, gloves), inventory and hotbar |
| Storage | Every ender chest page and backpack, one per page. Flip through with **<** and **>** |
| Wardrobe | Every wardrobe page laid out like the game (one set per column; the set you're wearing is filled in from your armor), then one page per **loadout** |
| Accs | Accessory bag |
| Pets | Every pet with level (to 100, or 200 for Golden, Jade and Rose Dragon), held item and skin; the active pet is marked |
| Trophy | All 18 trophy fish with bronze/silver/gold/diamond counts and your Trophy Hunter rank |
| Misc | Profile age, co-op size, deaths, kills, highest crit, items fished, gifts, essences, HOTM tier and powders, Kuudra tiers, Crimson faction and reputation, Jacob's medals and contests, minions crafted, sacks and more |

Beside the window, the player's skinned model turns to follow your cursor (drag it to spin it), with
level, networth, Catacombs, MP and skill average under it. The tab row spans the full width of the
window.

Search for another player from the box at the top of the window. Items show with their real icons, including custom player-head textures. Hover any item to see its full name and lore. If a player has more than one profile, use
**Profile ▸** to switch between them.

### M7 calculator

The Dungeons tab shows how many S+ Master Mode 7 runs the player needs to reach:

- **Catacombs 50**, hidden once reached.
- **Class average 50**: the total runs, plus how many to play as each class. The plan always
  plays whichever class is furthest from 50. A class marked *passive only* will reach 50 just from the
  team XP it gets while you play the others.

It uses the same model as [adjectils.com](https://adjectils.com/dungeon.html): every run is
S+, and the classes you don't play get a share (25% by default) of their class XP. Boosts are read
from the profile automatically:

- essence-shop class perks (Toxophilite, Unbridled Rage, Heart of Gold, Cold Efficiency, Diamond in the Rough)
- the highest Hecatomb level on any visible item
- Scarf's Studies, Thesis or Grimoire, and the Catacombs Expert Ring in the accessory bag
- the **Catacombs Graduate** (Scarf shard, +2% class XP per level) and **Catacombs Explorer**
  (Bonzo shard, +1% Catacombs XP per level) attributes from the profile's hunting shards
- Derpy as the current mayor

Hover the calculator to see exactly which boosts were found. Boosts the API can't see go in
`config/skyblock-pv.json`: `dungeonGlobalBoostPercent`, `dungeonExtraClassBoostPercent`, and
`dungeonTeamShare`. If the shard levels it shows are wrong, set `dungeonGraduateLevel` and
`dungeonExplorerLevel` (0–10; `-1` means read from the profile).

### Settings (`/slugggaddons`)

`/slugggaddons` (or the old `/skyblockpv`) opens the mod's settings, split into tabs by area of the game: General, GUI, Player,
Animations, Dungeons, Garden, Mining and Fishing. New tweaks land in the tab they belong to.
Everything there is client-side and only changes what you see:

- **GUI**: show or hide the model and summary column in `/pv`, and pick which tab `/pv` opens on.
- **Dungeons**: the M7 calculator's team share, global XP boost, extra class XP, and Graduate and
  Explorer levels (Auto reads them from the profile), and positional messages (below).

- **Player size**: stretch your own player model (optionally everyone's) on each axis: width, height
  and depth, from -3x to 3x. A negative height turns the model upside down. Hitboxes and movement are
  unchanged; only the drawn model is scaled.
- **First-person items**: item size, X/Y/Z position, X/Y/Z rotation, swing animation speed, and
  skipping the dip when you switch items. On 26.1.2 and 26.2 swing speed can be slower or faster;
  on 26.3, where swing timing is tied into combat, only the drawn animation is sped up, so it can only
  be made faster.

### Positional messages (`/posmsg`)

A replacement for Odin's old positional messages. Save a spot with a radius and a party message;
when you walk into it, a clickable line appears in chat, and clicking it puts `/pc <message>` in your
chat box for you to send with Enter. The mod never sends chat by itself.

| Command | What it does |
|---|---|
| `/posmsg add <name> <radius> <message>` | Saves the block you're standing on |
| `/posmsg addat <name> <x> <y> <z> <radius> <message>` | Saves exact coordinates |
| `/posmsg list` | Lists your messages, each with a **[send]** button |
| `/posmsg send <name>` | Opens chat with that message typed in |
| `/posmsg remove <name>` | Deletes one |
| `/posmsg toggle` | Turns the feature on or off |

Messages can include `{x}`, `{y}`, `{z}` (your block position) and `{name}`. `/pm` works as a
shorter alias. The Dungeons settings tab can also open the chat box automatically when you reach a
spot (you still press Enter).

### Networth

Networth is an estimate, in the same style as networth mods and sites. Each item counts at its
lowest BIN or bazaar price, plus enchantments, recombobulator, potato books, gemstones, master
stars, scrolls and similar upgrades. Pets, sacks, essence, purse and bank are included. Dungeon
stars bought with essence aren't counted. Bazaar prices come from the Hypixel API and lowest
BIN prices from [Coflnet](https://sky.coflnet.com) (`lowestBinUrl` in the config), refreshed every
10 minutes.

## Is it allowed on Hypixel?

Hypixel doesn't approve or whitelist mods. Its
[Allowed Modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications)
policy bans mods that give an advantage, automate actions, or change how the client
talks to the server. This mod does none of those:

- It reads the **official public Hypixel API** over HTTPS, the same data sites like SkyCrypt use.
- It only draws its own screen and changes how things look on your screen. It never sends
  packets to the server, never clicks or types for you, and never reveals information the game
  hides. Player size and item animations don't touch hitboxes, attack timing or anything the
  server sees.

As with any mod, Hypixel says all mods are used at your own risk.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and the
   [Fabric API](https://modrinth.com/mod/fabric-api) mod for your Minecraft version.
2. Download the jar for your Minecraft version from
   [Releases](https://github.com/StreetInman/skyblock-pv/releases) (for example
   `slugggaddons-0.4.0+26.1.2.jar`) and put it in your `mods` folder. Remove any older
   `skyblock-pv` jar.
3. Start the game once. The mod creates `config/slugggaddons.json` (copying your old
   `skyblock-pv.json` settings if you had them).
4. *(Development builds only)* Get a key from the [Hypixel Developer Dashboard](https://developer.hypixel.net/)
   and paste it into `apiKey` in that file. Development keys expire after a few days, so you'll need to replace yours from time to time.

## Commands

| Command | What it does |
|---|---|
| `/pv` | Opens your own profile |
| `/pv <player>` | Opens another player's profile |
| `/spv`, `/sbpv` | Same as `/pv`, for when another mod has taken `/pv` |
| `/slugggaddons` (or `/skyblockpv`) | Opens the settings |
| `/posmsg` (or `/pm`) | Positional party messages (above) |
| `/sbitems [search]` (or `/sbrecipe`, `/skyblockpv items`) | Item browser: every SkyBlock item, a page at a time, with search, recipes and "used in" |
| `/pvdump <player>` | Saves the raw API response to `config/slugggaddons/dumps/` (for debugging) |

## Privacy

Looking up a player sends their name to Mojang (to
get their UUID) and the UUID to the Hypixel API. In development builds your API key is
stored only in your local config file.

## Building from source

Requires JDK 25.

```sh
./gradlew build                 # mod jar for the default version → fabric/build/libs/
./gradlew build -Pmc=26.1.2     # or for another version in versions/
./gradlew :fabric:runClient     # launch Minecraft with the mod
./gradlew -PcoreOnly :core:test # data-layer tests only, no Minecraft download
```

The project has two modules:

- **`core/`** is plain Java with no Minecraft code. It holds the API clients, the NBT decoder,
  the profile parser (equipment, storage, trophy fish…) and skill math, and it's unit-tested.
- **`fabric/`** is the mod itself: the `/pv` command, the config file and the GUI.

Version-specific code is kept to one small `Compat` class per version under `fabric/src/compat/`.
See [docs/plan.md](docs/plan.md) for the full design and [docs/publishing.md](docs/publishing.md)
for releases, Modrinth and API keys.

## Roadmap

- [x] `/pv` command, API client, profile parsing, tabs listed above
- [x] Real item icons (via [legacy-item-dfu](https://github.com/AzureAaron/legacy-item-dfu))
- [x] Dungeons tab with M7 calculator, slayers
- [x] Storage tab (ender chest, backpacks)
- [x] Networth, player search, builds for 26.1.2 / 26.2 / 26.3
- [x] Pets tab, Misc tab, floor PBs, player model, tabbed settings, item and recipe browser
- [ ] Loadouts tab (the API exposes them under `member.loadout`)
- [ ] API proxy + production key so players don't need their own key
- [ ] First release on Modrinth

## Credits

- [legacy-item-dfu](https://github.com/AzureAaron/legacy-item-dfu) by AzureAaron (Apache-2.0) converts Hypixel's item data.
- The dungeon XP model follows [adjectils](https://adjectils.com/dungeon.html), as ported in the
  MIT-licensed [rtca-bot-hypixel](https://github.com/BLACKUM/rtca-bot-hypixel).
- Lowest BIN prices for networth come from [Coflnet](https://sky.coflnet.com).
- Item and recipe data for `/sbitems` comes from the [NotEnoughUpdates repository](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), downloaded to `config/slugggaddons/neu-repo.zip` and refreshed every 3 days.

## License

MIT. Not affiliated with or endorsed by Hypixel or Mojang.
