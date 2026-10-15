package cn.bigcoder.soa.helper.expression;

import cn.bigcoder.soa.helper.expression.ExpressionValues.ValueType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static cn.bigcoder.soa.helper.expression.ExpressionValues.asNumber;
import static cn.bigcoder.soa.helper.expression.ExpressionValues.asText;

/**
 * 路由条件表达式内置函数（函数名不区分大小写）
 */
enum BuiltinFunction {

    LOWER("lower", 1, 1, ValueType.TEXT) {
        @Override
        Object apply(List<Object> args) {
            return asText(args.get(0)).toLowerCase(Locale.ROOT);
        }
    },
    UPPER("upper", 1, 1, ValueType.TEXT) {
        @Override
        Object apply(List<Object> args) {
            return asText(args.get(0)).toUpperCase(Locale.ROOT);
        }
    },
    TRIM("trim", 1, 1, ValueType.TEXT) {
        @Override
        Object apply(List<Object> args) {
            return asText(args.get(0)).trim();
        }
    },
    LENGTH("length", 1, 1, ValueType.NUMBER) {
        @Override
        Object apply(List<Object> args) {
            return BigDecimal.valueOf(asText(args.get(0)).length());
        }
    },
    /**
     * substring(s, start[, end])：下标从 0 开始，越界时自动截断
     */
    SUBSTRING("substring", 2, 3, ValueType.TEXT) {
        @Override
        Object apply(List<Object> args) {
            String text = asText(args.get(0));
            int start = clampIndex(args.get(1), text.length());
            int end = args.size() > 2 ? clampIndex(args.get(2), text.length()) : text.length();
            return start >= end ? "" : text.substring(start, end);
        }
    };

    private final String functionName;
    private final int minArgs;
    private final int maxArgs;
    private final ValueType returnType;

    BuiltinFunction(String functionName, int minArgs, int maxArgs, ValueType returnType) {
        this.functionName = functionName;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.returnType = returnType;
    }

    abstract Object apply(List<Object> args);

    String functionName() {
        return functionName;
    }

    ValueType returnType() {
        return returnType;
    }

    boolean acceptsArgCount(int count) {
        return count >= minArgs && count <= maxArgs;
    }

    String arityDescription() {
        return minArgs == maxArgs ? String.valueOf(minArgs) : minArgs + "~" + maxArgs;
    }

    static Optional<BuiltinFunction> find(String name) {
        return Arrays.stream(values())
                .filter(f -> f.functionName.equalsIgnoreCase(name))
                .findFirst();
    }

    static String allNames() {
        return Arrays.stream(values()).map(f -> f.functionName).collect(Collectors.joining("、"));
    }

    /**
     * 下标截断到 [0, length]，在 BigDecimal 上比较以避免超大数字转 int 时溢出；小数向下取整
     */
    private static int clampIndex(Object value, int length) {
        BigDecimal index = asNumber(value);
        if (index.signum() < 0) {
            return 0;
        }
        if (index.compareTo(BigDecimal.valueOf(length)) > 0) {
            return length;
        }
        return index.intValue();
    }
}
