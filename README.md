# MvDevsUnion BetterSMP

A server-side Fabric mod for Minecraft 1.21.11 that gives admins control over key gameplay restrictions on their SMP server. All features are enabled by default and can be toggled live without restarting the server.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `>=0.18.4`
- Fabric API `0.141.3+1.21.11`
- Java `21`

## Features

| Feature | Default | Description |
|---|---|---|
| Disable Sleep | ON | Players cannot sleep in beds |
| Disable Nether | ON | Players cannot enter or activate the Nether |
| Disable End | ON | Players cannot enter the End or craft Eyes of Ender |
| Disable Diamonds | ON | Diamond ore is replaced with red wool as it loads |
| Disable Villager Interaction | ON | Players cannot right-click or trade with villagers |
| Disable Mending | ON | Mending enchanted books are deleted from player inventories |

## Commands

All commands require operator level 2.

| Command | Description |
|---|---|
| `/bettersmp status` | Shows the current on/off state of every feature |
| `/bettersmp enable <feature>` | Enables a feature for players (turns restriction off) |
| `/bettersmp disable <feature>` | Disables a feature for players (turns restriction on) |
| `/bettersmp reload` | Reloads the config file from disk |

### Feature names for commands

```
sleep
nether
end
diamonds
villagerInteraction
mending
```

### Examples

```
/bettersmp enable nether
/bettersmp disable sleep
/bettersmp status
/bettersmp reload
```

## Configuration

The config file is created automatically on first launch at:

```
config/mvdevsunionbettersmp.json
```

Default config:

```json
{
  "disableSleep": true,
  "disableNether": true,
  "disableEnd": true,
  "disableDiamonds": true,
  "disableVillagerInteraction": true,
  "disableMending": true
}
```

You can edit this file manually and run `/bettersmp reload` to apply changes, or use the `/bettersmp enable` and `/bettersmp disable` commands which save to the file automatically.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods` folder
3. Place the mod `.jar` in your `mods` folder
4. Start the server — the config file will be generated automatically

## Building from Source

```bash
./gradlew build
```

The compiled jar will be in `build/libs/`.
