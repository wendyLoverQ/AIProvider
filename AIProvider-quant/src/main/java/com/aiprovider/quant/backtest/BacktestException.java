package com.aiprovider.quant.backtest;

public class BacktestException extends RuntimeException { private final String errorCode; public BacktestException(String code,String message){super(message);this.errorCode=code;} public String getErrorCode(){return errorCode;} }
