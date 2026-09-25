FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# kernel-yocto looks the BSP definition up by KMACHINE, which defaults to
# MACHINE. The yocto-kernel-cache knows qemux86-64 (common-pc-64-tiny.scc:
# PCI, EFI, the usual PC drivers), not this machine.
KMACHINE:qemu-switch = "qemux86-64"

# What the tiny kernel type leaves out and the switch needs, shipped as kernel
# metadata so scc resolves the .scc and its .cfg fragments as one unit.
SRC_URI:append:qemu-switch = " file://qemu-switch-kmeta;type=kmeta;destsuffix=qemu-switch-kmeta"
KERNEL_FEATURES:append:qemu-switch = " features/qemu-switch/qemu-switch.scc"

# The default of 1 passes --classify to symbol_why.py, which hides most of
# what a tiny base silently drops. At 2 every requested-but-missing symbol is
# reported in .kernel-meta/cfg/mismatch.txt.
KCONF_AUDIT_LEVEL:qemu-switch = "2"
