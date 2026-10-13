package lbx;

/**
 * 解释器唯一的一种异常，覆盖词法 / 语法 / 运行时三类错误（语法文档 §10.3）。
 * 行号列号 1 起算；col 为 0 表示这类错误只报行号。
 */
class LbxError extends RuntimeException {

    final String phase;
    final int line;
    final int col;

    private LbxError(String phase, int line, int col, String msg) {
        // 关掉栈追踪：控制流也用异常实现（§见 Interp），不能在这里付出建栈的代价
        super(msg, null, false, false);
        this.phase = phase;
        this.line = line;
        this.col = col;
    }

    static LbxError lex(int line, int col, String msg) {
        return new LbxError("词法", line, col, msg);
    }

    static LbxError syntax(int line, int col, String msg) {
        return new LbxError("语法", line, col, msg);
    }

    static LbxError runtime(int line, String msg) {
        return new LbxError("运行时", line, 0, msg);
    }

    /** 输出成 §10.3 规定的样子，由 Main 打到 stderr。 */
    String format() {
        String pos = (col > 0) ? "行 " + line + ", 列 " + col : "行 " + line;
        return "[LBX " + phase + "错误] " + pos + ": " + getMessage();
    }
}
