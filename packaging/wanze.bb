# SPDX-License-Identifier: GPL-3.0-or-later
# SPDX-FileCopyrightText: 2026 Timo Könnecke (moWerk) <mo@mowerk.net>
#
# Reference recipe. Build against a checkout of this repo:
#
#     devtool add wanze /path/to/a-d-b-wanze
#     devtool build wanze
#     bitbake -c package_write_ipk wanze
#
# Note the last line: `devtool build` stops after do_packagedata, reports
# success, and writes no package at all.

SUMMARY = "Opportunistic telemetry probe for AsteroidOS watches"
DESCRIPTION = "Samples battery, screen and CPU state whenever the watch is \
already awake, so a watch can be measured while undocked without the probe \
waking it. The trace is harvested at the dock."
HOMEPAGE = "https://github.com/moWerk/a-d-b-wanze"
# Set explicitly: without it the distro-wide default lands in the ipk's
# control file and misattributes the package to another maintainer.
MAINTAINER = "Timo Könnecke (moWerk) <mo@mowerk.net>"
LICENSE = "GPL-3.0-or-later"
LIC_FILES_CHKSUM = "file://LICENSE;md5=84dcc94da3adb52b53ae4fa38fe49e5d"

SRC_URI = ""

# No compiler, no Qt: a shell sampler and two unit files. Deliberately so —
# the probe has to be defensible as something you leave on a worn watch.
inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "wanze.timer"
# The timer is what gets enabled; the service is triggered by it and must not
# be enabled on its own, or it would run once at boot and never again.
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/src/wanze-sample ${D}${bindir}/wanze-sample

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/src/wanze.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${S}/src/wanze.timer   ${D}${systemd_system_unitdir}/
}

FILES:${PN} += "${systemd_system_unitdir}"

RDEPENDS:${PN} = "systemd"
