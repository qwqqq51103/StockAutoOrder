package StockMainAction.model.game;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.PersonalStatistics;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AchievementTracker {
    private final Set<String> unlocked = new LinkedHashSet<>();

    public List<String> evaluate(StockMarketModel model, PersonalStatistics stats) {
        if (stats == null) {
            return new ArrayList<>(unlocked);
        }
        unlock(stats.getTotalTrades() > 0, "第一筆成交");
        unlock(stats.getTotalTrades() >= 10, "十筆交易練習");
        unlock(stats.getReturnRate() >= 1.0, "穩定獲利");
        unlock(stats.getReturnRate() >= 5.0, "單局高報酬");
        unlock(stats.getMaxDrawdown() <= 5.0 && (model == null || model.getTimeStep() >= 200), "低回撤控盤");
        unlock(stats.getCurrentHoldings() == 0 && stats.getTotalTrades() >= 2, "空手收工");
        return new ArrayList<>(unlocked);
    }

    private void unlock(boolean condition, String name) {
        if (condition) {
            unlocked.add(name);
        }
    }
}
