package com.telcobright.scheduler.handler.impl;

import com.telcobright.scheduler.handler.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Job handler for Payment Gateway application.
 * Processes payment transactions.
 */
public class PaymentGatewayJobHandler implements JobHandler {
    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayJobHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        String accountId = (String) jobData.get("accountId");
        Object amountObj = jobData.get("amount");
        Double amount = amountObj instanceof Double ? (Double) amountObj :
            Double.parseDouble(amountObj.toString());
        String currency = (String) jobData.get("currency");
        String transactionType = (String) jobData.getOrDefault("transactionType", "PAYMENT");
        String entityId = (String) jobData.get("entityId");
        String appName = (String) jobData.get("appName");

        logger.info("Executing Payment job for app '{}', entity: {}", appName, entityId);
        logger.info("Processing {} of {} {} for account {}",
            transactionType, amount, currency, accountId);

        // Simulate payment processing
        Thread.sleep(300); // Simulate payment gateway delay

        // Here you would integrate with actual payment gateway
        // PaymentGateway.process(accountId, amount, currency, transactionType);

        logger.info("Payment processed successfully at {}. Transaction ID: TXN-{}",
            LocalDateTime.now().format(TIME_FORMATTER), System.currentTimeMillis());
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        if (!jobData.containsKey("accountId")) {
            logger.error("Missing required field: accountId");
            return false;
        }
        if (!jobData.containsKey("amount")) {
            logger.error("Missing required field: amount");
            return false;
        }
        if (!jobData.containsKey("currency")) {
            logger.error("Missing required field: currency");
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "PaymentGatewayJobHandler";
    }
}