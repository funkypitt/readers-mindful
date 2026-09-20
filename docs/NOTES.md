# Reader's Mindful Tool — notes

Reference material moved out of the README.

## Ringing on time, no matter what

The same hardening as Retreat Timer:

- every bowl is armed with `AlarmManager.setAlarmClock()`, the one alarm type Android
  guarantees will fire at the exact time even in Doze or deep sleep;
- the alarm wakes a **foreground service** (type mediaPlayback) that holds a partial wake
  lock until the bowl has sounded, and stays up between the bowls of a running session so
  the OS never kills it; its silent notification carries a Stop action per function;
- the recording plays on the **alarm stream**, through silent mode and Do-Not-Disturb;
- everything is **re-armed after a reboot**, a time or time-zone change, and an app update:
  a running meditation carries on if its end is still ahead, a reminder stays on its grid;
- a "dead" session (the alarm was dropped and nothing re-armed it) is ended rather than shown
  as a countdown that never reaches zero;
- the settings offer the battery-optimisation exemption in one tap.

## Home screen

* **Standard widget** for any launcher, configured when placed: both functions stacked, or
  only one. Each line is minus · minutes · plus · ▶; while it runs it shows a live countdown
  (a Chronometer, no refresh needed) and ■; the meditation draws a thin progress rule.
* **Reader's Launcher tile** ("mindful"): both functions with a sideways swipe between them,
  or one; tap the words to change the minutes, ▶ / ■ to start and stop. Read through a
  signature-protected provider (`content://com.freedomfighter.readersmindful/state`); the
  launcher drives the exported `BellService` directly.
