# Solarius FINAL — Paper 1.21.11 (Sunflare Broadblade)

This project includes the latest **300-cuboid Sunflare Broadblade**, the two custom golden skeletal hands, the solar orb, and the full Solarius 4.0 plugin source. The sword, display entities and item all reference `solarius:solar_sword`—the matching resource pack is bundled.

## Easy install (when you already have a working Solarius 4.0 JAR)

You do **not** need to recompile just to change the sword appearance. Keep the Solarius 4.0 JAR in `plugins/`, and replace the previous client resource pack with `Solarius-ResourcePack-1.21.11.zip`. Enable the ZIP in Minecraft Java **Options → Resource Packs**, preferably with any older Solarius packs removed. Rejoin or press `F3 + T` to reload resources. Every player needs the resource pack to see the custom sword and hands.

## Fresh install / build the matching plugin

1. Extract this PROJECT ZIP on your computer.
2. Upload its **contents** (not the ZIP itself) to the root of https://github.com/exxclofiy-oss/Solarius. **Keep folder structure**; replace old files and especially `.github/workflows/blank.yml`.
3. In GitHub, go to **Actions → Build Solarius Final → Run workflow** on the `main` branch. A green check means the JAR **compiled** (not that in-game behavior has been tested).
4. Open the green run, find **Artifacts**, and download **Solarius-INSTALL-THIS**. Unzip it.
5. Put **`Solarius-4.0.0.jar`** in the **server** `plugins/` folder. Remove older `Solarius-*.jar` files first. Restart your **Paper 1.21.11** server.
6. Put **`Solarius-ResourcePack-1.21.11.zip`** (leave zipped) in your **Minecraft client's** `resourcepacks/` folder, enable it in Options → Resource Packs, and reconnect. Do NOT place the resource pack in `plugins/`.
7. In game, OP yourself through the server console (if needed), run `/plugins`, then `/solarius give YOUR_USERNAME`, `/solarius energy` and `/cooldown`.

## Commands

- `/solarius help` — controls and commands
- `/solarius give <player>` — gives the Solarius sword (OP)
- `/solarius energy` — your energy
- `/cooldown` or `/solarius cooldown` — your Beam, Duelist and Sunfall cooldowns
- `/cooldown <player>` — check another player's cooldowns (OP)
- `/cooldown reset <player> [all|beam|duelist|sunfall]` — reset cooldowns (OP)
- `/solarius setenergy <player> <0-100>` — set energy (OP)
- `/solarius reload` — reload configuration (OP)

**Controls when holding the custom sword:** Sneak + right-click = Beam (35 energy); Sneak + left-click = Duelist (60 energy, 20 seconds); Sneak + F = Sunfall (100 energy). 

## Source / validation

- Java 21; Paper API `1.21.11-R0.1-SNAPSHOT`; run `mvn clean package` to build manually.
- `models/blockbench/solar_sword.bbmodel` is the latest editable sword model; left/right hand and orb models are in the same folder.
- `resource-pack/` contains the unpacked Minecraft resource pack; `Solarius-ResourcePack-1.21.11.zip` is the same set of assets, ready to install.
- `tests/validate_assets.py` verifies 1.21.11 pack format, model IDs, required textures, element counts, model bounds, and archives. It is **not** a substitute for a build or an in-game test.
- This is a **standalone plugin**, not a modification of the existing PlattSMP JAR. If you need the abilities embedded into your existing proprietary plugin, its source code would be required.
- Detailed finger articulation and actual in-game behavior still require testing. Your Minecraft client needs the matching resource pack.
