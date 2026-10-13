package lbx;

/**
 * 记号种类：关键字与运算符都归 KEYWORD / OP，靠 text 区分。
 * 这样枚举只有 7 个值，而不是给每个关键字、每个运算符各开一个枚举常量（语法文档 §3.2、§3.4）。
 */
enum TokenKind { INT, FLOAT, STR, IDENT, KEYWORD, OP, EOF }

/** LBX 的一个记号（语法文档 §3）。line / col 均为 1 起算，用于 §10.3 的错误定位。 */
record Token(TokenKind kind, String text, Object value, int line, int col) {

    /** 错误消息里怎么称呼这个记号（§10.3 形如「期望 ';'，实际遇到 'end'」）。 */
    String describe() {
        if (kind == TokenKind.EOF) return "文件结尾";
        return "'" + text + "'";
    }
}
