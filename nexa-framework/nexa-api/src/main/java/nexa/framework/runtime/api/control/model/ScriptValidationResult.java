package nexa.framework.runtime.api.control.model;

import java.io.Serializable;
import java.util.List;

public record ScriptValidationResult(
        boolean valid,
        String language,
        List<String> errors) implements Serializable {
}