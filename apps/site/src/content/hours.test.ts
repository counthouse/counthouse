import assert from "node:assert/strict";
import { test } from "node:test";

import { days } from "../lib/open-status.ts";
import { hours } from "./hours.ts";

test("each day's ranges are within the day, non-empty, sorted and apart", () => {
  for (const day of days) {
    let end = -1;
    for (const [from, to] of hours.days[day]) {
      assert.ok(Number.isInteger(from) && Number.isInteger(to), `day ${day}`);
      assert.ok(
        from > end,
        `day ${day}: ${from} starts before ${end} ends or touches it`,
      );
      assert.ok(from < to && to <= 24 * 60, `day ${day}: ${from}-${to}`);
      end = to;
    }
  }
});

test("the time zone is one Intl knows", () => {
  assert.doesNotThrow(
    () => new Intl.DateTimeFormat("en", { timeZone: hours.timeZone }),
  );
});
