package cn.bigcoder.soa.helper.expression;

import java.math.BigDecimal;

/**
 * 表达式取值的类型转换。值只有三种：String、BigDecimal、Boolean
 */
final class ExpressionValues {

    enum ValueType {
        TEXT, NUMBER, BOOLEAN
    }

    private ExpressionValues() {
    }

    static String asText(Object value) {
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        return String.valueOf(value);
    }

    static BigDecimal asNumber(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                throw new RouteExpressionException("「" + text + "」不是数字，无法比较大小");
            }
        }
        throw new RouteExpressionException("「" + value + "」不是数字，无法比较大小");
    }

    static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new RouteExpressionException("「" + asText(value) + "」不是条件（true/false）");
    }

    /**
     * 相等判断：
     * 至少一边是数字、另一边是数字或数字文本时按数值比较（100017451 与 "100017451.0"、0123 与 "0123" 相等）；
     * 两边都是布尔时按布尔比较；其余按文本比较（"0123" 与 "123" 不相等）
     */
    static boolean looseEquals(Object left, Object right) {
        if (left instanceof BigDecimal || right instanceof BigDecimal) {
            BigDecimal l = toNumberOrNull(left);
            BigDecimal r = toNumberOrNull(right);
            if (l != null && r != null) {
                return l.compareTo(r) == 0;
            }
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return left.equals(right);
        }
        return asText(left).equals(asText(right));
    }

    private static BigDecimal toNumberOrNull(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
