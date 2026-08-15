package nexa.framework.runtime.domain.workspace.api;

import nexa.framework.runtime.domain.workspace.model.WorkspaceDefinition;
import java.nio.file.Path;

/**
 * WorkspaceService mendefinisikan antarmuka publik untuk memuat dan mengelola
 * definisi workspace (JSON parsing & file reading).
 */
public interface WorkspaceService {

    /**
     * Memuat WorkspaceDefinition dari teks mentah JSON.
     */
    WorkspaceDefinition fromJson(String json);

    /**
     * Memuat WorkspaceDefinition dari berkas penyimpanan di sistem file.
     */
    WorkspaceDefinition fromFile(Path path);
}

