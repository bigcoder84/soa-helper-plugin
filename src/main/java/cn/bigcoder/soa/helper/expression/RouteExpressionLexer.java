package cn.bigcoder.soa.helper.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * 路由条件表达式词法分析
 */
final class RouteExpressionLexer {

    enum TokenType {
        VARIABLE, IDENTIFIER, STRING, NUMBER,
        LPAREN, RPAREN, COMMA,
        EQ, NE, GT, GE, LT, LE,
        AND, OR, NOT,
        IN, CONTAINS, STARTS_WITH, ENDS_WITH, MATCHES,
        TRUE, FALSE,
        EOF
    }

    record Token(TokenType type, String text, int position) {
    }

    /**
     * 关键字（不区分大小写）
     */
    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            entry("and", TokenType.AND),
            entry("or", TokenType.OR),
            entry("not", TokenType.NOT),
            entry("in", TokenType.IN),
            entry("contains", TokenType.CONTAINS),
            entry("startswith", TokenType.STARTS_WITH),
            entry("endswith", TokenType.ENDS_WITH),
            entry("matches", TokenType.MATCHES),
            entry("true", TokenType.TRUE),
            entry("false", TokenType.FALSE));

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 中文输入法下容易误输入的标点
     */
    private static final String FULL_WIDTH_PUNCTUATION = "（），“”‘’！＝＆｜＜＞";

    /**
     * Character.isWhitespace 不认的空白：不间断空格、零宽空格、BOM
     */
    private static final String EXTRA_WHITESPACE = "\u00A0\u2007\u202F\u200B\uFEFF";

    private final String text;
    private int pos;

    private RouteExpressionLexer(String text) {
        this.text = text;
    }

    static List<Token> tokenize(String text) {
        return new RouteExpressionLexer(text).run();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= text.length()) {
                tokens.add(new Token(TokenType.EOF, "", pos));
                return tokens;
            }
            tokens.add(nextToken());
        }
    }

    private Token nextToken() {
        char c = text.charAt(pos);
        if (c == '$' && peek(1) == '{') {
            return readVariable();
        }
        if (c == '\'' || c == '"') {
            return readString(c);
        }
        if (isDigit(c) || (c == '-' && isDigit(peek(1)))) {
            return readNumber();
        }
        if (isIdentifierStart(c)) {
            return readWord();
        }
        return readSymbol(c);
    }

    private Token readSymbol(char c) {
        int start = pos;
        switch (c) {
            case '(':
                return symbol(TokenType.LPAREN, 1);
            case ')':
                return symbol(TokenType.RPAREN, 1);
            case ',':
                return symbol(TokenType.COMMA, 1);
            case '=':
                if (peek(1) == '=') {
                    return symbol(TokenType.EQ, 2);
                }
                throw new RouteExpressionException("请使用 == 判断相等", start);
            case '!':
                return peek(1) == '=' ? symbol(TokenType.NE, 2) : symbol(TokenType.NOT, 1);
            case '>':
                return peek(1) == '=' ? symbol(TokenType.GE, 2) : symbol(TokenType.GT, 1);
            case '<':
                return peek(1) == '=' ? symbol(TokenType.LE, 2) : symbol(TokenType.LT, 1);
            case '&':
                if (peek(1) == '&') {
                    return symbol(TokenType.AND, 2);
                }
                throw new RouteExpressionException("请使用 && 表示“并且”", start);
            case '|':
                if (peek(1) == '|') {
                    return symbol(TokenType.OR, 2);
                }
                throw new RouteExpressionException("请使用 || 表示“或者”", start);
            default:
                if (FULL_WIDTH_PUNCTUATION.indexOf(c) >= 0) {
                    throw new RouteExpressionException("中文标点「" + c + "」，请改用英文标点", start);
                }
                throw new RouteExpressionException("无法识别的字符「" + c + "」", start);
        }
    }

    private Token symbol(TokenType type, int length) {
        Token token = new Token(type, text.substring(pos, pos + length), pos);
        pos += length;
        return token;
    }

    private Token readVariable() {
        int start = pos;
        int end = text.indexOf('}', pos + 2);
        if (end < 0) {
            throw new RouteExpressionException("变量缺少结束的 }", start);
        }
        String name = text.substring(pos + 2, end).trim();
        if (name.contains("(") || name.contains("$")) {
            throw new RouteExpressionException(
                    "条件中的函数不要用 ${} 包裹，请写成 lower(${methodName}) 这样", start);
        }
        if (!VARIABLE_NAME.matcher(name).matches()) {
            throw new RouteExpressionException("变量名不合法：" + name, start);
        }
        pos = end + 1;
        return new Token(TokenType.VARIABLE, name, start);
    }

    /**
     * 读取字符串：支持 \" \' \\ \n \t \r 转义，其他反斜杠原样保留（便于书写正则，如 \d）
     */
    private Token readString(char quote) {
        int start = pos;
        StringBuilder value = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= text.length()) {
                throw new RouteExpressionException("字符串缺少结束引号 " + quote, start);
            }
            char c = text.charAt(pos++);
            if (c == quote) {
                return new Token(TokenType.STRING, value.toString(), start);
            }
            if (c == '\\' && pos < text.length()) {
                value.append(unescape(text.charAt(pos++)));
            } else {
                value.append(c);
            }
        }
    }

    private static String unescape(char c) {
        switch (c) {
            case '"':
            case '\'':
            case '\\':
                return String.valueOf(c);
            case 'n':
                return "\n";
            case 't':
                return "\t";
            case 'r':
                return "\r";
            default:
                return "\\" + c;
        }
    }

    private Token readNumber() {
        int start = pos;
        if (text.charAt(pos) == '-') {
            pos++;
        }
        while (pos < text.length() && isDigit(text.charAt(pos))) {
            pos++;
        }
        if (pos < text.length() && text.charAt(pos) == '.' && isDigit(peek(1))) {
            pos++;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        return new Token(TokenType.NUMBER, text.substring(start, pos), start);
    }

    private Token readWord() {
        int start = pos;
        while (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
            pos++;
        }
        String word = text.substring(start, pos);
        TokenType keyword = KEYWORDS.get(word.toLowerCase(Locale.ROOT));
        return new Token(keyword != null ? keyword : TokenType.IDENTIFIER, word, start);
    }

    private void skipWhitespace() {
        while (pos < text.length() && isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    /**
     * 除常规空白外，还包括从网页、聊天复制时常带上的不间断空格和零宽字符
     */
    private static boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || EXTRA_WHITESPACE.indexOf(c) >= 0;
    }

    private char peek(int offset) {
        int index = pos + offset;
        return index < text.length() ? text.charAt(index) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
