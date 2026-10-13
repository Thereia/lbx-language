# 基准 3：冒泡排序 4000 个整数 —— 与 samples/bench/sort.lbx 完全相同的算法
# 运行：python samples/bench/sort.py
def bubble(a):
    n = len(a)
    i = 0
    while i < n - 1:
        j = 0
        while j < n - 1 - i:
            if a[j] > a[j + 1]:
                t = a[j]
                a[j] = a[j + 1]
                a[j + 1] = t
            j = j + 1
        i = i + 1
    return a


# 倒序数组，保证两种语言的输入完全一致（不用随机数）
n = 4000
a = []
i = n
while i > 0:
    a.append(i)
    i = i - 1
bubble(a)
print(a[0], a[-1])
