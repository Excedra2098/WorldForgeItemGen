package dev.excedra.itemgen;

import java.util.function.LongSupplier;

/** Caps both work count and elapsed time, while always allowing one unit to make progress. */
final class TickWorkBudget {
    private final int limit;
    private final long deadline;
    private final LongSupplier clock;
    private int used;
    TickWorkBudget(int limit,int millis){this(limit,millis,System::nanoTime);}
    TickWorkBudget(int limit,int millis,LongSupplier clock){this.limit=limit;this.clock=clock;deadline=clock.getAsLong()+millis*1_000_000L;}
    boolean acquire(){if(used>=limit||(used>0&&clock.getAsLong()>=deadline))return false;used++;return true;}
}
