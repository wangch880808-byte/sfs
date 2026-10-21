# 查询性能基准

用于 V1.0.1.9 起的查询性能验收：同一份造数、同一台机器上，比较改造前后各接口的耗时、SQL 条数、扫描行数，
并逐项比对接口返回内容，确保“只变快、结果不变”。

**只连本机一次性环境**（`deploy/tests/e2e/local-env.sh` 建的 `nus-e2e-mysql` 容器与 127.0.0.1 上的后端），脚本会拒绝其他地址。

## 步骤

```bash
bash deploy/tests/e2e/local-env.sh up flyway        # 按当前基线（21）建库
bash deploy/tests/e2e/local-env.sh bootstrap
# 降低本机密码哈希强度只为造数快，不影响查询
NUS_PASSWORD_ENCODER_STRENGTH=4 bash deploy/tests/e2e/local-env.sh backend
python3 deploy/tests/perf/seed.py --out /tmp/perf-seed.json          # 6000 学生（--students 调整）与边界数据
docker exec -i -e MYSQL_PWD="$(cat ~/.cache/nus-e2e/db.pw)" nus-e2e-mysql mysql -uroot nus < deploy/tests/perf/inflate.sql   # 历史数据放大 10 倍
python3 deploy/tests/perf/bench.py --seed /tmp/perf-seed.json --out /tmp/perf-before
# 换成改造后的后端（同一个库：先执行新的迁移，再重启后端），再跑一次
python3 deploy/tests/perf/bench.py --seed /tmp/perf-seed.json --out /tmp/perf-after
python3 deploy/tests/perf/compare.py /tmp/perf-before /tmp/perf-after
```

- `seed.py`：结构（组织、班级、教师、课程、班课、任务）走接口，学生和提交记录用 SQL 按模板批量复制；造边界数据：移出班级的学生、归档任务、归档班课、打回重交、空分数、同分并列。
- `inflate.sql`：把任务、分配、提交、站内信放大 10 倍，模拟多个学期的积累。
- `bench.py`：管理员、教师、学生三种身份调用主要查询接口，所有接口先完整预热两轮，再各测 3 次取中位数；用 `performance_schema` 统计每次请求的 SQL 条数、耗时、扫描行数；保存每个接口的返回 JSON。
- `compare.py`：输出前后指标对照和返回内容的逐项差异。
