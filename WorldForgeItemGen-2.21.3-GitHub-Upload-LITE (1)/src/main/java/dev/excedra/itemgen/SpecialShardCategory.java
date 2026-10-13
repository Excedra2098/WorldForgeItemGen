package dev.excedra.itemgen;
import java.util.Locale;
import java.util.Set;
/** Data-only category name. Custom categories require no plugin changes. */
record SpecialShardCategory(String id) {
    static final Set<String> BUILTIN=Set.of("SEASONAL","HOLIDAY","EVENT","ANNIVERSARY","UPDATE_EXCLUSIVE","LIMITED_EDITION","VIP_EXCLUSIVE","DEVELOPER_EXCLUSIVE","CUSTOM");
    SpecialShardCategory {
        if(id==null || !id.matches("[A-Z][A-Z0-9_]{1,47}"))throw new IllegalArgumentException("Invalid category");
        id=id.toUpperCase(Locale.ROOT);
    }
    static SpecialShardCategory of(String value) {
        if(value==null)throw new IllegalArgumentException("Missing category");
        return new SpecialShardCategory(value.trim().toUpperCase(Locale.ROOT));
    }
}