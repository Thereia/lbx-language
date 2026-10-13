package lbx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 词法分析单元测试：只测 Lexer 的对外行为（记号种类、原文、行列号）。 */
class LexerTest {

    private static List<Token> lex(String src) {
        return new Lexer(src).lex();
    }

    @Test
    void 记号种类与原文正确() {
        List<Token> ts = lex("print(\"hi\");");
        assertEquals(6, ts.size());                       // 5 个记号 + EOF
        assertEquals(TokenKind.IDENT, ts.get(0).kind());
        assertEquals("print", ts.get(0).text());
        assertEquals(TokenKind.OP, ts.get(1).kind());
        assertEquals("(", ts.get(1).text());
        assertEquals(TokenKind.STR, ts.get(2).kind());
        assertEquals("hi", ts.get(2).value());            // 值是未解码的原文，解码在 Parser
        assertEquals(TokenKind.EOF, ts.get(5).kind());
    }

    @Test
    void 行号列号一基且换行不产生记号() {
        List<Token> ts = lex("x = 1;\ny = 2;\n");
        assertEquals(1, ts.get(0).line());
        assertEquals(1, ts.get(0).col());
        // 第二行第一个记号应在第 2 行第 1 列
        Token y = ts.stream().filter(t -> "y".equals(t.text())).findFirst().orElseThrow();
        assertEquals(2, y.line());
        assertEquals(1, y.col());
    }

    @Test
    void 最长匹配加关键字优先若iffy不是if() {
        List<Token> ts = lex("iffy");
        assertEquals(1, ts.size() - 1);
        assertEquals(TokenKind.IDENT, ts.get(0).kind());
        assertEquals("iffy", ts.get(0).text());
    }

    @Test
    void 关键字与运算符别名归一化() {
        assertEquals(TokenKind.KEYWORD, lex("while").get(0).kind());
        assertEquals("and", lex("&&").get(0).text());      // && 归一化成 and
        assertEquals("or", lex("||").get(0).text());
        assertEquals("not", lex("!").get(0).text());
    }

    @Test
    void 非法字符报词法错误并给出行列() {
        LbxError e = assertThrows(LbxError.class, () -> lex("x = 1 @ 2;"));
        assertEquals("词法", e.phase);
        assertEquals(1, e.line);
        assertEquals(7, e.col);
        assertEquals("非法字符 '@'", e.getMessage());
    }

    @Test
    void 未闭合块注释报错在注释起点() {
        LbxError e = assertThrows(LbxError.class, () -> lex("x = 1; /* 没关掉"));
        assertEquals("未闭合的块注释", e.getMessage());
        assertEquals(8, e.col);
    }
}
