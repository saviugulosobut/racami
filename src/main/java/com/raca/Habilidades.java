package com.raca;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Habilidades {
    public static final String TAG = "raca_ativa";
    public static final String TAG_VOO = "raca_voo";
    public static final String TAG_LOBO = "raca_lobo";

    // ===== AJUSTES (20 ticks = 1 segundo) =====
    static final float GRITO_DANO = 20.0f;        // 10 coracoes
    static final double GRITO_ALCANCE = 15.0;     // blocos
    static final int GRITO_RECARGA = 200;         // 10s
    static final int LOBOS_QTD = 30;
    static final int LOBOS_DURACAO = 600;         // 30s
    static final int LOBOS_RECARGA = 1200;        // 60s
    static final double LOBO_DANO = 8.0;
    static final double VIDA_EXTRA = 20.0;        // 10 coracoes
    static final int RESISTENCIA = 1;             // 1 = 40% menos dano
    static final float VOO_FOME = 0.03f;          // gasto de fome voando
    static final float PISAO_MIN_QUEDA = 4.0f;    // blocos de queda para ativar o pisao
    static final float PISAO_DANO_BLOCO = 1.5f;   // dano por bloco de queda
    static final float PISAO_DANO_MAX = 30.0f;
    static final double PISAO_RAIO = 6.0;
    static final double AURA_RAIO = 12.0;         // mobs hostis fogem de voce
    static final double AURA_VELOCIDADE = 1.3;    // velocidade da fuga
    static final int REGEN_INTERVALO = 20;        // a cada 1s
    static final float REGEN_CURA = 2.0f;         // 1 coracao por vez
    // ==========================================

    static final Identifier VIDA_ID = Identifier.of("raca", "vida_golem");
    static final Identifier KB_ID = Identifier.of("raca", "kb_golem");

    private static final Map<UUID, Integer> gritoLivre = new HashMap<>();
    private static final Map<UUID, Integer> lobosLivre = new HashMap<>();
    static final Map<UUID, Integer> LOBOS = new HashMap<>();
    private static final Map<UUID, Float> ultimaQueda = new HashMap<>();

    public static boolean temRaca(ServerPlayerEntity p) {
        return p.getCommandTags().contains(TAG);
    }

    private static final Map<UUID, Integer> avisoAte = new HashMap<>();

    static void aviso(ServerPlayerEntity p, String texto) {
        p.sendMessage(Text.literal(texto), true);
        avisoAte.put(p.getUuid(), p.getServer().getTicks() + 30);
    }

    private static String recarga(Map<UUID, Integer> mapa, ServerPlayerEntity p, int t) {
        int livre = mapa.getOrDefault(p.getUuid(), 0);
        return t >= livre ? "pronto" : ((livre - t) / 20 + 1) + "s";
    }

    // Barra de status na tela (acima da hotbar)
    private static void status(ServerPlayerEntity p, int t) {
        if (t < avisoAte.getOrDefault(p.getUuid(), 0)) return;
        String voo = p.getCommandTags().contains(TAG_VOO) ? "ON" : "OFF";
        p.sendMessage(Text.literal(
            "Grito [R]: " + recarga(gritoLivre, p, t)
            + "   Voo [V]: " + voo
            + "   Lobos [G]: " + recarga(lobosLivre, p, t)), true);
    }

    public static void ativar(ServerPlayerEntity p) {
        p.addCommandTag(TAG);
        passivos(p, 0);
        p.setHealth(p.getMaxHealth());
    }

    public static void desativar(ServerPlayerEntity p) {
        p.removeCommandTag(TAG);
        p.removeCommandTag(TAG_VOO);
        EntityAttributeInstance vida = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (vida != null) vida.removeModifier(VIDA_ID);
        EntityAttributeInstance kb = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(KB_ID);
        p.removeStatusEffect(StatusEffects.RESISTANCE);
        p.removeStatusEffect(StatusEffects.NIGHT_VISION);
        p.removeStatusEffect(StatusEffects.WATER_BREATHING);
        p.removeStatusEffect(StatusEffects.DOLPHINS_GRACE);
        desligarVoo(p);
    }

    // ---------- tick ----------
    public static void tick(MinecraftServer server) {
        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!temRaca(p)) continue;
            passivos(p, t);
            voo(p);
            float ultima = ultimaQueda.getOrDefault(p.getUuid(), 0.0f);
            if (p.isOnGround() && ultima >= PISAO_MIN_QUEDA) pisao(p, ultima);
            ultimaQueda.put(p.getUuid(), p.fallDistance);
            if (t % 2 == 0) aura(p);
            if (t % REGEN_INTERVALO == 0 && p.getHealth() < p.getMaxHealth()) p.heal(REGEN_CURA);
            if (t % 10 == 0) status(p, t);
        }
        if (t % 10 == 0) expirarLobos(server, t);
    }

    // Golem: mais vida, sem empurrao, menos dano
    private static void passivos(ServerPlayerEntity p, int t) {
        EntityAttributeInstance vida = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (vida != null && !vida.hasModifier(VIDA_ID)) {
            vida.addPersistentModifier(new EntityAttributeModifier(
                VIDA_ID, VIDA_EXTRA, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        EntityAttributeInstance kb = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null && !kb.hasModifier(KB_ID)) {
            kb.addPersistentModifier(new EntityAttributeModifier(
                KB_ID, 1.0, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        if (t % 10 == 0) {
            p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 40, RESISTENCIA, true, false, false));
            // Warden: enxerga no escuro
            p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION, 300, 0, true, false, false));
            // Agua: respira e nada rapido
            p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WATER_BREATHING, 40, 0, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DOLPHINS_GRACE, 40, 0, true, false, false));
        }
    }

    public static boolean semDanoDeQueda(ServerPlayerEntity p, DamageSource fonte) {
        return temRaca(p) && fonte.isOf(DamageTypes.FALL);
    }

    // ---------- Dragao: voo ----------
    private static void voo(ServerPlayerEntity p) {
        if (p.isCreative() || p.isSpectator()) return;
        if (!p.getCommandTags().contains(TAG_VOO)) return;

        if (p.getHungerManager().getFoodLevel() <= 0) {
            p.removeCommandTag(TAG_VOO);
            desligarVoo(p);
            aviso(p, "Sem fome para voar!");
            return;
        }
        if (!p.getAbilities().allowFlying) {
            p.getAbilities().allowFlying = true;
            p.sendAbilitiesUpdate();
        }
        if (p.getAbilities().flying) {
            p.addExhaustion(VOO_FOME);
        }
    }

    private static void desligarVoo(ServerPlayerEntity p) {
        if (p.isCreative() || p.isSpectator()) return;
        boolean estavaVoando = p.getAbilities().flying;
        p.getAbilities().allowFlying = false;
        p.getAbilities().flying = false;
        p.sendAbilitiesUpdate();
        if (estavaVoando) {
            p.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOW_FALLING, 100, 0, true, false, true));
        }
    }

    public static void alternarVoo(ServerPlayerEntity p) {
        if (p.isCreative() || p.isSpectator()) {
            aviso(p, "Voce ja pode voar neste modo");
            return;
        }
        if (p.getCommandTags().contains(TAG_VOO)) {
            p.removeCommandTag(TAG_VOO);
            desligarVoo(p);
            aviso(p, "Voo desligado");
        } else {
            if (p.getHungerManager().getFoodLevel() <= 0) {
                aviso(p, "Sem fome para voar!");
                return;
            }
            p.addCommandTag(TAG_VOO);
            p.getAbilities().allowFlying = true;
            p.sendAbilitiesUpdate();
            aviso(p, "Voo ligado (pule duas vezes para voar)");
        }
    }

    // ---------- Warden: grito sonico (raio, como o original) ----------
    public static void grito(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        int t = p.getServer().getTicks();
        int livre = gritoLivre.getOrDefault(p.getUuid(), 0);
        if (t < livre) {
            aviso(p, "Grito em recarga: " + ((livre - t) / 20 + 1) + "s");
            return;
        }
        gritoLivre.put(p.getUuid(), t + GRITO_RECARGA);

        Vec3d inicio = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        Vec3d fim = inicio.add(dir.multiply(GRITO_ALCANCE));

        // alvo mais proximo na linha da mira
        LivingEntity melhor = null;
        double melhorT = Double.MAX_VALUE;
        Vec3d seg = fim.subtract(inicio);
        double len2 = seg.lengthSquared();
        List<LivingEntity> candidatos = w.getEntitiesByClass(LivingEntity.class,
            new Box(inicio, fim).expand(3.0),
            e -> e != p
                && e.isAlive()
                && !(e instanceof ArmorStandEntity)
                && !(e instanceof TameableEntity tame && tame.isOwner(p)));
        for (LivingEntity e : candidatos) {
            Vec3d centro = e.getBoundingBox().getCenter();
            double tt = MathHelper.clamp(
                centro.subtract(inicio).dotProduct(seg) / len2, 0.0, 1.0);
            Vec3d ponto = inicio.add(seg.multiply(tt));
            double tol = Math.max(e.getWidth(), e.getHeight()) / 2.0 + 0.8;
            if (centro.distanceTo(ponto) > tol) continue;
            if (tt < melhorT) {
                melhorT = tt;
                melhor = e;
            }
        }

        int passos = (int) GRITO_ALCANCE;
        if (melhor != null) {
            passos = (int) (GRITO_ALCANCE * melhorT) + 7;
            melhor.timeUntilRegen = 0;
            melhor.damage(w.getDamageSources().sonicBoom(p), GRITO_DANO);
            double res = 1.0 - melhor.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            melhor.addVelocity(dir.x * 2.5 * res, dir.y * 0.5 * res, dir.z * 2.5 * res);
            melhor.velocityModified = true;
        }

        w.playSound(null, p.getX(), p.getY(), p.getZ(),
            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);
        for (int i = 1; i <= passos; i++) {
            w.spawnParticles(ParticleTypes.SONIC_BOOM,
                inicio.x + dir.x * i, inicio.y - 0.3 + dir.y * i, inicio.z + dir.z * i,
                1, 0, 0, 0, 0);
        }
    }

    // ---------- Aura de medo: mobs hostis fogem ----------
    private static void aura(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        List<HostileEntity> mobs = w.getEntitiesByClass(HostileEntity.class,
            p.getBoundingBox().expand(AURA_RAIO),
            m -> m.isAlive()
                && !(m instanceof WitherEntity)
                && m.squaredDistanceTo(p) <= AURA_RAIO * AURA_RAIO);
        for (HostileEntity m : mobs) {
            if (m.getTarget() == p) m.setTarget(null);
            Vec3d fora = m.getPos().subtract(p.getPos());
            if (fora.lengthSquared() < 0.01) continue;
            Vec3d destino = m.getPos().add(fora.normalize().multiply(10.0));
            m.getNavigation().startMovingTo(destino.x, destino.y, destino.z, AURA_VELOCIDADE);
        }
    }

    // ---------- Golem/Dragao: pisao ao cair ----------
    private static void pisao(ServerPlayerEntity p, float queda) {
        ServerWorld w = p.getServerWorld();
        float dano = Math.min(queda * PISAO_DANO_BLOCO, PISAO_DANO_MAX);
        List<LivingEntity> alvos = w.getEntitiesByClass(LivingEntity.class,
            p.getBoundingBox().expand(PISAO_RAIO),
            e -> e != p
                && e.isAlive()
                && !(e instanceof ArmorStandEntity)
                && !(e instanceof TameableEntity tame && tame.isOwner(p))
                && e.squaredDistanceTo(p) <= PISAO_RAIO * PISAO_RAIO);
        for (LivingEntity e : alvos) {
            e.damage(w.getDamageSources().playerAttack(p), dano);
            e.takeKnockback(1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
            e.addVelocity(0, 0.5, 0);
            e.velocityModified = true;
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
            SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS, 2.0f, 0.8f);
        w.spawnParticles(ParticleTypes.EXPLOSION,
            p.getX(), p.getY() + 0.1, p.getZ(), 8, 2.5, 0.1, 2.5, 0);
        aviso(p, "Pisao! " + (int) dano + " de dano");
    }

    // ---------- Player: lobos ----------
    public static void invocarLobos(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        int t = p.getServer().getTicks();
        int livre = lobosLivre.getOrDefault(p.getUuid(), 0);
        if (t < livre) {
            aviso(p, "Lobos em recarga: " + ((livre - t) / 20 + 1) + "s");
            return;
        }
        lobosLivre.put(p.getUuid(), t + LOBOS_RECARGA);

        for (int i = 0; i < LOBOS_QTD; i++) {
            WolfEntity lobo = EntityType.WOLF.create(w);
            if (lobo == null) continue;
            double ang = i * 2.4;
            double raio = 2.0 + Math.sqrt(i) * 0.8;
            lobo.refreshPositionAndAngles(
                p.getX() + Math.cos(ang) * raio, p.getY(), p.getZ() + Math.sin(ang) * raio,
                p.getYaw(), 0);
            lobo.setOwner(p);
            lobo.addCommandTag(TAG_LOBO);
            EntityAttributeInstance dano = lobo.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
            if (dano != null) dano.setBaseValue(LOBO_DANO);
            LOBOS.put(lobo.getUuid(), t + LOBOS_DURACAO);
            w.spawnEntity(lobo);
        }
        aviso(p, "Lobos invocados!");
    }

    // Quando o dono ataca alguem, os lobos vao com tudo
    public static void lobosAtacam(ServerPlayerEntity p, LivingEntity alvo) {
        if (!temRaca(p) || alvo == p) return;
        ServerWorld w = p.getServerWorld();
        List<WolfEntity> lobos = w.getEntitiesByClass(WolfEntity.class,
            p.getBoundingBox().expand(48),
            l -> l.getCommandTags().contains(TAG_LOBO) && l.isOwner(p));
        for (WolfEntity l : lobos) {
            if (l == alvo) continue;
            l.setSitting(false);
            l.setTarget(alvo);
        }
    }

    private static void expirarLobos(MinecraftServer server, int t) {
        Iterator<Map.Entry<UUID, Integer>> it = LOBOS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (t < e.getValue()) continue;
            for (ServerWorld sw : server.getWorlds()) {
                Entity lobo = sw.getEntity(e.getKey());
                if (lobo != null) {
                    sw.spawnParticles(ParticleTypes.POOF,
                        lobo.getX(), lobo.getY() + 0.5, lobo.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                    lobo.discard();
                    break;
                }
            }
            it.remove();
        }
    }
                               }
