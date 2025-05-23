// === 1. 創建新的價格提醒管理器 ===
// 位置: controller/PriceAlertManager.java
package StockMainAction.controller;

import StockMainAction.model.core.PriceAlert;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import java.awt.Toolkit;

/**
 * 價格提醒管理器 - 負責價格提醒的業務邏輯
 */
public class PriceAlertManager {
    private List<PriceAlert> alerts;
    private double currentPrice;
    private double previousPrice;
    
    public PriceAlertManager() {
        this.alerts = new ArrayList<>();
    }
    
    public void addAlert(PriceAlert alert) {
        alerts.add(alert);
    }
    
    public void removeAlert(int index) {
        if (index >= 0 && index < alerts.size()) {
            alerts.remove(index);
        }
    }
    
    public void clearAllAlerts() {
        alerts.clear();
    }
    
    public void updatePrice(double newPrice) {
        this.previousPrice = this.currentPrice;
        this.currentPrice = newPrice;
        checkAlerts();
    }
    
    private void checkAlerts() {
        for (PriceAlert alert : alerts) {
            if (alert.checkAlert(currentPrice, previousPrice)) {
                triggerAlert(alert);
            }
        }
    }
    
    private void triggerAlert(PriceAlert alert) {
        if (alert.isSoundEnabled()) {
            Toolkit.getDefaultToolkit().beep();
        }
        
        if (alert.isPopupEnabled()) {
            String message = String.format("價格提醒觸發！\n當前價格: %.2f", currentPrice);
            JOptionPane.showMessageDialog(null, message, "價格提醒", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    public List<PriceAlert> getAlerts() { return new ArrayList<>(alerts); }
    public double getCurrentPrice() { return currentPrice; }
}