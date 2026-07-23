package StockMainAction.view.main;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Command Palette 的命令清單與 callback dispatch。
 */
public final class CommandPaletteFactory {
    public static final String COMMAND_TOGGLE_THEME = "切換主題";
    public static final String COMMAND_RESET_ZOOM = "重置圖表縮放";
    public static final String COMMAND_PERF_POWER_SAVE = "效能: 節能";
    public static final String COMMAND_PERF_BALANCED = "效能: 平衡";
    public static final String COMMAND_PERF_PERFORMANCE = "效能: 效能";
    public static final String COMMAND_SEARCH_INFO = "搜索資訊...";

    private CommandPaletteFactory() {
    }

    public static String[] defaultCommands() {
        return new String[]{
                COMMAND_TOGGLE_THEME,
                COMMAND_RESET_ZOOM,
                COMMAND_PERF_POWER_SAVE,
                COMMAND_PERF_BALANCED,
                COMMAND_PERF_PERFORMANCE,
                COMMAND_SEARCH_INFO
        };
    }

    public static String promptSelection(Component parent) {
        String[] commands = defaultCommands();
        return (String) JOptionPane.showInputDialog(
                parent, "指令:", "Command Palette", JOptionPane.PLAIN_MESSAGE,
                null, commands, COMMAND_RESET_ZOOM);
    }

    public static void execute(String command, Actions actions) {
        if (command == null || actions == null) {
            return;
        }
        switch (command) {
            case COMMAND_TOGGLE_THEME -> actions.toggleTheme();
            case COMMAND_RESET_ZOOM -> actions.resetChartZoom();
            case COMMAND_PERF_POWER_SAVE -> actions.applyPerformanceMode("節能");
            case COMMAND_PERF_BALANCED -> actions.applyPerformanceMode("平衡");
            case COMMAND_PERF_PERFORMANCE -> actions.applyPerformanceMode("效能");
            case COMMAND_SEARCH_INFO -> actions.searchInfo();
            default -> { }
        }
    }

    public interface Actions {
        void toggleTheme();
        void resetChartZoom();
        void applyPerformanceMode(String mode);
        void searchInfo();
    }
}
