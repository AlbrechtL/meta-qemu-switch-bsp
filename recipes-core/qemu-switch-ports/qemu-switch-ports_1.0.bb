SUMMARY = "Front port names for the emulated QEMU switch"
DESCRIPTION = "Renames the virtio-net devices to lan1..lan8 at boot, in PCI \
order, the names the front ports have on the real switches."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://qemu-switch-ports"

S = "${UNPACKDIR}"

inherit allarch update-rc.d

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/qemu-switch-ports ${D}${sysconfdir}/init.d/qemu-switch-ports
}

# Before anything touches the network: clixon-backend is S05.
INITSCRIPT_NAME = "qemu-switch-ports"
INITSCRIPT_PARAMS = "defaults 03"

# ip (busybox).
RDEPENDS:${PN} = "busybox"
