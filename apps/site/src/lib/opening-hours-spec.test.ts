import assert from "node:assert/strict";
import { test } from "node:test";

import { hours } from "../content/hours.ts";
import type { WeeklyHours } from "./open-status.ts";
import { openingHoursSpecification } from "./opening-hours-spec.ts";

const spec = (dayOfWeek: string[], opens: string, closes: string) => ({
  "@type": "OpeningHoursSpecification",
  dayOfWeek,
  opens,
  closes,
});
const weekdays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"];

test("writes the clinic's week as one entry per shift, Sunday closed", () => {
  assert.deepEqual(openingHoursSpecification(hours), [
    spec(weekdays, "09:00", "12:00"),
    spec(weekdays, "15:00", "19:00"),
    spec(["Saturday"], "09:00", "13:00"),
    spec(["Sunday"], "00:00", "00:00"),
  ]);
});

test("pads minutes, ends the day at 23:59 and marks a closed weekday", () => {
  const week: WeeklyHours = {
    timeZone: "UTC",
    days: {
      1: [[545, 1440]],
      2: [],
      3: [[0, 1440]],
      4: [[0, 1440]],
      5: [[0, 1440]],
      6: [[0, 1440]],
      7: [[0, 1440]],
    },
  };
  assert.deepEqual(openingHoursSpecification(week), [
    spec(["Monday"], "09:05", "23:59"),
    spec(["Tuesday"], "00:00", "00:00"),
    spec(
      ["Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
      "00:00",
      "23:59",
    ),
  ]);
});
