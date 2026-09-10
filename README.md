# Reader's Mindful Tool

A black-and-white, text-only Android app for three things a meditator asks of a bowl,
built on the reliable bell mechanics of [Retreat Timer](https://github.com/funkypitt/retreat-timer)
and [Retreat Walk](https://github.com/funkypitt/retreat-walk). Part of the
[Reader's](https://github.com/funkypitt/readers-launcher) family: white on black or black on
white, no icons, no colours, the fewest taps possible.

* **A bowl every X minutes** — one strike, again and again, until you stop it (the Retreat
  Walk reminder). Bowls are timed from the start of the session, never from the previous
  bowl, so they never drift off the clock.
* **Meditate X minutes** — silence, then three bowls at the end, with a hairline showing how
  far the sit has come.
* **Bowls at set times** — every day at the times you choose, one strike or three (the basic
  Retreat Timer).

The bowl is one global choice among Retreat Timer's five loudness-matched recordings
(Tibetan bowl E♭, singing bowl, gong bowl, Satipanya, enpleineconscience.ch) — or **your own
mp3 or wav file**, which stands in for the single strike and plays three times over for the
three-bowl variant. Tap a bowl in the settings to hear it.

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

## Languages

English, French, German, Spanish, Portuguese, Russian.

## Building

```
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleDebug
```

minSdk 26, targetSdk 34. Bell recordings in `app/src/main/res/raw` are those of Retreat Timer.

## Licence

MIT.
