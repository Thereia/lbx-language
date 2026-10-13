package lbx;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把整份黑盒测试集（src/test/resources/lbx/ 下的 .lbx 与 .expected）接入 {@code mvn test}：
 * 本测试只是调用 {@link TestRunner#runAll}，所以「一条命令跑完所有测试」在 Maven 下同样成立。
 *
 * 注意两个路径都要显式算出来：
 *   - 测试数据在项目根目录的 src/test/resources/lbx/（surefire 会把 basedir 设为项目根）；
 *   - 子进程的 classpath 必须显式给 target/classes —— surefire 运行时
 *     {@code java.class.path} 里只有它自己的 booter，拿不到本项目的 classes。
 */
class BlackBoxSuiteTest {

    @Test
    void 黑盒测试集全绿且用例数达标() throws Exception {
        Path base = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        Path testsDir = base.resolve("src/test/resources/lbx");
        assertTrue(Files.isDirectory(testsDir), "找不到黑盒测试目录：" + testsDir);
        String childClasspath = base.resolve("target").resolve("classes").toString();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        int failed = TestRunner.runAll(testsDir, childClasspath, out);
        String report = buf.toString(StandardCharsets.UTF_8);

        assertEquals(0, failed, () -> "黑盒测试有失败用例：\n" + report);

        // 防止「一个用例都没跑到」也被判为通过：从汇总行里取用例数并设下限。
        Matcher m = Pattern.compile("共 (\\d+) 个用例").matcher(report);
        assertTrue(m.find(), "报告里没有汇总行，说明运行器没跑起来：\n" + report);
        int total = Integer.parseInt(m.group(1));
        assertTrue(total >= 50, "用例数偏少（" + total + "），可能测试数据没被找到：\n" + report);
    }
}
