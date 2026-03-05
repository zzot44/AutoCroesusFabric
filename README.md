# AutoCroesus (Fabric 1.21.11)

![Downloads](https://img.shields.io/github/downloads/zzot44/AutoCroesusFabric/total)

A Fabric client mod that auto-loots Croesus dungeon chests based on profit.

This is a port/rebuild of the original AutoCroesus idea for ChatTriggers.

## Credits
- **Original concept + core logic inspiration:** **UnclaimedBloom6**
- **Fabric port + maintenance:** this repo (zzot on dc for bugs or suggestions write me a dm) 

Without UnclaimedBloom6 this mod would not exist in this form.
Neither would it exist without CodeX.

Also: **no polar bears were harmed in the making of this port**.

## What It Does
When you run `/ac go`, the mod:
1. Finds and clicks Croesus in Dungeon Hub.
2. Opens each unclaimed run (across pages).
3. Reads chest rewards + costs.
4. Converts rewards to SkyBlock item IDs.
5. Prices each chest from live APIs.
6. Picks the best chest profit (and optionally second chest with key).
7. Handles optional Kismet rerolls for configured floors.
8. Logs claimed loot for later stats via `/ac loot`.

## Feature List
- Auto-claim across **all Croesus pages** until finished.
- Profit evaluation using:
  - Hypixel Bazaar API
  - Hypixel SkyBlock Items API
  - Moulberry lowest BIN API
- **Always Buy** list (force-open if item is present).
- **Worthless** list (set item value to 0 to avoid manipulation bait).
- Configurable:
  - click delay
  - first-click delay per GUI
  - chest key minimum profit
  - kismet minimum profit
  - kismet floors
- Kismet behavior:
  - rerolls Bedrock when below threshold
  - skips reroll floors if no Kismets are available
- Loot logging + filters (`floor`, `score`, `limit`).
- Overlay for chest profits and unclaimed run highlights.
- Kill-switch by holding `Shift` or `Esc`.

## Install
1. Install Fabric Loader for **Minecraft 1.21.11**.
2. Put the built jar in your `mods` folder.
3. Launch Minecraft.

## Quick Start
1. Stand near Croesus in Dungeon Hub.
2. Run `/ac api` once (optional, fetches prices now).
3. Run `/ac go`.
4. Watch it process until all runs are looted.

## Commands
Main aliases: `/autocroesus` and `/ac`

- `/ac go` -> start with API freshness check
- `/ac forcego` -> start immediately
- `/ac api` -> refresh API data
- `/ac settings` -> print current settings
- `/ac overlay` -> toggle overlay
- `/ac delay <ms>` -> set min click delay
- `/ac firstclickdelay <ms>` -> set first click delay per GUI
- `/ac kismet` -> toggle kismets
- `/ac kismet <F1..F7|M1..M7|min_profit>` -> add/remove floor or set threshold
- `/ac key` -> toggle chest keys
- `/ac key <min_profit>` -> set chest-key threshold
- `/ac alwaysbuy` -> show list
- `/ac alwaysbuy <ITEM_ID>` -> toggle item
- `/ac alwaysbuy reset` -> reset defaults
- `/ac worthless` -> show list
- `/ac worthless <ITEM_ID>` -> toggle item
- `/ac worthless reset` -> reset defaults
- `/ac loot help` -> usage help
- `/ac loot floor:F7 limit:100 score:300` -> filtered loot summary
- `/ac reset` -> hard-reset internal state
- `/ac noclick` -> dry-run click mode

## Config + Data Files
Created automatically in `config/autocroesus/`:
- `config.json`
- `always_buy.txt`
- `worthless.txt`
- `bzValues.json`
- `items.json`
- `binValues.json`
- `runLoot.txt`

## Build From Source
Requires **Java 21**.

### Windows
```bat
gradlew.bat build
```

### Linux/macOS
```bash
./gradlew build
```

Build output:
- `build/libs/autocroesus-<version>.jar`

## Safety / Risk
This mod automates GUI interactions. Use at your own risk on multiplayer servers.





