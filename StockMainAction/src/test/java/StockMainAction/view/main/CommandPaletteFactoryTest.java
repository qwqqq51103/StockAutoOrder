package StockMainAction.view.main;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CommandPaletteFactoryTest {
    @Test
    public void defaultCommandsStayStable() {
        assertArrayEquals(new String[]{
                "切換主題",
                "重置圖表縮放",
                "效能: 節能",
                "效能: 平衡",
                "效能: 效能",
                "搜索資訊..."
        }, CommandPaletteFactory.defaultCommands());
    }

    @Test
    public void executeDispatchesKnownCommandsToCallbacks() {
        List<String> calls = new ArrayList<>();
        CommandPaletteFactory.Actions actions = new CommandPaletteFactory.Actions() {
            @Override public void toggleTheme() { calls.add("theme"); }
            @Override public void resetChartZoom() { calls.add("reset"); }
            @Override public void applyPerformanceMode(String mode) { calls.add("perf:" + mode); }
            @Override public void searchInfo() { calls.add("search"); }
        };

        CommandPaletteFactory.execute("切換主題", actions);
        CommandPaletteFactory.execute("重置圖表縮放", actions);
        CommandPaletteFactory.execute("效能: 效能", actions);
        CommandPaletteFactory.execute("搜索資訊...", actions);
        CommandPaletteFactory.execute("未知", actions);

        assertEquals(List.of("theme", "reset", "perf:效能", "search"), calls);
    }
}
