# Projectile Preview
<sub>by Ryhox and Nexor</sub>

**Projectile Preview** is a Fabric client-side mod that shows the predicted flight path of projectiles before you throw or shoot them.

[![Downloads](https://img.shields.io/modrinth/dt/projectile.preview?color=00AF5C&label=downloads&logo=modrinth&style=for-the-badge)](https://modrinth.com/mod/projectile.preview)

---

## Features

- Live trajectory preview for projectiles
- Supports:
  - Bow
  - Crossbow (arrows, multishot, and firework rockets)
  - Trident
  - Snowballs and all three egg variants (white, blue, brown)
  - Ender pearls, splash/lingering potions, experience bottles, wind charges
- Accurate physics-based simulation
- Main-hand and offhand previews
- Client-side only

---

## Compatibility

- Minecraft **26.3**
- Fabric Loader **0.19.5+** required
- Java **25+** required
- Fabric API **0.161.0+26.3** tested
- Mod Menu **21.0.0** optional

---

## ⚠️ Server & Ban Warning

This mod provides a **gameplay advantage** and may be considered a **cheat** on many multiplayer servers.

- ❗ **Using this mod on servers can get you banned**
- ✔️ Intended for **singleplayer** or **servers where it is explicitly allowed**
- Always check server rules before using

You are responsible for how you use this mod.

---

## Installation

1. Install Fabric Loader
2. Install Fabric API
3. Put the mod jar into your `mods` folder
4. Launch the game

---

## Development Status

- Actively developed
- Expect breaking changes
- Code structure may change

---

## License

MIT License

You are free to:
- Use the code
- Modify it
- Copy it
- Include it in your own projects

Just keep the license and copyright notice.

---

## Credits

Developed by **Ryhox**

## Building and testing

```sh
./gradlew build
./gradlew runClientGameTest
```

The client game test launches Minecraft 26.3, compares deterministic projectile
paths with vanilla entities, checks the P configuration shortcut, and captures
screenshots under `build/run/clientGameTest/screenshots/`. Test code is excluded
from release jars. Use `-PcompatModJar=/absolute/path/to/other-mod.jar` to run
with another client mod installed.

The preview shows the mean shot direction; server-side random spread is not
predictable. Crossbow fireworks show their guaranteed flight time (vanilla adds
up to 11 random lifetime ticks). Riptide tridents and melee spears do not launch
a projectile. Minecraft 26.3 adds no new player-thrown projectile item; this
update also fills previous gaps in egg and crossbow ammunition support.

Airborne trajectories and ordinary block/entity hits are covered by the tests.
Water drag, bubble columns, portals, piercing continuation, and special block
interactions are not simulated. The displayed endpoint is the first collision,
not any later ricochet or portal exit.
