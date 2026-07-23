package StockMainAction.view.components;

import StockMainAction.model.game.GameMode;
import StockMainAction.model.game.GameSettings;
import StockMainAction.model.game.MarketWatchlist;
import StockMainAction.model.game.MissionTracker;
import StockMainAction.model.game.SimulationSpeed;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class GameDashboardPanel extends JPanel {
    private final JComboBox<GameMode> modeCombo = new JComboBox<>(GameMode.values());
    private final JComboBox<SimulationSpeed> speedCombo = new JComboBox<>(SimulationSpeed.values());
    private final JLabel seedLabel = new JLabel("-");
    private final JLabel scoreLabel = new JLabel("0");
    private final JLabel rankLabel = new JLabel("-");
    private final DefaultListModel<String> missionModel = new DefaultListModel<>();
    private final DefaultListModel<String> achievementModel = new DefaultListModel<>();
    private final DefaultListModel<String> watchlistModel = new DefaultListModel<>();
    private final JTextArea eventArea = new JTextArea(5, 30);
    private final JTextArea replayArea = new JTextArea(4, 30);
    private Listener listener;

    public GameDashboardPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("遊戲進度"));
        add(createControlPanel(), BorderLayout.NORTH);
        add(createProgressPanel(), BorderLayout.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void applySettings(GameSettings settings) {
        if (settings == null) return;
        modeCombo.setSelectedItem(settings.getMode());
        speedCombo.setSelectedItem(settings.getSpeed());
        seedLabel.setText(Long.toString(settings.getSeed()));
    }

    public void updateProgress(List<MissionTracker.MissionSnapshot> missions,
            List<String> achievements, int score, String rank) {
        SwingUtilities.invokeLater(() -> {
            missionModel.clear();
            if (missions != null) {
                for (MissionTracker.MissionSnapshot mission : missions) {
                    missionModel.addElement(mission.displayText());
                }
            }
            achievementModel.clear();
            if (achievements != null) {
                for (String achievement : achievements) {
                    achievementModel.addElement("✓ " + achievement);
                }
            }
            scoreLabel.setText(Integer.toString(score));
            rankLabel.setText(rank == null ? "-" : rank);
            replayArea.setText(String.format("seed=%s mode=%s speed=%s score=%d rank=%s%n可用相同 seed 重現相近市場節奏。",
                    seedLabel.getText(), modeCombo.getSelectedItem(), speedCombo.getSelectedItem(),
                    score, rank == null ? "-" : rank));
        });
    }

    public void updateWatchlist(List<MarketWatchlist.InstrumentSnapshot> instruments) {
        SwingUtilities.invokeLater(() -> {
            watchlistModel.clear();
            if (instruments != null) {
                for (MarketWatchlist.InstrumentSnapshot instrument : instruments) {
                    watchlistModel.addElement(instrument.displayText());
                }
            }
        });
    }

    public void appendEvent(String text) {
        if (text == null || text.isBlank()) return;
        SwingUtilities.invokeLater(() -> {
            eventArea.append(text + "\n");
            eventArea.setCaretPosition(eventArea.getDocument().getLength());
        });
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("模式與速度"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.gridy = 0;
        panel.add(new JLabel("模式"), gc);
        gc.gridx = 1;
        panel.add(modeCombo, gc);
        gc.gridx = 2;
        panel.add(new JLabel("速度"), gc);
        gc.gridx = 3;
        panel.add(speedCombo, gc);

        gc.gridx = 0; gc.gridy = 1;
        panel.add(new JLabel("種子"), gc);
        gc.gridx = 1;
        seedLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(seedLabel, gc);
        gc.gridx = 2;
        JButton saveButton = new JButton("保存設定");
        panel.add(saveButton, gc);

        modeCombo.addActionListener(e -> {
            if (listener != null) listener.onModeChanged((GameMode) modeCombo.getSelectedItem());
        });
        speedCombo.addActionListener(e -> {
            if (listener != null) listener.onSpeedChanged((SimulationSpeed) speedCombo.getSelectedItem());
        });
        saveButton.addActionListener(e -> {
            if (listener != null) listener.onSaveSettings();
        });
        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        summary.add(new JLabel("分數"));
        summary.add(scoreLabel);
        summary.add(new JLabel("評級"));
        summary.add(rankLabel);
        panel.add(summary, BorderLayout.NORTH);

        JList<String> missions = new JList<>(missionModel);
        JList<String> achievements = new JList<>(achievementModel);
        JList<String> watchlist = new JList<>(watchlistModel);
        JScrollPane missionScroll = new JScrollPane(missions);
        missionScroll.setBorder(BorderFactory.createTitledBorder("本局任務"));
        JScrollPane achievementScroll = new JScrollPane(achievements);
        achievementScroll.setBorder(BorderFactory.createTitledBorder("成就"));
        JScrollPane watchlistScroll = new JScrollPane(watchlist);
        watchlistScroll.setBorder(BorderFactory.createTitledBorder("多標的觀察"));
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, achievementScroll, watchlistScroll);
        rightSplit.setResizeWeight(0.55);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, missionScroll, rightSplit);
        split.setResizeWeight(0.62);
        split.setPreferredSize(new Dimension(560, 220));
        panel.add(split, BorderLayout.CENTER);

        eventArea.setEditable(false);
        eventArea.setLineWrap(true);
        eventArea.setWrapStyleWord(true);
        eventArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane eventScroll = new JScrollPane(eventArea);
        eventScroll.setBorder(BorderFactory.createTitledBorder("事件紀錄"));
        replayArea.setEditable(false);
        replayArea.setLineWrap(true);
        replayArea.setWrapStyleWord(true);
        JScrollPane replayScroll = new JScrollPane(replayArea);
        replayScroll.setBorder(BorderFactory.createTitledBorder("Replay / 分享摘要"));
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventScroll, replayScroll);
        bottomSplit.setResizeWeight(0.65);
        panel.add(bottomSplit, BorderLayout.SOUTH);
        return panel;
    }

    public interface Listener {
        void onModeChanged(GameMode mode);
        void onSpeedChanged(SimulationSpeed speed);
        void onSaveSettings();
    }
}
