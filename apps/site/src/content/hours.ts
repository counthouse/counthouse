import type { WeeklyHours } from "../lib/open-status.ts";

// The only copy of the opening hours. Monday = 1; minutes after midnight.
export const hours: WeeklyHours = {
  timeZone: "America/Montevideo",
  days: {
    1: [
      [540, 720],
      [900, 1140],
    ],
    2: [
      [540, 720],
      [900, 1140],
    ],
    3: [
      [540, 720],
      [900, 1140],
    ],
    4: [
      [540, 720],
      [900, 1140],
    ],
    5: [
      [540, 720],
      [900, 1140],
    ],
    6: [[540, 780]],
    7: [],
  },
};
