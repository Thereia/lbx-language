package lbx;

import java.util.List;

/** 语法树节点种类（语法文档 §12 的文法，每一类节点对应一个 kind）。 */
enum Kind {
    // 字面量与表达式
    INT, BOOL, NULL, STR, INTERP,
    VAR, BIN, UN, CALL, INDEX, FIELD, ARRAY, STRUCT,
    // 语句与声明
    BLOCK, EXPR, ASSIGN, IF, WHILE, FOR, RETURN, BREAK, CONTINUE, FUNC, TYPE, FIELD_DEF
}

/**
 * 语法树节点：整棵树只用这一个类（形态见 README §6.2）。
 * 各 kind 的字段用法：
 *
 *   INT / BOOL / NULL   value = 值（VInt / VBool / VNull）
 *   STR                 value = VStr
 *   INTERP              kids  = 交替出现的「字面量段 STR」与「表达式节点」
 *   VAR                 text  = 变量名
 *   BIN                 text  = 运算符   kids = [左, 右]
 *   UN                  text  = "-" 或 "not"   kids = [操作数]
 *   CALL                kids  = [被调用者, 实参...]
 *   INDEX               kids  = [数组, 下标]
 *   FIELD               text  = 字段名   kids = [对象]
 *   ARRAY               kids  = 元素
 *   STRUCT              text  = 类型名   kids = [FIELD_DEF...]
 *   BLOCK               kids  = 语句
 *   EXPR                kids  = [表达式]
 *   ASSIGN              kids  = [赋值目标, 值]
 *   IF                  kids  = [条件, 真分支, 假分支（可能没有）]；elif 解析为嵌套的 IF
 *   WHILE               kids  = [条件, 循环体]
 *   FOR                 names = [循环变量]   kids = [被遍历表达式, 循环体]
 *   RETURN              kids  = [表达式]，或为空表示 return;
 *   FUNC                text  = 函数名   names = 形参   kids = 函数体语句
 *   TYPE                text  = 类型名   kids = [FIELD_DEF...]
 *   FIELD_DEF           text  = 字段名   kids = [默认值 / 初始值表达式]
 */
final class Node {

    final Kind kind;
    final int line;
    String text;
    Object value;
    List<Node> kids = List.of();
    List<String> names = List.of();

    Node(Kind kind, int line) {
        this.kind = kind;
        this.line = line;
    }
}
