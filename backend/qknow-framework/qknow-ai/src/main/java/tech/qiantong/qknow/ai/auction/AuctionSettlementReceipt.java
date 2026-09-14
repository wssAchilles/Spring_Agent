package tech.qiantong.qknow.ai.auction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 不可变拍卖与信誉结算收据 (Java 21 Record, SHA-256 存证留痕)
 */
public record AuctionSettlementReceipt(
        String auctionId,
        String taskId,
        Map<String, List<String>> winnerTaskAllocation,
        Map<String, Double> vcgPayments,
        Map<String, Double> shapleyCreditDistribution,
        double totalSocialCost,
        String settlementHash,
        long timestampMs
) {

    public static AuctionSettlementReceipt createReceipt(
            String auctionId,
            String taskId,
            Map<String, List<String>> winnerTaskAllocation,
            Map<String, Double> vcgPayments,
            Map<String, Double> shapleyCreditDistribution,
            double totalSocialCost
    ) {
        long now = System.currentTimeMillis();
        Map<String, List<String>> allocation = Collections.unmodifiableMap(new HashMap<>(winnerTaskAllocation != null ? winnerTaskAllocation : Map.of()));
        Map<String, Double> payments = Collections.unmodifiableMap(new HashMap<>(vcgPayments != null ? vcgPayments : Map.of()));
        Map<String, Double> shapley = Collections.unmodifiableMap(new HashMap<>(shapleyCreditDistribution != null ? shapleyCreditDistribution : Map.of()));
        String hash = computeHash(auctionId, taskId, allocation, payments, shapley, totalSocialCost, now);

        return new AuctionSettlementReceipt(
                auctionId, taskId, allocation, payments, shapley, totalSocialCost, hash, now
        );
    }

    private static String computeHash(
            String auctionId, String taskId,
            Map<String, List<String>> alloc,
            Map<String, Double> payments,
            Map<String, Double> shapley,
            double cost, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = auctionId + ":" + taskId + ":" + alloc.hashCode() + ":" + payments.hashCode() + ":" + shapley.hashCode() + ":" + cost + ":" + ts;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "RECEIPT_HASH_" + auctionId + "_" + ts;
        }
    }
}
