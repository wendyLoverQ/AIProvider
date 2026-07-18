package com.aiprovider.model.vo;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public class AwsBillingMonitorVO {
    private final Plan plan;
    private final Cost cost;
    private final Credits credits;
    private final FreeTier freeTier;
    private final OffsetDateTime collectedAt;

    public AwsBillingMonitorVO(Plan plan, Cost cost, Credits credits, FreeTier freeTier, OffsetDateTime collectedAt) {
        this.plan=plan; this.cost=cost; this.credits=credits; this.freeTier=freeTier; this.collectedAt=collectedAt;
    }
    public Plan getPlan(){return plan;} public Cost getCost(){return cost;} public Credits getCredits(){return credits;}
    public FreeTier getFreeTier(){return freeTier;} public OffsetDateTime getCollectedAt(){return collectedAt;}

    public static class SourceState {
        private final boolean available;
        private final String unavailableReason;
        protected SourceState(boolean available,String unavailableReason){this.available=available;this.unavailableReason=unavailableReason;}
        public boolean isAvailable(){return available;} public String getUnavailableReason(){return unavailableReason;}
    }
    public static class Plan extends SourceState {
        private final String type,status,currency; private final Double remainingCredits; private final OffsetDateTime expirationDate;
        public Plan(boolean available,String reason,String type,String status,Double remainingCredits,String currency,OffsetDateTime expirationDate){
            super(available,reason);this.type=type;this.status=status;this.remainingCredits=remainingCredits;this.currency=currency;this.expirationDate=expirationDate;
        }
        public String getType(){return type;} public String getStatus(){return status;} public Double getRemainingCredits(){return remainingCredits;}
        public String getCurrency(){return currency;} public OffsetDateTime getExpirationDate(){return expirationDate;}
    }
    public static class Cost extends SourceState {
        private final String periodStart,periodEnd,currency; private final Double netUnblendedCost,unblendedCost; private final boolean estimated;
        public Cost(boolean available,String reason,String periodStart,String periodEnd,Double netUnblendedCost,Double unblendedCost,String currency,boolean estimated){
            super(available,reason);this.periodStart=periodStart;this.periodEnd=periodEnd;this.netUnblendedCost=netUnblendedCost;
            this.unblendedCost=unblendedCost;this.currency=currency;this.estimated=estimated;
        }
        public String getPeriodStart(){return periodStart;} public String getPeriodEnd(){return periodEnd;} public Double getNetUnblendedCost(){return netUnblendedCost;}
        public Double getUnblendedCost(){return unblendedCost;} public String getCurrency(){return currency;} public boolean isEstimated(){return estimated;}
    }
    public static class Credits extends SourceState {
        private final Double remainingAmount,initialAmount; private final String currency; private final List<Credit> items;
        public Credits(boolean available,String reason,Double remainingAmount,Double initialAmount,String currency,List<Credit> items){
            super(available,reason);this.remainingAmount=remainingAmount;this.initialAmount=initialAmount;this.currency=currency;
            this.items=items==null?Collections.<Credit>emptyList():Collections.unmodifiableList(items);
        }
        public Double getRemainingAmount(){return remainingAmount;} public Double getInitialAmount(){return initialAmount;}
        public String getCurrency(){return currency;} public List<Credit> getItems(){return items;}
    }
    public static class Credit {
        private final String description,status,currency; private final Double initialAmount,remainingAmount; private final OffsetDateTime expirationDate;
        public Credit(String description,String status,Double initialAmount,Double remainingAmount,String currency,OffsetDateTime expirationDate){
            this.description=description;this.status=status;this.initialAmount=initialAmount;this.remainingAmount=remainingAmount;this.currency=currency;this.expirationDate=expirationDate;
        }
        public String getDescription(){return description;} public String getStatus(){return status;} public Double getInitialAmount(){return initialAmount;}
        public Double getRemainingAmount(){return remainingAmount;} public String getCurrency(){return currency;} public OffsetDateTime getExpirationDate(){return expirationDate;}
    }
    public static class FreeTier extends SourceState {
        private final List<Usage> items;
        public FreeTier(boolean available,String reason,List<Usage> items){super(available,reason);this.items=items==null?Collections.<Usage>emptyList():Collections.unmodifiableList(items);}
        public List<Usage> getItems(){return items;}
    }
    public static class Usage {
        private final String service,operation,usageType,region,unit,description,freeTierType; private final Double actual,forecast,limit,usagePercent;
        public Usage(String service,String operation,String usageType,String region,Double actual,Double forecast,Double limit,String unit,String description,String freeTierType){
            this.service=service;this.operation=operation;this.usageType=usageType;this.region=region;this.actual=actual;this.forecast=forecast;
            this.limit=limit;this.unit=unit;this.description=description;this.freeTierType=freeTierType;
            this.usagePercent=limit!=null&&limit>0&&actual!=null?Math.max(0,actual/limit*100):null;
        }
        public String getService(){return service;} public String getOperation(){return operation;} public String getUsageType(){return usageType;}
        public String getRegion(){return region;} public Double getActual(){return actual;} public Double getForecast(){return forecast;}
        public Double getLimit(){return limit;} public String getUnit(){return unit;} public String getDescription(){return description;}
        public String getFreeTierType(){return freeTierType;} public Double getUsagePercent(){return usagePercent;}
    }
}
