# wanze

**A telemetry probe for [AsteroidOS](https://asteroidos.org/) watches that
records while nobody is watching.**

Install it and forget it. wanze sits on the watch, takes a sample whenever the
watch happens to be awake, and hands the whole trace over the next time the
watch is docked. It is cheap enough to leave on a watch you are wearing.

## The name

*Wanze* is German for **bug** — both the insect and the listening device.
English collapses those two with "defect", which is why `bug` would be an
unusable package name and `wanze` is not: nobody has to wonder what "the bug is
reporting correctly" means.

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
wake after the interval has passed. `AccuracySec` is deliberately sloppy
(5 minutes) so systemd can coalesce the firing with wakeups that were going to
happen anyway.

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

One CSV row per sample, in `/var/log/wanze.csv`:

| column | |
|---|---|
| `epoch` | wall clock — **not** to be trusted, see below |
| `uptime` | the timing source; a **drop** between rows is a reboot |
| `current_ua`, `capacity`, `status`, `voltage_uv`, `temp` | the battery |
| `charger` | whether anything is feeding it |
| `backlight` | screen-on proxy, so drain becomes attributable |
| `cpu_online`, `cpu_freq`, `load1` | what the watch was doing |
| `gauge` | which power-supply node was read, **by name** |
| `schema` | so a column change can never be read as a behaviour change |

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

[asteroid-docking-bay](https://github.com/moWerk/asteroid-docking-bay) can do
this, harvest the trace and read it back — `asteroid_docking_bay/wanze.py`.

## Reading a trace honestly

- **A file of zeros is "no instrument", not "no drain".** Some watches report
  `current_now = 0` forever; nemo is one.
- **The current sign convention is not shared between watches.** In the
  identical state — Full, on charge — one watch reports `+488 µA` and another
  `-468 µA`. Direction has to be learned per watch before any comparison.
- **Readings are noisy.** A few minutes is not a measurement.
- **A gap is sleep, a negative uptime step is a reboot.** Never compute elapsed
  time across the latter.

## Status

**v0.1 — early.** The sampler runs, the timer behaves, and the host side parses
and analyses. What is **not** yet established is the thing that matters most:

> **wanze has not yet proven that its own battery cost is negligible.**

Until an A/B on one watch — wanze installed versus not, refereed by a real
drain test — shows the overhead sits inside the noise, every number this
produces should be treated as provisional. An instrument that changes what it
measures is worse than no instrument, and this one would do it to itself.

Licensed **GPL-3.0-or-later**, matching the other AsteroidOS components.
