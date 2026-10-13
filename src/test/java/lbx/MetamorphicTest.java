package lbx;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蜕变测试（metamorphic testing）：**不写死期望值**，只检查"某些性质必须成立"。
 *
 * 为什么需要它：语法文档、实现、黑盒期望文件如果都出自同一个人的同一个心智模型，
 * 同一个错误假设会同时出现在三处（共模失效），固定期望值就抓不出来 —— 三处互相印证，看起来特别可信。
 * 蜕变断言不需要 oracle（不需要知道"正确答案"），所以对这种盲点免疫：
 * 它检查的是"同一计算的不同写法必须一致""排序结果必须满足排序的定义"这类不变式。
 *
 * 这里刻意只用 LBX 写被测程序，断言在 Java 侧做；断言本身不引用任何内部 API 的结果。
 */
class MetamorphicTest {

    /** 同进程跑一段 LBX 程序，返回它写到 stdout 的全部内容。 */
    private static String run(String src) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Node program = new Parser(new Lexer(src).lex()).parseProgram();
        new Interp(out, new BufferedReader(new StringReader(""))).run(program);
        return buf.toString(StandardCharsets.UTF_8);
    }

    /** 同一件事的两种写法必须得到同一个结果：循环累加 vs 求和公式。 */
    @Test
    void 循环累加与求和公式必须一致() {
        String byLoop = run("""
                n = 100;
                total = 0;
                for i in range(n)
                    total = total + i;
                end
                print(total);
                """);
        String byFormula = run("""
                n = 100;
                print(n * (n - 1) / 2);
                """);
        assertEquals(byFormula, byLoop, "循环累加与 n*(n-1)/2 必须得到同一个结果");
        assertEquals("4950\n", byLoop);
    }

    /**
     * 排序的三条定义性质，全部不需要手算期望值：
     *   ① 结果非递减；② 元素多重集与输入相同（不增不减）；③ 幂等（再排一次不变）。
     */
    @Test
    void 排序必须非递减元素不增不减且幂等() {
        String src = """
                func sortAsc(a)
                    n = len(a);
                    i = 0;
                    while i < n - 1
                        j = 0;
                        while j < n - 1 - i
                            if a[j] > a[j + 1]
                                t = a[j];
                                a[j] = a[j + 1];
                                a[j + 1] = t;
                            end
                            j = j + 1;
                        end
                        i = i + 1;
                    end
                    return a;
                end

                data = [5, 3, 9, 1, 3, 7];
                sortAsc(data);
                for x in data
                    print(x);
                end
                print("---");
                sortAsc(data);
                for x in data
                    print(x);
                end
                """;
        String[] parts = run(src).split("---\n");
        List<Long> once = numbers(parts[0]);
        List<Long> twice = numbers(parts[1]);

        for (int i = 1; i < once.size(); i++) {
            assertTrue(once.get(i - 1) <= once.get(i), "排序结果必须非递减：" + once);
        }

        List<Long> bagOfInput = new ArrayList<>(List.of(5L, 3L, 9L, 1L, 3L, 7L));
        List<Long> bagOfOutput = new ArrayList<>(once);
        Collections.sort(bagOfInput);
        Collections.sort(bagOfOutput);
        assertEquals(bagOfInput, bagOfOutput, "排序不能增加、丢失或改写元素");

        assertEquals(once, twice, "对已排序的数组再排一次，结果必须不变（幂等）");
    }

    /** 反转两次必须回到原样：不需要知道原数组"应该"是什么。 */
    @Test
    void 反转两次回到原样() {
        String src = """
                func reverse(a)
                    n = len(a);
                    i = 0;
                    while i < n / 2
                        t = a[i];
                        a[i] = a[n - 1 - i];
                        a[n - 1 - i] = t;
                        i = i + 1;
                    end
                    return a;
                end

                data = [1, 2, 3, 4, 5, 6, 7];
                before = [];
                for x in data
                    push(before, x);
                end
                reverse(reverse(data));
                for x in data
                    print(x);
                end
                print("---");
                for x in before
                    print(x);
                end
                """;
        String[] parts = run(src).split("---\n");
        assertEquals(numbers(parts[1]), numbers(parts[0]), "反转两次必须与原来逐元素相同");
    }

    private static List<Long> numbers(String block) {
        List<Long> out = new ArrayList<>();
        for (String line : block.split("\n")) {
            if (!line.isBlank()) out.add(Long.parseLong(line.trim()));
        }
        return out;
    }
}
