package lbx;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端单元测试：在**同一个 JVM 内**跑完整的「词法 → 语法 → 求值」链路，
 * 捕获 stdout 后断言输出。它测的是解释器对外可观察的行为（打印格式、作用域、引用语义、错误），
 * 不涉及 TestRunner 那套"新起进程"的黑盒机制。
 */
class InterpTest {

    /** 把一段 LBX 源码跑完，返回它写到 stdout 的全部内容。 */
    private static String run(String src) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Node program = new Parser(new Lexer(src).lex()).parseProgram();
        new Interp(out, new BufferedReader(new StringReader(""))).run(program);
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static LbxError errorOf(String src) {
        return assertThrows(LbxError.class, () -> run(src));
    }

    @Test
    void 优先级与括号() {
        assertEquals("7\n", run("print(1 + 2 * 3);"));
        assertEquals("9\n", run("print((1 + 2) * 3);"));
        assertEquals("1\n", run("print(- 2 + 10 / 3);"));
    }

    @Test
    void not的优先级低于相等比较() {
        // not a == b 解析成 not (a == b)，所以这里输出 true
        assertEquals("true\n", run("print(not 1 == 2);"));
    }

    @Test
    void 打印格式符合语法文档第八节() {
        assertEquals("abc 1\n", run("print(\"abc\", 1);"));
        assertEquals("[\"abc\", 1]\n", run("print([\"abc\", 1]);"));
        assertEquals("\n", run("print();"));                         // 无参数只输出一个换行
        assertEquals("Point { x = 3, y = 0 }\n",
                run("type Point = { x = 0, y = 0 };\np = Point { x = 3 };\nprint(p);"));
    }

    @Test
    void 字符串插值支持表达式与嵌套字符串() {
        assertEquals("2 与 内层\n", run("print(\"${1 + 1} 与 ${\"内层\"}\");"));
        assertEquals("${x}\n", run("print(\"\\${x}\");"));            // \${ 不插值
        assertEquals("3\n", run("print(\"${len(\"a}b\")}\");"));
    }

    @Test
    void 函数递归与先调用后定义() {
        String src = """
                print(later(3));
                func later(n)
                    return n * 2;
                end
                func fib(n)
                    if n < 2
                        return n;
                    end
                    return fib(n - 1) + fib(n - 2);
                end
                print(fib(10));
                """;
        assertEquals("6\n55\n", run(src));
    }

    @Test
    void 数组与结构是引用语义() {
        String src = """
                func addOne(arr)
                    push(arr, 1);
                end
                a = [];
                addOne(a);
                print(a);
                """;
        assertEquals("[1]\n", run(src));
    }

    @Test
    void 赋值规则查到就改最近的查不到才在当前层新建() {
        String src = """
                g = 1;
                func bump()
                    g = g + 10;
                    local = 5;
                    return g + local;
                end
                print(bump(), g);
                """;
        assertEquals("16 11\n", run(src));
    }

    @Test
    void 循环不产生新作用域所以循环变量在循环外可见() {
        assertEquals("8\n", run("for i in [7, 8]\nend\nprint(i);"));
    }

    @Test
    void break与continue按预期生效() {
        String src = """
                i = 0;
                while i < 5
                    i = i + 1;
                    if i == 3
                        continue;
                    end
                    if i == 5
                        break;
                    end
                    print(i);
                end
                """;
        assertEquals("1\n2\n4\n", run(src));
    }

    @Test
    void 运行时错误带行号() {
        assertEquals("未定义的变量 total", errorOf("print(total);").getMessage());
        assertEquals(1, errorOf("print(total);").line);
        assertEquals("除零", errorOf("print(1 / 0);").getMessage());
        assertEquals("数组下标越界：5（长度 2）",
                errorOf("a = [1, 2];\nprint(a[5]);").getMessage());
        assertEquals(2, errorOf("a = [1, 2];\nprint(a[5]);").line);
    }

    @Test
    void 语法错误带行列号() {
        LbxError e = errorOf("x = 1\nprint(x);");
        assertEquals("语法", e.phase);
        assertEquals(2, e.line);
        assertEquals(1, e.col);
        assertEquals("期望 ';'，实际遇到 'print'", e.getMessage());
    }

    @Test
    void 浮点的打印格式与转换() {
        assertEquals("1.5 2.0\n", run("print(1.5, 2.0);"));
        assertEquals("3.3333333333333335\n", run("print(10.0 / 3.0);"));
        assertEquals("3 0.5\n", run("print(10 / 3, 1 / 2.0);"));      // 整除与实数除法的分界
        assertEquals("float int\n", run("print(typeof(1.5), typeof(1));"));
        assertEquals("2.5 2\n", run("print(float(\"2.5\"), int(2.9));"));
        assertEquals("[\"a\", 1.5]\n", run("print([\"a\", 1.5]);"));   // 容器内也用 Double.toString
        assertEquals("true\n", run("print(1 == 1.0);"));
        assertEquals("0.25\n", run("print(\"${1.0 / 4.0}\");"));       // 插值里同样是浮点
    }

    /**
     * §8 规定换行是 LF。这条要在**字节层**断言，而不是在字符串层：
     * 如果哪天 print 被改回 println，Windows 上会输出 CRLF，而字符串比较（或运行器的 CRLF 归一化）
     * 都看不出来 —— 这个回归当初就是这么发生的。
     */
    @Test
    void 输出只含LF不含CR() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Node program = new Parser(new Lexer("print(1);\nprint(\"a\");\n").lex()).parseProgram();
        new Interp(out, new BufferedReader(new StringReader(""))).run(program);

        byte[] bytes = buf.toByteArray();
        for (byte b : bytes) {
            assertTrue(b != 0x0D, "输出里出现了 CR（0x0D）：解释器必须只输出 LF");
        }
        assertEquals("1\na\n", new String(bytes, StandardCharsets.UTF_8));
    }
}
