package dev.excedra.itemgen;

/** Realm geometry is measured from the registered Core; stored terrain stays absolute. */
final class CoreExpansionGeometry {
    static void validate(RealmCoreStore.Anchor center,int x,int y,int z,int radius,int maximum){
        validate(center,x,y,z,radius,maximum,5);
    }
    static void validate(RealmCoreStore.Anchor center,int x,int y,int z,int radius,int maximum,int coreRadius){
        var relative=center.relative(x,y,z);
        if(Math.abs((long)relative.get(0))+radius>maximum||Math.abs((long)relative.get(2))+radius>maximum)throw new IllegalStateException("Shard exceeds the expansion area around your Core ("+maximum+" blocks).");
        if(Math.abs((long)relative.get(0))<=radius+coreRadius&&Math.abs((long)relative.get(2))<=radius+coreRadius)throw new IllegalStateException("Place this expansion outward from the Core's protected center.");
    }
    static TerrainPlan.Pos bridgeStart(RealmCoreStore.Anchor center,int playerX,int groundY,int playerZ,int targetX,int targetY,int targetZ){
        var start=center.relative(playerX,groundY,playerZ);var target=center.relative(targetX,targetY,targetZ);
        return new TerrainPlan.Pos(Math.subtractExact(start.get(0),target.get(0)),Math.subtractExact(start.get(1),target.get(1)),Math.subtractExact(start.get(2),target.get(2)));
    }
}
