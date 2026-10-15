package cn.bigcoder.soa.helper.expression;

import cn.bigcoder.soa.helper.expression.ExpressionNode.Comparison;
import cn.bigcoder.soa.helper.expression.ExpressionNode.FunctionCall;
import cn.bigcoder.soa.helper.expression.ExpressionNode.In;
import cn.bigcoder.soa.helper.expression.ExpressionNode.Literal;
import cn.bigcoder.soa.helper.expression.ExpressionNode.Logical;
import cn.bigcoder.soa.helper.expression.ExpressionNode.Not;
import cn.bigcoder.soa.helper.expression.ExpressionNode.TextPredicate;
import cn.bigcoder.soa.helper.expression.ExpressionNode.Variable;
import cn.bigcoder.soa.helper.expression.ExpressionValues.ValueType;
import cn.bigcoder.soa.helper.expression.RouteExpressionLexer.Token;
import cn.bigcoder.soa.helper.expression.RouteExpressionLexer.TokenType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 路由条件表达式语法分析（递归下降）
 * <pre>
 * or         := and (('||' | 'or') and)*
 * and        := unary (('&&' | 'and') unary)*
 * unary      := ('!' | 'not') unary | comparison
 * comparison := value [ ('==' | '!=' | '>' | '>=' | '<' | '<=') value
 *                     | ['not'] 'in' '(' or (',' or)* [','] ')'
 *                     | ['not'] ('contains' | 'startsWith' | 'endsWith' | 'matches') value ]
 * value      := '(' or ')' | ${name} | name | name '(' args ')' | 'text' | "text" | number | true | false
 * </pre>
 */
final class RouteExpressionParser {

    private static final Set<TokenType> COMPARISON_OPERATORS =
            EnumSet.of(TokenType.EQ, TokenType.NE, TokenType.GT, TokenType.GE, TokenType.LT, TokenType.LE);

    private static final Set<TokenType> TEXT_OPERATORS =
            EnumSet.of(TokenType.CONTAINS, TokenType.STARTS_WITH, TokenType.ENDS_WITH, TokenType.MATCHES);

    private static final String MISSING_OPERATOR =
            "缺少比较运算符（如 ==、in、startsWith），条件需要得到 true / false";

    /**
     * 最大嵌套层数（括号、函数、!），防止异常输入导致栈溢出
     */
    private static final int MAX_DEPTH = 64;

    private final List<Token> tokens;
    private int index;
    private int depth;

    private RouteExpressionParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static ExpressionNode parse(String text) {
        if (text == null || text.isBlank()) {
            throw new RouteExpressionException("条件表达式不能为空");
        }
        RouteExpressionParser parser = new RouteExpressionParser(RouteExpressionLexer.tokenize(text));
        Token first = parser.peek();
        ExpressionNode root = parser.parseOr();
        Token rest = parser.peek();
        if (rest.type() != TokenType.EOF) {
            throw new RouteExpressionException("多余的内容「" + rest.text() + "」", rest.position());
        }
        requireCondition(root, first);
        return root;
    }

    private ExpressionNode parseOr() {
        enter(peek());
        try {
            return parseLogical(false);
        } finally {
            depth--;
        }
    }

    private ExpressionNode parseAnd() {
        return parseLogical(true);
    }

    /**
     * 解析 && 或 || 链；&& 的操作数是 unary，|| 的操作数是 && 链，以此实现 && 优先
     */
    private ExpressionNode parseLogical(boolean and) {
        TokenType operator = and ? TokenType.AND : TokenType.OR;
        Token firstToken = peek();
        ExpressionNode first = and ? parseUnary() : parseAnd();
        if (peek().type() != operator) {
            return first;
        }
        requireCondition(first, firstToken);
        List<ExpressionNode> operands = new ArrayList<>(List.of(first));
        while (match(operator)) {
            Token operandToken = peek();
            ExpressionNode operand = and ? parseUnary() : parseAnd();
            requireCondition(operand, operandToken);
            operands.add(operand);
        }
        return new Logical(and, operands);
    }

    private ExpressionNode parseUnary() {
        if (match(TokenType.NOT)) {
            Token operandToken = peek();
            enter(operandToken);
            try {
                ExpressionNode operand = parseUnary();
                requireCondition(operand, operandToken);
                return new Not(operand);
            } finally {
                depth--;
            }
        }
        return parseComparison();
    }

    private ExpressionNode parseComparison() {
        Token leftToken = peek();
        ExpressionNode left = parseValue();

        boolean negated = false;
        if (peek().type() == TokenType.NOT && isNegatable(peekAt(1).type())) {
            advance();
            negated = true;
        }

        Token operator = peek();
        if (COMPARISON_OPERATORS.contains(operator.type())) {
            advance();
            Token rightToken = peek();
            ExpressionNode right = parseValue();
            if (operator.type() != TokenType.EQ && operator.type() != TokenType.NE) {
                requireNotCondition(left, leftToken, "比较大小的两边需要是数字");
                requireNotCondition(right, rightToken, "比较大小的两边需要是数字");
            }
            return new Comparison(left, operator.type(), right);
        }
        if (TEXT_OPERATORS.contains(operator.type())) {
            advance();
            Token rightToken = peek();
            ExpressionNode right = parseValue();
            String message = operator.text() + " 两边需要是文本";
            requireNotCondition(left, leftToken, message);
            requireNotCondition(right, rightToken, message);
            Pattern pattern = operator.type() == TokenType.MATCHES && right instanceof Literal literal
                    ? ExpressionNode.compileRegex(ExpressionValues.asText(literal.value()), rightToken.position())
                    : null;
            return new TextPredicate(left, operator.type(), right, negated, pattern);
        }
        if (operator.type() == TokenType.IN) {
            advance();
            requireNotCondition(left, leftToken, "in 左边需要是变量、文本或数字");
            return new In(left, parseInList(), negated);
        }
        return left;
    }

    private List<ExpressionNode> parseInList() {
        expect(TokenType.LPAREN, "in 后面需要括号，例如 in ('a', 'b')");
        if (peek().type() == TokenType.RPAREN) {
            throw new RouteExpressionException("in 列表不能为空", peek().position());
        }
        List<ExpressionNode> items = new ArrayList<>();
        do {
            if (peek().type() == TokenType.RPAREN) {
                break; // 允许末尾逗号
            }
            Token itemToken = peek();
            ExpressionNode item = parseOr();
            requireNotCondition(item, itemToken, "in 列表中需要是变量、文本或数字");
            items.add(item);
        } while (match(TokenType.COMMA));
        expect(TokenType.RPAREN, "in 列表缺少右括号 )");
        return items;
    }

    private ExpressionNode parseValue() {
        Token token = advance();
        switch (token.type()) {
            case LPAREN: {
                ExpressionNode inner = parseOr();
                expect(TokenType.RPAREN, "缺少右括号 )");
                return inner;
            }
            case VARIABLE:
                return new Variable(token.text());
            case IDENTIFIER:
                return peek().type() == TokenType.LPAREN ? parseFunctionCall(token) : new Variable(token.text());
            case STRING:
                return new Literal(token.text(), ValueType.TEXT);
            case NUMBER:
                return new Literal(new BigDecimal(token.text()), ValueType.NUMBER);
            case TRUE:
            case FALSE:
                return new Literal(token.type() == TokenType.TRUE, ValueType.BOOLEAN);
            case EOF:
                throw new RouteExpressionException("表达式在结尾处不完整", token.position());
            default:
                throw new RouteExpressionException(
                        "此处需要变量、文本、数字或函数，实际是「" + token.text() + "」", token.position());
        }
    }

    private ExpressionNode parseFunctionCall(Token nameToken) {
        BuiltinFunction function = BuiltinFunction.find(nameToken.text())
                .orElseThrow(() -> new RouteExpressionException(
                        "未知函数 " + nameToken.text() + "，可用函数：" + BuiltinFunction.allNames(),
                        nameToken.position()));
        advance(); // (
        List<ExpressionNode> args = new ArrayList<>();
        if (!match(TokenType.RPAREN)) {
            do {
                Token argToken = peek();
                ExpressionNode arg = parseOr();
                requireNotCondition(arg, argToken, "函数 " + function.functionName() + " 的参数不能是条件表达式");
                args.add(arg);
            } while (match(TokenType.COMMA));
            expect(TokenType.RPAREN, "函数 " + function.functionName() + " 缺少右括号 )");
        }
        if (!function.acceptsArgCount(args.size())) {
            throw new RouteExpressionException("函数 " + function.functionName() + " 需要 "
                    + function.arityDescription() + " 个参数，实际 " + args.size() + " 个", nameToken.position());
        }
        return new FunctionCall(function, args);
    }

    private void enter(Token at) {
        if (++depth > MAX_DEPTH) {
            throw new RouteExpressionException("表达式嵌套过深（超过 " + MAX_DEPTH + " 层）", at.position());
        }
    }

    // ===== 类型检查 =====

    private static void requireCondition(ExpressionNode node, Token at) {
        if (node.type() != ValueType.BOOLEAN) {
            throw new RouteExpressionException(MISSING_OPERATOR, at.position());
        }
    }

    private static void requireNotCondition(ExpressionNode node, Token at, String message) {
        if (node.type() == ValueType.BOOLEAN) {
            throw new RouteExpressionException(message, at.position());
        }
    }

    private static boolean isNegatable(TokenType type) {
        return type == TokenType.IN || TEXT_OPERATORS.contains(type);
    }

    // ===== Token 游标 =====

    private Token peek() {
        return tokens.get(index);
    }

    private Token peekAt(int offset) {
        return tokens.get(Math.min(index + offset, tokens.size() - 1));
    }

    private Token advance() {
        Token token = tokens.get(index);
        if (token.type() != TokenType.EOF) {
            index++;
        }
        return token;
    }

    private boolean match(TokenType type) {
        if (peek().type() == type) {
            advance();
            return true;
        }
        return false;
    }

    private void expect(TokenType type, String message) {
        Token token = peek();
        if (token.type() != type) {
            throw new RouteExpressionException(message, token.position());
        }
        advance();
    }
}
