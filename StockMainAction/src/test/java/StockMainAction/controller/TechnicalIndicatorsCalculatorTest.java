package StockMainAction.controller;

import org.junit.Test;

import static org.junit.Assert.*;

public class TechnicalIndicatorsCalculatorTest {
    @Test
    public void macdReturnsNullBeforeEnoughPriceData() {
        TechnicalIndicatorsCalculator calculator = new TechnicalIndicatorsCalculator();

        for (int i = 0; i < 20; i++) {
            calculator.updatePriceData(10.0 + i * 0.01, 10.1 + i * 0.01, 9.9 + i * 0.01);
        }

        assertNull(calculator.calculateMACD());
    }

    @Test
    public void macdProducesFiniteValuesAfterEnoughPriceData() {
        TechnicalIndicatorsCalculator calculator = new TechnicalIndicatorsCalculator();

        for (int i = 0; i < 60; i++) {
            double price = 10.0 + Math.sin(i / 5.0) * 0.2 + i * 0.01;
            calculator.updatePriceData(price, price + 0.1, price - 0.1);
        }

        double[] macd = calculator.calculateMACD();

        assertNotNull(macd);
        assertEquals(3, macd.length);
        assertTrue(Double.isFinite(macd[0]));
        assertTrue(Double.isFinite(macd[1]));
        assertTrue(Double.isFinite(macd[2]));
    }

    @Test
    public void invalidPriceDataIsIgnoredAndDoesNotPoisonMacd() {
        TechnicalIndicatorsCalculator calculator = new TechnicalIndicatorsCalculator();
        calculator.updatePriceData(Double.NaN, Double.NaN, Double.NaN);
        calculator.updatePriceData(-1.0, -1.0, -1.0);

        for (int i = 0; i < 60; i++) {
            double price = 10.0 + i * 0.02;
            calculator.updatePriceData(price, Double.NaN, Double.NaN);
        }

        double[] macd = calculator.calculateMACD();

        assertNotNull(macd);
        assertTrue(Double.isFinite(macd[0]));
        assertTrue(Double.isFinite(macd[1]));
        assertTrue(Double.isFinite(macd[2]));
    }
}
