package dev.excedra.itemgen;
import java.util.List;
/** Structured result for authoring tools; does not write server files. */
record SpecialShardValidationReport(List<String> errors,List<String> warnings) {
    SpecialShardValidationReport {errors=List.copyOf(errors);warnings=List.copyOf(warnings);}
    boolean valid(){return errors.isEmpty();}
}