package nexa.plugin.control.dto;

import java.io.Serializable;

/** Standard response returned by successful control operations. */
public record ActionResponse(
        boolean success,
        String message) implements Serializable {
}
