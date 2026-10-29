import { useEffect, useState } from 'react';

/**
 * `flag`, but turning true only once it has stayed true for `delayMs`; it turns false at once. For a notice that
 * should not flash for a state that usually passes quickly.
 */
export function useDelayedFlag(flag: boolean, delayMs: number): boolean {
  const [elapsed, setElapsed] = useState(false);

  useEffect(() => {
    if (!flag) {
      return;
    }
    const timer = window.setTimeout(() => setElapsed(true), delayMs);
    return () => {
      window.clearTimeout(timer);
      setElapsed(false);
    };
  }, [flag, delayMs]);

  return flag && elapsed;
}
