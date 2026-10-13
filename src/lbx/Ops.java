package lbx;

/**
 * §5 运算规则集中在这里：二元运算符、一元运算符、比较、条件检查。
 * 求值器（Interp）里只负责「先算哪一边」，类型判断一律回这里，
 * 所以「int + string 报错」这类规则全文只写一次。
 */
final class Ops {

    private Ops() {}

    // ---------------- 算术（§5 第 6、7、8 级：只有 int，+ 另支持字符串拼接） ----------------

    static Value add(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() + y.v());
        if (a instanceof VStr x && b instanceof VStr y) return new VStr(x.v() + y.v());
        throw typeErr("+", a, b, line);
    }

    static Value sub(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() - y.v());
        throw typeErr("-", a, b, line);
    }

    static Value mul(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() * y.v());
        throw typeErr("*", a, b, line);
    }

    /** §5：`/` 是整除（向零截断），除零报运行时错误。 */
    static Value div(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) {
            if (y.v() == 0) throw LbxError.runtime(line, "除零");
            return new VInt(x.v() / y.v());
        }
        throw typeErr("/", a, b, line);
    }

    static Value mod(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) {
            if (y.v() == 0) throw LbxError.runtime(line, "模零");
            return new VInt(x.v() % y.v());
        }
        throw typeErr("%", a, b, line);
    }

    static Value neg(Value a, int line) {
        if (a instanceof VInt x) return new VInt(-x.v());
        throw LbxError.runtime(line, "运算符 - 不支持 " + a.typeName());
    }

    // ---------------- 比较（§5 第 4、5 级） ----------------

    /**
     * `==` 的语义：类型不同直接得 false（不报错）；数组 / 结构与函数比较引用。
     * `!=` 由调用方取反。
     */
    static boolean eq(Value a, Value b) {
        if (a instanceof VInt x && b instanceof VInt y) return x.v() == y.v();
        if (a instanceof VStr x && b instanceof VStr y) return x.v().equals(y.v());
        if (a instanceof VBool x && b instanceof VBool y) return x.v() == y.v();
        if (a instanceof VNull && b instanceof VNull) return true;
        return a == b;   // 容器 / 函数比引用；类型不同时必然为 false
    }

    /** `<` `<=` `>` `>=`：只有 int 与 int、string 与 string（字典序）合法。 */
    static boolean cmp(String op, Value a, Value b, int line) {
        int c;
        if (a instanceof VInt x && b instanceof VInt y) c = Long.compare(x.v(), y.v());
        else if (a instanceof VStr x && b instanceof VStr y) c = x.v().compareTo(y.v());
        else throw LbxError.runtime(line,
                "运算符 " + op + " 不支持 " + a.typeName() + " 和 " + b.typeName());
        return switch (op) {
            case "<"  -> c < 0;
            case "<=" -> c <= 0;
            case ">"  -> c > 0;
            default   -> c >= 0;          // ">="
        };
    }

    // ---------------- 逻辑（§5 第 1、2、3 级，短路由 Interp 负责） ----------------

    /** §6.1：条件表达式必须是 bool，`if 1` 报运行时错误。 */
    static boolean cond(Value v, int line) {
        if (v instanceof VBool b) return b.v();
        throw LbxError.runtime(line, "条件必须是布尔值，实际是 " + v.typeName());
    }

    static Value not(Value v, int line) {
        return new VBool(!cond(v, line));
    }

    // ---------------- 内置函数的参数检查（§9） ----------------

    /** 取一个整数参数，不是 int 就报错。 */
    static long intOf(Value v, String what, int line) {
        if (v instanceof VInt i) return i.v();
        throw LbxError.runtime(line, what + " 的参数必须是整数，实际是 " + v.typeName());
    }

    private static LbxError typeErr(String op, Value a, Value b, int line) {
        return LbxError.runtime(line,
                "运算符 " + op + " 不支持 " + a.typeName() + " 和 " + b.typeName());
    }
}
