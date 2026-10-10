package dev.excedra.itemgen;

import org.bukkit.*;

/** Shared burst entry point, exercised against the target Paper particle data contracts. */
final class ShatterEffects {
    static void burst(World world,Location center,int budget,Color color){
        world.spawnParticle(Particle.FLASH,center.clone().add(0,2,0),1,color);
        world.spawnParticle(Particle.FIREWORK,center,Math.min(budget/2,48),2,1.5,2,.18);
    }
}
