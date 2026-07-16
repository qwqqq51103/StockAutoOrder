package com.stockgame.server.gui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * Spring Boot 伺服器圖形控制台（Swing 深色主題，Windows 中文相容版）。
 */
public class ServerDashboard extends JFrame {

    // ── 配色 ─────────────────────────────────────────────────────────────────
    private static final Color BG       = new Color(13, 17, 23);
    private static final Color BG2      = new Color(22, 27, 34);
    private static final Color BG3      = new Color(33, 38, 45);
    private static final Color BORDER_C = new Color(48, 54, 61);
    private static final Color TEXT     = new Color(230, 237, 243);
    private static final Color MUTED    = new Color(125, 133, 144);
    private static final Color GREEN    = new Color(63, 185, 80);
    private static final Color RED      = new Color(248, 81, 73);
    private static final Color BLUE     = new Color(88, 166, 255);

    // ── 全域字型（支援中文） ────────────────────────────────────────────────
    private static final String FONT_NAME = detectChineseFont();

    private static String detectChineseFont() {
        String[] candidates = {
            "Microsoft JhengHei UI", "Microsoft JhengHei",
            "微軟正黑體", "新細明體", "Dialog"
        };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.util.Set<String> available = new java.util.HashSet<>();
        for (Font f : ge.getAllFonts()) available.add(f.getFamily());
        for (String c : candidates) {
            if (available.contains(c)) return c;
        }
        return Font.DIALOG;
    }

    // ── 元件 ─────────────────────────────────────────────────────────────────
    private JButton   startBtn, stopBtn, openBrowserBtn;
    private JLabel    statusDot, statusLabel, uptimeLabel;
    private JLabel    priceLabel, changeLabel;
    private JLabel    openVal, highVal, lowVal, volVal;
    private JTextArea logArea;
    private DefaultTableModel playersModel;

    // ── 狀態 ─────────────────────────────────────────────────────────────────
    private Process  serverProcess;
    private boolean  serverRunning = false;
    private Instant  startTime;

    private final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "dashboard-poll");
            t.setDaemon(true); return t;
        });
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String API = "http://localhost:8080";
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── 建構 ─────────────────────────────────────────────────────────────────
    public ServerDashboard() {
        super("股市遊戲  伺服器控制台");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { confirmClose(); }
        });
        setSize(1150, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        // 全域字型設定
        setUIFont(new Font(FONT_NAME, Font.PLAIN, 13));

        buildUI();
        startPolling();
        log("【系統】控制台就緒，按「啟動伺服器」開始。");
        log("【系統】字型：" + FONT_NAME);
    }

    /** 設定全域 Swing 字型 */
    private static void setUIFont(Font font) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object val = UIManager.get(key);
            if (val instanceof Font) UIManager.put(key, font);
        }
    }

    // ── UI 組建 ──────────────────────────────────────────────────────────────
    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildTopBar(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildBottom(),  BorderLayout.SOUTH);
    }

    private JPanel buildTopBar() {
        JPanel bar = bg(BG2, new BorderLayout());
        bar.setBorder(new CompoundBorder(
            new MatteBorder(0,0,1,0,BORDER_C), new EmptyBorder(10,16,10,16)));

        JLabel logo = lbl("[ 股市模擬遊戲 ]  伺服器控制台", 17, Font.BOLD, BLUE);
        bar.add(logo, BorderLayout.WEST);

        JPanel btns = bg(BG2, new FlowLayout(FlowLayout.RIGHT, 8, 0));
        startBtn       = mkBtn("▶  啟動伺服器",  GREEN, Color.BLACK, e -> doStart());
        stopBtn        = mkBtn("■  停止伺服器",  RED,   Color.WHITE, e -> doStop());
        openBrowserBtn = mkBtn(">>  開啟遊戲網頁", BLUE, Color.WHITE, e -> openBrowser());
        stopBtn.setEnabled(false);
        btns.add(startBtn); btns.add(stopBtn); btns.add(openBrowserBtn);
        bar.add(btns, BorderLayout.EAST);
        return bar;
    }

    private JSplitPane buildCenter() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeft(), buildLogPanel());
        sp.setDividerLocation(440);
        sp.setDividerSize(5);
        sp.setBackground(BG3);
        return sp;
    }

    // ── 左側面板 ─────────────────────────────────────────────────────────────
    private JPanel buildLeft() {
        JPanel p = bg(BG, new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 10, 10, 6));
        JPanel top = bg(BG, new GridLayout(2, 1, 0, 8));
        top.add(buildStatusCard());
        top.add(buildMarketCard());
        p.add(top,           BorderLayout.NORTH);
        p.add(buildPlayers(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStatusCard() {
        JPanel card = card("【 伺服器狀態 】");

        JPanel row = bg(BG2, new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusDot   = lbl("●", 18, Font.PLAIN, RED);
        statusLabel = lbl("已停止", 14, Font.BOLD, RED);
        uptimeLabel = lbl("", 11, Font.PLAIN, MUTED);
        row.add(statusDot); row.add(statusLabel); row.add(uptimeLabel);
        card.add(row);

        JPanel grid = bg(BG2, new GridLayout(2, 2, 4, 4));
        grid.setBorder(new EmptyBorder(4, 0, 0, 0));
        grid.add(infoRow("REST API",   API + "/api"));
        grid.add(infoRow("WebSocket",  API + "/ws"));
        grid.add(infoRow("遊戲網頁",   "localhost:8080"));
        grid.add(infoRow("資料庫",     "MySQL  localhost:3306"));
        card.add(grid);
        return card;
    }

    private JPanel buildMarketCard() {
        JPanel card = card("【 即時行情 】");

        JPanel priceRow = bg(BG2, new FlowLayout(FlowLayout.LEFT, 10, 2));
        priceLabel  = lbl("--", 30, Font.BOLD, TEXT);
        changeLabel = lbl("--", 14, Font.BOLD, MUTED);
        priceRow.add(priceLabel); priceRow.add(changeLabel);
        card.add(priceRow);

        JPanel stats = bg(BG2, new GridLayout(1, 4, 6, 0));
        stats.setBorder(new EmptyBorder(4, 0, 0, 0));
        openVal = lbl("--", 13, Font.BOLD, TEXT);
        highVal = lbl("--", 13, Font.BOLD, GREEN);
        lowVal  = lbl("--", 13, Font.BOLD, RED);
        volVal  = lbl("--", 13, Font.BOLD, BLUE);
        stats.add(statBox("開盤", openVal));
        stats.add(statBox("最高", highVal));
        stats.add(statBox("最低", lowVal));
        stats.add(statBox("成交量", volVal));
        card.add(stats);
        return card;
    }

    private JPanel buildPlayers() {
        JPanel card = bg(BG2, new BorderLayout(0, 6));
        card.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1,true), new EmptyBorder(10,12,10,12)));
        card.add(lbl("【 排行榜（依已實現損益）】", 12, Font.BOLD, MUTED), BorderLayout.NORTH);

        String[] cols = {"排名", "玩家", "已實現損益"};
        playersModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(playersModel);
        styleTable(tbl);
        JScrollPane sp = new JScrollPane(tbl);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_C));
        sp.getViewport().setBackground(BG2);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // ── Log 面板 ──────────────────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel p = bg(BG, new BorderLayout(0, 6));
        p.setBorder(new EmptyBorder(10, 6, 10, 10));

        JPanel header = bg(BG, new BorderLayout());
        header.add(lbl("【 伺服器 Log 輸出 】", 13, Font.BOLD, MUTED), BorderLayout.WEST);
        JButton clr = mkBtn("清除", BG3, MUTED, e -> logArea.setText(""));
        clr.setFont(new Font(FONT_NAME, Font.PLAIN, 11));
        clr.setPreferredSize(new Dimension(58, 24));
        header.add(clr, BorderLayout.EAST);
        p.add(header, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
        logArea.setBackground(new Color(8, 12, 18));
        logArea.setForeground(new Color(150, 210, 150));
        logArea.setCaretColor(GREEN);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_C));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBottom() {
        JPanel bar = bg(BG3, new BorderLayout());
        bar.setBorder(new CompoundBorder(
            new MatteBorder(1,0,0,0,BORDER_C), new EmptyBorder(5,14,5,14)));
        bar.add(lbl("Stock Game Server v1.0  |  Java 17  |  Spring Boot 3.2.5  |  MySQL", 11, Font.PLAIN, MUTED), BorderLayout.WEST);
        bar.add(lbl("Port: 8080", 11, Font.PLAIN, MUTED), BorderLayout.EAST);
        return bar;
    }

    // ── 控制邏輯 ─────────────────────────────────────────────────────────────
    private void doStart() {
        startBtn.setEnabled(false);
        log("【系統】正在啟動 Spring Boot 伺服器...");
        // 啟動前先確認 8080 是否被佔用
        killPort(8080);

        Thread t = new Thread(() -> {
            try {
                String mvnw = System.getProperty("user.dir") + "\\mvnw.cmd";
                ProcessBuilder pb = new ProcessBuilder(mvnw, "clean", "spring-boot:run");
                pb.directory(new java.io.File(System.getProperty("user.dir")));
                pb.environment().put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");
                pb.redirectErrorStream(true);
                serverProcess = pb.start();
                startTime     = Instant.now();

                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(serverProcess.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String l2 = line;
                    SwingUtilities.invokeLater(() -> {
                        if (l2.contains("Started StockGameServerApplication")) setRunning(true);
                        if (!l2.contains("Downloading") && !l2.contains("Downloaded"))
                            log(l2);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> log("【錯誤】" + ex.getMessage()));
            }
            SwingUtilities.invokeLater(() -> setRunning(false));
        }, "server-thread");
        t.setDaemon(true);
        t.start();
    }

    private void doStop() {
        log("【系統】正在停止伺服器...");
        // 1. 殺掉 Maven + 所有子程序
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.descendants().forEach(ProcessHandle::destroyForcibly);
            serverProcess.destroyForcibly();
        }
        // 2. 透過 netstat 找到佔用 8080 的 PID，用 taskkill 強制結束
        killPort(8080);
        setRunning(false);
        log("【系統】伺服器已停止");
    }

    /**
     * 找出佔用指定 port 的所有程序並強制結束。
     */
    private void killPort(int port) {
        try {
            Process netstat = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "netstat -ano"});
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(netstat.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(":" + port + " ") && line.contains("LISTENING")) {
                    String[] parts = line.trim().split("\\s+");
                    String pid = parts[parts.length - 1];
                    if (pid.matches("\\d+") && !pid.equals("0")) {
                        Runtime.getRuntime().exec(new String[]{"taskkill", "/F", "/PID", pid});
                        log("【系統】已終止 port " + port + " 程序  PID: " + pid);
                    }
                }
            }
            br.close();
            Thread.sleep(800);   // 等待系統確實釋放 port
        } catch (Exception e) {
            log("【系統】killPort 例外：" + e.getMessage());
        }
    }

    private void setRunning(boolean on) {
        serverRunning = on;
        SwingUtilities.invokeLater(() -> {
            startBtn.setEnabled(!on);
            stopBtn.setEnabled(on);
            statusDot.setForeground(on ? GREEN : RED);
            statusLabel.setText(on ? "運行中" : "已停止");
            statusLabel.setForeground(on ? GREEN : RED);
            if (!on) {
                uptimeLabel.setText("");
                // 清空行情顯示
                priceLabel.setText("--");
                changeLabel.setText("--");
                openVal.setText("--"); highVal.setText("--");
                lowVal.setText("--");  volVal.setText("--");
            } else {
                log("【系統】✓ 伺服器啟動成功！監聽 port 8080");
            }
        });
    }

    private void openBrowser() {
        try { Desktop.getDesktop().browse(new URI("http://localhost:8080")); }
        catch (Exception e) { log("【提示】請手動開啟 http://localhost:8080"); }
    }

    private void confirmClose() {
        if (JOptionPane.showConfirmDialog(this,
                "確定關閉控制台並停止伺服器？", "確認關閉",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            doStop();
            scheduler.shutdownNow();
            dispose();
            System.exit(0);
        }
    }

    // ── 輪詢 ─────────────────────────────────────────────────────────────────
    private void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!serverRunning) return;
            try {
                pollQuote();
            } catch (java.net.ConnectException e) {
                // 伺服器尚未就緒，靜默等待
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> log("【行情輪詢】" + e.getMessage()));
            }
        }, 3, 1, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            if (!serverRunning) return;
            try { pollLeaderboard(); } catch (Exception ignored) {}
        }, 5, 5, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            if (!serverRunning || startTime == null) return;
            long s = Duration.between(startTime, Instant.now()).getSeconds();
            String up = String.format("已運行 %02d:%02d:%02d", s/3600, (s%3600)/60, s%60);
            SwingUtilities.invokeLater(() -> uptimeLabel.setText(up));
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void pollQuote() throws Exception {
        String body = get("/api/market/quote");
        double last   = dbl(body, "lastPrice");
        double change = dbl(body, "change");
        double pct    = dbl(body, "changePct");
        double open   = dbl(body, "openPrice");
        double high   = dbl(body, "highPrice");
        double low    = dbl(body, "lowPrice");
        int    vol    = (int) dbl(body, "totalVolume");
        boolean up    = change >= 0;

        SwingUtilities.invokeLater(() -> {
            priceLabel.setText(String.format("%.2f", last));
            priceLabel.setForeground(up ? GREEN : RED);
            changeLabel.setText(String.format("%s%.2f (%s%.2f%%)", up?"+":"", change, up?"+":"", pct));
            changeLabel.setForeground(up ? GREEN : RED);
            openVal.setText(String.format("%.2f", open));
            highVal.setText(String.format("%.2f", high));
            lowVal.setText(String.format("%.2f", low));
            volVal.setText(String.format("%,d", vol));
        });
    }

    private void pollLeaderboard() throws Exception {
        String body = get("/api/account/leaderboard");
        SwingUtilities.invokeLater(() -> {
            playersModel.setRowCount(0);
            if (body == null || body.length() < 5) return;
            int rank = 1;
            for (String entry : body.split("\\},\\{")) {
                double pnl = dbl(entry, "realizedPnl");
                playersModel.addRow(new Object[]{
                    "#" + rank++,
                    "玩家 " + rank,
                    (pnl >= 0 ? "+" : "") + String.format("%,.0f", pnl)
                });
            }
        });
    }

    // ── Log ──────────────────────────────────────────────────────────────────
    public void log(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now().format(TF) + "] " + line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            if (logArea.getLineCount() > 2000) {
                try { logArea.getDocument().remove(0, logArea.getLineEndOffset(500)); }
                catch (Exception ignored) {}
            }
        });
    }

    // ── UI 工廠 ───────────────────────────────────────────────────────────────
    private JPanel bg(Color c, LayoutManager lm) {
        JPanel p = new JPanel(lm); p.setBackground(c); return p;
    }
    private JPanel card(String title) {
        JPanel c = bg(BG2, null);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1,true), new EmptyBorder(10,12,10,12)));
        JLabel t = lbl(title, 12, Font.BOLD, MUTED);
        t.setBorder(new EmptyBorder(0,0,8,0));
        c.add(t);
        return c;
    }
    private JPanel statBox(String title, JLabel val) {
        JPanel p = bg(BG3, new BorderLayout(0,3));
        p.setBorder(new CompoundBorder(new LineBorder(BORDER_C,1,true), new EmptyBorder(6,8,6,8)));
        p.add(lbl(title, 10, Font.PLAIN, MUTED), BorderLayout.NORTH);
        p.add(val, BorderLayout.CENTER);
        return p;
    }
    private JPanel infoRow(String k, String v) {
        JPanel p = bg(BG2, new FlowLayout(FlowLayout.LEFT, 3, 2));
        p.add(lbl(k + "：", 11, Font.PLAIN, MUTED));
        p.add(lbl(v, 11, Font.BOLD, BLUE));
        return p;
    }
    private JLabel lbl(String t, float sz, int style, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(new Font(FONT_NAME, style, (int) sz));
        l.setForeground(c);
        return l;
    }
    private JButton mkBtn(String text, Color bg, Color fg, ActionListener al) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(fg);
        b.setFont(new Font(FONT_NAME, Font.BOLD, 13));
        b.setBorder(new EmptyBorder(8,16,8,16));
        b.setFocusPainted(false);
        b.addActionListener(al);
        return b;
    }
    private void styleTable(JTable tbl) {
        tbl.setBackground(BG2); tbl.setForeground(TEXT);
        tbl.setGridColor(BORDER_C); tbl.setRowHeight(24);
        tbl.setFont(new Font(FONT_NAME, Font.PLAIN, 12));
        tbl.getTableHeader().setBackground(BG3);
        tbl.getTableHeader().setForeground(MUTED);
        tbl.getTableHeader().setFont(new Font(FONT_NAME, Font.BOLD, 12));
    }

    // ── HTTP 工具 ─────────────────────────────────────────────────────────────
    private String get(String path) throws Exception {
        HttpResponse<String> r = httpClient.send(
            HttpRequest.newBuilder().uri(URI.create(API + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        return r.body();
    }
    private double dbl(String json, String key) {
        try {
            int i = json.indexOf("\""+key+"\":");
            if (i < 0) return 0;
            int s = i + key.length() + 3, e = s;
            while (e < json.length() &&
                   (Character.isDigit(json.charAt(e))||json.charAt(e)=='.'||json.charAt(e)=='-')) e++;
            return Double.parseDouble(json.substring(s, e));
        } catch (Exception ex) { return 0; }
    }

    // ── 入口 ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        // 確保 UTF-8 輸出
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("stdout.encoding", "UTF-8");
        SwingUtilities.invokeLater(() -> new ServerDashboard().setVisible(true));
    }
}
