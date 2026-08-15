package nexa.framework.runtime.domain.workspace.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nexa.framework.runtime.domain.workspace.api.WorkspaceService;
import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WorkspaceJsonLoader bertanggung jawab untuk memproses serialisasi/deserialisasi
 * JSON ke bentuk objek Java Records (WorkspaceDefinition).
 */
public final class WorkspaceJsonLoader implements WorkspaceService {

    private final ObjectMapper objectMapper;

    public WorkspaceJsonLoader() {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public WorkspaceDefinition fromJson(String json) {
        try {
            return objectMapper.readValue(json, WorkspaceDefinition.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to parse workspace JSON", ex);
        }
    }

    @Override
    public WorkspaceDefinition fromFile(Path path) {
        try {
            String json = Files.readString(path);
            return fromJson(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read workspace JSON file " + path, ex);
        }
    }
}

