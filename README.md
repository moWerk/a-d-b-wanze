# a-d-b-wanze

**A battery-life and sleep-behaviour probe for [AsteroidOS](https://asteroidos.org/)
watches — a porting instrument, for measuring your own hardware.**

Install it and forget it. wanze sits on the watch, takes a sample whenever the
watch happens to be awake anyway, and hands the whole trace over the next time
the watch is docked. It is cheap enough to leave on a watch you are wearing,
which is the only way to find out what that watch does all day.

## Scope — what it is for, and what it will not do

wanze is a **porting and development instrument**. It exists to answer one
class of question about a device you are bringing AsteroidOS up on: *why does
this watch drain, and does it ever actually sleep?* Neither question can be
answered at the dock, because a docked watch is charging and awake. They have
to be measured on the watch, undocked, while it is doing nothing — which is
exactly when no debugger is attached and nobody is looking at it.

It records **device and kernel telemetry only**. The CSV header is the complete
list; there is nothing else in it:

- the battery — current, capacity, voltage, temperature, charger presence
- the screen — backlight brightness and the panel's own power state
- the kernel's suspend counters, and optionally the name of the wakeup source
- CPU cores online, CPU frequency, 1-minute load average
- `/proc/uptime`, and the wall clock as an anchor

It reads **no sensors**: no accelerometer, no heart rate, no microphone, no
location. It reads **no user content**: no notifications, messages, contacts,
calendar entries, app data or screen contents. It does no network scanning and
identifies nobody. Every column is a property of the hardware and the kernel —
there is no column that is about a person.

The trace stays on the watch until you fetch it over USB at the cradle. Network
upload is **off by default**, and when you switch it on it goes to an address
you set yourself (`WANZE_UPLOAD_TO`) — normally your own laptop.

It is meant to be installed by the owner of a watch, on that owner's own watch,
and it does not hide. `/usr/bin/wanze-sample`, `/etc/systemd/system/wanze.timer`
and `/var/log/wanze.csv` are ordinary paths under their own names, `systemctl
list-timers` shows it, and `systemctl disable --now wanze.timer` ends it.

## The name

*Wanze* is German for **bug** — both the insect and the listening device.
English collapses those two with "defect", which is why `bug` would be an
unusable package name and `wanze` is not: nobody has to wonder what "the bug is
reporting correctly" means.

The repository carries the `a-d-b-` prefix of its host tool,
[asteroid-docking-bay](https://github.com/moWerk/asteroid-docking-bay), so that
the scope travels with the name. A pun should not have to be explained twice.

The metaphor turned out tighter than intended, because **host** does double
duty. A bedbug lives in the furniture, feeds unnoticed while you sleep, and is
found only when you go looking. That is the architecture: it lives in the
watch, takes what it needs while the watch is busy with something else, adds
nothing of its own, and gives everything up when probed.

Pronounced *VAHN-tseh*. You will say *whaan-seee*, and that is fine.

## The one rule

> **Never wake the watch. Record only when something else already did.**

This is the whole design. On a watch the expensive part of a periodic job is
not the reading — a handful of sysfs reads costs nothing — it is the **wakeup**.
A probe that wakes the CPU every minute to record the battery is draining the
battery it claims to measure.

So wanze is not a daemon. It is a `systemd` timer with **`WakeSystem=false`**,
firing a `Type=oneshot` service that appends one line and exits. Such a timer
arms no RTC alarm: it fires when the watch is already awake, or on the next
wake after a scheduled slot has passed. `AccuracySec` is deliberately sloppy
(5 minutes) so systemd can coalesce the firing with wakeups that were going to
happen anyway.

The schedule is a **realtime** `OnCalendar=*:0/5`, and that is not an
arbitrary choice. systemd's monotonic timers (`OnUnitActiveSec=` and friends)
run on a clock that pauses in suspend, so a 5-minute monotonic interval means
every 5 *awake* minutes. On a watch that sleeps well — the whole point of
measuring — that is barely any samples at all: wanze shipped this way, and on
a phone sleeping 94% of a 39-minute window it recorded one row and then
nothing. A realtime clock keeps running while the watch sleeps, and systemd
guarantees that a calendar slot which passed repeatedly during one sleep
produces a *single* activation on the next wake. That is exactly the wanted
behaviour: one catch-up sample, never a burst backfilling the gap.

`WakeSystem=false` is what keeps the realtime clock safe to use here. With it
enabled, a calendar slot elapsing during suspend would *wake the watch* — which
would make wanze the thing it was built not to be. The clock changed; the
promise did not.

Nothing is resident between samples. There is no loop to hold the CPU, no
wakelock to leak, and no process to survive a bad update.

## Gaps are data

The obvious objection is that samples become irregular and full of holes. That
is the second half of the design, not a defect.

**A gap means the watch genuinely slept through it.** That is precisely the
standby behaviour you want to measure, and precisely what a regular sampler
destroys by waking through it. A trace that is 92% holes is describing a very
healthy watch.

Gap analysis happens on the host, and it runs on **`uptime`, never on the wall
clock** — see below for why that is not a stylistic preference.

## What it records

One CSV row per sample, in `/var/log/wanze.csv`. Schema 2:

| column | |
|---|---|
| `epoch` | wall clock — **not** to be trusted, see below |
| `uptime` | the timing source; a **drop** between rows is a reboot |
| `current_ua`, `capacity`, `status`, `voltage_uv`, `temp` | the battery |
| `charger` | whether anything is feeding it |
| `backlight` | screen-on proxy, resolved per watch, so drain becomes attributable |
| `panel_power_state` | what the panel itself says — the ground truth `backlight` only approximates |
| `cpu_online`, `cpu_freq`, `load1` | what the watch was doing |
| `suspend_success`, `suspend_fail` | the kernel's own suspend counters; both flat means suspend was never attempted |
| `wakeup_source` | what woke it, when `WANZE_WAKEUP_SOURCES=1` — off by default |
| `reason` | why this row exists, when it was not the ordinary timer |
| `gauge` | which power-supply node was read, **by name** |
| `schema` | so a column change can never be read as a behaviour change |

A column the watch does not offer is reported as **unavailable**, never as `0`.
A missing instrument and a measured zero are different findings.

The buffer is a bounded ring, trimmed with hysteresis so a watch left undocked
for months cannot fill the rootfs, and so the rewrite happens rarely.

## Do not trust the watch's clock

Measured on live hardware while building this, on two watches that were
otherwise recording perfectly:

| watch | wall clock |
|---|---|
| `catfish` | **130 days behind** the host |
| `nemo` | **~2 hours ahead** of the host |

An absolute timestamp from a watch means nothing on its own. `uptime` cannot be
moved by a clock adjustment, so every timing figure comes from it, and the wall
clock is kept only as an anchor. Comparing the two deltas is itself a check: if
they disagree within one boot, the clock was stepped mid-trace — an NTP sync,
typically — which would silently corrupt anything computed from `epoch`.

The host reports the skew rather than quietly correcting it. A timestamp that
has been fixed up looks trustworthy; an obviously wrong one does not.

## Install

wanze is a sampler plus two unit files:

```sh
install -m755 src/wanze-sample /usr/bin/wanze-sample
install -m644 src/wanze.service src/wanze.timer /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now wanze.timer
```

To remove it: `systemctl disable --now wanze.timer`, then delete those three
files and `/var/log/wanze.csv`.

`packaging/wanze.bb` builds the same thing as an ipk for an AsteroidOS image.

[asteroid-docking-bay](https://github.com/moWerk/asteroid-docking-bay) can do
the install, harvest the trace and read it back — `asteroid_docking_bay/wanze.py`.

## Reading a trace honestly

- **A file of zeros is "no instrument", not "no drain".** Some watches report
  `current_now = 0` forever; nemo is one.
- **The current sign convention is not shared between watches.** In the
  identical state — Full, on charge — one watch reports `+488 µA` and another
  `-468 µA`. Direction has to be learned per watch before any comparison.
- **Readings are noisy.** A few minutes is not a measurement.
- **A gap is sleep, a negative uptime step is a reboot.** Never compute elapsed
  time across the latter.
- **`display_on_fraction` means nothing without `asleep_fraction`.** The timer
  never wakes the watch, so a watch whose screen never turns off is also a watch
  that never slept, and it has no gaps to show for it. The pair is the
  diagnosis; either number alone is not.

## Status

**v0.1 — early.** The sampler runs, the timer behaves, and the host side parses
and analyses. What is **not** yet established is the thing that matters most:

> **wanze has not yet proven that its own battery cost is negligible.**

Until an A/B on one watch — wanze installed versus not, refereed by a real
drain test — shows the overhead sits inside the noise, every number this
produces should be treated as provisional. An instrument that changes what it
measures is worse than no instrument, and this one would do it to itself.

Licensed **GPL-3.0-or-later**, matching the other AsteroidOS components.
