package nexa.framework.runtime.domain.statistics;

import nexa.framework.runtime.domain.statistics.api.StatisticsService;
import nexa.framework.runtime.domain.statistics.controller.DefaultStatisticsService;

/**
 * StatisticsModule merakit kebutuhan internal untuk pencatatan statistika.
 * Bertindak sebagai Composition Root pada tingkat domain.
 */
public final class StatisticsModule {

    private final StatisticsService statisticsService;

    public StatisticsModule() {
        this.statisticsService = new DefaultStatisticsService();
    }

    /**
     * Menyediakan instance StatisticsService untuk domain lain.
     */
    public StatisticsService statisticsService() {
        return statisticsService;
    }
}
