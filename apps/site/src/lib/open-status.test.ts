import assert from "node:assert/strict";
import { test } from "node:test";

import { hours } from "../content/hours.ts";
import { isOpen, type Day, type WeeklyHours } from "./open-status.ts";

// Montevideo has been UTC−3 all year since 2015.
const at = (local: string) => new Date(`${local}-03:00`);

test("runs in a visitor time zone other than the clinic's", () => {
  assert.equal(process.env.TZ, "Asia/Tokyo");
  assert.equal(new Date(0).getTimezoneOffset(), -540);
});

test("ranges include their start and exclude their end", () => {
  // 23 Nov 2026 is a Monday, 28 Nov a Saturday, 29 Nov a Sunday.
  const cases: [string, boolean][] = [
    ["2026-11-23T08:59", false],
    ["2026-11-23T09:00", true],
    ["2026-11-23T11:59", true],
    ["2026-11-23T12:00", false],
    ["2026-11-23T14:59", false],
    ["2026-11-23T15:00", true],
    ["2026-11-23T18:59", true],
    ["2026-11-23T19:00", false],
    ["2026-11-28T12:59", true],
    ["2026-11-28T13:00", false],
    ["2026-11-29T10:00", false],
    ["2026-11-29T23:59", false],
    ["2026-11-30T00:00", false],
  ];
  for (const [local, open] of cases)
    assert.equal(isOpen(hours, at(local)), open, local);
});

// An independent clock: fixed UTC−3, no Intl.
function oracle(week: WeeklyHours, instant: Date): boolean {
  const local = new Date(instant.getTime() - 3 * 3_600_000);
  const day = (((local.getUTCDay() + 6) % 7) + 1) as Day;
  const minute = local.getUTCHours() * 60 + local.getUTCMinutes();
  return week.days[day].some(([from, to]) => minute >= from && minute < to);
}

test("agrees with an independent clock on 10 000 random instants", () => {
  const start = Date.UTC(2026, 0, 1);
  const span = 4 * 365 * 24 * 3_600_000;
  for (let i = 0; i < 10_000; i++) {
    const instant = new Date(start + Math.floor(Math.random() * span));
    assert.equal(
      isOpen(hours, instant),
      oracle(hours, instant),
      instant.toISOString(),
    );
  }
});
