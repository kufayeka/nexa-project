package nexa.framework.runtime.domain.workspace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * InputExecutionPolicyDefinition mengontrol kebijakan eksekusi input,
 * terutama batas maksimum eksekusi konkuren (maxConcurrentExecutions)
 * untuk menghindari overloading sistem.
 */
public record InputExecutionPolicyDefinition(
        @JsonProperty("maxConcurrentExecutions") Integer maxConcurrentExecutions) {

    public InputExecutionPolicyDefinition {
        // Jika kebijakan kosong atau tidak valid, default-kan ke tak terbatas
        // (Integer.MAX_VALUE)
        if (maxConcurrentExecutions == null || maxConcurrentExecutions < 1) {
            maxConcurrentExecutions = Integer.MAX_VALUE;
        }
    }
}
