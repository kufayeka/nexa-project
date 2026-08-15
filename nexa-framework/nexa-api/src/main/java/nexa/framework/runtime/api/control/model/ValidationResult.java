package nexa.framework.runtime.api.control.model;

import java.io.Serializable;
import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) implements Serializable {
}