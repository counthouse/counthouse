import { groupDays } from "./hours-list.ts";
import type { Day, WeeklyHours } from "./open-status.ts";

export type OpeningHoursSpecification = {
  "@type": "OpeningHoursSpecification";
  dayOfWeek: string[];
  opens: string;
  closes: string;
};

const dayNames: Record<Day, string> = {
  1: "Monday",
  2: "Tuesday",
  3: "Wednesday",
  4: "Thursday",
  5: "Friday",
  6: "Saturday",
  7: "Sunday",
};

// Google writes the end of the day as 23:59 and a closed day as 00:00 to 00:00
// (developers.google.com/search/docs/appearance/structured-data/local-business).
const time = (minutes: number) =>
  minutes >= 1440
    ? "23:59"
    : `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;

/** schema.org opening hours: one entry per shift for each group of days with the same shifts. */
export function openingHoursSpecification(
  hours: WeeklyHours,
): OpeningHoursSpecification[] {
  return groupDays(hours).flatMap(({ from, to, ranges }) => {
    const dayOfWeek = Array.from(
      { length: to - from + 1 },
      (_, i) => dayNames[(from + i) as Day],
    );
    const shifts: (readonly [number, number])[] =
      ranges.length > 0 ? [...ranges] : [[0, 0]];
    return shifts.map(([opens, closes]) => ({
      "@type": "OpeningHoursSpecification" as const,
      dayOfWeek,
      opens: time(opens),
      closes: time(closes),
    }));
  });
}
