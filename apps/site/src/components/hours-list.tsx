import { hours } from "@/content/hours";
import type { Strings } from "@/content/strings";
import type { Locale } from "@/i18n/locales";
import { formatTime, groupDays } from "@/lib/hours-list";

// A list, not a table: when the day and its hours do not fit side by side (a phone with large text), the
// hours wrap below the day.
export function HoursList({
  locale,
  strings,
}: {
  locale: Locale;
  strings: Strings;
}) {
  return (
    <dl data-hours className="flex max-w-sm flex-col gap-3">
      {groupDays(hours).map(({ from, to, ranges }) => (
        <div key={from} className="flex flex-wrap justify-between gap-x-4">
          <dt>{strings.dayGroup(from, to)}</dt>
          <dd className="ml-auto text-right">
            {ranges.length === 0
              ? strings.emergenciesOnly
              : ranges.map(([start, end]) => (
                  <span key={start} className="block">
                    {formatTime(start, locale)}
                    {/* Screen readers say the dash poorly: they hear the word instead. */}
                    <span aria-hidden="true">–</span>
                    <span className="sr-only">{strings.rangeTo}</span>
                    {formatTime(end, locale)}
                  </span>
                ))}
          </dd>
        </div>
      ))}
    </dl>
  );
}
