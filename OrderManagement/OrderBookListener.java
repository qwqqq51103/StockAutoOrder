package OrderManagement;

/**
 * OrderBookListener 接口，用於監聽 OrderBook 的更新
 */
public interface OrderBookListener {
    /**
     * 當 OrderBook 更新時調用的方法
     */
    void onOrderBookUpdated();
}
