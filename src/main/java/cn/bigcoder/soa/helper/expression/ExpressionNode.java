package cn.bigcoder.soa.helper.expression;

import cn.bigcoder.soa.helper.expression.ExpressionValues.ValueType;
import cn.bigcoder.soa.helper.expression.RouteExpressionLexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static cn.bigcoder.soa.helper.expression.ExpressionValues.asBoolean;
import static cn.bigcoder.soa.helper.expression.ExpressionValues.asNumber;
import static cn.bigcoder.soa.helper.expression.ExpressionValues.asText;
import static cn.bigcoder.soa.helper.expression.ExpressionValues.looseEquals;

/**
 * 路由条件表达式语法树节点
 */
interface ExpressionNode {

    Object evaluate(Map<String, String> variables);

    /**
     * 静态类型，用于解析期检查（如 && 两边必须是条件）
     */
    ValueType type();

    void collectVariables(Set<String> names);

    record Literal(Object value, ValueType type) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            return value;
        }

        @Override
        public void collectVariables(Set<String> names) {
        }
    }

    record Variable(String name) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            String value = variables.get(name);
            if (value == null) {
                throw new RouteExpressionException("未知变量 " + name);
            }
            return value.trim();
        }

        @Override
        public ValueType type() {
            return ValueType.TEXT;
        }

        @Override
        public void collectVariables(Set<String> names) {
            names.add(name);
        }
    }

    record FunctionCall(BuiltinFunction function, List<ExpressionNode> args) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            List<Object> values = new ArrayList<>(args.size());
            for (ExpressionNode arg : args) {
                values.add(arg.evaluate(variables));
            }
            return function.apply(values);
        }

        @Override
        public ValueType type() {
            return function.returnType();
        }

        @Override
        public void collectVariables(Set<String> names) {
            args.forEach(arg -> arg.collectVariables(names));
        }
    }

    record Not(ExpressionNode operand) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            return !asBoolean(operand.evaluate(variables));
        }

        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }

        @Override
        public void collectVariables(Set<String> names) {
            operand.collectVariables(names);
        }
    }

    /**
     * && / || ，短路求值
     */
    record Logical(boolean and, List<ExpressionNode> operands) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            for (ExpressionNode operand : operands) {
                boolean value = asBoolean(operand.evaluate(variables));
                if (and != value) {
                    return value;
                }
            }
            return and;
        }

        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }

        @Override
        public void collectVariables(Set<String> names) {
            operands.forEach(operand -> operand.collectVariables(names));
        }
    }

    /**
     * == != > >= < <=
     */
    record Comparison(ExpressionNode left, TokenType operator, ExpressionNode right) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            Object l = left.evaluate(variables);
            Object r = right.evaluate(variables);
            switch (operator) {
                case EQ:
                    return looseEquals(l, r);
                case NE:
                    return !looseEquals(l, r);
                default:
                    int result = asNumber(l).compareTo(asNumber(r));
                    return switch (operator) {
                        case GT -> result > 0;
                        case GE -> result >= 0;
                        case LT -> result < 0;
                        case LE -> result <= 0;
                        default -> throw new IllegalStateException("Unexpected operator: " + operator);
                    };
            }
        }

        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }

        @Override
        public void collectVariables(Set<String> names) {
            left.collectVariables(names);
            right.collectVariables(names);
        }
    }

    /**
     * contains / startsWith / endsWith / matches，negated 表示 not 形式。
     * matches 右侧为字面量时，正则在解析期预编译（pattern 非空）
     */
    record TextPredicate(ExpressionNode left, TokenType operator, ExpressionNode right,
                         boolean negated, Pattern pattern) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            String l = asText(left.evaluate(variables));
            boolean result = switch (operator) {
                case CONTAINS -> l.contains(asText(right.evaluate(variables)));
                case STARTS_WITH -> l.startsWith(asText(right.evaluate(variables)));
                case ENDS_WITH -> l.endsWith(asText(right.evaluate(variables)));
                case MATCHES -> resolvePattern(variables).matcher(l).matches();
                default -> throw new IllegalStateException("Unexpected operator: " + operator);
            };
            return negated != result;
        }

        private Pattern resolvePattern(Map<String, String> variables) {
            if (pattern != null) {
                return pattern;
            }
            return compileRegex(asText(right.evaluate(variables)), -1);
        }

        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }

        @Override
        public void collectVariables(Set<String> names) {
            left.collectVariables(names);
            right.collectVariables(names);
        }
    }

    record In(ExpressionNode left, List<ExpressionNode> items, boolean negated) implements ExpressionNode {
        @Override
        public Object evaluate(Map<String, String> variables) {
            Object value = left.evaluate(variables);
            for (ExpressionNode item : items) {
                if (looseEquals(value, item.evaluate(variables))) {
                    return !negated;
                }
            }
            return negated;
        }

        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }

        @Override
        public void collectVariables(Set<String> names) {
            left.collectVariables(names);
            items.forEach(item -> item.collectVariables(names));
        }
    }

    static Pattern compileRegex(String regex, int position) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new RouteExpressionException("正则表达式有误：" + e.getDescription(), position);
        }
    }
}
