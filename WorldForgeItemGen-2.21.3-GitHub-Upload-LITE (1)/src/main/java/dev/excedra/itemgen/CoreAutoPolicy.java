package dev.excedra.itemgen;
import java.util.*;
/** Pure eligibility policy; a completed or staff-reclaimed realm is never re-enrolled. */
final class CoreAutoPolicy {
 static boolean eligible(Collection<String> groups,Collection<String> staff,Collection<String> players){var actual=new HashSet<String>();groups.forEach(g->actual.add(g.toLowerCase(Locale.ROOT)));return staff.stream().noneMatch(g->actual.contains(g.toLowerCase(Locale.ROOT)))&&players.stream().anyMatch(g->actual.contains(g.toLowerCase(Locale.ROOT)));}
 static boolean starter(List<String> identities,List<String> configured){return identities.stream().map(CoreTemplateAnchor::key).anyMatch(id->configured.stream().map(CoreTemplateAnchor::key).anyMatch(id::equals));}
 static boolean pending(String state){return state.equals("WAITING");}
}
