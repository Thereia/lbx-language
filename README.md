# LBX 语言与解释器

编译原理课程大作业：设计并实现一门**解释型通用脚本语言 LBX**（取名自设计者"刘泊鑫"拼音首字母），
配套黑盒测试集、性能测试、面向人与 AI 的开发指南，以及用编程 Agent 基于 LBX 开发的应用。

- 源文件扩展名：`.lbx`（UTF-8 无 BOM）
- 解释器实现语言：**Java 17**，纯 JDK，无第三方依赖
- 版本管理：Git

> **第一次打开这个仓库，先看 [docs/00-导读.md](docs/00-导读.md)** ——
> 它按"你要干什么"给出三条阅读路线，其中验收主线是：跟着一条数据读三个文件（约 2 小时）。

## 一、作业要求与分工对照

| # | 要求 | 分值 | 产出位置 | 状态 |
|---|---|---|---|---|
| 1 | 设计语言 + 实现解释器 | 20 | `docs/01-语法规则.md`、`src/` | 解释器 M1–M6 已完成并实测通过（语法 v1.3，2026-10-13 加入 float） |
| 2 | 黑盒测试集（用 LBX 写，一条命令跑完） | 20 | `src/test/resources/lbx/`、`src/main/java/lbx/TestRunner.java` | 60 个用例全绿（18 正常 + 40 错误 + 2 命令行），见 `src/test/resources/lbx/README.md` |
| 3 | 性能测试（LBX vs Python） | 10 | `samples/bench/`、`docs/04-性能测试报告.md` | 3 个基准完成：fib(30) 8.9 倍、循环累加 1.09 倍、排序 2.30 倍 |
| 4 | 语法说明 + 给人/AI 的开发指南 | 20 | `docs/01`、`docs/02`、`docs/03` | 三份齐备：语法 v1.3 + 入门指南 + 给 Agent 的 Skill 文档（断言全部实跑核对） |
| 5 | 编程 Agent 用 LBX 开发 ≥200 行应用 + 开发记录 | 30 | `samples/app/`、`docs/05-Agent开发记录.md`、`docs/agent-log/` | 完成：Agent 只凭 `docs/03` 写出 462 行 21 点游戏（有效代码 390 行），真跑 8 局、真 EOF 不崩、60 个用例回归全绿 |

验收方式：线下讲解 PPT + 实现细节 + 演示 + 答辩。

## 二、语言速览

语句以 `;` 结束，块以 `end` 收尾；动态类型、无 `var`；
数值类型为 `int` 与 `float`，两个整数之间 `/` 是整除，只要出现浮点就按实数运算。
完整规则见 [docs/01-语法规则.md](docs/01-语法规则.md)。

```lbx
type Point = { x = 0, y = 0 };      // 结构类型，带字段默认值

func fib(n)
    if n < 2
        return n;
    end
    return fib(n - 1) + fib(n - 2);
end

p = Point { x = 3 };                // y 自动取默认值 0
nums = [1, 2, 3];
total = 0;
for n in nums
    total = total + n;
end
print("total = ${total}, p.y = ${p.y}, fib(10) = ${fib(10)}");
```

## 三、目录结构

```
LBX/
├─ 作业要求.txt              原始作业要求（勿删）
├─ README.md                 本文件：总览、进度、运行方式
├─ pom.xml                   Maven 构建：Java 17、UTF-8、运行时零依赖，测试用 JUnit 5
├─ docs/                     全部文档
│  ├─ 00-导读.md             入口：按目的分三条阅读路线（验收主线约 2 小时）
│  ├─ 01-语法规则.md         交付物1：LBX 语法规则（v1.2，权威）
│  ├─ 02-开发指南-人.md      交付物4：面向人的入门指南（每条语法都写了设计原因）
│  ├─ 03-开发指南-AI.md      交付物4：面向编程 Agent 的 Skill 文档（语法卡片 + 错误手册 + 自检清单）
│  ├─ 04-性能测试报告.md     交付物3：性能测试报告
│  ├─ 05-Agent开发记录.md    交付物5：Agent 开发过程记录（任务、独立验证、坑与指南缺口）
│  ├─ 06-系统介绍.md         交付物8：PPT 大纲与讲稿
│  ├─ 07-实现讲解.md         验收用：逐文件逐方法讲解 + 17 条验收问答
│  └─ agent-log/             交付物5：Agent 一手开发日志（blackjack-dev-log.md，645 行）
├─ samples/                  所有 LBX / Python 脚本（都不是 Java 代码，所以不进 src）
│  ├─ README.md              这里有什么、怎么跑
│  ├─ examples/              语言示例：hello / fib / features / guess / demo-tokens / demo-ast
│  ├─ app/blackjack.lbx      交付物5：编程 Agent 用 LBX 写的小游戏（462 行）
│  └─ bench/                 交付物3：性能基准（fib / loop / sort 的 .lbx 与 .py）
├─ src/
│  ├─ main/java/lbx/         解释器源码（单包 10 个文件，逐文件说明见 §6.3）
│  │  ├─ Main.java           入口：读文件 → 词法 → 语法 → 求值；--tokens / --ast；退出码
│  │  ├─ LbxError.java       唯一异常：三类错误与 §10.3 的消息格式
│  │  ├─ Token.java          记号：enum TokenKind + record Token
│  │  ├─ Node.java           语法树：enum Kind + 单个 Node 类
│  │  ├─ Lexer.java          词法分析：字符流 → 记号流（含 ${} 插值的边界切分）
│  │  ├─ Parser.java         语法分析：递归下降 + 优先级爬升
│  │  ├─ Value.java          值模型：sealed interface + 9 个实现（§8 的 str() / repr() 在各类里）
│  │  ├─ Ops.java            §5 运算规则集中一处
│  │  ├─ Interp.java         求值器：作用域栈、eval、内置函数、控制流异常
│  │  └─ TestRunner.java     黑盒测试运行器（命令行工具 + 供 JUnit 调用）
│  └─ test/
│     ├─ java/lbx/           JUnit 测试 4 个类，随 `mvn test` 一起跑
│     └─ resources/lbx/      交付物2：黑盒测试集（60 个用例）
│        ├─ README.md        覆盖矩阵与目录约定
│        ├─ cases/           18 个正常用例（.lbx + .expected，可选 .in）
│        └─ errors/          40 个错误用例（.lbx + .expected）
└─ target/                   Maven 构建产物（不入版本库）
```

## 四、里程碑

| 里程碑 | 内容 | 验收标准（每步可独立跑通） | 状态 |
|---|---|---|---|
| M0 | git 初始化、目录骨架、语法规则定稿、实现架构定稿 | 仓库有历史、文档齐 | 完成 |
| M1 | Lexer：字符流 → 记号流 | `--tokens` 跑 `print("hi");` 打出记号，行列号正确 | 完成 |
| M2 | Parser + AST：优先级爬升、语句、`end` 块 | `--ast` 打印缩进语法树；`if x == 1 … end` 树形状正确 | 完成 |
| M3 | 求值器：作用域、变量、运算、`print`/`input`、分支循环 | `print(1 + 2 * 3);` → `7`；`print("a" + "b");` → `ab` | 完成 |
| M4 | 函数：定义/调用/参数/`return`/递归 | `print(fib(20));` → `6765` | 完成 |
| M5 | 数组、结构类型、下标与字段读写 | 语法文档 §11 的 guess.lbx 跑通 | 完成 |
| M6 | 错误处理：行列号定位、退出码 | 12 个错误脚本 stderr 格式正确、退出码 1；文件不存在退出码 2 | 完成 |
| M7 | 黑盒测试集 + 一键运行（交付物2） | 60 个用例一次全绿（`mvn -o -q test` 或 `java -cp target\classes lbx.TestRunner`，退出码 0） | 完成 |
| M8 | 性能测试与报告（交付物3） | 3 个基准两边输出一致、耗时已实测并写成报告（含启动开销剥离与归因） | 完成 |
| M9 | 人/AI 开发指南（交付物4）+ 语法文档校订（实现中若发现偏差，升版本并记录） | `docs/02` + `docs/03` 完稿；文档里 25 条具体输出断言逐条实跑核对通过；文档声称"不支持"的 7 种写法逐个验证确实报错 | 完成 |
| M10 | Agent 用 LBX 开发 ≥200 行应用 + 开发记录（交付物5） | Agent 只凭 `docs/03` 一轮写通 462 行 21 点游戏；独立验证 6 项全过；报回 3 处指南缺口已修补 | 完成 |
| M11 | PPT 与验收演练（交付物8） | 能逐条讲清实现细节并现场演示 | 待办 |

## 五、构建与运行

```powershell
mvn -o -q compile                                            # 编译（-o = 离线，本机 .m2 已具备全部插件）
java -cp target\classes lbx.Main samples/examples/features.lbx         # 跑脚本
java -cp target\classes lbx.Main --tokens samples/examples/demo-tokens.lbx   # 只看记号流
java -cp target\classes lbx.Main --ast    samples/examples/demo-ast.lbx      # 只看语法树
mvn -o -q test                                               # 单元测试 + 60 个黑盒用例，一条命令
java -cp target\classes lbx.TestRunner                       # 不经 Maven 也能直接跑黑盒套件
mvn -o -q package                                            # 打成 target/lbx.jar（清单里带 Main-Class）
```

解释器**运行时零第三方依赖**，只需要 JDK 17；JUnit 5 仅在测试期使用。
`pom.xml` 里所有插件版本都写死了，所以 `-o` 能完全离线构建；不加 `-o` 也可以，只是 Maven 会联网检查更新。

## 六、实现架构（M0 定稿）

本文件是**实现**层面的设计记录；`docs/01-语法规则.md` 是**语言语义**层面的规格，两者不混。
（规格不规定实现怎么写，实现不得违反规格。）

### 6.1 作业要求 → 实现落点

| 作业要求原文 | 对应实现 |
|---|---|
| 整数，字符串等基本数据类型 | `Value`（sealed interface）+ `VInt` / `VFloat` / `VStr` / `VBool` / `VNull`；`typeName()` 即语法文档 §4 类型表 |
| 数组 | `VArray`（`List<Value>`，下标 0 起、越界报运行时错误、引用语义） |
| 结构类型 | `type` 声明 → `Map<String, LinkedHashMap<String, Value>>` 存字段默认值；`VStruct`（类型名 + 保序字段表，保序即为 §8 打印顺序） |
| 基本输入输出 | `VBuiltin` 表中的 `print`（按 §8 走 `str()` / `repr()`）、`input`（读一行去换行，EOF 返回 `null`） |
| 分支循环控制 | `Node` 的 `if` / `while` / `for` + `Interp` 对应分支 + `BreakEx` / `ContinueEx` 异常 |
| 函数定义和调用 | `VFunc`（名字 / 形参 / 函数体）+ 作用域栈 + `ReturnEx`；**定义即注册**，故支持互相递归与先调用后定义 |

### 6.2 关键设计决定

| 决定 | 内容 | 理由 |
|---|---|---|
| 值用显式类型，不用 `java.lang.Object` | `sealed interface Value` + 9 个实现 | 「基本数据类型」是作业硬要求，必须在代码里看得见；`typeof()`、§5 运算规则各自只在一个地方实现。v1.3 加浮点只动了这一个文件加 `Ops` 的分派，既有用例全部不用改 |
| 语法树只有 1 个 `Node` 类 | `enum Kind` + 操作数字段 + 子节点表 | 比「Expr/Stmt 各 10 个子类 + Visitor」少写约 500 行样板；`switch (node.kind)` 一处分发，验收好讲 |
| 表达式用优先级爬升 | 1 张优先级表 + 1 个 `expr(minPrec)` 方法 | 替代 9 个 `or_expr` / `and_expr` / … 方法 |
| 控制流用异常 | `ReturnEx` / `BreakEx` / `ContinueEx`（关闭栈追踪） | 免去每个语句返回「是否 return/break」标志位 |
| 不做配对缓冲区 | 整个 `.lbx` 文件一次读成 String，回退即 `i--` | 脚本规模是脚本级（几 KB），配对缓冲区是流式读入才需要的（见词法分析实验） |
| 不做错误恢复 | 第一个错误即退出（语法文档 §10.3） | 规格如此规定 |
| 不做 Visitor / 字节码 / VM | 纯 AST 走树求值 | 与《两周自制脚本语言》Stone 同路线；规格未要求 |
| Java 17 语言特性使用边界 | 可用 `sealed` / `record` / `instanceof` 模式匹配；**不可用 `switch` 模式匹配**（17 中是预览特性，需 `--enable-preview`） | 避免编译期踩坑 |

### 6.3 实际代码量与工程结构

标准 Maven 布局：源码在 `src/main/java/lbx/`，测试在 `src/test/java/lbx/`，构建产物在 `target/`。

解释器与运行器（`src/main/java/lbx/`，10 个文件 2066 行）：

| 文件 | 行数 | 文件 | 行数 |
|---|---|---|---|
| `Parser.java` | 582 | `Value.java` | 166 |
| `Interp.java` | 486 | `Ops.java` | 138 |
| `Lexer.java` | 267 | `Main.java` | 94 |
| `TestRunner.java` | 225 | `Node.java` | 53 |
| `LbxError.java` | 38 | `Token.java` | 17 |
| **合计** | | | **2066** |

测试（`src/test/java/lbx/`，4 个文件 373 行）分两层：
`LexerTest` / `OpsTest` / `InterpTest` 是**单元与端到端测试**（同进程直接调解释器内部 API），
`BlackBoxSuiteTest` 调用 `TestRunner` 把 `src/test/resources/lbx/` 下的黑盒用例并进 `mvn test`。
黑盒测试集本身是 60 个用例（18 正常 + 40 错误 + 2 个命令行用例），共 437 行 LBX 源码与 58 个 `.expected` 期望文件。
`TestRunner` 放在 main 而不放 test，是因为它同时是**命令行工具**（`java -cp target\classes lbx.TestRunner`），
AI 开发指南与文档都直接引用这条命令。

### 6.4 实现中发现并修掉的两个真 bug（值得写进 PPT）

| # | 现象 | 原因 | 修法 |
|---|---|---|---|
| 1 | `print("${"内层字符串"}")` 报「非法字符」 | 词法分析器切字符串时不知道 `${}` 内部是表达式，把内层字符串的引号当成了外层字符串的结束引号 | `string()` 进入 `${` 后维护括号深度，并整段跳过片段里的嵌套字符串；同时把内置函数 `type` 改名为 `typeof`（它与关键字 `type` 撞名，本就无法调用），升语法文档 v1.1 |
| 2 | `func judge(...)` 里用了 `elif`，后面 `func main` 报「定义只能出现在最外层」 | `elif` 递归解析时内层 `ifStmt()` 已消费结尾的 `end`，外层又 `expectKeyword("end")`，把函数自己的 `end` 抢走 | `elif` 分支直接返回，不再要求第二个 `end` |
| 3 | 输出在 Windows 上是 `\r\n`，Linux 上是 `\n` | `PrintStream.println` 用的是 `System.lineSeparator()` | `print` 内置函数改成显式输出 `'\n'`；不固定行尾的话，黑盒测试按字节比对换个平台就全错（见 §六 与 `src/test/resources/lbx/README.md`） |

## 七、实现要点备忘

- **UTF-8**：Windows 下 Java 默认字符集为 GBK，所有文件读取必须显式 `StandardCharsets.UTF_8`，
  stdout/stderr 需包装为 UTF-8 的 `PrintStream`，批处理脚本开头 `chcp 65001`，否则中文输出乱码。
- **输出必须干净**：解释器自身不打印任何调试信息，正常结果走 stdout、错误走 stderr，
  黑盒测试逐字节比对 stdout。
- **错误必带行号**：语法错误带行列号，运行时错误带行号，退出码 0/1/2 见语法文档第 10 节。
