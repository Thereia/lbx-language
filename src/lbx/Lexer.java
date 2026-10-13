package lbx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 词法分析：字符流 → 记号流（语法文档 §3）。
 *
 * v1.0 的两条简化（README §6.2）：
 *   1. 语句靠 `;` 分句，所以换行、缩进、空行全是空白，不产生 NEWLINE 记号（行号仍要统计，供报错用）；
 *   2. 整个脚本一次性读进 String，回退就是下标回退，不需要词法分析实验里那套配对缓冲区。
 */
final class Lexer {

    /** 关键字表（§3.2，共 17 个）。最长匹配出完整标识符后再查这张表，所以 iffy 仍是标识符。 */
    static final Set<String> KEYWORDS = Set.of(
            "func", "end", "if", "elif", "else", "while", "for", "in",
            "return", "break", "continue", "type", "true", "false", "null",
            "and", "or", "not");

    /** 两字符运算符（最长匹配优先）。 */
    private static final String[] OPS2 = {"==", "!=", "<=", ">="};

    /** 单字符运算符与分隔符（§3.4）。 */
    private static final String OPS1 = "+-*/%<>=()[]{},;.";

    private final String src;
    private final List<Token> out = new ArrayList<>();
    private int pos = 0;
    private int line;
    private int col = 1;

    Lexer(String src) {
        this(src, 1);
    }

    /** baseLine：语法树里的字符串插值片段要按它所在的那一行报错，所以支持给一个起始行号。 */
    Lexer(String src, int baseLine) {
        this.src = src;
        this.line = baseLine;
    }

    List<Token> lex() {
        while (pos < src.length()) {
            char c = cur();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {   // 换行不产生记号
                next();
                continue;
            }
            if (c == '/' && peek(1) == '/') {                       // 行注释
                while (pos < src.length() && cur() != '\n') next();
                continue;
            }
            if (c == '/' && peek(1) == '*') {                       // 块注释
                blockComment();
                continue;
            }
            if (c >= '0' && c <= '9') {
                number();
                continue;
            }
            if (isIdentStart(c)) {
                ident();
                continue;
            }
            if (c == '"') {
                string();
                continue;
            }
            operator();
        }
        out.add(new Token(TokenKind.EOF, "", null, line, col));
        return out;
    }

    // ---------------- 各类记号 ----------------

    private void number() {
        int l = line, c0 = col;
        int start = pos;
        while (pos < src.length() && cur() >= '0' && cur() <= '9') next();
        String text = src.substring(start, pos);
        long v;
        try {
            v = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw LbxError.lex(l, c0, "整数超出范围：" + text);
        }
        out.add(new Token(TokenKind.INT, text, v, l, c0));
    }

    private void ident() {
        int l = line, c0 = col;
        int start = pos;
        while (pos < src.length() && isIdentPart(cur())) next();
        String text = src.substring(start, pos);
        TokenKind kind = KEYWORDS.contains(text) ? TokenKind.KEYWORD : TokenKind.IDENT;
        out.add(new Token(kind, text, null, l, c0));
    }

    /**
     * 字符串字面量。这里**只切边界、不解码转义**：值保留双引号之间的原文（含 \n 之类的转义写法），
     * 交给 Parser 处理转义与 ${} 插值 —— 这样 `\${`（不想插值的美元号）才区分得开。
     * §3.3 规定字符串可跨行，所以这里遇到换行不报错。
     *
     * 进入 ${...} 之后要数括号深度、并跳过片段里的嵌套字符串，
     * 否则 ${"内层字符串"} 里的引号会被误认成外层字符串的结束引号。
     */
    private void string() {
        int l = line, c0 = col;
        next();                                  // 开头的 "
        StringBuilder raw = new StringBuilder();
        int braceDepth = 0;                      // 处在 ${...} 内部时大于 0
        while (true) {
            if (pos >= src.length()) throw LbxError.lex(l, c0, "未闭合的字符串");
            char c = cur();
            if (c == '\\') {                     // 转义：连反斜杠一起原样收进来
                raw.append(c);
                next();
                if (pos < src.length()) {
                    raw.append(cur());
                    next();
                }
                continue;
            }
            if (braceDepth > 0) {
                if (c == '"') {                  // 插值片段里的嵌套字符串：整段收进来
                    raw.append(c);
                    next();
                    while (true) {
                        if (pos >= src.length()) throw LbxError.lex(l, c0, "未闭合的字符串");
                        char d = cur();
                        if (d == '\\') {
                            raw.append(d);
                            next();
                            if (pos < src.length()) {
                                raw.append(cur());
                                next();
                            }
                            continue;
                        }
                        raw.append(d);
                        next();
                        if (d == '"') break;
                    }
                    continue;
                }
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
                raw.append(c);
                next();
                continue;
            }
            if (c == '"') {
                next();
                break;
            }
            if (c == '$' && peek(1) == '{') {    // 开始一段插值
                braceDepth = 1;
                raw.append(c);
                next();
                raw.append(cur());
                next();
                continue;
            }
            raw.append(c);
            next();
        }
        out.add(new Token(TokenKind.STR, "\"" + raw + "\"", raw.toString(), l, c0));
    }

    private void blockComment() {
        int l = line, c0 = col;
        next();
        next();                                  // /*
        while (true) {
            if (pos >= src.length()) throw LbxError.lex(l, c0, "未闭合的块注释");
            if (cur() == '*' && peek(1) == '/') {
                next();
                next();
                return;
            }
            next();
        }
    }

    /** 运算符与分隔符，最长匹配优先；&& || ! 归一化成 and / or / not 三个关键字（§3.2 别名）。 */
    private void operator() {
        int l = line, c0 = col;
        for (String op : OPS2) {
            if (src.startsWith(op, pos)) {
                next();
                next();
                out.add(new Token(TokenKind.OP, op, null, l, c0));
                return;
            }
        }
        if (src.startsWith("&&", pos)) {
            next();
            next();
            out.add(new Token(TokenKind.KEYWORD, "and", null, l, c0));
            return;
        }
        if (src.startsWith("||", pos)) {
            next();
            next();
            out.add(new Token(TokenKind.KEYWORD, "or", null, l, c0));
            return;
        }
        if (cur() == '!') {
            next();
            out.add(new Token(TokenKind.KEYWORD, "not", null, l, c0));
            return;
        }
        if (OPS1.indexOf(cur()) >= 0) {
            String text = String.valueOf(cur());
            next();
            out.add(new Token(TokenKind.OP, text, null, l, c0));
            return;
        }
        throw LbxError.lex(l, c0, "非法字符 '" + cur() + "'");
    }

    // ---------------- 字符流小工具 ----------------

    private char cur() {
        return src.charAt(pos);
    }

    private char peek(int k) {
        int i = pos + k;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    /** 取下一个字符并推进位置；行号列号在这里统一维护，所以注释、字符串里的换行也不会算错。 */
    private char next() {
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return c;
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }
}
