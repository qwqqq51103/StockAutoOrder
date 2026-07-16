package StockMainAction.view;

import StockMainAction.model.StockMarketModel;
import StockMainAction.model.core.Transaction;
import StockMainAction.view.transaction.TransactionHistoryPanel;
import StockMainAction.view.swing.SwingUpdateCoalescer;
import StockMainAction.view.swing.WindowSizing;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Window adapter for the bounded, incremental transaction history panel. */
public class TransactionHistoryViewer extends JFrame implements StockMarketModel.TransactionListener {
    private final StockMarketModel model;
    private final TransactionHistoryPanel historyPanel;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final ArrayDeque<Transaction> pendingTransactions = new ArrayDeque<>();
    private final SwingUpdateCoalescer transactionFlush =
            new SwingUpdateCoalescer(50, this::flushTransactions);
    private final ComponentAdapter visibilityListener;
    private final WindowStateListener windowStateListener;

    public TransactionHistoryViewer(StockMarketModel model) {
        this.model = model;
        this.historyPanel = new TransactionHistoryPanel();

        setTitle("成交記錄管理中心");
        WindowSizing.apply(this, new Dimension(1500, 900),
                new Dimension(1000, 650), null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setContentPane(historyPanel);

        visibilityListener = new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent event) {
                updateRefreshState();
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                updateRefreshState();
            }
        };
        windowStateListener = event -> updateRefreshState();
        addComponentListener(visibilityListener);
        addWindowStateListener(windowStateListener);

        if (model != null) {
            // Register first. An event racing with the snapshot is harmless because the panel de-duplicates IDs.
            model.addTransactionListener(this);
            historyPanel.addTransactions(model.getTransactionHistory());
        }
        updateRefreshState();
    }

    @Override
    public void onTransactionAdded(Transaction transaction) {
        enqueueTransaction(transaction);
    }

    public void addTransactions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        for (Transaction transaction : transactions) enqueueTransaction(transaction);
    }

    public void addTransaction(Transaction transaction) {
        onTransactionAdded(transaction);
    }

    public List<Transaction> getTransactionHistory() {
        if (SwingUtilities.isEventDispatchThread()) {
            flushTransactions();
            return historyPanel.getTransactions();
        }
        AtomicReference<List<Transaction>> result = new AtomicReference<>();
        invokeOnEdtAndWait(() -> {
            flushTransactions();
            result.set(historyPanel.getTransactions());
        });
        return result.get();
    }

    public void clearTransactionHistory() {
        runOnEdt(() -> {
            if (!disposed.get()) {
                synchronized (pendingTransactions) {
                    pendingTransactions.clear();
                }
                historyPanel.clear();
            }
        });
    }

    @Override
    public void dispose() {
        if (!SwingUtilities.isEventDispatchThread()) {
            invokeOnEdtAndWait(this::dispose);
            return;
        }
        if (!disposed.compareAndSet(false, true)) {
            return;
        }

        historyPanel.dispose();
        transactionFlush.close();
        synchronized (pendingTransactions) { pendingTransactions.clear(); }
        removeComponentListener(visibilityListener);
        removeWindowStateListener(windowStateListener);
        if (model != null) {
            model.removeTransactionListener(this);
        }
        super.dispose();
    }

    TransactionHistoryPanel getHistoryPanel() {
        return historyPanel;
    }

    private void updateRefreshState() {
        boolean active = isVisible()
                && (getExtendedState() & Frame.ICONIFIED) == 0
                && !disposed.get();
        historyPanel.setRefreshActive(active);
    }

    private void enqueueTransaction(Transaction transaction) {
        if (transaction == null || disposed.get()) return;
        synchronized (pendingTransactions) {
            if (pendingTransactions.size() >= TransactionHistoryPanel.DEFAULT_MAX_ROWS) {
                pendingTransactions.removeFirst();
            }
            pendingTransactions.addLast(transaction);
        }
        transactionFlush.request();
    }

    private void flushTransactions() {
        if (disposed.get()) return;
        List<Transaction> batch;
        synchronized (pendingTransactions) {
            if (pendingTransactions.isEmpty()) return;
            batch = new ArrayList<>(pendingTransactions);
            pendingTransactions.clear();
        }
        historyPanel.addTransactions(batch);
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            invokeOnEdtAndWait(action);
        }
    }

    private static void invokeOnEdtAndWait(Runnable action) {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Swing EDT", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Swing EDT action failed", exception.getCause());
        }
    }
}
