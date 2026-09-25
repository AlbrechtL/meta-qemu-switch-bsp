FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# The distro (poky-tiny) uses musl, which efibootguard is not built against
# upstream.
SRC_URI += "file://0001-w83627hf_wdt-build-with-musl.patch"

# bg_setenv/bg_printenv parse their options with argp, which glibc has built
# in and musl does not. configure has no check for it; pass the library in
# LIBS, which ends up behind the objects on every link line.
DEPENDS:append:libc-musl:class-target = " argp-standalone"
EXTRA_OECONF:append:libc-musl:class-target = " LIBS=-largp"
