"use client";

import { Badge } from "@nicolassancho/ui";
import { useSyncExternalStore } from "react";

import { hours } from "@/content/hours";
import { isOpen } from "@/lib/open-status";

// Wakes at each minute boundary. Timers are throttled in hidden tabs and frozen in the back-forward
// cache, so showing the page again catches up and re-arms.
function subscribe(onChange: () => void) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const arm = () => {
    clearTimeout(timer);
    timer = setTimeout(update, 60_000 - (Date.now() % 60_000));
  };
  const update = () => {
    onChange();
    arm();
  };
  arm();
  document.addEventListener("visibilitychange", update);
  window.addEventListener("pageshow", update);
  return () => {
    clearTimeout(timer);
    document.removeEventListener("visibilitychange", update);
    window.removeEventListener("pageshow", update);
  };
}

// A stable snapshot between ticks: the minute, not the millisecond.
const currentMinute = () => Math.floor(Date.now() / 60_000);
// The static HTML must hold no status: it would be the build's, and differ from the visitor's.
const noMinute = () => null;

export function OpenStatus({
  openLabel,
  closedLabel,
}: {
  openLabel: string;
  closedLabel: string;
}) {
  const minute = useSyncExternalStore(subscribe, currentMinute, noMinute);
  const open = minute !== null && isOpen(hours, new Date(minute * 60_000));
  return (
    // One Badge line, in rem, so the space is reserved before the status arrives at any text size.
    <div data-open-status className="min-h-7">
      {minute !== null && (
        <Badge variant={open ? "success" : "neutral"} dot>
          {open ? openLabel : closedLabel}
        </Badge>
      )}
    </div>
  );
}
