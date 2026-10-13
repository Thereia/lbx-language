# samples —— 所有 LBX 与 Python 脚本

这里的文件都**不是 Java 代码**（解释器源码在 `src/main/java/lbx/`），所以单独放在一个目录：
它们是三种不同交付物的脚本部分，加上一批能直接跑的语言范例。

编译一次解释器之后（在仓库根目录）：

```
mvn -o -q compile
```

## 目录说明

| 目录 / 文件 | 是什么 | 对应交付物 |
|---|---|---|
| `examples/` | LBX 语言范例：`hello` 最小示例、`fib` 递归与循环、`features` 一份脚本覆盖全部功能、`guess` 带输入交互、`demo-tokens` / `demo-ast` 给 `--tokens`、`--ast` 演示用 | 语言示例 |
| `app/blackjack.lbx` | 编程 Agent 只用 `docs/03-开发指南-AI.md` 写出来的 21 点游戏，462 行（有效代码 390 行） | 交付物5（30 分） |
| `bench/` | 性能基准：`fib` / `loop` / `sort` 各有 `.lbx` 与 `.py` 两版，算法逐行对应、输入相同 | 交付物3（10 分） |

## 怎么跑

```powershell
# 语言范例
java -cp target\classes lbx.Main samples/examples/features.lbx
java -cp target\classes lbx.Main samples/examples/guess.lbx

# 看内部：记号流与语法树
java -cp target\classes lbx.Main --tokens samples/examples/demo-tokens.lbx
java -cp target\classes lbx.Main --ast    samples/examples/demo-ast.lbx

# Agent 写的小游戏（下注 1/2/3/a，要牌 h，停牌 s，收工 q）
java -cp target\classes lbx.Main samples/app/blackjack.lbx

# 性能基准（两个版本输出必须一致）
java -cp target\classes lbx.Main samples/bench/fib.lbx
python samples/bench/fib.py
```

基准的计时方法与结果见 `bench/README.md` 与 `../docs/04-性能测试报告.md`；
小游戏的开发过程与独立验证见 `../docs/05-Agent开发记录.md`。
