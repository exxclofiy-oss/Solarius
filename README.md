# Solarius — Paper 1.21.11 (prototype)

This is a **source project**, not a precompiled plugin JAR. Build with Java 21 and Maven:

    mvn clean package

Copy `target/solarius-1.0.0.jar` into your server's `plugins/` folder and restart. Use `/solarius give <player>` as OP to obtain the Solarius sword.

Controls while holding the sword: Sneak + left-click = Duelist; Sneak + right-click = beam; sneak and right-click within 400 ms of starting to sneak = Sunfall. The latter timing is a **prototype shortcut**, not a reliable double-sneak detection. Change the bindings in code if desired.

## Important limitations
- Not compiled or tested against Paper 1.21.11 in this environment.
- Golden hands are basic gold-block display segments; swords are vanilla golden swords. **No Blockbench models, custom resource pack, finger rigging, or actual sword-swing animations are included.**
- Duelist damage is server-driven with particles, not hitboxes tracking physical blade contact.
- Solar energy and cooldowns are in-memory only; they reset on restart.
- Protection-plugin region flags (WorldGuard etc.) are not integrated; test PvP protection carefully.
- Combat charge is rate-limited globally per player; friendly-fire and team protection handling is basic.
- Requires an actual build and server testing before production use.

All core settings are in `config.yml`. This is a standalone plugin; it does not modify or depend on the existing PlattSMP JAR.
