# 基准 2：整数循环累加 —— 与 samples/bench/loop.lbx 完全相同的算法
# 运行：python samples/bench/loop.py
total = 0
i = 0
while i < 30000000:
    total = total + i
    i = i + 1
print(total)
