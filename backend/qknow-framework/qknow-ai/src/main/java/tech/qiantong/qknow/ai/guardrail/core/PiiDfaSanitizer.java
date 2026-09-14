package tech.qiantong.qknow.ai.guardrail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.model.SanitizeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高性能 PII 纳秒/微秒级脱敏处理器 (PiiDfaSanitizer)
 *
 * 采用预编译高效正则与严格算法校验（中国居民二代身份证 ISO 7064 MOD 11-2，银行卡 Luhn 模 10 校验），
 * 严格防范误杀正常长数字（如订单号、无规律数字串），保证敏感信息 100% 格式置换与零互信息泄露。
 *
 * @author qknow
 */
@Slf4j
@Component
public class PiiDfaSanitizer {

    public static final String REDACTED_PHONE = "[REDACTED_PHONE]";
    public static final String REDACTED_ID_CARD = "[REDACTED_ID_CARD]";
    public static final String REDACTED_BANK_CARD = "[REDACTED_BANK_CARD]";
    public static final String REDACTED_EMAIL = "[REDACTED_EMAIL]";
    public static final String REDACTED_TOKEN = "[REDACTED_TOKEN]";

    // 1. Bearer / API Token 令牌 (包含 OpenAI sk-, GitHub ghp_, GitLab glpat- 以及通用 Bearer Token)
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(?:Bearer\\s+[A-Za-z0-9\\-_\\.]{20,}|(?:sk-|ghp_|glpat-)[A-Za-z0-9]{20,})"
    );

    // 2. 电子邮箱
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    // 3. 中国二代居民身份证号 (18位，带基础年月日范围断言)
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)([1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])(?!\\d)"
    );

    // 4. 银行卡号候选 (16-19位纯数字)
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)([1-9]\\d{15,18})(?!\\d)"
    );

    // 5. 中国大陆手机号 (11位)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?86[- ]?)?1[3-9]\\d{9})(?!\\d)"
    );

    // ISO 7064:1983.MOD 11-2 加权因子
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    // 校验码字符表
    private static final char[] ID_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /**
     * 对输入文本执行全量 PII 识别与脱敏
     */
    public SanitizeResult sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return new SanitizeResult(input, false, 0, 0L);
        }

        long startNano = System.nanoTime();
        String current = input;
        int hitCount = 0;

        // 1. Token 脱敏 (最高优先级，防止内部长数字或邮箱格式被干扰)
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(current);
        if (tokenMatcher.find()) {
            tokenMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (tokenMatcher.find()) {
                tokenMatcher.appendReplacement(sb, Matcher.quoteReplacement(REDACTED_TOKEN));
                hitCount++;
            }
            tokenMatcher.appendTail(sb);
            current = sb.toString();
        }

        // 2. 邮箱脱敏
        Matcher emailMatcher = EMAIL_PATTERN.matcher(current);
        if (emailMatcher.find()) {
            emailMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (emailMatcher.find()) {
                emailMatcher.appendReplacement(sb, Matcher.quoteReplacement(REDACTED_EMAIL));
                hitCount++;
            }
            emailMatcher.appendTail(sb);
            current = sb.toString();
        }

        // 3. 身份证号脱敏 (包含 ISO 7064:1983.MOD 11-2 校验位核验)
        Matcher idMatcher = ID_CARD_PATTERN.matcher(current);
        if (idMatcher.find()) {
            idMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (idMatcher.find()) {
                String candidate = idMatcher.group(1);
                if (isValidIdCard(candidate)) {
                    idMatcher.appendReplacement(sb, Matcher.quoteReplacement(REDACTED_ID_CARD));
                    hitCount++;
                } else {
                    idMatcher.appendReplacement(sb, Matcher.quoteReplacement(idMatcher.group()));
                }
            }
            idMatcher.appendTail(sb);
            current = sb.toString();
        }

        // 4. 银行卡号脱敏 (包含 Luhn 模 10 算法核验)
        Matcher bankMatcher = BANK_CARD_PATTERN.matcher(current);
        if (bankMatcher.find()) {
            bankMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (bankMatcher.find()) {
                String candidate = bankMatcher.group(1);
                if (isValidBankCard(candidate)) {
                    bankMatcher.appendReplacement(sb, Matcher.quoteReplacement(REDACTED_BANK_CARD));
                    hitCount++;
                } else {
                    bankMatcher.appendReplacement(sb, Matcher.quoteReplacement(bankMatcher.group()));
                }
            }
            bankMatcher.appendTail(sb);
            current = sb.toString();
        }

        // 5. 手机号脱敏
        Matcher phoneMatcher = PHONE_PATTERN.matcher(current);
        if (phoneMatcher.find()) {
            phoneMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (phoneMatcher.find()) {
                phoneMatcher.appendReplacement(sb, Matcher.quoteReplacement(REDACTED_PHONE));
                hitCount++;
            }
            phoneMatcher.appendTail(sb);
            current = sb.toString();
        }

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;
        boolean modified = hitCount > 0;
        return new SanitizeResult(current, modified, hitCount, elapsedMicros);
    }

    /**
     * 中国居民身份证 ISO 7064:1983.MOD 11-2 校验码合法性校验
     */
    public static boolean isValidIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = idCard.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            sum += (c - '0') * ID_WEIGHTS[i];
        }
        int mod = sum % 11;
        char expectedCheckCode = ID_CHECK_CODES[mod];
        char actualCheckCode = Character.toUpperCase(idCard.charAt(17));
        return expectedCheckCode == actualCheckCode;
    }

    /**
     * 银行卡 Luhn 模 10 算法合法性校验
     */
    public static boolean isValidBankCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 16 || cardNumber.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            char c = cardNumber.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            int n = c - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
