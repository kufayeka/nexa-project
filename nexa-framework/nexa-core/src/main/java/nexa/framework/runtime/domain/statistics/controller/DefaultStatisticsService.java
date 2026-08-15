package nexa.framework.runtime.domain.statistics.controller;

import nexa.framework.runtime.domain.statistics.api.StatisticsService;
import nexa.framework.runtime.domain.statistics.service.FlowStatistics;

/**
 * DefaultStatisticsService adalah implementasi default dari StatisticsService.
 */
public final class DefaultStatisticsService implements StatisticsService {

    @Override
    public FlowStatistics createStatistics(String workspaceId, String flowId) {
        return new FlowStatistics(workspaceId, flowId);
    }
}
