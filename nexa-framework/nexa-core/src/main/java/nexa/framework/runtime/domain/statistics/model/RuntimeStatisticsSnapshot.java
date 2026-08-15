package nexa.framework.runtime.domain.statistics.model;

public record RuntimeStatisticsSnapshot(
                String workspaceId,
                String flowId,
                long running,
                long waiting,
                long failed,
                long cancelled,
                long completed,
                long rejected,
                double averageExecutionTimeMillis) {
}


