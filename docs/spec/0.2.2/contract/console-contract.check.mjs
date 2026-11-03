#!/usr/bin/env node
/**
 * 控制台（dsh-java-app 自带 Web 控制台）契约校验。
 *
 * 事实源：本目录 fixtures/*.json（client-contract.md §8）。fixture 是「归一化后的领域对象」，
 * 服务端返回 { code, info, data } 信封，校验前统一剥离（与 static/app.js 的 apiRequest 一致）。
 *
 * 用法：
 *   node console-contract.check.mjs                     # 默认 http://127.0.0.1:8090
 *   BASE_URL=http://127.0.0.1:8090 ROOM_ID=room_xxx node console-contract.check.mjs
 *
 * 说明：
 *   - 只做「字段契约」校验（键集覆盖 + seq 单调 + contractVersion），不做值断言，
 *     避免真实数据变动导致假红灯。
 *   - 需要后端已启动；拿不到数据的项目标记为 SKIP（不计失败）。
 */
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE_URL || "http://127.0.0.1:8090";
const ROOM_ID = process.env.ROOM_ID || "";

const results = [];
const record = (name, status, detail) => {
  results.push({ name, status, detail });
  console.log(`${status === "PASS" ? "✓" : status === "SKIP" ? "·" : "✗"} ${name}${detail ? " — " + detail : ""}`);
};

async function fixture(file) {
  return JSON.parse(await readFile(join(here, "fixtures", file), "utf8"));
}

/** 剥信封：{ code: '00000', info, data } → data */
async function api(path, options) {
  const response = await fetch(BASE + path, options);
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload || payload.code !== "00000") {
    throw new Error((payload && payload.info) || `HTTP ${response.status}`);
  }
  return payload.data;
}

/** 键集覆盖：sample 至少包含 reference 的全部键（值为 null/undefined 的键视为可选，跳过） */
function missingKeys(reference, sample) {
  const keys = Object.keys(reference).filter((k) => reference[k] !== null && reference[k] !== undefined);
  return keys.filter((k) => !(k in sample));
}

async function main() {
  // 1. contractVersion：所有 fixture 必须为 1（两端契约测试同样断言）
  const files = [
    "session-list.json",
    "session-timeline.json",
    "stream-events.json",
    "approvals-questions.json",
    "digital-humans.json",
    "room-events.json",
    "feishu-bots.json",
  ];
  for (const file of files) {
    const fx = await fixture(file);
    record(`fixture ${file} contractVersion=1`, fx.contractVersion === 1 ? "PASS" : "FAIL",
      fx.contractVersion === 1 ? "" : `实际 ${fx.contractVersion}`);
  }

  // 2. 会话列表：GET /api/harness/console/sessions?limit=&offset=
  try {
    const list = await api("/api/harness/console/sessions?limit=5&offset=0");
    if (!Array.isArray(list) || list.length === 0) {
      record("session-list 字段契约", "SKIP", "无会话数据");
    } else {
      const ref = (await fixture("session-list.json")).sessions[0];
      const missing = missingKeys(ref, list[0]);
      record("session-list 字段契约", missing.length ? "FAIL" : "PASS",
        missing.length ? "缺字段 " + missing.join(",") : `${list.length} 条`);
    }
  } catch (e) {
    record("session-list 字段契约", "FAIL", e.message);
  }

  // 3. 消息时间线：GET /api/harness/console/sessions/{sessionId}/messages
  try {
    const list = await api("/api/harness/console/sessions?limit=1&offset=0");
    const sessionId = Array.isArray(list) && list.length ? list[0].sessionId || list[0].agentId : null;
    if (!sessionId) {
      record("session-timeline 字段契约", "SKIP", "无可用 sessionId");
    } else {
      const messages = await api(`/api/harness/console/sessions/${encodeURIComponent(sessionId)}/messages`);
      if (!Array.isArray(messages) || messages.length === 0) {
        record("session-timeline 字段契约", "SKIP", "无消息数据");
      } else {
        // 契约用 createdAt，后端当前为 occurredAt → 两者其一即可
        const hasTime = messages[0].createdAt || messages[0].occurredAt;
        const missing = missingKeys({ role: "user", content: "" }, messages[0]);
        record("session-timeline 字段契约", missing.length || !hasTime ? "FAIL" : "PASS",
          missing.length ? "缺字段 " + missing.join(",") : !hasTime ? "缺 createdAt/occurredAt" : `${messages.length} 条`);
      }
    }
  } catch (e) {
    record("session-timeline 字段契约", "FAIL", e.message);
  }

  // 4. 数字人：GET /api/digital-humans
  try {
    const humans = await api("/api/digital-humans");
    if (!Array.isArray(humans) || humans.length === 0) {
      record("digital-humans 字段契约", "SKIP", "无数字人数据");
    } else {
      const ref = (await fixture("digital-humans.json")).humans[0];
      const missing = missingKeys(ref, humans[0]);
      const epMissing = ref.endpoint && humans[0].endpoint ? missingKeys(ref.endpoint, humans[0].endpoint) : [];
      record("digital-humans 字段契约", missing.length || epMissing.length ? "FAIL" : "PASS",
        missing.length || epMissing.length ? "缺字段 " + [...missing, ...epMissing.map((k) => "endpoint." + k)].join(",") : `${humans.length} 个`);
    }
  } catch (e) {
    record("digital-humans 字段契约", "FAIL", e.message);
  }

  // 5. 审批 / 提问：pending 拉取
  for (const [kind, path] of [["approvals", "/api/harness/approvals/runtime/pending"], ["questions", "/api/harness/questions/runtime/pending"]]) {
    try {
      const data = await api(path);
      const ref = (await fixture("approvals-questions.json"))[kind][0];
      if (!Array.isArray(data) || data.length === 0) {
        record(`${kind} pending 字段契约`, "SKIP", "无待处理项");
      } else {
        const missing = missingKeys(ref, data[0]);
        record(`${kind} pending 字段契约`, missing.length ? "FAIL" : "PASS",
          missing.length ? "缺字段 " + missing.join(",") : `${data.length} 条`);
      }
    } catch (e) {
      record(`${kind} pending 字段契约`, "FAIL", e.message);
    }
  }

  // 6. 飞书 Bot 管理：GET /api/harness/bots（v0.2.2），校验字段覆盖与状态枚举
  try {
    const bots = await api("/api/harness/bots");
    const fx = await fixture("feishu-bots.json");
    if (!Array.isArray(bots) || bots.length === 0) {
      record("feishu-bots 字段契约", "SKIP", "无 Bot 数据");
    } else {
      const ref = fx.bots[0];
      const missing = missingKeys(ref, bots[0]);
      const states = new Set(fx.connectionStates);
      const badState = bots.find((b) => b.connectionState && !states.has(b.connectionState));
      const leaked = bots.find((b) => "appSecret" in b);
      record(
        "feishu-bots 字段契约",
        missing.length || badState || leaked ? "FAIL" : "PASS",
        missing.length
          ? "缺字段 " + missing.join(",")
          : badState
            ? "非法 connectionState " + badState.connectionState
            : leaked
              ? "响应泄漏 appSecret"
              : `${bots.length} 个`
      );
    }
  } catch (e) {
    record("feishu-bots 字段契约", "FAIL", e.message);
  }

  // 7. 房间事件：GET /api/collaboration/rooms/{roomId}/events?afterSeq=0，校验 seq 单调递增
  if (!ROOM_ID) {
    record("room-events seq 单调", "SKIP", "未设置 ROOM_ID");
  } else {
    try {
      const events = await api(`/api/collaboration/rooms/${encodeURIComponent(ROOM_ID)}/events?afterSeq=0`);
      if (!Array.isArray(events) || events.length === 0) {
        record("room-events seq 单调", "SKIP", "无事件");
      } else {
        const ref = (await fixture("room-events.json")).events[0];
        const missing = missingKeys(ref, events[0]);
        const monotonic = events.every((e, i) => i === 0 || e.seq > events[i - 1].seq);
        record("room-events 字段契约", missing.length ? "FAIL" : "PASS", missing.length ? "缺字段 " + missing.join(",") : `${events.length} 条`);
        record("room-events seq 单调", monotonic ? "PASS" : "FAIL", monotonic ? "" : "seq 非递增");
      }
    } catch (e) {
      record("room-events seq 单调", "FAIL", e.message);
    }
  }

  const failed = results.filter((r) => r.status === "FAIL");
  console.log(`\n${results.filter((r) => r.status === "PASS").length} passed, ${failed.length} failed, ${results.filter((r) => r.status === "SKIP").length} skipped`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error("契约校验执行失败：" + e.message);
  process.exit(1);
});
