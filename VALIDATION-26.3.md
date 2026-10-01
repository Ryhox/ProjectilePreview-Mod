# Minecraft 26.3 validation — 1 October 2026

The release jars are version `1.1.0+26.3`, built with Loom 1.17.16,
Gradle 9.6.1, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3,
and optional Mod Menu 21.0.0. They target Java 25.

## Research

- [Fabric 26.3 porting notes](https://www.fabricmc.net/2026/09/15/263.html): SDL input migration and development dependencies.
- [Mojang 26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3): new content and transparency changes.
- [Mojang spear behavior](https://www.minecraft.net/en-us/article/unveiling-mounts-of-mayhem): jab/charge melee attacks.
- Vanilla 26.3 source and item assets were inspected through Loom. Comparing the 26.2/26.3 item assets showed no new player-thrown projectile item. Blue and brown eggs were previously missing from this mod; they are newly supported here, not newly introduced in 26.3.

## Verification

Both projects passed `build` and real `runClientGameTest` sessions,
including runs with the other release jar and Mod Menu installed together.
The final combined test runs passed on Temurin Java 25.0.2; earlier runs also
passed on OpenJDK 26.
Game tests run in a disposable creative world; test classes are excluded from
the release jars. Packaged Minecraft/Loader/Java requirements and heart texture
paths were checked. `git diff --check` passed in both repositories.

Projectile Preview tests compare actual vanilla entities with the predicted
spawn, launch velocity, and tick positions. Eggs (all variants), snowballs,
experience bottles, splash/lingering potions, bow arrows, tridents, and crossbow
fireworks use zero uncertainty for reproducibility. The checks require spawn
and velocity error below 0.000001 blocks and flight error below 0.00001 blocks
for the sampled ticks. Loaded projectile counts and Riptide rejection are
asserted. These are sampled deterministic air trajectories, not a proof of
all gameplay situations.

Visual inspection covered 17 Projectile Preview screenshots and 9 Health
Indicators screenshots at 1280 × 720. Flight paths, multishot, crossbow rockets,
offhand origin, block/entity highlights, impact markers, configuration screens,
bars, half/multiple-row hearts, numeric values, player poison/wither/absorption,
health offset, F1 hiding, and improved transparency were inspected.

## Local evidence

- [Screenshots](build/validation-26.3/screenshots/)
- [Client test log](build/validation-26.3/client-test.log)

Evidence is kept under `build/validation-26.3` so another client-test run does
not erase this capture set. This directory is local build output, not committed
source. Reproduce with `./gradlew runClientGameTest`; include another mod with
`-PcompatModJar=/absolute/path/to/mod.jar`.

## Practical limits

Projectile previews exclude unpredictable server shot spread and show only the
first collision. Fireworks show the guaranteed portion of their random lifetime.
Water drag, bubble columns, portals, piercing continuation, and special block
effects are not simulated. Vanilla does not supply detailed effects/absorption
for every remote entity. Arbitrary third-party modpacks and multiplayer servers
were not tested.
