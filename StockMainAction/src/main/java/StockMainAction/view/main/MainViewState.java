package StockMainAction.view.main;

/** Latest immutable market tick values accepted by the UI. */
public record MainViewState(int timeStep, double price, double sma, int volume) {
    public MainViewState withPrice(int nextTimeStep, double nextPrice, double nextSma) {
        return new MainViewState(nextTimeStep, nextPrice, nextSma, volume);
    }

    public MainViewState withVolume(int nextTimeStep, int nextVolume) {
        return new MainViewState(nextTimeStep, price, sma, nextVolume);
    }
}
