SUMMARY = "Overlay root setup for the emulated QEMU switch"
DESCRIPTION = "/sbin/overlay-init, started as init= by the kernel: stacks an \
overlay with its upper layer on the ext4 data partition on the squashfs root \
of the active slot and pivots into it before busybox init runs."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://overlay-init"

S = "${UNPACKDIR}"

inherit allarch

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${base_sbindir}
    install -m 0755 ${S}/overlay-init ${D}${base_sbindir}/overlay-init

    # Mount points. The squashfs cannot grow them at boot.
    install -d ${D}/overlay ${D}/rom ${D}/mnt
}

FILES:${PN} = "${base_sbindir}/overlay-init /overlay /rom /mnt"

# mount (ext4, overlay, --move) and pivot_root, both from busybox here.
RDEPENDS:${PN} = "busybox"
