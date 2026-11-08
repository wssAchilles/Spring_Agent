package tech.qiantong.qknow.hermes.cognitive.core.evaluation.tau;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TauRetailTools {
    private static final Pattern CALC_TOKEN = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?|[()+*/-])\\s*");
    private final TauRetailEnvironment environment;

    public TauRetailTools(TauRetailEnvironment environment) {
        this.environment = environment;
    }

    public ToolResult call(TauRetailTaskLoader.ToolCall call) {
        ToolResult result;
        try {
            result = new ToolResult(true, dispatch(call.name(), call.arguments()), null);
        } catch (TauRetailEnvironment.TauRetailToolException e) {
            result = new ToolResult(false, new JSONObject(), e.code());
        } catch (RuntimeException e) {
            result = new ToolResult(false, new JSONObject(), "INVALID_ARGUMENT");
        }
        environment.record(call, result);
        return result;
    }

    private JSONObject dispatch(String name, JSONObject args) {
        return switch (name) {
            case "find_user_id_by_email" -> findUserByEmail(args.getString("email"));
            case "find_user_id_by_name_zip" -> findUserByNameZip(args);
            case "get_user_details" -> required(environment.table("users"), args.getString("user_id"), "USER_NOT_FOUND");
            case "get_order_details" -> required(environment.table("orders"), args.getString("order_id"), "ORDER_NOT_FOUND");
            case "get_product_details" -> required(environment.table("products"), args.getString("product_id"), "PRODUCT_NOT_FOUND");
            case "get_item_details" -> getItemDetails(args.getString("item_id"));
            case "calculate" -> calculate(args.getString("expression"));
            case "cancel_pending_order" -> cancelOrder(args);
            case "modify_pending_order_address" -> modifyOrderAddress(args);
            case "modify_pending_order_items" -> replaceItems(args, true);
            case "exchange_delivered_order_items" -> replaceItems(args, false);
            case "return_delivered_order_items" -> returnItems(args);
            case "modify_pending_order_payment" -> modifyPayment(args);
            case "modify_user_address" -> modifyUserAddress(args);
            case "transfer_to_human_agents" -> transfer(args);
            default -> throw new TauRetailEnvironment.TauRetailToolException("UNKNOWN_TOOL", "tool is not registered");
        };
    }

    private JSONObject findUserByEmail(String email) {
        return environment.table("users").values().stream().map(JSONObject.class::cast)
                .filter(user -> email != null && email.equalsIgnoreCase(user.getString("email")))
                .findFirst().map(user -> new JSONObject(Map.of("user_id", user.getString("user_id"))))
                .orElseThrow(() -> new TauRetailEnvironment.TauRetailToolException("USER_NOT_FOUND", "user not found"));
    }

    private JSONObject findUserByNameZip(JSONObject args) {
        return environment.table("users").values().stream().map(JSONObject.class::cast)
                .filter(user -> {
                    JSONObject name = user.getJSONObject("name");
                    JSONObject address = user.getJSONObject("address");
                    return name != null && address != null
                            && args.getString("first_name").equalsIgnoreCase(name.getString("first_name"))
                            && args.getString("last_name").equalsIgnoreCase(name.getString("last_name"))
                            && args.getString("zip").equals(address.getString("zip"));
                })
                .findFirst().map(user -> new JSONObject(Map.of("user_id", user.getString("user_id"))))
                .orElseThrow(() -> new TauRetailEnvironment.TauRetailToolException("USER_NOT_FOUND", "user not found"));
    }

    private JSONObject getItemDetails(String itemId) {
        for (Object raw : environment.table("products").values()) {
            JSONObject product = (JSONObject) raw;
            JSONObject variants = product.getJSONObject("variants");
            JSONObject variant = variants == null ? null : variants.getJSONObject(itemId);
            if (variant != null) {
                return TauRetailEnvironment.copy(variant);
            }
        }
        throw new TauRetailEnvironment.TauRetailToolException("ITEM_NOT_FOUND", "item not found");
    }

    private JSONObject calculate(String expression) {
        Matcher matcher = CALC_TOKEN.matcher(expression == null ? "" : expression);
        List<String> tokens = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (!expression.substring(cursor, matcher.start()).isBlank()) {
                throw new TauRetailEnvironment.TauRetailToolException("INVALID_EXPRESSION", "unsupported expression");
            }
            tokens.add(matcher.group(1));
            cursor = matcher.end();
        }
        if (tokens.isEmpty() || !expression.substring(cursor).isBlank()) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_EXPRESSION", "unsupported expression");
        }
        ExpressionParser parser = new ExpressionParser(tokens);
        BigDecimal result = parser.expression();
        if (!parser.atEnd()) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_EXPRESSION", "unsupported expression");
        }
        return new JSONObject(Map.of("result", result.stripTrailingZeros().toPlainString()));
    }

    private JSONObject cancelOrder(JSONObject args) {
        JSONObject order = order(args.getString("order_id"));
        requireStatus(order, "pending");
        String reason = requiredString(args, "reason");
        if (!List.of("no longer needed", "ordered by mistake").contains(reason)) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_REASON", "unsupported cancellation reason");
        }
        JSONArray history = order.getJSONArray("payment_history");
        JSONArray refunds = new JSONArray();
        for (Object raw : history) {
            JSONObject payment = (JSONObject) raw;
            JSONObject refund = new JSONObject();
            refund.put("transaction_type", "refund");
            refund.put("amount", payment.get("amount"));
            refund.put("payment_method_id", payment.get("payment_method_id"));
            refunds.add(refund);
            updateGiftCard(order.getString("user_id"), payment.getString("payment_method_id"),
                    decimal(payment.get("amount")));
        }
        history.addAll(refunds);
        order.put("status", "cancelled");
        order.put("cancel_reason", reason);
        return new JSONObject(Map.of("status", "cancelled"));
    }

    private JSONObject modifyOrderAddress(JSONObject args) {
        JSONObject order = order(args.getString("order_id"));
        requirePending(order);
        order.put("address", addressFrom(args));
        return new JSONObject(Map.of("status", "updated"));
    }

    private JSONObject modifyUserAddress(JSONObject args) {
        JSONObject user = required(environment.table("users"), args.getString("user_id"), "USER_NOT_FOUND");
        user.put("address", addressFrom(args));
        return new JSONObject(Map.of("status", "updated"));
    }

    private JSONObject modifyPayment(JSONObject args) {
        JSONObject order = order(args.getString("order_id"));
        requirePending(order);
        JSONArray history = order.getJSONArray("payment_history");
        if (history == null || history.size() != 1
                || !"payment".equals(history.getJSONObject(0).getString("transaction_type"))) {
            throw new TauRetailEnvironment.TauRetailToolException("PAYMENT_HISTORY_INVALID", "pending order must have one payment");
        }
        JSONObject original = history.getJSONObject(0);
        String oldPaymentId = original.getString("payment_method_id");
        String newPaymentId = requiredString(args, "payment_method_id");
        if (newPaymentId.equals(oldPaymentId)) {
            throw new TauRetailEnvironment.TauRetailToolException("PAYMENT_METHOD_UNCHANGED", "payment method must change");
        }
        JSONObject newMethod = paymentMethod(order.getString("user_id"), newPaymentId);
        BigDecimal amount = decimal(original.get("amount"));
        requireGiftCardBalance(newMethod, amount);
        JSONObject payment = new JSONObject();
        payment.put("transaction_type", "payment");
        payment.put("amount", original.get("amount"));
        payment.put("payment_method_id", newPaymentId);
        JSONObject refund = new JSONObject();
        refund.put("transaction_type", "refund");
        refund.put("amount", original.get("amount"));
        refund.put("payment_method_id", oldPaymentId);
        history.add(payment);
        history.add(refund);
        updateGiftCard(order.getString("user_id"), newPaymentId, amount.negate());
        updateGiftCard(order.getString("user_id"), oldPaymentId, amount);
        return new JSONObject(Map.of("status", "updated"));
    }

    private JSONObject replaceItems(JSONObject args, boolean pending) {
        JSONObject order = order(args.getString("order_id"));
        if (pending) {
            requirePending(order);
        } else {
            requireStatus(order, "delivered");
        }
        JSONArray oldIds = requiredArray(args, "item_ids");
        JSONArray newIds = requiredArray(args, "new_item_ids");
        if (oldIds.size() != newIds.size() || oldIds.isEmpty()) {
            throw new TauRetailEnvironment.TauRetailToolException("ITEM_MAPPING_INVALID", "item mapping is invalid");
        }
        JSONArray items = order.getJSONArray("items");
        List<Integer> indexes = new ArrayList<>();
        List<JSONObject> variants = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        BigDecimal priceDifference = BigDecimal.ZERO;
        for (int index = 0; index < oldIds.size(); index++) {
            String oldId = String.valueOf(oldIds.get(index));
            int itemIndex = findItem(items, oldId, seen);
            if (itemIndex < 0) {
                throw new TauRetailEnvironment.TauRetailToolException("ITEM_NOT_FOUND", "order item not found");
            }
            seen.add(oldId);
            JSONObject oldItem = (JSONObject) items.get(itemIndex);
            String newId = String.valueOf(newIds.get(index));
            if (oldId.equals(newId)) {
                throw new TauRetailEnvironment.TauRetailToolException("ITEM_UNCHANGED", "new item must differ from old item");
            }
            JSONObject variant = productVariant(oldItem.getString("product_id"), newId);
            indexes.add(itemIndex);
            variants.add(variant);
            priceDifference = priceDifference.add(decimal(variant.get("price")).subtract(decimal(oldItem.get("price"))));
        }
        String paymentMethodId = requiredString(args, "payment_method_id");
        JSONObject paymentMethod = paymentMethod(order.getString("user_id"), paymentMethodId);
        priceDifference = priceDifference.setScale(2, java.math.RoundingMode.HALF_UP);
        requireGiftCardBalance(paymentMethod, priceDifference);
        if (pending) {
            JSONArray history = order.getJSONArray("payment_history");
            JSONObject transaction = new JSONObject();
            transaction.put("transaction_type", priceDifference.signum() > 0 ? "payment" : "refund");
            transaction.put("amount", priceDifference.abs());
            transaction.put("payment_method_id", paymentMethodId);
            history.add(transaction);
            updateGiftCard(order.getString("user_id"), paymentMethodId, priceDifference.negate());
            for (int index = 0; index < indexes.size(); index++) {
                JSONObject oldItem = items.getJSONObject(indexes.get(index));
                JSONObject variant = variants.get(index);
                oldItem.put("item_id", variant.getString("item_id"));
                oldItem.put("price", variant.get("price"));
                oldItem.put("options", variant.getJSONObject("options"));
            }
            order.put("status", "pending (item modified)");
        } else {
            order.put("status", "exchange requested");
            order.put("exchange_items", sortedValues(oldIds));
            order.put("exchange_new_items", sortedValues(newIds));
            order.put("exchange_payment_method_id", paymentMethodId);
            order.put("exchange_price_difference", priceDifference);
        }
        return new JSONObject(Map.of("status", "updated", "item_count", oldIds.size()));
    }

    private JSONObject returnItems(JSONObject args) {
        JSONObject order = order(args.getString("order_id"));
        requireStatus(order, "delivered");
        JSONArray itemIds = requiredArray(args, "item_ids");
        if (itemIds.isEmpty()) {
            throw new TauRetailEnvironment.TauRetailToolException("ITEM_MAPPING_INVALID", "no items to return");
        }
        List<String> allItemIds = order.getJSONArray("items").stream().map(JSONObject.class::cast)
                .map(item -> item.getString("item_id")).toList();
        for (Object id : itemIds) {
            String itemId = String.valueOf(id);
            if (count(itemIds, itemId) > allItemIds.stream().filter(itemId::equals).count()) {
                throw new TauRetailEnvironment.TauRetailToolException("ITEM_NOT_FOUND", "order item not found");
            }
        }
        String paymentMethodId = requiredString(args, "payment_method_id");
        JSONObject method = paymentMethod(order.getString("user_id"), paymentMethodId);
        String original = order.getJSONArray("payment_history").getJSONObject(0).getString("payment_method_id");
        if (!"gift_card".equals(method.getString("source")) && !paymentMethodId.equals(original)) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_REFUND_METHOD", "refund method must be original or gift card");
        }
        order.put("status", "return requested");
        order.put("return_items", sortedValues(itemIds));
        order.put("return_payment_method_id", paymentMethodId);
        return new JSONObject(Map.of("status", "returned", "item_count", itemIds.size()));
    }

    private JSONObject transfer(JSONObject args) {
        String summary = requiredString(args, "summary");
        return new JSONObject(Map.of("status", "transferred", "summary", summary));
    }

    private JSONObject productVariant(String productId, String itemId) {
        JSONObject product = environment.table("products").getJSONObject(productId);
        JSONObject variants = product == null ? null : product.getJSONObject("variants");
        JSONObject variant = variants == null ? null : variants.getJSONObject(itemId);
        if (variant == null || !variant.getBooleanValue("available")) {
            throw new TauRetailEnvironment.TauRetailToolException("PRODUCT_VARIANT_NOT_FOUND", "product variant not found or unavailable");
        }
        return new JSONObject(Map.of("name", product.getString("name"), "product_id", productId,
                "item_id", itemId, "price", variant.get("price"), "options", variant.getJSONObject("options")));
    }

    private JSONObject addressFrom(JSONObject args) {
        JSONObject address = new JSONObject();
        for (String key : List.of("address1", "address2", "city", "country", "state", "zip")) {
            String value = args.getString(key);
            if (value == null) {
                throw new TauRetailEnvironment.TauRetailToolException("INVALID_ARGUMENT", "address is incomplete");
            }
            address.put(key, value);
        }
        return address;
    }

    private JSONObject order(String orderId) {
        if (orderId == null) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_ARGUMENT", "order id is required");
        }
        return required(environment.table("orders"), orderId, "ORDER_NOT_FOUND");
    }

    private JSONObject required(JSONObject table, String key, String errorCode) {
        JSONObject value = key == null ? null : table.getJSONObject(key);
        if (value == null) {
            throw new TauRetailEnvironment.TauRetailToolException(errorCode, "requested record not found");
        }
        return value;
    }

    private int findItem(JSONArray items, String itemId) {
        return findItem(items, itemId, List.of());
    }

    private int findItem(JSONArray items, String itemId, List<String> alreadyMatched) {
        int occurrence = 0;
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.getJSONObject(i).getString("item_id"))) {
                if (occurrence++ < alreadyMatched.stream().filter(itemId::equals).count()) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private void requireStatus(JSONObject order, String expected) {
        String status = order.getString("status");
        if (status == null || !status.equalsIgnoreCase(expected)) {
            throw new TauRetailEnvironment.TauRetailToolException("ORDER_STATUS_MISMATCH", "order status does not allow this operation");
        }
    }

    private void requirePending(JSONObject order) {
        String status = order.getString("status");
        if (status == null || !status.equals("pending")) {
            throw new TauRetailEnvironment.TauRetailToolException("ORDER_STATUS_MISMATCH", "order status does not allow this operation");
        }
    }

    private JSONObject paymentMethod(String userId, String paymentMethodId) {
        JSONObject user = required(environment.table("users"), userId, "USER_NOT_FOUND");
        JSONObject methods = user.getJSONObject("payment_methods");
        JSONObject method = methods == null ? null : methods.getJSONObject(paymentMethodId);
        if (method == null) {
            throw new TauRetailEnvironment.TauRetailToolException("PAYMENT_METHOD_NOT_FOUND", "payment method not found");
        }
        return method;
    }

    private void requireGiftCardBalance(JSONObject paymentMethod, BigDecimal amount) {
        if ("gift_card".equals(paymentMethod.getString("source"))
                && decimal(paymentMethod.get("balance")).compareTo(amount.max(BigDecimal.ZERO)) < 0) {
            throw new TauRetailEnvironment.TauRetailToolException("GIFT_CARD_BALANCE_LOW", "gift card balance is insufficient");
        }
    }

    private void updateGiftCard(String userId, String paymentMethodId, BigDecimal delta) {
        JSONObject method = paymentMethod(userId, paymentMethodId);
        if ("gift_card".equals(method.getString("source"))) {
            BigDecimal balance = decimal(method.get("balance")).add(delta).setScale(2, java.math.RoundingMode.HALF_UP);
            method.put("balance", balance);
        }
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long count(JSONArray values, String target) {
        return values.stream().map(String::valueOf).filter(target::equals).count();
    }

    private List<String> sortedValues(JSONArray values) {
        return values.stream().map(String::valueOf).sorted().toList();
    }

    private String requiredString(JSONObject args, String field) {
        String value = args.getString(field);
        if (value == null || value.isBlank()) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_ARGUMENT", field + " is required");
        }
        return value;
    }

    private JSONArray requiredArray(JSONObject args, String field) {
        JSONArray value = args.getJSONArray(field);
        if (value == null) {
            throw new TauRetailEnvironment.TauRetailToolException("INVALID_ARGUMENT", field + " is required");
        }
        return value;
    }

    public record ToolResult(boolean success, JSONObject output, String errorCode) {
        public ToolResult {
            output = output == null ? new JSONObject() : JSON.parseObject(output.toJSONString());
        }
    }

    private static final class ExpressionParser {
        private final List<String> tokens;
        private int index;

        private ExpressionParser(List<String> tokens) {
            this.tokens = tokens;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (peek("+") || peek("-")) {
                String operator = tokens.get(index++);
                BigDecimal right = term();
                value = operator.equals("+") ? value.add(right) : value.subtract(right);
            }
            return value;
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (peek("*") || peek("/")) {
                String operator = tokens.get(index++);
                BigDecimal right = factor();
                value = operator.equals("*") ? value.multiply(right)
                        : value.divide(right, 8, java.math.RoundingMode.HALF_UP);
            }
            return value;
        }

        private BigDecimal factor() {
            if (peek("-")) {
                index++;
                return factor().negate();
            }
            if (peek("+")) {
                index++;
                return factor();
            }
            if (peek("(")) {
                index++;
                BigDecimal value = expression();
                if (!peek(")")) {
                    throw new TauRetailEnvironment.TauRetailToolException("INVALID_EXPRESSION", "unclosed group");
                }
                index++;
                return value;
            }
            if (index >= tokens.size() || !tokens.get(index).matches("\\d+(?:\\.\\d+)?")) {
                throw new TauRetailEnvironment.TauRetailToolException("INVALID_EXPRESSION", "number expected");
            }
            return new BigDecimal(tokens.get(index++));
        }

        private boolean peek(String token) {
            return index < tokens.size() && tokens.get(index).equals(token);
        }

        private boolean atEnd() {
            return index == tokens.size();
        }
    }
}
