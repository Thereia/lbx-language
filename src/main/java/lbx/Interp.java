package lbx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求值器：遍历语法树算出结果（语法文档 §7 语义、§9 内置函数）。
 *
 * 三个关键实现：
 *   1. 作用域栈：栈底是全局作用域，每次函数调用压一层（§7.2）；
 *   2. eval 一个 switch：表达式按 kind 分发，类型规则一律转给 Ops；
 *   3. 控制流用异常：return / break / continue 靠三个无栈追踪的异常往外跳，
 *      这样每个语句方法都不必返回「是否要中断」的标志位。
 */
final class Interp {

    /** return 的实现：携带返回值往外抛。 */
    static final class ReturnEx extends RuntimeException {
        final Value v;

        ReturnEx(Value v) {
            super(null, null, false, false);
            this.v = v;
        }
    }

    static final class BreakEx extends RuntimeException {
        BreakEx() { super(null, null, false, false); }
    }

    static final class ContinueEx extends RuntimeException {
        ContinueEx() { super(null, null, false, false); }
    }

    /** §7.1 递归深度上限。 */
    private static final int MAX_CALL_DEPTH = 1000;

    /** range 生成序列的长度上限，防止误写 range(1000000000) 把演示卡死。 */
    private static final long MAX_RANGE = 10_000_000L;

    /** 作用域栈，第 0 个是全局作用域。 */
    private final List<Map<String, Value>> scopes = new ArrayList<>();

    /** type 声明的字段默认值：类型名 → （字段名 → 默认值），用 LinkedHashMap 保住字段顺序（§7.4）。 */
    private final Map<String, LinkedHashMap<String, Value>> types = new LinkedHashMap<>();

    private final PrintStream out;
    private final BufferedReader in;
    private int callDepth = 0;

    Interp(PrintStream out, BufferedReader in) {
        this.out = out;
        this.in = in;
    }

    // ---------------- 程序执行 ----------------

    void run(Node program) {
        scopes.add(new HashMap<>());
        registerBuiltins();

        // 第一遍：登记全部顶层 func / type —— 这就是 §7.1 的「定义即注册」，
        // 所以函数之间可以互相递归，也可以先调用后定义。
        for (Node st : program.kids) {
            if (st.kind == Kind.FUNC) {
                scopes.get(0).put(st.text, new VFunc(st.text, st.names, st.kids));
            } else if (st.kind == Kind.TYPE) {
                registerType(st);
            }
        }

        // 第二遍：自上而下执行顶层语句。
        for (Node st : program.kids) exec(st);
    }

    private void registerType(Node n) {
        LinkedHashMap<String, Value> defaults = new LinkedHashMap<>();
        for (Node f : n.kids) defaults.put(f.text, eval(f.kids.get(0)));
        types.put(n.text, defaults);
    }

    private void exec(Node n) {
        switch (n.kind) {
            case BLOCK -> {
                for (Node s : n.kids) exec(s);
            }
            case FUNC, TYPE -> {
                // 第一遍已登记，执行阶段不做事
            }
            case EXPR -> eval(n.kids.get(0));
            case ASSIGN -> assign(n);
            case IF -> {
                if (Ops.cond(eval(n.kids.get(0)), n.line)) exec(n.kids.get(1));
                else if (n.kids.size() > 2) exec(n.kids.get(2));
            }
            case WHILE -> {
                while (Ops.cond(eval(n.kids.get(0)), n.line)) {
                    try {
                        exec(n.kids.get(1));
                    } catch (BreakEx e) {
                        break;
                    } catch (ContinueEx e) {
                        // 直接进入下一轮
                    }
                }
            }
            case FOR -> {
                Value iter = eval(n.kids.get(0));
                if (!(iter instanceof VArray arr)) {
                    throw LbxError.runtime(n.line, "for 只能遍历数组，实际是 " + iter.typeName());
                }
                String var = n.names.get(0);
                for (Value item : arr.items) {
                    assignName(var, item);          // §7.2：循环不产生新作用域
                    try {
                        exec(n.kids.get(1));
                    } catch (BreakEx e) {
                        break;
                    } catch (ContinueEx e) {
                        // 直接进入下一轮
                    }
                }
            }
            case RETURN -> throw new ReturnEx(n.kids.isEmpty() ? VNull.NIL : eval(n.kids.get(0)));
            case BREAK -> throw new BreakEx();
            case CONTINUE -> throw new ContinueEx();
            default -> throw new IllegalStateException("未知语句：" + n.kind);
        }
    }

    private void assign(Node n) {
        Node target = n.kids.get(0);
        Value v = eval(n.kids.get(1));
        switch (target.kind) {
            case VAR -> assignName(target.text, v);
            case INDEX -> {
                Value av = eval(target.kids.get(0));
                if (!(av instanceof VArray arr)) {
                    throw LbxError.runtime(target.line, "下标赋值只能用于数组，实际是 " + av.typeName());
                }
                int i = indexOf(eval(target.kids.get(1)), target.line);
                if (i < 0 || i >= arr.items.size()) throw outOfRange(target.line, i, arr.items.size());
                arr.items.set(i, v);
            }
            case FIELD -> {
                Value ov = eval(target.kids.get(0));
                if (!(ov instanceof VStruct st)) {
                    throw LbxError.runtime(target.line, "字段赋值只能用于结构，实际是 " + ov.typeName());
                }
                if (!st.fields.containsKey(target.text)) {
                    throw LbxError.runtime(target.line, "结构 " + st.type + " 没有字段 " + target.text);
                }
                st.fields.put(target.text, v);
            }
            default -> throw new IllegalStateException("不是可赋值目标：" + target.kind);
        }
    }

    // ---------------- 表达式求值 ----------------

    private Value eval(Node n) {
        switch (n.kind) {
            case INT, FLOAT, BOOL, NULL, STR -> {
                return (Value) n.value;
            }
            case INTERP -> {
                StringBuilder b = new StringBuilder();
                for (Node p : n.kids) {
                    // kids 交替出现「字面量段」与「表达式节点」：
                    // 字面量段直接取文本（不加引号），表达式段求值后按 §8 的 str() 规则
                    if (p.kind == Kind.STR) b.append(((Value) p.value).str());
                    else b.append(eval(p).str());
                }
                return new VStr(b.toString());
            }
            case VAR -> {
                return lookup(n.text, n.line);
            }
            case BIN -> {
                return binOp(n);
            }
            case UN -> {
                Value v = eval(n.kids.get(0));
                return n.text.equals("-") ? Ops.neg(v, n.line) : Ops.not(v, n.line);
            }
            case CALL -> {
                return call(n);
            }
            case INDEX -> {
                Value av = eval(n.kids.get(0));
                if (!(av instanceof VArray arr)) {
                    throw LbxError.runtime(n.line, "下标运算只能用于数组，实际是 " + av.typeName());
                }
                int i = indexOf(eval(n.kids.get(1)), n.line);
                if (i < 0 || i >= arr.items.size()) throw outOfRange(n.line, i, arr.items.size());
                return arr.items.get(i);
            }
            case FIELD -> {
                Value ov = eval(n.kids.get(0));
                if (!(ov instanceof VStruct st)) {
                    throw LbxError.runtime(n.line, "字段访问只能用于结构，实际是 " + ov.typeName());
                }
                Value v = st.fields.get(n.text);
                if (v == null) throw LbxError.runtime(n.line, "结构 " + st.type + " 没有字段 " + n.text);
                return v;
            }
            case ARRAY -> {
                List<Value> items = new ArrayList<>();
                for (Node e : n.kids) items.add(eval(e));
                return new VArray(items);
            }
            case STRUCT -> {
                LinkedHashMap<String, Value> defaults = types.get(n.text);
                if (defaults == null) throw LbxError.runtime(n.line, "未定义的结构类型 " + n.text);
                VStruct st = new VStruct(n.text);
                st.fields.putAll(defaults);          // 缺省字段取类型声明里的默认值（§7.4）
                for (Node f : n.kids) {
                    if (!st.fields.containsKey(f.text)) {
                        throw LbxError.runtime(f.line, "结构 " + n.text + " 没有字段 " + f.text);
                    }
                    st.fields.put(f.text, eval(f.kids.get(0)));
                }
                return st;
            }
            default -> throw new IllegalStateException("未知表达式：" + n.kind);
        }
    }

    private Value binOp(Node n) {
        Node ln = n.kids.get(0);
        Node rn = n.kids.get(1);
        String op = n.text;
        int line = n.line;

        // 逻辑运算要短路，所以不能先把两边都算出来
        if (op.equals("and")) {
            if (!Ops.cond(eval(ln), line)) return new VBool(false);
            return new VBool(Ops.cond(eval(rn), line));
        }
        if (op.equals("or")) {
            if (Ops.cond(eval(ln), line)) return new VBool(true);
            return new VBool(Ops.cond(eval(rn), line));
        }

        Value a = eval(ln);
        Value b = eval(rn);
        return switch (op) {
            case "+" -> Ops.add(a, b, line);
            case "-" -> Ops.sub(a, b, line);
            case "*" -> Ops.mul(a, b, line);
            case "/" -> Ops.div(a, b, line);
            case "%" -> Ops.mod(a, b, line);
            case "==" -> new VBool(Ops.eq(a, b));
            case "!=" -> new VBool(!Ops.eq(a, b));
            default -> new VBool(Ops.cmp(op, a, b, line));   // < <= > >=
        };
    }

    private Value call(Node n) {
        Value callee = eval(n.kids.get(0));
        List<Value> args = new ArrayList<>();
        for (int i = 1; i < n.kids.size(); i++) args.add(eval(n.kids.get(i)));

        if (callee instanceof VBuiltin b) return b.fn.call(args, n.line);
        if (callee instanceof VFunc f) return callFunc(f, args, n.line);
        throw LbxError.runtime(n.line, "不是函数，无法调用：" + callee.typeName());
    }

    private Value callFunc(VFunc f, List<Value> args, int line) {
        if (args.size() != f.params.size()) {
            throw LbxError.runtime(line, "函数 " + f.name + " 需要 " + f.params.size()
                    + " 个参数，实际给了 " + args.size() + " 个");
        }
        if (callDepth >= MAX_CALL_DEPTH) {
            throw LbxError.runtime(line, "递归过深（超过 " + MAX_CALL_DEPTH + " 层）");
        }
        Map<String, Value> scope = new HashMap<>();
        for (int i = 0; i < args.size(); i++) scope.put(f.params.get(i), args.get(i));

        callDepth++;
        scopes.add(scope);
        try {
            for (Node s : f.body) exec(s);
            return VNull.NIL;                    // 走到 end 就是返回 null（§7.1）
        } catch (ReturnEx r) {
            return r.v;
        } finally {
            scopes.remove(scopes.size() - 1);
            callDepth--;
        }
    }

    // ---------------- 作用域（§7.2、§7.3） ----------------

    private Value lookup(String name, int line) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Value v = scopes.get(i).get(name);
            if (v != null) return v;
        }
        throw LbxError.runtime(line, "未定义的变量 " + name);
    }

    /** §7.3：查到就改最近的那一个，查不到才在当前（最内层）作用域新建。 */
    private void assignName(String name, Value v) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) {
                scopes.get(i).put(name, v);
                return;
            }
        }
        scopes.get(scopes.size() - 1).put(name, v);
    }

    private int indexOf(Value v, int line) {
        if (!(v instanceof VInt i)) {
            throw LbxError.runtime(line, "下标必须是整数，实际是 " + v.typeName());
        }
        if (i.v() < Integer.MIN_VALUE || i.v() > Integer.MAX_VALUE) {
            throw LbxError.runtime(line, "数组下标越界：" + i.v());
        }
        return (int) i.v();
    }

    private LbxError outOfRange(int line, int i, int size) {
        return LbxError.runtime(line, "数组下标越界：" + i + "（长度 " + size + "）");
    }

    // ---------------- 内置函数（§9） ----------------

    private void registerBuiltins() {
        Map<String, Value> g = scopes.get(0);

        g.put("print", new VBuiltin("print", (args, line) -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) b.append(' ');
                b.append(args.get(i).str());     // §8：规则在 Value 里，此处只用 str()
            }
            // 故意不用 println：PrintStream.println 用的是 System.lineSeparator()，
            // 在 Windows 上会输出 \r\n，而 §8 要的是确定的一个换行（LF）。
            // 不固定下来的话，按字节比对的黑盒测试换个平台就全错。
            out.print(b);
            out.print('\n');
            return VNull.NIL;
        }));

        g.put("input", new VBuiltin("input", (args, line) -> {
            if (args.size() > 1) {
                throw LbxError.runtime(line, "input 最多接受 1 个参数，实际给了 " + args.size() + " 个");
            }
            if (args.size() == 1) {
                out.print(args.get(0).str());
                out.flush();
            }
            try {
                String s = in.readLine();
                return s == null ? VNull.NIL : new VStr(s);    // 读到 EOF 返回 null
            } catch (IOException e) {
                throw LbxError.runtime(line, "读取输入失败");
            }
        }));

        g.put("len", new VBuiltin("len", (args, line) -> {
            expectArgs("len", args, 1, line);
            Value v = args.get(0);
            if (v instanceof VArray arr) return new VInt(arr.items.size());
            if (v instanceof VStr s) return new VInt(s.v().codePointCount(0, s.v().length()));
            throw LbxError.runtime(line, "len 不支持 " + v.typeName());
        }));

        g.put("int", new VBuiltin("int", (args, line) -> {
            expectArgs("int", args, 1, line);
            Value v = args.get(0);
            if (v instanceof VInt) return v;
            if (v instanceof VFloat f) {
                // §9：浮点转整数按向零截断，与整除的截断方向一致
                double d = f.v();
                if (d < (double) Long.MIN_VALUE || d >= 9.223372036854776E18) {
                    throw LbxError.runtime(line, "整数超出范围：" + new VFloat(d).str());
                }
                return new VInt((long) d);
            }
            if (v instanceof VBool b) return new VInt(b.v() ? 1 : 0);
            if (v instanceof VStr s) {
                String t = s.v().trim();
                if (t.matches("-?\\d+")) {
                    try {
                        return new VInt(Long.parseLong(t));
                    } catch (NumberFormatException e) {
                        throw LbxError.runtime(line, "整数超出范围：" + t);
                    }
                }
                throw LbxError.runtime(line, "无法把字符串 \"" + s.v() + "\" 转成整数");
            }
            throw LbxError.runtime(line, "int 不支持 " + v.typeName());
        }));

        // v1.3 新增：没有它就没办法把 input() 读到的字符串变成小数（§9）
        g.put("float", new VBuiltin("float", (args, line) -> {
            expectArgs("float", args, 1, line);
            Value v = args.get(0);
            if (v instanceof VFloat) return v;
            if (v instanceof VInt i) return new VFloat((double) i.v());
            if (v instanceof VBool b) return new VFloat(b.v() ? 1.0 : 0.0);
            if (v instanceof VStr s) {
                String t = s.v().trim();
                if (t.matches("-?\\d+(\\.\\d+)?")) {
                    try {
                        return new VFloat(Double.parseDouble(t));
                    } catch (NumberFormatException e) {
                        throw LbxError.runtime(line, "浮点数超出范围：" + t);
                    }
                }
                throw LbxError.runtime(line, "无法把字符串 \"" + s.v() + "\" 转成浮点数");
            }
            throw LbxError.runtime(line, "float 不支持 " + v.typeName());
        }));

        g.put("str", new VBuiltin("str", (args, line) -> {
            expectArgs("str", args, 1, line);
            return new VStr(args.get(0).str());
        }));

        // 注意：内置函数叫 typeof 而不是 type —— type 是声明结构类型的关键字（§3.2），
        // 两者同名会让 type(x) 永远无法调用。见语法文档 §13 决策记录。
        g.put("typeof", new VBuiltin("typeof", (args, line) -> {
            expectArgs("typeof", args, 1, line);
            return new VStr(args.get(0).typeName());
        }));

        g.put("push", new VBuiltin("push", (args, line) -> {
            expectArgs("push", args, 2, line);
            if (!(args.get(0) instanceof VArray arr)) {
                throw LbxError.runtime(line, "push 的第 1 个参数必须是数组，实际是 " + args.get(0).typeName());
            }
            arr.items.add(args.get(1));
            return new VInt(arr.items.size());
        }));

        g.put("pop", new VBuiltin("pop", (args, line) -> {
            expectArgs("pop", args, 1, line);
            if (!(args.get(0) instanceof VArray arr)) {
                throw LbxError.runtime(line, "pop 的参数必须是数组，实际是 " + args.get(0).typeName());
            }
            if (arr.items.isEmpty()) throw LbxError.runtime(line, "对空数组做 pop");
            return arr.items.remove(arr.items.size() - 1);
        }));

        g.put("range", new VBuiltin("range", (args, line) -> {
            if (args.size() < 1 || args.size() > 3) {
                throw LbxError.runtime(line, "range 需要 1 到 3 个参数，实际给了 " + args.size() + " 个");
            }
            long start = 0, step = 1, end;
            if (args.size() == 1) {
                end = Ops.intOf(args.get(0), "range", line);
            } else {
                start = Ops.intOf(args.get(0), "range", line);
                end = Ops.intOf(args.get(1), "range", line);
                if (args.size() == 3) step = Ops.intOf(args.get(2), "range", line);
            }
            if (step == 0) throw LbxError.runtime(line, "range 的步长不能为 0");
            long count = (step > 0)
                    ? (end - start + step - 1) / step
                    : (start - end - step - 1) / (-step);
            List<Value> items = new ArrayList<>();
            if (count <= 0) return new VArray(items);
            if (count > MAX_RANGE) {
                throw LbxError.runtime(line, "range 生成的序列过长（超过 " + MAX_RANGE + " 个元素）");
            }
            for (long x = start; step > 0 ? x < end : x > end; x += step) items.add(new VInt(x));
            return new VArray(items);
        }));
    }

    private void expectArgs(String name, List<Value> args, int n, int line) {
        if (args.size() != n) {
            throw LbxError.runtime(line, name + " 需要 " + n + " 个参数，实际给了 " + args.size() + " 个");
        }
    }
}
