package lbx;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 解释器入口：读脚本 → 词法分析 → 语法分析 → 求值（语法文档 §10）。
 *
 * 用法：java -cp target\classes lbx.Main [--tokens|--ast] &lt;脚本.lbx&gt;
 *   --tokens / --ast 是自测与答辩讲解用的开关，正常执行时不打印任何多余内容。
 * 退出码：0 正常；1 词法 / 语法 / 运行时错误；2 命令行用法错误（§10.2）。
 */
public final class Main {

    public static void main(String[] args) {
        // Windows 下 Java 默认字符集是 GBK，这里强制 stdout / stderr 走 UTF-8，
        // 否则脚本里的中文输出会乱码（词法分析实验里踩过同一个坑）。
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.setOut(out);
        System.setErr(err);

        boolean showTokens = false;
        boolean showAst = false;
        List<String> files = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--tokens")) showTokens = true;
            else if (a.equals("--ast")) showAst = true;
            else files.add(a);
        }
        if (files.size() != 1) {
            err.print("[LBX 用法错误] 用法：java -cp target\\classes lbx.Main [--tokens|--ast] <脚本.lbx>\n");
            System.exit(2);
            return;
        }

        String src;
        try {
            src = Files.readString(Path.of(files.get(0)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.print("[LBX 用法错误] 读不到脚本文件：" + files.get(0) + "\n");
            System.exit(2);
            return;
        }

        try {
            List<Token> toks = new Lexer(src).lex();
            if (showTokens) {
                for (Token t : toks) out.print(tokenLine(t) + "\n");
                return;
            }
            Node program = new Parser(toks).parseProgram();
            if (showAst) {
                printAst(program, out, 0);
                return;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            new Interp(out, in).run(program);
        } catch (LbxError e) {
            err.print(e.format() + "\n");
            System.exit(1);
        } catch (StackOverflowError e) {
            err.print("[LBX 运行时错误] 求值嵌套过深\n");
            System.exit(1);
        }
    }

    /** --tokens 的输出格式：行:列 记号种类 原文 [值]。 */
    private static String tokenLine(Token t) {
        String value = (t.value() == null) ? "" : "   " + t.value();
        return String.format("%3d:%-3d %-8s %s%s", t.line(), t.col(), t.kind(), t.text(), value);
    }

    /** --ast 的输出格式：缩进树。 */
    private static void printAst(Node n, PrintStream out, int depth) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < depth; i++) b.append("  ");
        b.append(n.kind);
        if (n.text != null && !n.text.isEmpty()) b.append(" (").append(n.text).append(')');
        if (n.value != null) b.append(" = ").append(n.value);
        if (!n.names.isEmpty()) b.append(" params=").append(n.names);
        out.print(b + "\n");
        for (Node k : n.kids) printAst(k, out, depth + 1);
    }
}
