package nexa.plugin.control.dto;

import java.io.Serializable;

/** Stable machine-readable error envelope for REST clients. */
public record ErrorResponse(
        String code,
        String message,
        String path) implements Serializable {
}
