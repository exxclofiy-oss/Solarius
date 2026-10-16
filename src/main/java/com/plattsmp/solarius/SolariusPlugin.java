package com.plattsmp.solarius;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.util.*;

public final class SolariusPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID,Integer> energy = new HashMap<>();
    private final Map<UUID,Map<String,Long>> cooldown = new HashMap<>();
    private final Map<UUID,Long> lastCombatGain = new HashMap<>();
    private final Map<UUID,Long> lastSneak = new HashMap<>();
    private final Map<UUID,Guardian> guardians = new HashMap<>();
    private NamespacedKey key;

    @Override public void onEnable() {
        saveDefaultConfig(); key = new NamespacedKey(this,"solarius_weapon");
        getServer().getPluginManager().registerEvents(this,this);
        Objects.requireNonNull(getCommand("solarius")).setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this,()->{
            for(Player p:Bukkit.getOnlinePlayers()) {
                if(isSunlit(p)) addEnergy(p,getConfig().getInt("energy.sunlight-per-second",2));
                if(hasWeapon(p)) p.sendActionBar(net.kyori.adventure.text.Component.text("☀ SOLARIUS  "+energy.getOrDefault(p.getUniqueId(),0)+"/100",net.kyori.adventure.text.format.NamedTextColor.GOLD));
            }
        },20L,20L);
        Bukkit.getScheduler().runTaskTimer(this,()->{for(Guardian g:new ArrayList<>(guardians.values()))g.tick();},1L,2L);
    }
    @Override public void onDisable(){for(Guardian g:new ArrayList<>(guardians.values()))g.remove();guardians.clear();}
    private boolean hasWeapon(Player p){ItemStack i=p.getInventory().getItemInMainHand();return i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE);}
    private ItemStack weapon(){ItemStack i=new ItemStack(Material.GOLDEN_SWORD);ItemMeta m=i.getItemMeta();m.setDisplayName("§6☀ Solarius");m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);m.setUnbreakable(true);i.setItemMeta(m);return i;}
    private void addEnergy(Player p,int amount){energy.merge(p.getUniqueId(),amount,(a,b)->Math.max(0,Math.min(100,a+b)));}
    private boolean isSunlit(Player p){World w=p.getWorld();long t=w.getTime();return w.getEnvironment()==World.Environment.NORMAL && !w.hasStorm() && t<12300 && w.getHighestBlockYAt(p.getLocation())<=p.getLocation().getBlockY();}
    private boolean activate(Player p,String name,int cost,int seconds){UUID id=p.getUniqueId();long now=System.currentTimeMillis();long ready=cooldown.getOrDefault(id,Collections.emptyMap()).getOrDefault(name,0L);if(ready>now){p.sendMessage("§eCooldown: "+((ready-now+999)/1000)+"s");return false;}if(energy.getOrDefault(id,0)<cost){p.sendMessage("§cNot enough solar energy! ("+cost+" needed)");return false;}energy.put(id,energy.getOrDefault(id,0)-cost);cooldown.computeIfAbsent(id,k->new HashMap<>()).put(name,now+seconds*1000L);return true;}
    private int cfg(String path,int fallback){return getConfig().getInt(path,fallback);}
    private double dbl(String path,double fallback){return getConfig().getDouble(path,fallback);}
    @EventHandler public void onHit(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player attacker && e.getEntity() instanceof LivingEntity){if(hasWeapon(attacker))gainCombat(attacker,cfg("energy.hit",8));}
        if(e.getEntity() instanceof Player victim){gainCombat(victim,cfg("energy.damaged",4));Guardian g=guardians.get(victim.getUniqueId());if(g!=null && !g.expired())e.setDamage(e.getDamage()*(1-dbl("abilities.duelist.damage-reduction",.15)));}
    }
    private void gainCombat(Player p,int amount){long now=System.currentTimeMillis();if(now-lastCombatGain.getOrDefault(p.getUniqueId(),0L)>=350){addEnergy(p,amount);lastCombatGain.put(p.getUniqueId(),now);}}
    @EventHandler public void onSneak(PlayerToggleSneakEvent e){if(e.isSneaking())lastSneak.put(e.getPlayer().getUniqueId(),System.currentTimeMillis());}
    @EventHandler public void onInteract(PlayerInteractEvent e){Player p=e.getPlayer();if(!hasWeapon(p)||!p.isSneaking())return;Action a=e.getAction();if(a!=Action.RIGHT_CLICK_AIR && a!=Action.RIGHT_CLICK_BLOCK && a!=Action.LEFT_CLICK_AIR && a!=Action.LEFT_CLICK_BLOCK)return;e.setCancelled(true);if(a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK){duelist(p);return;}long now=System.currentTimeMillis();long sneakAt=lastSneak.getOrDefault(p.getUniqueId(),0L);if(now-sneakAt<400) sunfall(p);else beam(p);}
    private void beam(Player p){if(!activate(p,"beam",cfg("abilities.beam.cost",35),cfg("abilities.beam.cooldown-seconds",12)))return;Location start=p.getEyeLocation();Vector dir=start.getDirection().normalize();World w=p.getWorld();double range=dbl("abilities.beam.range",25);Set<UUID> struck=new HashSet<>();for(double d=0;d<range;d+=.35){Location l=start.clone().add(dir.clone().multiply(d));if(l.getBlock().getType().isSolid())break;w.spawnParticle(Particle.DUST,l,3,.08,.08,.08,0,new Particle.DustOptions(Color.fromRGB(255,190,25),1.7f));for(Entity entity:w.getNearbyEntities(l,.55,.55,.55)){if(entity instanceof LivingEntity target && valid(p,target) && struck.add(entity.getUniqueId())){target.damage(dbl("abilities.beam.damage-health",7),p);target.setVelocity(target.getVelocity().add(dir.clone().multiply(.4)));w.spawnParticle(Particle.END_ROD,l,20,.3,.3,.3,.08);w.playSound(l,Sound.ENTITY_FIREWORK_ROCKET_BLAST,1,1.3f);return;}}}w.playSound(start,Sound.ENTITY_BLAZE_SHOOT,1,1.6f);}
    private void duelist(Player p){if(guardians.containsKey(p.getUniqueId())){p.sendMessage("§eDuelist is already active.");return;}if(!activate(p,"duelist",cfg("abilities.duelist.cost",60),cfg("abilities.duelist.cooldown-seconds",45)))return;Guardian g=new Guardian(p);guardians.put(p.getUniqueId(),g);p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,1.4f);}
    private void sunfall(Player p){if(!activate(p,"sunfall",cfg("abilities.sunfall.cost",100),cfg("abilities.sunfall.cooldown-seconds",90)))return;Location center=p.getLocation().clone();new BukkitRunnable(){int ticks=0;public void run(){if(!p.isOnline()||p.isDead()){cancel();return;}Location orb=p.getLocation().add(0,3,0);p.getWorld().spawnParticle(Particle.DUST,orb,35,.55,.55,.55,0,new Particle.DustOptions(Color.fromRGB(255,185,0),2.5f));if(++ticks>=10){cancel();explode(p,center);}}}.runTaskTimer(this,0L,4L);}
    private void explode(Player p,Location c){World w=c.getWorld();double r=dbl("abilities.sunfall.radius",7);for(int i=0;i<160;i++){double a=2*Math.PI*i/160;Location l=c.clone().add(Math.cos(a)*r,.4,Math.sin(a)*r);w.spawnParticle(Particle.DUST,l,4,.15,.15,.15,0,new Particle.DustOptions(Color.YELLOW,2));}w.spawnParticle(Particle.FLASH,c.clone().add(0,1,0),2);w.playSound(c,Sound.ENTITY_GENERIC_EXPLODE,1.5f,.8f);for(Entity entity:w.getNearbyEntities(c,r,r,r)){if(entity instanceof LivingEntity target && valid(p,target)){double d=target.getLocation().distance(c);if(d>r)continue;double hearts=d<=2?5:d<=4?4:d<=6?3:2;target.damage(hearts*2,p);Vector knock=target.getLocation().toVector().subtract(c.toVector()).setY(.35);if(knock.lengthSquared()>.01)target.setVelocity(knock.normalize().multiply(.8));target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING,100,0));}}}
    private boolean valid(Player owner,LivingEntity target){if(target==owner||target.isDead()||target instanceof ArmorStand)return false;if(target instanceof Player other){if(other.getGameMode()==GameMode.SPECTATOR||other.getGameMode()==GameMode.CREATIVE||!owner.getWorld().getPVP())return false;if(owner.getScoreboard().getEntryTeam(owner.getName())!=null && owner.getScoreboard().getEntryTeam(owner.getName()).hasEntry(other.getName()) && !owner.getScoreboard().getEntryTeam(owner.getName()).allowFriendlyFire())return false;}return true;}
    @EventHandler public void onQuit(PlayerQuitEvent e){stop(e.getPlayer());}
    @EventHandler public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e){stop(e.getEntity());}
    @EventHandler public void onWorld(PlayerChangedWorldEvent e){stop(e.getPlayer());}
    private void stop(Player p){Guardian g=guardians.remove(p.getUniqueId());if(g!=null)g.remove();}
    private final class Guardian{
        final UUID owner;final long end;final List<BlockDisplay> arms=new ArrayList<>();final List<ItemDisplay> swords=new ArrayList<>();int ticks=0;long lastAttack=0;long lastExecution=0;
        Guardian(Player p){owner=p.getUniqueId();end=System.currentTimeMillis()+cfg("abilities.duelist.duration-seconds",20)*1000L;for(int side:new int[]{-1,1}){for(int joint=0;joint<4;joint++){BlockDisplay b=p.getWorld().spawn(p.getLocation(),BlockDisplay.class);b.setBlock(Material.GOLD_BLOCK.createBlockData());b.setTransformation(new Transformation(new Vector3f(-.10f,-.10f,-.10f),new AxisAngle4f(),new Vector3f(.20f,.20f,.42f),new AxisAngle4f()));arms.add(b);}ItemDisplay sword=p.getWorld().spawn(p.getLocation(),ItemDisplay.class);sword.setItemStack(new ItemStack(Material.GOLDEN_SWORD));sword.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);swords.add(sword);}}
        boolean expired(){return System.currentTimeMillis()>=end;}
        void tick(){Player p=Bukkit.getPlayer(owner);if(p==null||!p.isOnline()||p.isDead()||expired()){remove();guardians.remove(owner);return;}ticks++;Location base=p.getLocation();Vector right=base.getDirection().setY(0);if(right.lengthSquared()<.01)right=new Vector(0,0,1);right.normalize();right=new Vector(-right.getZ(),0,right.getX());for(int sideIndex=0;sideIndex<2;sideIndex++){int side=sideIndex==0?-1:1;Location origin=base.clone().add(right.clone().multiply(side*1.15)).add(0,1.6+Math.sin(ticks*.13)*.08,0);for(int joint=0;joint<4;joint++){Location at=origin.clone().add(0,-joint*.25,0).add(right.clone().multiply(side*joint*.06));arms.get(sideIndex*4+joint).teleport(at);}swords.get(sideIndex).teleport(origin.clone().add(0,-1.1,0));}p.getWorld().spawnParticle(Particle.DUST,base.clone().add(0,1.4,0),3,1,.4,1,0,new Particle.DustOptions(Color.fromRGB(255,185,0),1));long now=System.currentTimeMillis();if(now-lastAttack<1000)return;LivingEntity target=null;double best=dbl("abilities.duelist.range",12);for(Entity e:p.getNearbyEntities(best,best,best))if(e instanceof LivingEntity l && valid(p,l)){double d=l.getLocation().distance(p.getLocation());if(d<best){best=d;target=l;}}if(target==null)return;boolean special=now-lastExecution>=5000;if(special){lastExecution=now;target.damage(dbl("abilities.duelist.execution-damage-health",4),p);}else target.damage(dbl("abilities.duelist.strike-damage-health",2),p);lastAttack=now;Location hit=target.getLocation().add(0,1,0);p.getWorld().spawnParticle(Particle.SWEEP_ATTACK,hit,special?4:1,.5,.4,.5,0);p.getWorld().spawnParticle(Particle.DUST,hit,22,.45,.6,.45,0,new Particle.DustOptions(Color.fromRGB(255,200,20),1.5f));p.getWorld().playSound(hit,Sound.ENTITY_PLAYER_ATTACK_SWEEP,1,1.3f);}
        void remove(){for(Entity e:arms)e.remove();for(Entity e:swords)e.remove();arms.clear();swords.clear();}
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(args.length>0 && args[0].equalsIgnoreCase("give")){if(!sender.hasPermission("solarius.admin")){sender.sendMessage("§cNo permission.");return true;}Player target=args.length>1?Bukkit.getPlayerExact(args[1]):sender instanceof Player p?p:null;if(target==null){sender.sendMessage("§cPlayer not found.");return true;}target.getInventory().addItem(weapon());sender.sendMessage("§6Solarius weapon given to "+target.getName());return true;}if(sender instanceof Player p){sender.sendMessage("§6Solarius energy: "+energy.getOrDefault(p.getUniqueId(),0)+"/100");return true;}sender.sendMessage("Usage: /solarius give <player>");return true;}
}
