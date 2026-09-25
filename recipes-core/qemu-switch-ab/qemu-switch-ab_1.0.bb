SUMMARY = "A/B slot handling for the emulated QEMU switch"
DESCRIPTION = "Confirms a freshly updated slot to EFI Boot Guard once the \
system is up, and brings the environment tools the confirmation uses. The \
environments live in BGENV.DAT on the config partitions BOOT0 and BOOT1."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://qemu-switch-ab-confirm"

S = "${UNPACKDIR}"

inherit allarch update-rc.d

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${S}/qemu-switch-ab-confirm ${D}${sysconfdir}/init.d/qemu-switch-ab-confirm
}

# Last in rc5.d, after clixon-backend (S05) and clixon-restconf (S35).
INITSCRIPT_NAME = "qemu-switch-ab-confirm"
INITSCRIPT_PARAMS = "defaults 99"

# bg_printenv and bg_setenv.
RDEPENDS:${PN} = "busybox efibootguard-tools"
