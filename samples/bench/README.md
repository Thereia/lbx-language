# 性能测试脚本

作业提交内容第 4 项（性能测试）的脚本部分。测试结果与分析见
[../docs/04-性能测试报告.md](../docs/04-性能测试报告.md)。

## 三个基准

每个基准都有 LBX 与 Python 两个版本，**算法逐行对应、输入完全相同**，
所以两边的输出必须一致——这是比较有效性的前提。

| 基准 | LBX | Python | 工作量 | 两边应输出 |
|---|---|---|---|---|
| 递归 | `fib.lbx` | `fib.py` | `fib(30)` | `832040` |
| 循环 | `loop.lbx` | `loop.py` | `while` 累加 3000 万次 | `449999985000000` |
| 排序 | `sort.lbx` | `sort.py` | 冒泡排序 4000 个整数 | `1 4000` |

## 运行

```bat
mvn -o -q compile

java -cp target\classes lbx.Main samples\bench\fib.lbx
python samples\bench\fib.py

java -cp target\classes lbx.Main samples\bench\loop.lbx
python samples\bench\loop.py

java -cp target\classes lbx.Main samples\bench\sort.lbx
python samples\bench\sort.py
```

## 计时并核对两边结果一致

```powershell
$sw = [System.Diagnostics.Stopwatch]::new()
foreach ($name in @('fib','loop','sort')) {
    foreach ($pair in @(@('LBX', 'java', @('-cp','target\classes','lbx.Main',"samples/bench/$name.lbx")),
                        @('Python', 'python', @("samples/bench/$name.py")))) {
        $label = $pair[0]; $exe = $pair[1]; $argList = $pair[2]
        $best = [double]::MaxValue; $out = ''
        foreach ($i in 1..3) {
            $sw.Restart(); $out = & $exe @argList 2>&1; $sw.Stop()
            if ($sw.Elapsed.TotalSeconds -lt $best) { $best = $sw.Elapsed.TotalSeconds }
        }
        "{0,-7} {1,-7} 最小 {2,6:N3} s   输出 {3}" -f $name, $label, $best, ($out -join ' ')
    }
}
```

## 实测结果摘要（2026-10-13，本机）

| 基准 | LBX | Python | 倍数 |
|---|---|---|---|
| 递归 fib(30) | 0.844 s | 0.095 s | 8.9 倍 |
| 循环累加 3×10⁷ | 2.652 s | 2.433 s | 1.09 倍 |
| 冒泡排序 4000 | 2.146 s | 0.931 s | 2.30 倍 |

含各自运行时启动开销（JVM 约 0.088 s，CPython 约 0.014 s）。详细分析与优化方向见性能报告。
