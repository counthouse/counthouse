/** Monday = 1 … Sunday = 7, as in ISO 8601. */
export type Day = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** Minutes after midnight in the clinic's time zone, `from` included and `to` excluded. */
export type Range = readonly [from: number, to: number];

export type WeeklyHours = {
  /** An IANA time zone. */
  timeZone: string;
  days: Record<Day, readonly Range[]>;
};

export const days: readonly Day[] = [1, 2, 3, 4, 5, 6, 7];

const weekday: Record<string, Day> = {
  Mon: 1,
  Tue: 2,
  Wed: 3,
  Thu: 4,
  Fri: 5,
  Sat: 6,
  Sun: 7,
};

/** The weekday and minute of `instant` on a wall clock in `timeZone`. */
export function wallClock(
  instant: Date,
  timeZone: string,
): { day: Day; minute: number } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(instant);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";
  const day = weekday[part("weekday")];
  if (!day) throw new Error(`Unknown weekday in ${timeZone}`);
  return { day, minute: Number(part("hour")) * 60 + Number(part("minute")) };
}

export function isOpen(hours: WeeklyHours, instant: Date): boolean {
  const { day, minute } = wallClock(instant, hours.timeZone);
  return hours.days[day].some(([from, to]) => minute >= from && minute < to);
}
