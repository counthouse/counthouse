import assert from "node:assert/strict";
import { test } from "node:test";

import { hours } from "../content/hours.ts";
import { formatTime, groupDays } from "./hours-list.ts";
import type { WeeklyHours } from "./open-status.ts";

test("groups the clinic's week into weekdays, Saturday and Sunday", () => {
  assert.deepEqual(
    groupDays(hours).map(({ from, to }) => [from, to]),
    [
      [1, 5],
      [6, 6],
      [7, 7],
    ],
  );
});

test("keeps a week with no two equal neighbours as seven groups", () => {
  const week: WeeklyHours = {
    timeZone: "UTC",
    days: {
      1: [[0, 1]],
      2: [],
      3: [[0, 1]],
      4: [],
      5: [[0, 1]],
      6: [],
      7: [[0, 2]],
    },
  };
  assert.equal(groupDays(week).length, 7);
});

test("formats a 24-hour time without a leading zero in both languages", () => {
  for (const locale of ["es", "pt"] as const) {
    assert.equal(formatTime(540, locale), "9:00");
    assert.equal(formatTime(1140, locale), "19:00");
    assert.equal(formatTime(0, locale), "0:00");
  }
});
