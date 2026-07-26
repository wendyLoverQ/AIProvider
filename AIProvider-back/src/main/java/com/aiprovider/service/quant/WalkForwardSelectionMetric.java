package com.aiprovider.service.quant;

public enum WalkForwardSelectionMetric {
  TRAIN_TOTAL_RETURN_RATIO(false),
  TRAIN_PROFIT_FACTOR(false),
  TRAIN_NET_PROFIT(false),
  TRAIN_WIN_RATE(false),
  TRAIN_MAXIMUM_DRAWDOWN_RATIO(true);

  private final boolean ascending;

  WalkForwardSelectionMetric(boolean ascending) {
    this.ascending = ascending;
  }

  public boolean isAscending() {
    return ascending;
  }

  public String column() {
    return switch (this) {
      case TRAIN_TOTAL_RETURN_RATIO -> "tr.TotalReturnRatio";
      case TRAIN_PROFIT_FACTOR -> "tr.ProfitFactor";
      case TRAIN_NET_PROFIT -> "tr.NetProfit";
      case TRAIN_WIN_RATE -> "tr.WinRate";
      case TRAIN_MAXIMUM_DRAWDOWN_RATIO -> "tr.MaximumDrawdownRatio";
    };
  }
}
