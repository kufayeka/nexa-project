package nexa.framework.runtime.domain.statistics.api;

import nexa.framework.runtime.domain.statistics.service.FlowStatistics;

/**
 * StatisticsService menyediakan antarmuka untuk membuat pencatat statistik baru
 * untuk flow di runtime.
 */
public interface StatisticsService {

    /**
     * Membuat instance baru pencatat statistik FlowStatistics.
     */
    FlowStatistics createStatistics(String workspaceId, String flowId);
}
