# LBX 语言与解释器

编译原理课程大作业：设计并实现一门**解释型通用脚本语言 LBX**（取名自设计者"刘泊鑫"拼音首字母），
配套黑盒测试集、性能测试、面向人与 AI 的开发指南，以及用编程 Agent 基于 LBX 开发的应用。

- 源文件扩展名：`.lbx`（UTF-8 无 BOM）
- 解释器实现语言：**Java 17**，纯 JDK，无第三方依赖
- 版本管理：Git

## 一、作业要求与分工对照

| # | 要求 | 分值 | 产出位置 | 状态 |
|---|---|---|---|---|
| 1 | 设计语言 + 实现解释器 | 20 | `docs/01-语法规则.md`、`src/` | 解释器 M1–M6 已完成并实测通过（语法 v1.1） |
| 2 | 黑盒测试集（用 LBX 写，一条命令跑完） | 20 | `tests/`、`src/lbx/TestRunner.java` | 53 个用例全绿（17 正常 + 36 错误），见 `tests/README.md` |
| 3 | 性能测试（LBX vs Python） | 10 | `bench/`、`docs/04-性能测试报告.md` | 3 个基准完成：fib(30) 8.9 倍、循环累加 1.09 倍、排序 2.30 倍 |
| 4 | 语法说明 + 给人/AI 的开发指南 | 20 | `docs/01`、`docs/02`、`docs/03` | 三份齐备：语法 v1.1 + 入门指南 451 行 + 给 Agent 的 Skill 文档 393 行（断言全部实跑核对） |
| 5 | 编程 Agent 用 LBX 开发 ≥200 行应用 + 开发记录 | 30 | `examples/`、`docs/05-Agent开发记录.md`、`docs/agent-log/` | 未开工 |

验收方式：线下讲解 PPT + 实现细节 + 演示 + 答辩。

## 二、语言速览

语句以 `;` 结束，块以 `end` 收尾；动态类型、无 `var`、整数为唯一数值类型（`/` 为整除）。
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
├─ docs/
│  ├─ 01-语法规则.md         交付物1：LBX 语法规则（v1.1，权威）
│  ├─ 02-开发指南-人.md      交付物4：面向人的入门指南（每条语法都写了设计原因）
│  ├─ 03-开发指南-AI.md      交付物4：面向编程 Agent 的 Skill 文档（语法卡片 + 错误手册 + 自检清单）
│  ├─ 04-性能测试报告.md     交付物3：性能测试报告
│  ├─ 05-Agent开发记录.md    交付物5：Agent 开发过程记录
│  ├─ 06-系统介绍.md         交付物8：PPT 大纲与讲稿
│  ├─ 07-实现讲解.md         验收用：逐文件逐方法讲解 + 17 条验收问答
│  └─ agent-log/             交付物5：Agent 会话原始记录
├─ src/lbx/                  单包平铺（编译只需一行 javac，见 §六）
│  ├─ Main.java              入口：读文件 → 词法 → 语法 → 求值；错误打印与退出码；--tokens / --ast
│  ├─ LbxError.java          唯一异常：词法 / 语法 / 运行时三类错误与 §10.3 的消息格式
│  ├─ Token.java             记号：enum TokenKind + record Token
│  ├─ Lexer.java             词法分析：字符流 → 记号流（含 ${} 插值的边界切分）
│  ├─ Node.java              语法树：enum Kind + 单个 Node 类
│  ├─ Parser.java            语法分析：递归下降 + 优先级爬升；拼装插值表达式
│  ├─ Value.java             值模型：sealed interface + 8 个实现，每类自带 §8 的 str() / repr()
│  ├─ Ops.java               §5 运算规则（类型判断、除零、比较、条件检查）集中一处
│  ├─ Interp.java            求值器：作用域栈、eval、内置函数表、控制流异常
│  └─ TestRunner.java        黑盒测试运行器：新起 JVM 跑 lbx.Main，比对 stdout / stderr / 退出码
├─ tests/                    交付物2：黑盒测试集（说明见 tests/README.md）
│  ├─ README.md              怎么跑、目录约定、覆盖矩阵
│  ├─ cases/                 正常用例：名字.lbx + 名字.expected（+ 可选 名字.in）
│  └─ errors/                错误用例：名字.lbx + 名字.expected（期望 stderr 与退出码 1）
├─ bench/                    交付物3：性能测试脚本（lbx + py 同算法同输入，说明见 bench/README.md）
└─ examples/                 LBX 语言编写的示例程序（hello / fib / features / guess）
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
| M7 | 黑盒测试集 + 一键运行（交付物2） | 53 个用例一次全绿（`java -cp build lbx.TestRunner`，退出码 0） | 完成 |
| M8 | 性能测试与报告（交付物3） | 3 个基准两边输出一致、耗时已实测并写成报告（含启动开销剥离与归因） | 完成 |
| M9 | 人/AI 开发指南（交付物4）+ 语法文档校订（实现中若发现偏差，升版本并记录） | `docs/02` + `docs/03` 完稿；文档里 25 条具体输出断言逐条实跑核对通过；文档声称"不支持"的 7 种写法逐个验证确实报错 | 完成 |
| M10 | Agent 用 LBX 开发 ≥200 行应用 + 开发记录（交付物5） | 应用 ≥200 行 LBX 且可运行，开发记录留档 | 待办 |
| M11 | PPT 与验收演练（交付物8） | 能逐条讲清实现细节并现场演示 | 待办 |

## 五、构建与运行（M1 起可用）

```bat
:: 编译解释器与测试运行器（单包平铺，所以一行就够，不需要列文件清单）
javac -encoding UTF-8 -d build src\lbx\*.java

:: 运行脚本
java -cp build lbx.Main examples\fib.lbx

:: 自测 / 验收讲解：只看记号流或语法树
java -cp build lbx.Main --tokens examples\fib.lbx
java -cp build lbx.Main --ast    examples\fib.lbx

:: 一条命令跑完全部黑盒测试（53 个用例，退出码 0 表示全绿）
java -cp build lbx.TestRunner
```

解释器不依赖任何第三方库，只用 JDK 17 的 `javac` / `java`，也不需要 Maven / Gradle / IDE。

## 六、实现架构（M0 定稿）

本文件是**实现**层面的设计记录；`docs/01-语法规则.md` 是**语言语义**层面的规格，两者不混。
（规格不规定实现怎么写，实现不得违反规格。）

### 6.1 作业要求 → 实现落点

| 作业要求原文 | 对应实现 |
|---|---|
| 整数，字符串等基本数据类型 | `Value`（sealed interface）+ `VInt` / `VStr` / `VBool` / `VNull`；`typeName()` 即语法文档 §4 类型表 |
| 数组 | `VArray`（`List<Value>`，下标 0 起、越界报运行时错误、引用语义） |
| 结构类型 | `type` 声明 → `Map<String, LinkedHashMap<String, Value>>` 存字段默认值；`VStruct`（类型名 + 保序字段表，保序即为 §8 打印顺序） |
| 基本输入输出 | `VBuiltin` 表中的 `print`（按 §8 走 `str()` / `repr()`）、`input`（读一行去换行，EOF 返回 `null`） |
| 分支循环控制 | `Node` 的 `if` / `while` / `for` + `Interp` 对应分支 + `BreakEx` / `ContinueEx` 异常 |
| 函数定义和调用 | `VFunc`（名字 / 形参 / 函数体）+ 作用域栈 + `ReturnEx`；**定义即注册**，故支持互相递归与先调用后定义 |

### 6.2 关键设计决定

| 决定 | 内容 | 理由 |
|---|---|---|
| 值用显式类型，不用 `java.lang.Object` | `sealed interface Value` + 8 个实现 | 「基本数据类型」是作业硬要求，必须在代码里看得见；`typeof()`、§5 运算规则各自只在一个地方实现 |
| 语法树只有 1 个 `Node` 类 | `enum Kind` + 操作数字段 + 子节点表 | 比「Expr/Stmt 各 10 个子类 + Visitor」少写约 500 行样板；`switch (node.kind)` 一处分发，验收好讲 |
| 表达式用优先级爬升 | 1 张优先级表 + 1 个 `expr(minPrec)` 方法 | 替代 9 个 `or_expr` / `and_expr` / … 方法 |
| 控制流用异常 | `ReturnEx` / `BreakEx` / `ContinueEx`（关闭栈追踪） | 免去每个语句返回「是否 return/break」标志位 |
| 不做配对缓冲区 | 整个 `.lbx` 文件一次读成 String，回退即 `i--` | 语法文档 §3 规定「整数禁止浮点」，文件规模为脚本级；配对缓冲区是流式读入才需要的（见词法分析实验） |
| 不做错误恢复 | 第一个错误即退出（语法文档 §10.3） | 规格如此规定 |
| 不做 Visitor / 字节码 / VM | 纯 AST 走树求值 | 与《两周自制脚本语言》Stone 同路线；规格未要求 |
| Java 17 语言特性使用边界 | 可用 `sealed` / `record` / `instanceof` 模式匹配；**不可用 `switch` 模式匹配**（17 中是预览特性，需 `--enable-preview`） | 避免编译期踩坑 |

### 6.3 实际代码量（10 个文件，1957 行）

| 文件 | 行数 | 文件 | 行数 |
|---|---|---|---|
| `Parser.java` | 578 | `Value.java` | 154 |
| `Interp.java` | 457 | `Ops.java` | 105 |
| `Lexer.java` | 255 | `Main.java` | 94 |
| `TestRunner.java` | 206 | `Node.java` | 53 |
| `LbxError.java` | 38 | `Token.java` | 17 |
| **合计** | | | **1957** |

黑盒测试集另有 53 个 `.lbx` 用例（17 正常 + 36 错误），共 405 行 LBX 源码。

### 6.4 实现中发现并修掉的两个真 bug（值得写进 PPT）

| # | 现象 | 原因 | 修法 |
|---|---|---|---|
| 1 | `print("${"内层字符串"}")` 报「非法字符」 | 词法分析器切字符串时不知道 `${}` 内部是表达式，把内层字符串的引号当成了外层字符串的结束引号 | `string()` 进入 `${` 后维护括号深度，并整段跳过片段里的嵌套字符串；同时把内置函数 `type` 改名为 `typeof`（它与关键字 `type` 撞名，本就无法调用），升语法文档 v1.1 |
| 2 | `func judge(...)` 里用了 `elif`，后面 `func main` 报「定义只能出现在最外层」 | `elif` 递归解析时内层 `ifStmt()` 已消费结尾的 `end`，外层又 `expectKeyword("end")`，把函数自己的 `end` 抢走 | `elif` 分支直接返回，不再要求第二个 `end` |
| 3 | 输出在 Windows 上是 `\r\n`，Linux 上是 `\n` | `PrintStream.println` 用的是 `System.lineSeparator()` | `print` 内置函数改成显式输出 `'\n'`；不固定行尾的话，黑盒测试按字节比对换个平台就全错（见 §六 与 `tests/README.md`） |

## 七、实现要点备忘

- **UTF-8**：Windows 下 Java 默认字符集为 GBK，所有文件读取必须显式 `StandardCharsets.UTF_8`，
  stdout/stderr 需包装为 UTF-8 的 `PrintStream`，批处理脚本开头 `chcp 65001`，否则中文输出乱码。
- **输出必须干净**：解释器自身不打印任何调试信息，正常结果走 stdout、错误走 stderr，
  黑盒测试逐字节比对 stdout。
- **错误必带行号**：语法错误带行列号，运行时错误带行号，退出码 0/1/2 见语法文档第 10 节。
