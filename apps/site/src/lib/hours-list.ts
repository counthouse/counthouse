import { htmlLang, type Locale } from "../i18n/locales.ts";
import { days, type Day, type Range, type WeeklyHours } from "./open-status.ts";

export type DayGroup = { from: Day; to: Day; ranges: readonly Range[] };

const sameRanges = (a: readonly Range[], b: readonly Range[]) =>
  a.length === b.length &&
  a.every(([f, t], i) => f === b[i]![0] && t === b[i]![1]);

/** Consecutive days with the same ranges, Monday first. */
export function groupDays(hours: WeeklyHours): DayGroup[] {
  const groups: DayGroup[] = [];
  for (const day of days) {
    const last = groups.at(-1);
    if (last && sameRanges(last.ranges, hours.days[day])) last.to = day;
    else groups.push({ from: day, to: day, ranges: hours.days[day] });
  }
  return groups;
}

/** "9:00", "19:00": a 24-hour clock in the page's language. */
export function formatTime(minutes: number, locale: Locale): string {
  return new Intl.DateTimeFormat(htmlLang[locale], {
    hour: "numeric",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: "UTC",
  }).format(Date.UTC(2000, 0, 1, 0, minutes));
}
