package lbx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LBX 的全部数据类型（作业要求第 1 条：整数、字符串等基本数据类型）。
 * 规格见语法文档 §4：每个类型自带 typeName()，以及 §8 规定的两种打印形式
 * —— str() 用于容器外层（字符串不加引号），repr() 用于容器内层（字符串带引号）。
 *
 * 用 sealed 接口而不是 java.lang.Object：基本数据类型必须在代码里看得见，
 * 这样 typeof()、§5 运算规则、§8 打印规则都只需要在一处实现。
 */
sealed interface Value permits VInt, VFloat, VStr, VBool, VNull, VArray, VStruct, VFunc, VBuiltin {

    /** §4 类型名，即内置函数 typeof(x) 的返回值。 */
    String typeName();

    /** §8 打印：出现在最外层时用。字符串返回原文本。 */
    String str();

    /** §8 打印：出现在数组 / 结构内部时用。字符串带双引号并转义。 */
    default String repr() {
        return str();
    }
}

/** 整数。§5：两个整数之间的算术保持整数语义（`/` 是整除）。 */
record VInt(long v) implements Value {

    public String typeName() { return "int"; }

    public String str() { return Long.toString(v); }
}

/**
 * 浮点数（v1.3 加入，见语法文档 §13 第 12 条）。
 * §8 的打印形式直接用 {@link Double#toString}：它给出「最短且能原样读回」的十进制表示，
 * 因此 2.0 打印成 {@code 2.0}、1.0/3.0 打印成 {@code 0.3333333333333333}，确定且不丢精度。
 */
record VFloat(double v) implements Value {

    public String typeName() { return "float"; }

    public String str() { return Double.toString(v); }
}

/** 字符串，不可变：拼接产生新串（§4）。 */
record VStr(String v) implements Value {

    public String typeName() { return "string"; }

    public String str() { return v; }

    public String repr() { return "\"" + escape(v) + "\""; }

    /** repr 用的转义（§8：容器内层的字符串要能被原样读回来）。 */
    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default   -> b.append(c);
            }
        }
        return b.toString();
    }
}

/** 布尔。§5 规定条件必须是布尔值，没有隐式真值判断。 */
record VBool(boolean v) implements Value {

    public String typeName() { return "bool"; }

    public String str() { return v ? "true" : "false"; }
}

/** 空值，全语言只有一个实例。 */
record VNull() implements Value {

    static final VNull NIL = new VNull();

    public String typeName() { return "null"; }

    public String str() { return "null"; }
}

/** 数组：引用语义，下标从 0 开始（§4、§7.4）。 */
final class VArray implements Value {

    final List<Value> items;

    VArray() { this.items = new ArrayList<>(); }

    VArray(List<Value> items) { this.items = items; }

    public String typeName() { return "array"; }

    public String str() {
        return items.stream().map(Value::repr).collect(Collectors.joining(", ", "[", "]"));
    }
}

/** 结构：引用语义，字段用 LinkedHashMap 保序，保序即 §8 的打印顺序（§7.4）。 */
final class VStruct implements Value {

    final String type;
    final LinkedHashMap<String, Value> fields = new LinkedHashMap<>();

    VStruct(String type) { this.type = type; }

    public String typeName() { return "struct"; }

    public String str() {
        if (fields.isEmpty()) return type + " { }";
        String body = fields.entrySet().stream()
                .map(e -> e.getKey() + " = " + e.getValue().repr())
                .collect(Collectors.joining(", "));
        return type + " { " + body + " }";
    }
}

/** 用户函数（§7.1）。 */
final class VFunc implements Value {

    final String name;
    final List<String> params;
    final List<Node> body;

    VFunc(String name, List<String> params, List<Node> body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    public String typeName() { return "function"; }

    public String str() { return "<func " + name + ">"; }
}

/** 内置函数（§9）：用 lambda 实现，所以不需要为每个内置函数写一个类。 */
final class VBuiltin implements Value {

    /** 内置函数的实现体。line 用于报运行时错误的行号。 */
    interface Native {
        Value call(List<Value> args, int line);
    }

    final String name;
    final Native fn;

    VBuiltin(String name, Native fn) {
        this.name = name;
        this.fn = fn;
    }

    public String typeName() { return "function"; }

    public String str() { return "<builtin " + name + ">"; }
}
