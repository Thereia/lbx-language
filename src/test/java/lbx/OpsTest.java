package lbx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运算规则单元测试：逐条对应语法文档 §5 的优先级表与类型规则。
 * 这些规则集中在 Ops 里，所以可以直接对方法断言，不需要跑整个解释器。
 */
class OpsTest {

    @Test
    void 整数与字符串的加法各有各的规则() {
        assertEquals(new VInt(3), Ops.add(new VInt(1), new VInt(2), 1));
        assertEquals(new VStr("ab"), Ops.add(new VStr("a"), new VStr("b"), 1));
    }

    @Test
    void 混合类型相加报错并说明两种类型() {
        LbxError e = assertThrows(LbxError.class,
                () -> Ops.add(new VInt(1), new VStr("a"), 7));
        assertEquals("运行时", e.phase);
        assertEquals(7, e.line);
        assertEquals("运算符 + 不支持 int 和 string", e.getMessage());
    }

    @Test
    void 除法是整除且向零截断() {
        assertEquals(new VInt(3), Ops.div(new VInt(10), new VInt(3), 1));
        assertEquals(new VInt(-3), Ops.div(new VInt(-10), new VInt(3), 1));
    }

    @Test
    void 除零与模零分别报错() {
        assertEquals("除零", assertThrows(LbxError.class,
                () -> Ops.div(new VInt(1), new VInt(0), 1)).getMessage());
        assertEquals("模零", assertThrows(LbxError.class,
                () -> Ops.mod(new VInt(1), new VInt(0), 1)).getMessage());
    }

    @Test
    void 相等比较在类型不同时得假而不报错() {
        assertTrue(Ops.eq(new VInt(1), new VInt(1)));
        assertTrue(Ops.eq(new VStr("a"), new VStr("a")));
        assertTrue(Ops.eq(VNull.NIL, VNull.NIL));
        assertFalse(Ops.eq(new VInt(1), new VStr("1")));     // 类型不同 → false
        assertFalse(Ops.eq(new VInt(1), new VBool(true)));
    }

    @Test
    void 数组与结构比较引用而不是内容() {
        VArray a = new VArray();
        VArray b = new VArray();
        assertTrue(Ops.eq(a, a));
        assertFalse(Ops.eq(a, b));                            // 两个不同对象 → false
    }

    @Test
    void 大小比较只支持整数与字符串() {
        assertTrue(Ops.cmp("<", new VInt(1), new VInt(2), 1));
        assertTrue(Ops.cmp(">=", new VInt(2), new VInt(2), 1));
        assertTrue(Ops.cmp("<", new VStr("abc"), new VStr("abd"), 1));
        LbxError e = assertThrows(LbxError.class,
                () -> Ops.cmp("<", new VInt(1), new VStr("a"), 3));
        assertEquals("运算符 < 不支持 int 和 string", e.getMessage());
    }

    @Test
    void 条件必须是布尔值() {
        assertTrue(Ops.cond(new VBool(true), 1));
        assertFalse(Ops.cond(new VBool(false), 1));
        LbxError e = assertThrows(LbxError.class, () -> Ops.cond(new VInt(1), 4));
        assertEquals("条件必须是布尔值，实际是 int", e.getMessage());
    }

    @Test
    void 一元负号只支持整数() {
        assertEquals(new VInt(-5), Ops.neg(new VInt(5), 1));
        assertEquals(new VInt(5), Ops.neg(new VInt(-5), 1));
        assertThrows(LbxError.class, () -> Ops.neg(new VStr("a"), 1));
    }

    @Test
    void 打印形式区分容器内外() {
        assertEquals("abc", new VStr("abc").str());           // 外层：不加引号
        assertEquals("\"abc\"", new VStr("abc").repr());      // 内层：加引号
        VArray arr = new VArray();
        arr.items.add(new VStr("a"));
        arr.items.add(new VInt(1));
        assertEquals("[\"a\", 1]", arr.str());                // 容器内用 repr
    }

    @Test
    void 整数与浮点混合运算按实数进行且结果为浮点() {
        assertEquals(new VFloat(3.0), Ops.add(new VInt(1), new VFloat(2.0), 1));
        assertEquals(new VFloat(3.0), Ops.add(new VFloat(1.0), new VInt(2), 1));
        assertEquals(new VFloat(2.0), Ops.sub(new VFloat(3.0), new VInt(1), 1));
        assertEquals(new VFloat(5.0), Ops.mul(new VFloat(2.5), new VInt(2), 1));
        assertEquals(new VFloat(0.5), Ops.mod(new VFloat(2.5), new VFloat(1.0), 1));
        assertEquals(new VFloat(-1.5), Ops.neg(new VFloat(1.5), 1));
    }

    @Test
    void 两个整数相除仍是整除而带浮点就是实数除法() {
        assertEquals(new VInt(3), Ops.div(new VInt(10), new VInt(3), 1));
        assertEquals(new VFloat(0.5), Ops.div(new VInt(1), new VFloat(2.0), 1));
        assertEquals(new VFloat(10.0 / 3.0), Ops.div(new VFloat(10.0), new VInt(3), 1));
        assertEquals("除零", assertThrows(LbxError.class,
                () -> Ops.div(new VFloat(1.0), new VFloat(0.0), 1)).getMessage());
        assertEquals("除零", assertThrows(LbxError.class,
                () -> Ops.div(new VFloat(1.0), new VInt(0), 1)).getMessage());
    }

    @Test
    void 两个数值类型之间按数值比较() {
        assertTrue(Ops.eq(new VInt(1), new VFloat(1.0)));
        assertTrue(Ops.eq(new VFloat(2.0), new VInt(2)));
        assertFalse(Ops.eq(new VInt(1), new VFloat(1.5)));
        assertTrue(Ops.cmp("<", new VInt(1), new VFloat(1.5), 1));
        assertTrue(Ops.cmp(">=", new VFloat(2.0), new VInt(2), 1));
        // 非数值类型之间仍然遵循「类型不同 → false / 报错」
        assertFalse(Ops.eq(new VFloat(1.0), new VStr("1.0")));
        assertEquals("运算符 < 不支持 float 和 string", assertThrows(LbxError.class,
                () -> Ops.cmp("<", new VFloat(1.0), new VStr("a"), 2)).getMessage());
    }
}
