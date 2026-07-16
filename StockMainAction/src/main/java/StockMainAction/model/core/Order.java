package StockMainAction.model.core;

import StockMainAction.model.user.UserAccount;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Mutable remaining quantity with immutable order identity and intent. */
public class Order {
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong();

    private final String id;
    private final OrderSide side;
    private final OrderType orderType;
    private double price;
    private final int originalVolume;
    private int volume;
    private final Trader trader;
    private final UserAccount traderAccount;
    private final long timestamp;
    private final long sequence;
    private final boolean simulation;
    private OrderStatus status;

    public Order(String type, double price, int volume, Trader trader,
            boolean isSimulation, boolean isMarketOrder, boolean isFillOrKill) {
        this(OrderSide.fromLegacy(type), price, volume, trader, isSimulation,
                isMarketOrder ? OrderType.MARKET : isFillOrKill ? OrderType.FOK : OrderType.LIMIT);
    }

    public Order(OrderSide side, double price, int volume, Trader trader,
            boolean simulation, OrderType orderType) {
        this(side, price, volume, trader, simulation, orderType, Clock.systemUTC());
    }

    public Order(OrderSide side, double price, int volume, Trader trader,
            boolean simulation, OrderType orderType, Clock clock) {
        if (side == null || orderType == null) {
            throw new IllegalArgumentException("Order side and type are required");
        }
        if (trader == null || trader.getAccount() == null) {
            throw new IllegalArgumentException("Trader and account are required");
        }
        if (volume <= 0) {
            throw new IllegalArgumentException("Order volume must be positive");
        }
        validatePrice(price, orderType);

        this.id = UUID.randomUUID().toString();
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.originalVolume = volume;
        this.volume = volume;
        this.trader = trader;
        this.traderAccount = trader.getAccount();
        this.timestamp = Objects.requireNonNull(clock, "clock").millis();
        this.sequence = NEXT_SEQUENCE.incrementAndGet();
        this.simulation = simulation;
        this.status = OrderStatus.NEW;
    }

    private Order(Order source) {
        this.id = source.id;
        this.side = source.side;
        this.orderType = source.orderType;
        this.price = source.price;
        this.originalVolume = source.originalVolume;
        this.volume = source.volume;
        this.trader = source.trader;
        this.traderAccount = source.traderAccount;
        this.timestamp = source.timestamp;
        this.sequence = source.sequence;
        this.simulation = source.simulation;
        this.status = source.status;
    }

    public static Order createMarketBuyOrder(int volume, Trader trader) {
        return new Order(OrderSide.BUY, 0, volume, trader, false, OrderType.MARKET);
    }

    public static Order createMarketSellOrder(int volume, Trader trader) {
        return new Order(OrderSide.SELL, 0, volume, trader, false, OrderType.MARKET);
    }

    public static Order createLimitBuyOrder(double price, int volume, Trader trader) {
        return new Order(OrderSide.BUY, price, volume, trader, false, OrderType.LIMIT);
    }

    public static Order createLimitSellOrder(double price, int volume, Trader trader) {
        return new Order(OrderSide.SELL, price, volume, trader, false, OrderType.LIMIT);
    }

    public static Order createFokBuyOrder(double price, int volume, Trader trader) {
        return new Order(OrderSide.BUY, price, volume, trader, false, OrderType.FOK);
    }

    public static Order createFokSellOrder(double price, int volume, Trader trader) {
        return new Order(OrderSide.SELL, price, volume, trader, false, OrderType.FOK);
    }

    public String getId() { return id; }
    public String getType() { return side.legacyValue(); }
    public OrderSide getSide() { return side; }
    public OrderType getOrderType() { return orderType; }
    public double getPrice() { return price; }
    public int getOriginalVolume() { return originalVolume; }
    public int getVolume() { return volume; }
    public Trader getTrader() { return trader; }
    public UserAccount getTraderAccount() { return traderAccount; }
    public long getTimestamp() { return timestamp; }
    public long getSequence() { return sequence; }
    public boolean isSimulation() { return simulation; }
    public boolean isMarketOrder() { return orderType == OrderType.MARKET; }
    public boolean isFillOrKill() { return orderType == OrderType.FOK; }
    public OrderStatus getStatus() { return status; }

    void setVolume(int volume) {
        if (volume < 0) {
            throw new IllegalArgumentException("Order volume must not be negative");
        }
        this.volume = volume;
        if (volume == 0) {
            status = OrderStatus.FILLED;
        } else if (volume < originalVolume) {
            status = OrderStatus.PARTIALLY_FILLED;
        } else {
            status = OrderStatus.OPEN;
        }
    }

    void setPrice(double price) {
        validatePrice(price, orderType);
        this.price = price;
    }

    void markOpen() { status = OrderStatus.OPEN; }
    void markCancelled() { status = OrderStatus.CANCELLED; }
    void markRejected() { status = OrderStatus.REJECTED; }

    Order detachedCopy() { return new Order(this); }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %s @ %.2f x %d (%s)",
                side, orderType, price, volume, trader.getTraderType());
    }

    private static void validatePrice(double price, OrderType orderType) {
        if (!Double.isFinite(price)) {
            throw new IllegalArgumentException("Order price must be finite");
        }
        if (orderType == OrderType.MARKET && price != 0.0) {
            throw new IllegalArgumentException("Market order price must be zero");
        }
        if (orderType != OrderType.MARKET && price <= 0.0) {
            throw new IllegalArgumentException("Limit order price must be positive");
        }
    }
}
