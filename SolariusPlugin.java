package com.plattsmp.solarius;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Solarius for Paper 1.21.11: standalone solar-combat plugin.
 * The display entities use the item_model component and require the included resource pack.
 * All gameplay tasks and entity operations run synchronously on the server thread.
 */
public final class SolariusPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int MAX_ENERGY = 100;
    private static final long HIT_CHARGE_INTERVAL_MS = 500;
    private final Map<UUID, Integer> energy = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastHitCharge = new HashMap<>();
    private final Map<UUID, Long> lastDamageCharge = new HashMap<>();
    private final Map<UUID, UUID> focusTarget = new HashMap<>();
    private final Map<UUID, Long> focusTime = new HashMap<>();
    private final Map<UUID, Guardian> guardians = new HashMap<>();
    private final Set<UUID> abilityDamageSources = new HashSet<>();
    private final Set<UUID> confirmedAbilityHits = new HashSet<>();
    private NamespacedKey swordKey;
    private NamespacedKey displayKey;
    private File playerDataFile;
    private YamlConfiguration playerData;
    private long serverTicks = 0;

    @Override public void onEnable() {
        saveDefaultConfig();
        swordKey = new NamespacedKey(this, "solarius_weapon");
        displayKey = new NamespacedKey(this, "solarius_display");
        playerDataFile = new File(getDataFolder(), "players.yml");
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        loadPlayerData();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String name : List.of("solarius", "cooldown")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name));
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        // Remove displays left by a crash, /reload or older Solarius versions (loaded chunks only).
        for (World world : Bukkit.getWorlds()) for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) removeIfOrphan(display);
        Bukkit.getScheduler().runTaskTimer(this, this::tickEnergy, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            serverTicks += 2;
            for (Guardian g : new ArrayList<>(guardians.values())) g.tick();
        }, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, this::savePlayerData, 2400L, 2400L);
        getLogger().info("Solarius 4.0 enabled. Install the Solarius-ResourcePack ZIP on the client for 3D models.");
    }
    @Override public void onDisable() {
        for (Guardian g : new ArrayList<>(guardians.values())) g.remove();
        guardians.clear();
        // Remove any orphaned miniature sun displays after cancellation/reload.
        for (World world : Bukkit.getWorlds())
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) removeIfOrphan(display);
        savePlayerData();
    }

    private int cfg(String path, int fallback) { return getConfig().getInt(path, fallback); }
    private double cfgd(String path, double fallback) { return getConfig().getDouble(path, fallback); }
    private boolean flag(String path, boolean fallback) { return getConfig().getBoolean(path, fallback); }
    private boolean holdsSword(Player player) { return isSword(player.getInventory().getItemInMainHand()); }
    private boolean ownsSword(Player player) {
        if (holdsSword(player)) return true;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (isSword(stack)) return true;
        return false;
    }
    private boolean isSword(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }
    private ItemStack modelItem(Material material, String model) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("solarius", model));
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack makeSword() {
        ItemStack sword = modelItem(Material.GOLDEN_SWORD, "solar_sword");
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("☀ Solarius", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Solar Obliteration · Sneak + Right Click", NamedTextColor.YELLOW),
                Component.text("Duelist · Sneak + Left Click", NamedTextColor.YELLOW),
                Component.text("Sunfall · Sneak + Swap Hand (F)", NamedTextColor.YELLOW)));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(meta);
        return sword;
    }
    private int getEnergy(Player p) { return energy.getOrDefault(p.getUniqueId(), 0); }
    private void addEnergy(Player p, int amount) { energy.put(p.getUniqueId(), Math.clamp(getEnergy(p) + amount, 0, MAX_ENERGY)); }

    private boolean sunlit(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL || w.hasStorm()) return false;
        long time = w.getTime();
        if (time >= 12300) return false;
        Location eyes = p.getEyeLocation();
        // Highest obstructing block must be below the player's eyes; glass is intentionally treated as cover.
        return w.getHighestBlockYAt(eyes) < eyes.getBlockY();
    }
    private void tickEnergy() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!ownsSword(p)) continue;
            if (sunlit(p)) addEnergy(p, cfg("energy.sunlight-per-second", 2));
            if (flag("ui.action-bar", true)) {
                int value = getEnergy(p), segments = value / 10;
                String bar = "▰".repeat(segments) + "▱".repeat(10 - segments);
                p.sendActionBar(Component.text("☀ SOLARIUS  " + bar + "  " + value + "/100", NamedTextColor.GOLD));
            }
        }
    }
    private boolean pay(Player p, String ability, int amount, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(ability, 0L);
        if (readyAt > now) {
            p.sendMessage("§6☀ §e" + ability + " cooldown: " + ((readyAt - now + 999) / 1000) + "s");
            return false;
        }
        if (getEnergy(p) < amount) {
            p.sendMessage("§c☀ Need " + amount + " Solar Energy (you have " + getEnergy(p) + ").");
            return false;
        }
        addEnergy(p, -amount);
        cooldowns.computeIfAbsent(p.getUniqueId(), id -> new HashMap<>()).put(ability, now + cooldownSeconds * 1000L);
        return true;
    }
    private void chargeCombat(Player p, int amount, Map<UUID, Long> gate) {
        long now = System.currentTimeMillis();
        if (now - gate.getOrDefault(p.getUniqueId(), 0L) < HIT_CHARGE_INTERVAL_MS) return;
        gate.put(p.getUniqueId(), now);
        addEnergy(p, amount);
    }

    // Apply PvP mitigation before the damage event finishes, without disturbing cancelled hits.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Guardian guardian = guardians.get(player.getUniqueId());
        if (guardian != null && !guardian.expired()) {
            double reduction = Math.clamp(cfgd("abilities.duelist.damage-reduction", .15), 0, 1);
            event.setDamage(event.getDamage() * (1 - reduction));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (abilityDamageSources.contains(attacker.getUniqueId())) confirmedAbilityHits.add(event.getEntity().getUniqueId());
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!abilityDamageSources.contains(attacker.getUniqueId()) && ownsSword(attacker)) {
            chargeCombat(attacker, cfg("energy.hit", 8), lastHitCharge);
        }
        if (ownsSword(victim)) chargeCombat(victim, cfg("energy.damaged", 4), lastDamageCharge);
        focusTarget.put(attacker.getUniqueId(), victim.getUniqueId());
        focusTarget.put(victim.getUniqueId(), attacker.getUniqueId());
        focusTime.put(attacker.getUniqueId(), System.currentTimeMillis());
        focusTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (!holdsSword(p) || !p.isSneaking()) return;
        Action a = event.getAction();
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        switch (a) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { event.setCancelled(true); beam(p); }
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> { event.setCancelled(true); duelist(p); }
            default -> { }
        }
    }
    // More reliable than the old double-sneak timing (which could accidentally cast Sunfall instead of beam).
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (p.isSneaking() && holdsSword(p)) { event.setCancelled(true); sunfall(p); }
    }

    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(255, 189, 34), 1.6f);
    private static final Particle.DustOptions LIGHT = new Particle.DustOptions(Color.fromRGB(255, 242, 117), 1.1f);
    private void beam(Player caster) {
        if (!pay(caster, "beam", cfg("abilities.beam.cost", 35), cfg("abilities.beam.cooldown-seconds", 12))) return;
        World world = caster.getWorld();
        Location origin = caster.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        double distance = cfgd("abilities.beam.range", 25);
        world.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.1f, 1.5f);
        world.spawnParticle(Particle.FLASH, origin.clone().add(direction), 1);
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(origin, distance, distance, distance))
            if (e instanceof LivingEntity living && canAttack(caster, living)) candidates.add(living);
        Location impact = origin.clone().add(direction.clone().multiply(distance));
        LivingEntity hit = null;
        for (double step = 0; step <= distance; step += .35) {
            Location pos = origin.clone().add(direction.clone().multiply(step));
            if (pos.getBlock().getType().isSolid()) { impact = pos; break; }
            world.spawnParticle(Particle.DUST, pos, 2, .055, .055, .055, 0, DUST);
            if (((int) (step * 10)) % 7 < 3) world.spawnParticle(Particle.END_ROD, pos, 1, .055, .055, .055, 0);
            for (LivingEntity target : candidates) {
                if (target.getBoundingBox().expand(.3).contains(pos.toVector())) {
                    impact = pos; hit = target; break;
                }
            }
            if (hit != null) break;
        }
        world.spawnParticle(Particle.DUST, impact, 35, .35, .35, .35, 0, LIGHT);
        world.spawnParticle(Particle.FLASH, impact, 1);
        world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, .9f, 1.45f);
        if (hit != null && canAttack(caster, hit)) {
            if (applyAbilityDamage(caster, hit, cfgd("abilities.beam.damage-health", 7))) {
                Vector push = direction.clone().multiply(.35); push.setY(Math.max(.17, push.getY()));
                hit.setVelocity(hit.getVelocity().add(push));
            }
        }
    }
    private void duelist(Player p) {
        if (guardians.containsKey(p.getUniqueId())) { p.sendMessage("§eDuelist is already active."); return; }
        if (!pay(p, "duelist", cfg("abilities.duelist.cost", 60), cfg("abilities.duelist.cooldown-seconds", 45))) return;
        Guardian g = new Guardian(p);
        guardians.put(p.getUniqueId(), g);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        p.sendMessage("§6☀ §eDuelist summoned for " + cfg("abilities.duelist.duration-seconds", 20) + " seconds.");
    }
    private void sunfall(Player caster) {
        if (!pay(caster, "sunfall", cfg("abilities.sunfall.cost", 100), cfg("abilities.sunfall.cooldown-seconds", 90))) return;
        Location center = caster.getLocation().clone();
        World world = center.getWorld();
        ItemDisplay orb = createDisplay(center.clone().add(0, 3.2, 0), modelItem(Material.STICK, "sun_orb"));
        orb.setTransformation(transform(.15f, 0, 0, 0));
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, .7f);
        for (int step = 0; step < 10; step++) {
            final int frame = step;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!orb.isValid()) return;
                if (!caster.isOnline() || caster.isDead() || caster.getWorld() != world) { orb.remove(); return; }
                float scale = .15f + frame * .12f;
                orb.setTransformation(transform(scale, 0, frame * .3f, 0));
                Location at = center.clone().add(0, 3.2, 0);
                world.spawnParticle(Particle.DUST, at, 25, .6, .55, .6, 0, DUST);
                world.spawnParticle(Particle.END_ROD, at, 8, .3, .4, .3, .03);
                if (frame == 9) {
                    orb.remove();
                    explode(caster, center);
                }
            }, (step + 1) * 4L);
        }
    }
    private void explode(Player caster, Location center) {
        World world = center.getWorld();
        double radius = cfgd("abilities.sunfall.radius", 7);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .65f);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 3);
        for (int ring = 0; ring < 3; ring++) {
            double r = radius * (ring + 1) / 3;
            for (int i = 0; i < 85; i++) {
                double angle = 2 * Math.PI * i / 85;
                Location l = center.clone().add(Math.cos(angle) * r, .2 + .17 * ring, Math.sin(angle) * r);
                world.spawnParticle(Particle.DUST, l, 2, 0, 0, 0, 0, ring == 2 ? LIGHT : DUST);
            }
        }
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || !canAttack(caster, target)) continue;
            double dist = entity.getLocation().distance(center);
            if (dist > radius) continue;
            double hearts = dist <= 2 ? 5 : dist <= 4 ? 4 : dist <= 6 ? 3 : 2;
            if (applyAbilityDamage(caster, target, hearts * 2)) {
                Vector push = target.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() > .001) {
                    push.normalize().multiply(.8); push.setY(.4);
                    target.setVelocity(target.getVelocity().add(push));
                }
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            }
        }
    }
    private boolean applyAbilityDamage(Player owner, LivingEntity target, double damage) {
        confirmedAbilityHits.remove(target.getUniqueId());
        abilityDamageSources.add(owner.getUniqueId());
        try { target.damage(Math.max(0, damage), owner); }
        finally { abilityDamageSources.remove(owner.getUniqueId()); }
        return confirmedAbilityHits.remove(target.getUniqueId());
    }
    private boolean canAttack(Player owner, LivingEntity victim) {
        if (victim == owner || !victim.isValid() || victim.isDead() || victim instanceof ArmorStand) return false;
        if (victim.getWorld() != owner.getWorld()) return false;
        if (victim instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
            if (!owner.getWorld().getPVP()) return false;
            org.bukkit.scoreboard.Team team = owner.getScoreboard().getEntryTeam(owner.getName());
            return team == null || !team.hasEntry(player.getName()) || team.allowFriendlyFire();
        }
        // Duelist must not attack animals, villagers, tameable pets or decoration.
        return victim instanceof Monster || victim instanceof Slime || victim instanceof Phantom;
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { stopGuardian(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { stopGuardian(e.getEntity()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { stopGuardian(e.getPlayer()); }
    @EventHandler public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        for (Entity e : event.getChunk().getEntities()) if (e instanceof ItemDisplay display) removeIfOrphan(display);
    }
    private void stopGuardian(Player player) {
        Guardian removed = guardians.remove(player.getUniqueId());
        if (removed != null) removed.remove();
    }
    private ItemDisplay createDisplay(Location at, ItemStack model) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class);
        display.setItemStack(model);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setTeleportDuration(2);
        display.setInterpolationDuration(2);
        display.setInterpolationDelay(0);
        display.setViewRange(1.5f);
        display.setPersistent(false); // Do not retain constructs across server restarts.
        display.setShadowRadius(0);
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
        return display;
    }
    private void removeIfOrphan(ItemDisplay display) {
        if (display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
            // Displays belonging to an active instance are not orphans.
            for (Guardian guardian : guardians.values()) if (guardian.includes(display)) return;
            display.remove();
        }
    }
    private Transformation transform(float scale, float rotX, float rotY, float rotZ) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateXYZ(rotX, rotY, rotZ),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private final class Guardian {
        final UUID playerId;
        final ItemDisplay[] hands = new ItemDisplay[2];
        final ItemDisplay[] blades = new ItemDisplay[2];
        final long expiresAt;
        final World world;
        int age = 0;
        int strikeSide = -1;
        int strikeAge = -1;
        int lastStrike = -20;
        int lastSpecial = 0;
        LivingEntity victim;
        boolean special = false;
        boolean dealt = false;

        Guardian(Player p) {
            this.playerId = p.getUniqueId();
            this.world = p.getWorld();
            this.expiresAt = System.currentTimeMillis() + cfg("abilities.duelist.duration-seconds", 20) * 1000L;
            for (int s = 0; s < 2; s++) {
                Location pos = p.getLocation().add(s == 0 ? -1 : 1, .9, 0);
                hands[s] = createDisplay(pos, modelItem(Material.STICK, s == 0 ? "skeletal_hand_left" : "skeletal_hand_right"));
                blades[s] = createDisplay(pos, modelItem(Material.GOLDEN_SWORD, "solar_sword"));
                hands[s].setTransformation(transform(.04f, 0, 0, 0));
                blades[s].setTransformation(transform(.04f, 0, 0, 0));
            }
            ring(p.getLocation(), 1.5, 40);
        }
        boolean includes(ItemDisplay display) {
            return Arrays.stream(hands).anyMatch(d -> d.getUniqueId().equals(display.getUniqueId()))
                    || Arrays.stream(blades).anyMatch(d -> d.getUniqueId().equals(display.getUniqueId()));
        }
        boolean expired() { return System.currentTimeMillis() >= expiresAt; }
        void remove() { for (ItemDisplay d : hands) if (d != null && d.isValid()) d.remove();
                        for (ItemDisplay d : blades) if (d != null && d.isValid()) d.remove(); }
        LivingEntity chooseTarget(Player owner) {
            UUID focus = focusTarget.get(owner.getUniqueId());
            if (focus != null && System.currentTimeMillis() - focusTime.getOrDefault(owner.getUniqueId(), 0L) < 6000) {
                Entity e = Bukkit.getEntity(focus);
                if (e instanceof LivingEntity l && canAttack(owner, l) && owner.getLocation().distanceSquared(l.getLocation()) <= Math.pow(cfgd("abilities.duelist.range", 12), 2)
                        && owner.hasLineOfSight(l)) return l;
            }
            LivingEntity closest = null;
            double range = cfgd("abilities.duelist.range", 12);
            double closestDist = range * range;
            for (Entity e : owner.getNearbyEntities(range, range, range)) {
                if (!(e instanceof LivingEntity l) || !canAttack(owner, l) || !owner.hasLineOfSight(l)) continue;
                double d = e.getLocation().distanceSquared(owner.getLocation());
                if (d < closestDist) { closestDist = d; closest = l; }
            }
            return closest;
        }
        void tick() {
            Player owner = Bukkit.getPlayer(playerId);
            if (owner == null || !owner.isOnline() || owner.isDead() || owner.getWorld() != world || expired()) {
                remove(); guardians.remove(playerId); return;
            }
            age += 2;
            double range = cfgd("abilities.duelist.range", 12);
            if (strikeAge < 0 && age - lastStrike >= 20) {
                LivingEntity candidate = chooseTarget(owner);
                if (candidate != null) {
                    victim = candidate;
                    special = age - lastSpecial >= 100;
                    if (special) lastSpecial = age;
                    strikeSide = (strikeSide + 1) % 2;
                    strikeAge = 0;
                    dealt = false;
                    lastStrike = age;
                    world.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, .65f, special ? .75f : 1.6f);
                }
            }
            if (strikeAge >= 0) strikeAge += 2;
            Vector forward = owner.getLocation().getDirection().setY(0);
            if (forward.lengthSquared() < .01) forward = new Vector(0, 0, 1);
            forward.normalize();
            Vector side = new Vector(-forward.getZ(), 0, forward.getX());
            double phase = strikeAge < 0 ? 0 : Math.sin(Math.PI * Math.min(1, strikeAge / 14.0));
            boolean targetValid = victim != null && canAttack(owner, victim)
                    && owner.getLocation().distanceSquared(victim.getLocation()) <= range * range
                    && owner.hasLineOfSight(victim);
            if (strikeAge >= 0 && strikeAge >= 8 && !dealt) {
                dealt = true;
                if (targetValid) {
                    double damage = cfgd(special ? "abilities.duelist.execution-damage-health" : "abilities.duelist.strike-damage-health", special ? 4 : 2);
                    applyAbilityDamage(owner, victim, damage);
                    Location impact = victim.getLocation().add(0, victim.getHeight() * .65, 0);
                    world.spawnParticle(Particle.SWEEP_ATTACK, impact, special ? 3 : 1, .45, .35, .45, 0);
                    world.spawnParticle(Particle.DUST, impact, special ? 38 : 17, .42, .45, .42, 0, DUST);
                }
            }
            for (int s = 0; s < 2; s++) {
                double sideSign = s == 0 ? -1 : 1;
                double bob = Math.sin(age * .13 + s) * .065;
                Location at = owner.getLocation().clone().add(side.clone().multiply(sideSign * 1.1)).add(0, .85 + bob, 0)
                        .subtract(forward.clone().multiply(.42));
                boolean attacking = strikeAge >= 0 && (special || strikeSide == s) && targetValid;
                if (attacking) {
                    // The construct should visibly HUNT its target, not slash empty air
                    // next to the caster while damaging an enemy ten blocks away.
                    // Lunge to striking distance, then retreat along the same path.
                    Vector targetPoint = victim.getLocation().add(0, Math.min(1.25, victim.getHeight() * .6), 0).toVector();
                    Vector toward = targetPoint.subtract(at.toVector());
                    double distanceToTarget = toward.length();
                    if (distanceToTarget > .01) {
                        double approach = Math.min(Math.max(0, distanceToTarget - .7), range);
                        at.add(toward.multiply((approach * phase) / distanceToTarget));
                    }
                }
                at.setYaw(owner.getLocation().getYaw());
                at.setPitch(0);
                if (hands[s].isValid()) {
                    hands[s].teleport(at);
                    float scale = age <= 16 ? Math.max(.05f, (age / 16f)) : 1.0f;
                    float swing = attacking ? (float) (Math.sin(Math.PI * Math.min(1, strikeAge / 14.0)) * sideSign * .35) : 0;
                    hands[s].setTransformation(transform(scale * 1.18f, swing, 0, 0));
                }
                if (blades[s].isValid()) {
                    // The sword handle sits inside the skeletal fingers. Rotate the blade
                    // downward so it extends away from the forearm, not through it.
                    Location bladeAt = at.clone().add(forward.clone().multiply(.12)).add(0, .38, 0);
                    blades[s].teleport(bladeAt);
                    float swing = attacking ? (float) ((s == 0 ? -1 : 1) * Math.sin(Math.PI * Math.min(1, strikeAge / 14.0)) * 1.35) : (float) (sideSign * .26);
                    float scale = age <= 16 ? Math.max(.05f, (age / 16f)) : 1.14f;
                    blades[s].setTransformation(transform(scale, .1f, 0, (float) Math.PI + swing));
                    if (attacking && strikeAge <= 14) world.spawnParticle(Particle.DUST, bladeAt.clone().add(0, .85, 0), 5, .2, .6, .2, 0, LIGHT);
                }
            }
            if (age % 6 == 0) world.spawnParticle(Particle.DUST, owner.getLocation().add(0, 1.4, 0), 3, 1, .6, 1, 0, DUST);
            if (strikeAge > 14) { strikeAge = -1; victim = null; }
            // Brief visual cue before despawning, without affecting 20-second lifetime.
            if (expiresAt - System.currentTimeMillis() < 1200 && age % 6 == 0) ring(owner.getLocation().add(0, 1, 0), 1.15, 12);
        }
    }
    private void ring(Location center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double theta = i * 2 * Math.PI / count;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(theta) * radius, .25, Math.sin(theta) * radius), 1, 0, 0, 0, 0, DUST);
        }
    }
    private void loadPlayerData() {
        for (String id : playerData.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                energy.put(uuid, Math.clamp(playerData.getInt(id + ".energy", 0), 0, MAX_ENERGY));
                Map<String, Long> cds = new HashMap<>();
                for (String key : List.of("beam", "duelist", "sunfall")) {
                    long value = playerData.getLong(id + ".cooldowns." + key, 0L);
                    if (value > System.currentTimeMillis()) cds.put(key, value);
                }
                if (!cds.isEmpty()) cooldowns.put(uuid, cds);
            } catch (IllegalArgumentException ex) { getLogger().warning("Skipping invalid player data entry " + id); }
        }
    }
    private void savePlayerData() {
        if (playerData == null) return;
        for (UUID id : energy.keySet()) {
            String path = id.toString();
            playerData.set(path + ".energy", energy.get(id));
            for (Map.Entry<String, Long> cd : cooldowns.getOrDefault(id, Map.of()).entrySet()) {
                playerData.set(path + ".cooldowns." + cd.getKey(), cd.getValue());
            }
        }
        try { playerData.save(playerDataFile); }
        catch (IOException e) { getLogger().severe("Could not save players.yml: " + e.getMessage()); }
    }
    private static final List<String> SKILLS = List.of("beam", "duelist", "sunfall");

    private void showCooldowns(CommandSender receiver, UUID playerId, String name) {
        long now = System.currentTimeMillis();
        Map<String, Long> times = cooldowns.getOrDefault(playerId, Map.of());
        receiver.sendMessage("§6☀ §eSolarius cooldowns §7— " + name);
        for (String skill : SKILLS) {
            long timeLeft = times.getOrDefault(skill, 0L) - now;
            String title = switch (skill) {
                case "beam" -> "Solar Obliteration";
                case "duelist" -> "Duelist";
                default -> "Sunfall";
            };
            receiver.sendMessage("§e" + title + ": " +
                    (timeLeft <= 0 ? "§aREADY" : "§c" + ((timeLeft + 999) / 1000) + " seconds"));
        }
    }

    private boolean cooldownCommand(CommandSender sender, String[] args) {
        // /cooldown, /cooldown <online-player> (admin), /cooldown reset <player> [all|beam|duelist|sunfall] (admin)
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("solarius.admin")) {
                sender.sendMessage("§cYou need solarius.admin to reset cooldowns."); return true;
            }
            if (args.length < 2 || args.length > 3) {
                sender.sendMessage("§cUsage: /cooldown reset <player> [all|beam|duelist|sunfall]"); return true;
            }
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage("§cThat player must be online."); return true; }
            String choice = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "all";
            if (!choice.equals("all") && !SKILLS.contains(choice)) {
                sender.sendMessage("§cChoose: all, beam, duelist, sunfall"); return true;
            }
            Map<String, Long> map = cooldowns.get(player.getUniqueId());
            if (map != null) {
                if (choice.equals("all")) map.clear(); else map.remove(choice);
                if (map.isEmpty()) cooldowns.remove(player.getUniqueId());
            }
            // Persist reset: erase corresponding entries in players.yml as well.
            for (String skill : SKILLS) {
                if (choice.equals("all") || choice.equals(skill)) {
                    playerData.set(player.getUniqueId() + ".cooldowns." + skill, null);
                }
            }
            savePlayerData();
            sender.sendMessage("§aReset " + choice + " cooldown for " + player.getName() + ".");
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage("§cUsage: /cooldown [player] OR /cooldown reset <player> [ability]"); return true;
        }
        Player target;
        if (args.length == 1) {
            if (!sender.hasPermission("solarius.admin")) {
                sender.sendMessage("§cOnly operators can inspect another player's cooldowns."); return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
        } else target = sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage("§cPlayer not found. Console: /cooldown <player>"); return true;
        }
        showCooldowns(sender, target.getUniqueId(), target.getName());
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cooldown")) return cooldownCommand(sender, args);
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "give" -> {
                if (!sender.hasPermission("solarius.admin")) { sender.sendMessage("§cNo permission."); return true; }
                Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (player == null) { sender.sendMessage("§cUsage: /solarius give <player>"); return true; }
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(makeSword());
                for (ItemStack extra : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
                sender.sendMessage("§6☀ Gave a Solarius sword to " + player.getName());
                return true;
            }
            case "energy" -> {
                if (sender instanceof Player p) sender.sendMessage("§6☀ Solar Energy: " + getEnergy(p) + "/100");
                else sender.sendMessage("§cOnly players can check their own energy.");
                return true;
            }
            case "setenergy" -> {
                if (!sender.hasPermission("solarius.admin")) { sender.sendMessage("§cNo permission."); return true; }
                if (args.length != 3) { sender.sendMessage("§cUsage: /solarius setenergy <player> <0-100>"); return true; }
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) { sender.sendMessage("§cPlayer must be online."); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0 || amount > 100) throw new NumberFormatException();
                    energy.put(player.getUniqueId(), amount);
                    sender.sendMessage("§aSet " + player.getName() + "'s Solar Energy to " + amount + "/100.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cEnergy must be a whole number from 0 to 100.");
                }
                return true;
            }
            case "cooldown", "cooldowns" -> {
                return cooldownCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "reload" -> {
                if (!sender.hasPermission("solarius.admin")) { sender.sendMessage("§cNo permission."); return true; }
                reloadConfig(); sender.sendMessage("§6☀ Solarius configuration reloaded."); return true;
            }
            case "help" -> {
                sender.sendMessage("§6☀ Solarius v4.0");
                sender.sendMessage("§eSneak + Right Click §7→ Solar Obliteration beam");
                sender.sendMessage("§eSneak + Left Click §7→ Duelist (two skeletal hands and sunforged swords)");
                sender.sendMessage("§eSneak + Swap Hand/F §7→ Sunfall ultimate");
                sender.sendMessage("§e/solarius energy §7→ Show charge");
                sender.sendMessage("§e/cooldown §7→ Show all 3 cooldowns");
                if (sender.hasPermission("solarius.admin")) {
                    sender.sendMessage("§e/solarius give <player> §7| §e/solarius setenergy <player> <0-100>");
                    sender.sendMessage("§e/cooldown reset <player> [all|beam|duelist|sunfall] §7| §e/solarius reload");
                }
                return true;
            }
            default -> { sender.sendMessage("§cUnknown subcommand. Use /solarius help"); return true; }
        }
    }
    @Override public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("cooldown")) return completeCooldown(s, args);
        if (args.length == 1) {
            List<String> words = new ArrayList<>(List.of("help", "energy", "cooldown"));
            if (s.hasPermission("solarius.admin")) words.addAll(List.of("give", "setenergy", "reload"));
            return filter(words, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("cooldown"))
            return completeCooldown(s, Arrays.copyOfRange(args, 1, args.length));
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("setenergy")) && s.hasPermission("solarius.admin"))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("setenergy") && s.hasPermission("solarius.admin"))
            return filter(List.of("0", "50", "100"), args[2]);
        return List.of();
    }
    private List<String> completeCooldown(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("solarius.admin")) {
                completions.add("reset");
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
            return filter(completions, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("solarius.admin"))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("solarius.admin"))
            return filter(List.of("all", "beam", "duelist", "sunfall"), args[2]);
        return List.of();
    }
    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
