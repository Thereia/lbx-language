# 基准 1：递归 fib(30) —— 与 samples/bench/fib.lbx 完全相同的算法
# 运行：python samples/bench/fib.py
def fib(n):
    if n < 2:
        return n
    return fib(n - 1) + fib(n - 2)


print(fib(30))
