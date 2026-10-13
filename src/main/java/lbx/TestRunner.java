package lbx;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * LBX 黑盒测试运行器（作业提交内容第 3 项）。
 *
 * 「黑盒」的含义落实在实现上：本运行器**不调用解释器的任何内部 API**，
 * 每个用例都是新起一个 JVM 跑 {@code lbx.Main}，只观察三样东西：
 *   ① 子进程的 stdout（与 .expected 逐字节比对）
 *   ② 子进程的 stderr（错误用例与 .expected 比对）
 *   ③ 子进程的退出码（0 正常 / 1 出错 / 2 用法错误）
 * 另有可选的 .in 文件作为子进程的标准输入。
 *
 * 两种用法：
 *   命令行：{@code java -cp target/classes lbx.TestRunner [测试目录，默认 src/test/resources/lbx]}，退出码 0 = 全绿；
 *   编程调用：{@code TestRunner.runAll(testsDir, childClasspath, out)} 返回失败用例数，
 *   供 {@code src/test/java} 下的 JUnit 测试把它并入 {@code mvn test}。
 *   两者都要显式给出「子进程该用哪个 classpath」：surefire 运行时 {@code java.class.path}
 *   里只有它自己的 booter，拿不到本项目的 classes。
 */
public final class TestRunner {

    private final Path testsDir;
    private final String childClasspath;
    private final PrintStream out;
    private final String javaBin;
    private final Path emptyStdin;

    private final List<String> failures = new ArrayList<>();
    private int total = 0;
    private int passed = 0;

    private TestRunner(Path testsDir, String childClasspath, PrintStream out) throws IOException {
        this.testsDir = testsDir;
        this.childClasspath = childClasspath;
        this.out = out;
        this.javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        this.emptyStdin = Files.createTempFile("lbx-empty-stdin", ".bin");
    }

    public static void main(String[] args) throws Exception {
        // Windows 下 Java 默认字符集是 GBK，不强制 UTF-8 的话运行器自己的中文输出在控制台会乱码。
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        System.setOut(out);

        Path testsDir = Path.of(args.length > 0 ? args[0] : "src/test/resources/lbx").toAbsolutePath();
        if (!Files.isDirectory(testsDir)) {
            out.println("[tests] 找不到测试目录：" + testsDir);
            System.exit(1);
            return;
        }
        int failed = runAll(testsDir, System.getProperty("java.class.path"), out);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** 跑完整个黑盒测试集，返回**失败用例数**（0 表示全绿）。 */
    static int runAll(Path testsDir, String childClasspath, PrintStream out) throws Exception {
        return new TestRunner(testsDir, childClasspath, out).run();
    }

    private int run() throws Exception {
        out.println("[tests] 测试目录：" + testsDir);
        out.println("[tests] 解释器：" + javaBin);
        out.println();

        // ---------- 正常用例：退出码 0，stdout 与 .expected 逐字节一致 ----------
        out.println("正常用例（期望退出码 0，stdout 与 .expected 一致）");
        Path casesDir = testsDir.resolve("cases");
        if (Files.isDirectory(casesDir)) {
            for (Path lbx : listLbx(casesDir)) {
                String base = stripExt(lbx);
                Path expected = Path.of(base + ".expected");
                Path stdin = Path.of(base + ".in");
                Result r = run(lbx, Files.exists(stdin) ? stdin : emptyStdin);
                String label = "cases/" + lbx.getFileName();
                if (r.exit() != 0) {
                    ok(false, label, "退出码应为 0，实际 " + r.exit() + "；stderr=" + oneLine(r.err()));
                } else if (!Files.exists(expected)) {
                    ok(false, label, "缺少期望输出文件 " + expected.getFileName());
                } else if (hasCR(r.out())) {
                    ok(false, label, "stdout 含 CR（0x0D）：§8 规定换行为 LF，含 CR 会让跨平台的逐字节比对失效");
                } else {
                    byte[] exp = Files.readAllBytes(expected);
                    ok(equal(exp, r.out()), label, diff(exp, r.out()));
                }
            }
        }

        // ---------- 错误用例：退出码 1，stderr 与 .expected 一致 ----------
        out.println();
        out.println("错误用例（期望退出码 1，stderr 与 .expected 一致）");
        Path errorsDir = testsDir.resolve("errors");
        if (Files.isDirectory(errorsDir)) {
            for (Path lbx : listLbx(errorsDir)) {
                String base = stripExt(lbx);
                Path expected = Path.of(base + ".expected");
                Result r = run(lbx, emptyStdin);
                String label = "errors/" + lbx.getFileName();
                if (r.exit() != 1) {
                    ok(false, label, "退出码应为 1，实际 " + r.exit());
                } else if (!Files.exists(expected)) {
                    ok(false, label, "缺少期望错误文件 " + expected.getFileName());
                } else if (hasCR(r.err())) {
                    ok(false, label, "stderr 含 CR（0x0D）：§10.3 的错误消息换行也必须是 LF");
                } else {
                    byte[] exp = Files.readAllBytes(expected);
                    ok(equal(exp, r.err()), label, diff(exp, r.err()));
                }
            }
        }

        // ---------- 命令行用例：语法文档 §10.2 的退出码 2 ----------
        out.println();
        out.println("命令行用例（期望退出码 2）");
        Result noArg = run(null, emptyStdin);
        ok(noArg.exit() == 2 && text(noArg.err()).startsWith("[LBX 用法错误] 用法："),
                "cli/不带参数", "退出码=" + noArg.exit() + "；stderr=" + oneLine(noArg.err()));

        Result missing = run(testsDir.resolve("__不存在的脚本__.lbx"), emptyStdin);
        ok(missing.exit() == 2 && text(missing.err()).startsWith("[LBX 用法错误] 读不到脚本文件："),
                "cli/文件不存在", "退出码=" + missing.exit() + "；stderr=" + oneLine(missing.err()));

        Files.deleteIfExists(emptyStdin);

        out.println();
        out.println("[tests] 共 " + total + " 个用例，" + passed + " 通过，" + (total - passed) + " 失败");
        if (!failures.isEmpty()) {
            out.println();
            out.println("失败清单：");
            for (String f : failures) out.println("  - " + f);
        }
        return total - passed;
    }

    // ---------------- 一个用例的执行 ----------------

    private record Result(int exit, byte[] out, byte[] err) {}

    /** 新起一个 JVM 跑解释器；script 为 null 表示不带脚本参数（测用法错误）。 */
    private Result run(Path script, Path stdin) throws Exception {
        Path outFile = Files.createTempFile("lbx-run-out", ".bin");
        Path errFile = Files.createTempFile("lbx-run-err", ".bin");
        List<String> cmd = new ArrayList<>(List.of(
                javaBin, "-Dfile.encoding=UTF-8", "-cp", childClasspath, "lbx.Main"));
        if (script != null) cmd.add(script.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(outFile.toFile());
        pb.redirectError(errFile.toFile());
        pb.redirectInput(stdin.toFile());
        Process proc = pb.start();
        if (!proc.waitFor(120, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("用例超时（120 秒）：" + script);
        }
        byte[] o = Files.readAllBytes(outFile);
        byte[] e = Files.readAllBytes(errFile);
        Files.deleteIfExists(outFile);
        Files.deleteIfExists(errFile);
        return new Result(proc.exitValue(), o, e);
    }

    // ---------------- 比对与输出 ----------------

    private void ok(boolean good, String label, String detail) {
        total++;
        if (good) {
            passed++;
            out.println("  ok   " + label);
        } else {
            failures.add(label + " —— " + detail);
            out.println("  FAIL " + label + " —— " + detail);
        }
    }

    /**
     * 逐字节比对（先统一 CRLF→LF）。行尾归一化是防御性的：
     * 解释器自己只输出 LF（见 Interp 里 print 的实现），但期望文件若被编辑器改成 CRLF 也不该误报。
     */
    private static boolean equal(byte[] a, byte[] b) {
        return text(a).equals(text(b));
    }

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /**
     * 输出里是否混进了回车（0x0D）。
     *
     * 内容比对时我们容忍 CRLF（防止期望文件被编辑器改坏），但**容忍不等于放行**：
     * 如果解释器自己输出 CRLF（例如有人把 print 改回 println，Windows 上就会），
     * 归一化会把这个回归悄悄盖住。所以这里显式报失败。
     */
    private static boolean hasCR(byte[] b) {
        for (byte x : b) {
            if (x == 0x0D) return true;
        }
        return false;
    }

    private static String oneLine(byte[] b) {
        return text(b).trim().replace("\n", " / ");
    }

    /** 报出第一个不同的行，方便定位。 */
    private static String diff(byte[] expected, byte[] actual) {
        String[] e = text(expected).split("\n", -1);
        String[] a = text(actual).split("\n", -1);
        for (int i = 0; i < Math.max(e.length, a.length); i++) {
            String ei = i < e.length ? e[i] : "<没有这一行>";
            String ai = i < a.length ? a[i] : "<没有这一行>";
            if (!ei.equals(ai)) {
                return "第 " + (i + 1) + " 行不同：期望 [" + ei + "] 实际 [" + ai + "]";
            }
        }
        return "内容一致（不应报错）";
    }

    private static List<Path> listLbx(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".lbx"))
                    .sorted()
                    .toList();
        }
    }

    private static String stripExt(Path p) {
        String s = p.toString();
        return s.endsWith(".lbx") ? s.substring(0, s.length() - 4) : s;
    }
}
