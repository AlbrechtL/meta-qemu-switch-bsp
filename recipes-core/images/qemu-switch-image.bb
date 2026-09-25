# Disk image for the emulated switch: EFI Boot Guard on the ESP, two config
# partitions with a kernel each, two squashfs root filesystem slots and a data
# partition for the overlay (files/wic/qemu-switch-ab.wks.in).
#
# Boots to a shell on its own. The distro layer adds its userspace from a
# .bbappend, as it does for the other BSPs' images.

SUMMARY = "A/B disk image for the emulated QEMU x86-64 switch"

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    qemu-switch-overlay-init \
    qemu-switch-ports \
    qemu-switch-ab \
    ${CORE_IMAGE_EXTRA_INSTALL} \
"

IMAGE_LINGUAS = " "

LICENSE = "MIT"

inherit core-image

COMPATIBLE_MACHINE = "^qemu-switch$"

# The squashfs is the content of both slots, and what an update writes. The
# .wic is the whole disk: scripts/qemu-switch boots a copy of it. The kernel
# is not in the root filesystem but on the config partitions, where EFI Boot
# Guard loads it from; wic takes it from DEPLOY_DIR_IMAGE.
# No read-only-rootfs feature: overlay-init makes / writable before busybox
# init runs.
IMAGE_FSTYPES = "squashfs-xz wic wic.bmap"
IMAGE_TYPEDEP:wic = "squashfs-xz"
WKS_FILE = "qemu-switch-ab.wks.in"
# EFI Boot Guard instead of the syslinux, grub-efi and systemd-boot oe-core
# builds for every x86 wic image; wic's recursive do_deploy dependency takes
# its bootloader from DEPLOY_DIR_IMAGE. The config partitions are written
# with bg_setenv, mkdosfs and mcopy from the native sysroot.
WKS_FILE_DEPENDS_BOOTLOADERS = "efibootguard"
WKS_FILE_DEPENDS:append = " efibootguard-native dosfstools-native mtools-native"
do_image_wic[depends] += "virtual/kernel:do_deploy"

# The wks.in expands it.
WICVARS:append = " EFIBOOTGUARD_WATCHDOG_TIMEOUT"

# Same block size as the other BSPs' squashfs.
EXTRA_IMAGECMD:squashfs-xz = "-b 262144"

qemu_switch_fstab() {
    # overlay-init mounts /var/volatile itself and creates log/ and tmp/ in
    # it. Left in fstab, "mount -a" would stack an empty tmpfs over them.
    sed -i '\#[[:space:]]/var/volatile[[:space:]]#d' ${IMAGE_ROOTFS}${sysconfdir}/fstab
}
ROOTFS_POSTPROCESS_COMMAND += "qemu_switch_fstab;"

# The kernel has already mounted devtmpfs on /dev (CONFIG_DEVTMPFS_MOUNT), and
# overlay-init moves it into the overlay root. Mounting it again fails:
#   mount: mounting devtmpfs on /dev failed: Resource busy
qemu_switch_drop_devtmpfs_inittab() {
    sed -i '\#^::sysinit:/bin/mount -t devtmpfs devtmpfs /dev$#d' ${IMAGE_ROOTFS}${sysconfdir}/inittab
}
ROOTFS_POSTPROCESS_COMMAND += "qemu_switch_drop_devtmpfs_inittab;"
