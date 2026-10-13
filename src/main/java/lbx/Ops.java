package lbx;

/**
 * §5 运算规则集中在这里：二元运算符、一元运算符、比较、条件检查。
 * 求值器（Interp）里只负责「先算哪一边」，类型判断一律回这里，
 * 所以「int + string 报错」这类规则全文只写一次。
 */
final class Ops {

    private Ops() {}

    // ---------------- 算术（§5 第 6、7、8 级） ----------------
    //
    // 数值规则（v1.3）：
    //   两个 int 之间的 + - * / % 保持整数语义，其中 `/` 是整除（向零截断）；
    //   只要有一边是 float，就按实数运算，结果一律是 float（int 自动提升）。

    static Value add(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() + y.v());
        if (a instanceof VStr x && b instanceof VStr y) return new VStr(x.v() + y.v());
        if (isNum(a) && isNum(b)) return new VFloat(dbl(a) + dbl(b));
        throw typeErr("+", a, b, line);
    }

    static Value sub(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() - y.v());
        if (isNum(a) && isNum(b)) return new VFloat(dbl(a) - dbl(b));
        throw typeErr("-", a, b, line);
    }

    static Value mul(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) return new VInt(x.v() * y.v());
        if (isNum(a) && isNum(b)) return new VFloat(dbl(a) * dbl(b));
        throw typeErr("*", a, b, line);
    }

    /** §5：两个整数相除是整除；有浮点则实数除法。除零一律报运行时错误（不产生 Infinity / NaN）。 */
    static Value div(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) {
            if (y.v() == 0) throw LbxError.runtime(line, "除零");
            return new VInt(x.v() / y.v());
        }
        if (isNum(a) && isNum(b)) {
            double d = dbl(b);
            if (d == 0.0) throw LbxError.runtime(line, "除零");   // -0.0 也会被判为 0
            return new VFloat(dbl(a) / d);
        }
        throw typeErr("/", a, b, line);
    }

    static Value mod(Value a, Value b, int line) {
        if (a instanceof VInt x && b instanceof VInt y) {
            if (y.v() == 0) throw LbxError.runtime(line, "模零");
            return new VInt(x.v() % y.v());
        }
        if (isNum(a) && isNum(b)) {
            double d = dbl(b);
            if (d == 0.0) throw LbxError.runtime(line, "模零");
            return new VFloat(dbl(a) % d);
        }
        throw typeErr("%", a, b, line);
    }

    static Value neg(Value a, int line) {
        if (a instanceof VInt x) return new VInt(-x.v());
        if (a instanceof VFloat x) return new VFloat(-x.v());
        throw LbxError.runtime(line, "运算符 - 不支持 " + a.typeName());
    }

    // ---------------- 比较（§5 第 4、5 级） ----------------

    /**
     * `==` 的语义：类型不同直接得 false（不报错）；数组 / 结构与函数比较引用。
     * 例外（v1.3）：int 与 float 之间**按数值比较**，所以 1 == 1.0 得 true
     * ——否则 `if x == 1` 在 x 是 1.0 时会莫名判假。`!=` 由调用方取反。
     */
    static boolean eq(Value a, Value b) {
        if (a instanceof VInt x && b instanceof VInt y) return x.v() == y.v();
        if (isNum(a) && isNum(b)) return dbl(a) == dbl(b);
        if (a instanceof VStr x && b instanceof VStr y) return x.v().equals(y.v());
        if (a instanceof VBool x && b instanceof VBool y) return x.v() == y.v();
        if (a instanceof VNull && b instanceof VNull) return true;
        return a == b;   // 容器 / 函数比引用；类型不同时必然为 false
    }

    /** `<` `<=` `>` `>=`：数值之间（int / float 任意组合）与 string 与 string（字典序）合法。 */
    static boolean cmp(String op, Value a, Value b, int line) {
        int c;
        if (a instanceof VInt x && b instanceof VInt y) c = Long.compare(x.v(), y.v());
        else if (isNum(a) && isNum(b)) c = Double.compare(dbl(a), dbl(b));
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

    // ---------------- 数值类型的小工具（int / float 提升，§5） ----------------

    /** 是不是两个数值类型之一。 */
    private static boolean isNum(Value v) {
        return v instanceof VInt || v instanceof VFloat;
    }

    /** 取数值的 double 值：int 自动提升为 float。 */
    private static double dbl(Value v) {
        return (v instanceof VInt i) ? (double) i.v() : ((VFloat) v).v();
    }
}
