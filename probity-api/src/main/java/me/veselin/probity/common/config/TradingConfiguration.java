package me.veselin.probity.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for trading-related constants.
 * Centralizes magic numbers used across portfolio, simulation, and risk calculations.
 */
@Data
@Component
@ConfigurationProperties(prefix = "probity.trading")
public class TradingConfiguration {

    /**
     * Risk-free rate (as decimal, e.g., 0.045 for 4.5%).
     * Used in risk calculations.
     */
    private double riskFreeRate = 0.045;

    /**
     * Standard trading days per year (NYSE calendar).
     * Used for annualizing daily volatility and returns.
     */
    private double tradingDaysPerYear = 252.0;

    /**
     * Default historical lookback period in trading days for parameter estimation.
     * Used to derive μ and σ from historical market data.
     */
    private int historyLookbackDays = 90;

    /**
     * Z-score for 95% confidence level (standard normal distribution).
     * Used for VaR calculations.
     */
    private double zScore95 = 1.645;

    /**
     * Volatility threshold (%) for risk score addon calculation.
     * Assets above this threshold receive additional risk points.
     */
    private int volatilityThreshold = 15;

    /**
     * Volatility band width (%) for risk score calculation.
     * Used to determine how many bands above the threshold.
     */
    private int volatilityAddonFactor = 10;

    /**
     * Risk score increment per volatility band.
     */
    private int riskScoreIncrement = 5;

    /**
     * Default annual volatility assumption (as decimal, e.g., 0.15 for 15%).
     * Used when insufficient historical data is available.
     */
    private double defaultAnnualVolatility = 0.15;
}
