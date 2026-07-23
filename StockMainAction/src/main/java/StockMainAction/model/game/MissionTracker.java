package StockMainAction.model.game;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.PersonalStatistics;
import java.util.ArrayList;
import java.util.List;

public final class MissionTracker {
    public List<MissionSnapshot> evaluate(StockMarketModel model, PersonalStatistics stats) {
        List<MissionSnapshot> missions = new ArrayList<>();
        int tick = model == null ? 0 : model.getTimeStep();
        double returnRate = stats == null ? 0.0 : stats.getReturnRate();
        double drawdown = stats == null ? 0.0 : stats.getMaxDrawdown();
        int trades = stats == null ? 0 : stats.getTotalTrades();
        double cashRatio = 0.0;
        if (stats != null && stats.getCurrentPortfolioValue() > 0) {
            cashRatio = stats.getCurrentCash() / stats.getCurrentPortfolioValue();
        }

        missions.add(percent("撐過開盤震盪", tick, 300,
                tick >= 300 && drawdown <= 10.0,
                "300 tick 內最大回撤不超過 10%"));
        missions.add(percent("完成三筆有效交易", trades, 3,
                trades >= 3,
                "至少完成 3 筆個人買賣"));
        missions.add(percent("小幅打敗現金", returnRate, 1.0,
                returnRate >= 1.0,
                "帳戶報酬率達 1%"));
        missions.add(percent("保留安全墊", cashRatio, 0.25,
                cashRatio >= 0.25,
                "現金佔總資產至少 25%"));
        return missions;
    }

    private static MissionSnapshot percent(String name, double current, double target,
            boolean completed, String description) {
        double progress = target <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, current / target));
        return new MissionSnapshot(name, description, progress, completed);
    }

    public record MissionSnapshot(String name, String description, double progress, boolean completed) {
        public String displayText() {
            String mark = completed ? "✓" : "□";
            return String.format("%s %s - %.0f%% (%s)", mark, name, progress * 100.0, description);
        }
    }
}
