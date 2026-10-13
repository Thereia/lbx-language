package lbx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语法分析：记号流 → 语法树（语法文档 §6 语句、§12 文法）。
 *
 * 两条实现路线：
 *   语句  —— 递归下降，每个语句形式一个方法（和递归下降分析实验是同一套做法）；
 *   表达式 —— 优先级爬升：§5 的九级优先级压成一张表 BIN + 一个 expr(minPrec) 方法，
 *             不再为每一级写一个 or_expr / and_expr / … 方法。
 */
final class Parser {

    /** §5 的二元运算符优先级表，数字越大结合越紧。 */
    private static final Map<String, Integer> BIN = Map.ofEntries(
            Map.entry("or", 1), Map.entry("and", 2),
            Map.entry("==", 4), Map.entry("!=", 4),
            Map.entry("<", 5), Map.entry("<=", 5), Map.entry(">", 5), Map.entry(">=", 5),
            Map.entry("+", 6), Map.entry("-", 6),
            Map.entry("*", 7), Map.entry("/", 7), Map.entry("%", 7));

    /** 一元 not 的优先级（§5 第 3 级：比 == 松，所以 not a == b 是 not (a == b)）。 */
    private static final int PREC_NOT = 3;

    /** 一元负号的优先级（§5 第 8 级：比 * 紧，所以 -a * b 是 (-a) * b）。 */
    private static final int PREC_NEG = 8;

    private final List<Token> toks;
    private int pos = 0;
    private int funcDepth = 0;   // 用于拒绝函数体之外的 return（§6.1）
    private int loopDepth = 0;   // 用于拒绝循环体之外的 break / continue（§6.1）

    Parser(List<Token> toks) {
        this.toks = toks;
    }

    // ---------------- 程序与声明 ----------------

    Node parseProgram() {
        List<Node> stmts = new ArrayList<>();
        while (peek().kind() != TokenKind.EOF) stmts.add(topStatement());
        Node program = new Node(Kind.BLOCK, 1);
        program.kids = stmts;
        return program;
    }

    /** 最外层才允许 func / type 定义（§7.1 只谈顶层函数，§12 文法亦然）。 */
    private Node topStatement() {
        Token t = peek();
        if (t.kind() == TokenKind.KEYWORD) {
            if (t.text().equals("func")) return funcDecl();
            if (t.text().equals("type")) return typeDecl();
        }
        return statement();
    }

    private Node funcDecl() {
        Token kw = next();
        Token name = expectIdent();
        expectOp("(");
        List<String> params = new ArrayList<>();
        if (!isOp(")")) {
            params.add(expectIdent().text());
            while (isOp(",")) {
                next();
                params.add(expectIdent().text());
            }
        }
        expectOp(")");
        funcDepth++;
        List<Node> body = blockBody();
        funcDepth--;
        expectKeyword("end");
        Node n = new Node(Kind.FUNC, kw.line());
        n.text = name.text();
        n.names = params;
        n.kids = body;
        return n;
    }

    /** type Point = { x = 0, y = 0 }; */
    private Node typeDecl() {
        Token kw = next();
        Token name = expectIdent();
        expectOp("=");
        expectOp("{");
        List<Node> fields = new ArrayList<>();
        if (!isOp("}")) {
            fields.add(fieldDef());
            while (isOp(",")) {
                next();
                if (isOp("}")) break;          // 允许尾逗号
                fields.add(fieldDef());
            }
        }
        expectOp("}");
        semicolon();
        Node n = new Node(Kind.TYPE, kw.line());
        n.text = name.text();
        n.kids = fields;
        return n;
    }

    /** 字段定义：名字 = 表达式。type 声明里是默认值，结构字面量里是初始值。 */
    private Node fieldDef() {
        Token f = expectIdent();
        expectOp("=");
        Node v = expr();
        Node n = new Node(Kind.FIELD_DEF, f.line());
        n.text = f.text();
        n.kids = List.of(v);
        return n;
    }

    // ---------------- 语句 ----------------

    private Node statement() {
        Token t = peek();
        if (t.kind() == TokenKind.KEYWORD) {
            switch (t.text()) {
                case "if":
                    return ifStmt();
                case "while":
                    return whileStmt();
                case "for":
                    return forStmt();
                case "return": {
                    if (funcDepth == 0) throw LbxError.syntax(t.line(), t.col(), "return 只能出现在函数体内");
                    next();
                    Node n = new Node(Kind.RETURN, t.line());
                    if (!isOp(";")) n.kids = List.of(expr());
                    semicolon();
                    return n;
                }
                case "break": {
                    if (loopDepth == 0) throw LbxError.syntax(t.line(), t.col(), "break 只能出现在循环体内");
                    next();
                    semicolon();
                    return new Node(Kind.BREAK, t.line());
                }
                case "continue": {
                    if (loopDepth == 0) throw LbxError.syntax(t.line(), t.col(), "continue 只能出现在循环体内");
                    next();
                    semicolon();
                    return new Node(Kind.CONTINUE, t.line());
                }
                case "func":
                case "type":
                    throw LbxError.syntax(t.line(), t.col(), t.text() + " 定义只能出现在最外层");
                default:
                    break;      // true / false / null / not 交给表达式解析
            }
        }
        if (isOp(";")) {                            // 空语句（§6.1）
            next();
            return new Node(Kind.BLOCK, t.line());
        }
        Node e = expr();
        if (isOp("=")) {
            Token eq = next();
            Node v = expr();
            if (e.kind != Kind.VAR && e.kind != Kind.INDEX && e.kind != Kind.FIELD) {
                throw LbxError.syntax(e.line, eq.col(), "赋值号左边必须是变量、下标或字段");
            }
            semicolon();
            Node n = new Node(Kind.ASSIGN, e.line);
            n.kids = List.of(e, v);
            return n;
        }
        semicolon();
        Node n = new Node(Kind.EXPR, e.line);
        n.kids = List.of(e);
        return n;
    }

    /** if / elif / else，用嵌套 IF 表示 elif 链（§6.1）。 */
    private Node ifStmt() {
        Token kw = next();
        Node cond = expr();
        Node thenBlock = block(blockBody(), kw.line());
        Node alt = null;
        Token t = peek();
        if (t.kind() == TokenKind.KEYWORD && t.text().equals("elif")) {
            // elif 分支递归调用自己，那一层会消费掉结尾的 end，
            // 所以这条路径上不能再 expectKeyword("end")，否则会把外层的 end 抢走。
            alt = block(List.of(ifStmt()), t.line());
            Node n = new Node(Kind.IF, kw.line());
            n.kids = List.of(cond, thenBlock, alt);
            return n;
        }
        if (t.kind() == TokenKind.KEYWORD && t.text().equals("else")) {
            next();
            alt = block(blockBody(), t.line());
        }
        expectKeyword("end");
        Node n = new Node(Kind.IF, kw.line());
        n.kids = (alt == null) ? List.of(cond, thenBlock) : List.of(cond, thenBlock, alt);
        return n;
    }

    private Node whileStmt() {
        Token kw = next();
        Node cond = expr();
        loopDepth++;
        List<Node> body = blockBody();
        loopDepth--;
        expectKeyword("end");
        Node n = new Node(Kind.WHILE, kw.line());
        n.kids = List.of(cond, block(body, kw.line()));
        return n;
    }

    private Node forStmt() {
        Token kw = next();
        Token name = expectIdent();
        expectKeyword("in");
        Node iter = expr();
        loopDepth++;
        List<Node> body = blockBody();
        loopDepth--;
        expectKeyword("end");
        Node n = new Node(Kind.FOR, kw.line());
        n.names = List.of(name.text());
        n.kids = List.of(iter, block(body, kw.line()));
        return n;
    }

    /** 语句序列，直到 end / else / elif（这三个关键字不消费，交给调用方判断）。 */
    private List<Node> blockBody() {
        List<Node> list = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t.kind() == TokenKind.EOF) {
                throw LbxError.syntax(t.line(), t.col(), "文件意外结束，缺少 'end'");
            }
            if (t.kind() == TokenKind.KEYWORD
                    && (t.text().equals("end") || t.text().equals("else") || t.text().equals("elif"))) {
                break;
            }
            list.add(statement());
        }
        return list;
    }

    // ---------------- 表达式：优先级爬升 ----------------

    Node expr() {
        return expr(0);
    }

    /** 解析优先级不低于 minPrec 的表达式；左结合用 minPrec = 优先级 + 1 实现。 */
    private Node expr(int minPrec) {
        Node left = prefixOperand();
        while (true) {
            Token t = peek();
            if (t.kind() != TokenKind.OP && t.kind() != TokenKind.KEYWORD) break;
            Integer p = BIN.get(t.text());
            if (p == null || p < minPrec) break;
            next();
            Node right = expr(p + 1);
            Node n = new Node(Kind.BIN, t.line());
            n.text = t.text();
            n.kids = List.of(left, right);
            left = n;
        }
        return left;
    }

    /** 前缀运算符：一元负号（紧）与 not（松），优先级不同所以各自指定 minPrec。 */
    private Node prefixOperand() {
        Token t = peek();
        if (t.kind() == TokenKind.OP && t.text().equals("-")) {
            next();
            return unary("-", expr(PREC_NEG), t.line());
        }
        if (t.kind() == TokenKind.KEYWORD && t.text().equals("not")) {
            next();
            return unary("not", expr(PREC_NOT), t.line());
        }
        return postfix();
    }

    /** 后缀：调用 ()、下标 []、字段 .（§5 第 9 级，最紧）。 */
    private Node postfix() {
        Node e = primary();
        while (true) {
            Token t = peek();
            if (t.kind() == TokenKind.OP && t.text().equals("(")) {
                next();
                List<Node> kids = new ArrayList<>();
                kids.add(e);
                if (!isOp(")")) {
                    kids.add(expr());
                    while (isOp(",")) {
                        next();
                        if (isOp(")")) break;   // 允许尾逗号
                        kids.add(expr());
                    }
                }
                expectOp(")");
                Node n = new Node(Kind.CALL, t.line());
                n.kids = kids;
                e = n;
            } else if (t.kind() == TokenKind.OP && t.text().equals("[")) {
                next();
                Node idx = expr();
                expectOp("]");
                Node n = new Node(Kind.INDEX, t.line());
                n.kids = List.of(e, idx);
                e = n;
            } else if (t.kind() == TokenKind.OP && t.text().equals(".")) {
                next();
                Token f = expectIdent();
                Node n = new Node(Kind.FIELD, t.line());
                n.text = f.text();
                n.kids = List.of(e);
                e = n;
            } else {
                break;
            }
        }
        return e;
    }

    private Node primary() {
        Token t = peek();
        switch (t.kind()) {
            case INT: {
                next();
                return lit(Kind.INT, new VInt((Long) t.value()), t.line());
            }
            case FLOAT: {
                next();
                return lit(Kind.FLOAT, new VFloat((Double) t.value()), t.line());
            }
            case STR: {
                next();
                return stringNode(t);
            }
            case IDENT: {
                next();
                if (isOp("{")) return structLiteral(t);      // Point { x = 3 }
                Node n = new Node(Kind.VAR, t.line());
                n.text = t.text();
                return n;
            }
            case KEYWORD: {
                if (t.text().equals("true")) {
                    next();
                    return lit(Kind.BOOL, new VBool(true), t.line());
                }
                if (t.text().equals("false")) {
                    next();
                    return lit(Kind.BOOL, new VBool(false), t.line());
                }
                if (t.text().equals("null")) {
                    next();
                    return lit(Kind.NULL, VNull.NIL, t.line());
                }
                break;
            }
            case OP: {
                if (t.text().equals("(")) {                      // 括号只影响结合顺序
                    next();
                    Node e = expr();
                    expectOp(")");
                    return e;
                }
                if (t.text().equals("[")) {                      // 数组字面量
                    next();
                    List<Node> items = new ArrayList<>();
                    if (!isOp("]")) {
                        items.add(expr());
                        while (isOp(",")) {
                            next();
                            if (isOp("]")) break;
                            items.add(expr());
                        }
                    }
                    expectOp("]");
                    Node n = new Node(Kind.ARRAY, t.line());
                    n.kids = items;
                    return n;
                }
                break;
            }
            default:
                break;
        }
        throw LbxError.syntax(t.line(), t.col(), "期望表达式，实际遇到 " + t.describe());
    }

    private Node structLiteral(Token typeName) {
        expectOp("{");
        List<Node> fields = new ArrayList<>();
        if (!isOp("}")) {
            fields.add(fieldDef());
            while (isOp(",")) {
                next();
                if (isOp("}")) break;
                fields.add(fieldDef());
            }
        }
        expectOp("}");
        Node n = new Node(Kind.STRUCT, typeName.line());
        n.text = typeName.text();
        n.kids = fields;
        return n;
    }

    // ---------------- 字符串与 ${} 插值（§3.3、§7.5） ----------------

    private Node stringNode(Token t) {
        String raw = (String) t.value();
        if (raw.indexOf("${") < 0) return lit(Kind.STR, new VStr(decode(raw, t)), t.line());

        List<Node> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                // \${ 是不想插值的美元号：转成普通 $ 收进字面量段，其余转义留给 decode 处理
                if (raw.charAt(i + 1) == '$') buf.append('$');
                else buf.append(c).append(raw.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '$' && i + 1 < raw.length() && raw.charAt(i + 1) == '{') {
                if (buf.length() > 0) {
                    parts.add(lit(Kind.STR, new VStr(decode(buf.toString(), t)), t.line()));
                    buf.setLength(0);
                }
                int end = matchBrace(raw, i + 2, t);
                parts.add(parseFragment(raw.substring(i + 2, end), t));
                i = end + 1;
                continue;
            }
            buf.append(c);
            i++;
        }
        if (buf.length() > 0) parts.add(lit(Kind.STR, new VStr(decode(buf.toString(), t)), t.line()));

        Node n = new Node(Kind.INTERP, t.line());
        n.kids = parts;
        return n;
    }

    /** 找出与 ${ 配对的 }（要跳过片段里的字符串与嵌套的 {}），返回 } 的下标。 */
    private int matchBrace(String s, int from, Token t) {
        int depth = 1;
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                i++;
                while (i < s.length() && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\') i++;
                    i++;
                }
                i++;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        throw LbxError.syntax(t.line(), t.col(), "字符串插值缺少配对的 '}'");
    }

    /** 把插值片段当成一个独立的小程序来解析，只允许含一个表达式。 */
    private Node parseFragment(String src, Token t) {
        Parser p = new Parser(new Lexer(src, t.line()).lex());
        Node e = p.expr();
        Token rest = p.peek();
        if (rest.kind() != TokenKind.EOF) {
            throw LbxError.syntax(t.line(), t.col(), "字符串插值里有多余内容：" + rest.describe());
        }
        return e;
    }

    /** 解码 §3.3 规定的转义；不认识的转义按原样保留。 */
    private String decode(String s, Token t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char d = s.charAt(++i);
            switch (d) {
                case 'n'  -> b.append('\n');
                case 't'  -> b.append('\t');
                case '\\' -> b.append('\\');
                case '"'  -> b.append('"');
                case '$'  -> b.append('$');
                default   -> b.append('\\').append(d);
            }
        }
        return b.toString();
    }

    // ---------------- 记号流小工具 ----------------

    private Token peek() {
        return toks.get(pos);
    }

    private Token next() {
        return toks.get(pos++);
    }

    private boolean isOp(String s) {
        Token t = peek();
        return t.kind() == TokenKind.OP && t.text().equals(s);
    }

    private void expectOp(String s) {
        if (!isOp(s)) {
            Token t = peek();
            throw LbxError.syntax(t.line(), t.col(), "期望 '" + s + "'，实际遇到 " + t.describe());
        }
        next();
    }

    private void expectKeyword(String s) {
        Token t = peek();
        if (t.kind() != TokenKind.KEYWORD || !t.text().equals(s)) {
            throw LbxError.syntax(t.line(), t.col(), "期望 '" + s + "'，实际遇到 " + t.describe());
        }
        next();
    }

    /** 简单语句与 type 声明必须以 ; 结束（§6.1、§6.2）。 */
    private void semicolon() {
        if (!isOp(";")) {
            Token t = peek();
            throw LbxError.syntax(t.line(), t.col(), "期望 ';'，实际遇到 " + t.describe());
        }
        next();
    }

    private Token expectIdent() {
        Token t = peek();
        if (t.kind() != TokenKind.IDENT) {
            throw LbxError.syntax(t.line(), t.col(), "期望标识符，实际遇到 " + t.describe());
        }
        return next();
    }

    // ---------------- 造节点的小工具 ----------------

    private Node lit(Kind kind, Value v, int line) {
        Node n = new Node(kind, line);
        n.value = v;
        return n;
    }

    private Node unary(String op, Node operand, int line) {
        Node n = new Node(Kind.UN, line);
        n.text = op;
        n.kids = List.of(operand);
        return n;
    }

    private Node block(List<Node> stmts, int line) {
        Node n = new Node(Kind.BLOCK, line);
        n.kids = stmts;
        return n;
    }
}
