# meta-qemu-switch-bsp

> **⚠️ Proof of concept.** This project is a proof of concept, created with
> the help of AI. It has not undergone thorough review or hardening, and
> should not be assumed suitable for production use.

Yocto BSP layer for an **emulated 8 port Ethernet switch on QEMU x86-64**: a
q35 PC with UEFI firmware (OVMF), [EFI Boot Guard](https://github.com/siemens/efibootguard)
as bootloader, eight virtio-net front ports and a virtio disk with A/B root
filesystems. It is built on oe-core's `qemux86-64` machine and
[meta-efibootguard](https://github.com/siemens/meta-efibootguard).

Part of **Ethernet Switch OS**. The build lives in
[ethernet-switch-os](https://github.com/AlbrechtL/ethernet-switch-os), which
checks this layer out with [kas](https://kas.readthedocs.io/) and builds the
images (`kas/board/qemux86-64-switch.yml`), and runs them
(`scripts/x86-64-q35-qemu`). The userspace comes from
[meta-ethernet-switch-os](https://github.com/AlbrechtL/meta-ethernet-switch-os).

The layer is hardware only: it boots to a shell without
meta-ethernet-switch-os and knows nothing about it. The distro layer adds its
userspace to `qemu-switch-image` through a `.bbappend`, as it does for the
other BSPs.

## Machine

| Machine | Emulates |
|---|---|
| `qemux86-64-switch` | q35 PC, OVMF, i6300esb watchdog, virtio disk, 8 × virtio-net |

`MACHINEOVERRIDES` gets `qemux86-64`, so oe-core's `qemux86-64` settings and
`linux-yocto-tiny` apply, and `qemu-switch` for everything specific to this
layer (`COMPATIBLE_MACHINE = "^qemu-switch$"`).

## Disk layout

`qemu-switch-image` builds a GPT disk image (`files/wic/qemu-switch-ab.wks.in`):

| Partition | Label | Content | Size |
|---|---|---|---|
| `/dev/vda1` | `efi` | ESP: `EFI/BOOT/bootx64.efi`, EFI Boot Guard | 32 MiB |
| `/dev/vda2` | `BOOT0` | config partition of slot A: `bzImage`, `BGENV.DAT`, `EFILABEL` | 32 MiB |
| `/dev/vda3` | `BOOT1` | config partition of slot B: the same | 32 MiB |
| `/dev/vda4` | | slot A: squashfs root filesystem | 128 MiB |
| `/dev/vda5` | | slot B: squashfs root filesystem | 128 MiB |
| `/dev/vda6` | `data` | ext4, upper layer of the root overlay, shared by both slots | 128 MiB |

Both slots start out with the same squashfs. The kernel is not in the
squashfs but on the config partitions, one per slot, which is where EFI Boot
Guard loads it from.

## Boot

1. OVMF starts `EFI/BOOT/bootx64.efi` from the ESP: EFI Boot Guard.
2. EFI Boot Guard reads the boot environments (`BGENV.DAT`) of BOOT0 and
   BOOT1 and picks the one with the **highest revision**. It arms the
   i6300esb watchdog with the environment's `watchdog_timeout_sec` (60 s)
   and starts `kernelfile` (`C:BOOT0:bzImage`, i.e. `bzImage` on the
   partition whose `EFILABEL` says `BOOT0`) with `kernelparams`.
3. The command line names the slot: `root=/dev/vda4` in BOOT0,
   `root=/dev/vda5` in BOOT1, plus `init=/sbin/overlay-init panic=5`.
4. `overlay-init` (`qemu-switch-overlay-init`) mounts `/dev/vda6` and stacks
   an overlay on the squashfs, as on the other BSPs: the squashfs under
   `/rom`, the upper layer under `/overlay`. Then busybox init.
5. `rcS.d/S00watchdog.sh` (meta-efibootguard's busybox bbappend) starts
   busybox `watchdog`, which feeds `/dev/watchdog` from then on.
6. `qemu-switch-ports` (S03) renames the virtio-net devices to `lan1`..`lan8`
   in PCI order, the names the front ports have on the real switches.
   `scripts/x86-64-q35-qemu` puts front port N into PCI slot `0x10+N-1`.
7. `qemu-switch-ab-confirm` (S99) confirms a freshly updated slot, see below.

BOOT0 starts with revision 2 and BOOT1 with revision 1, so a new disk boots
slot A.

## A/B update and rollback

EFI Boot Guard keeps the update state in the environments. `ustate` is
`OK` (0), `INSTALLED` (1), `TESTING` (2) or `FAILED` (3).

An update (the `.swu` in meta-ethernet-switch-os, SWUpdate with
`CONFIG_BOOTLOADER_EBG`) writes the slot that is not running, the kernel on
that slot's config partition, and then, through libebgenv, a **new
environment**: a copy of the running one with `kernelfile` and
`kernelparams` for the new slot and the next revision, stored in place of the
environment with the lower revision. That one is always the idle slot's, so
slot A stays paired with BOOT0 and slot B with BOOT1. Its `ustate` is
`INSTALLED`.

On the next boot EFI Boot Guard picks the new environment, as it has the
highest revision, sets it to `TESTING` and boots it. Then:

- **The system comes up**: `qemu-switch-ab-confirm` runs `bg_setenv -c` at the
  end of rc5.d, which sets `ustate` back to `OK`. The slot stays.
- **It does not**: any reset before the confirmation -- the watchdog firing
  because the system hangs, `panic=5` after a kernel panic, a reboot -- finds
  the environment still in `TESTING`. EFI Boot Guard marks it `FAILED`, sets
  its revision to 0 and boots the other environment: the previous slot, with
  the configuration from the shared data partition.

The next update goes to the failed slot again, as its environment now has the
lowest revision.

The state is visible on the switch:

```sh
bg_printenv        # both environments
bg_printenv -c     # the one that booted
```

## Kernel

`linux-yocto-tiny` for `qemux86-64` (KMACHINE, the `common-pc-64-tiny` BSP
of the yocto-kernel-cache) plus `features/qemu-switch/qemu-switch.scc`: the
virtio devices, EFI, the i6300esb watchdog, the file systems of the disk
layout, the VLAN-aware bridge, and what the tiny kernel type leaves out and
the userspace needs (multiuser, epoll, eventfd, ...). No modules.

## efibootguard on musl

The ethernet-switch-os distro is poky-tiny, i.e. musl, which meta-efibootguard
does not build against as it is. `recipes-bsp/efibootguard` fixes that:

- a patch that gives the w83627hf watchdog driver an `outb_p()`, which musl's
  `<sys/io.h>` lacks, and
- `argp-standalone` for `bg_setenv`/`bg_printenv`, which use glibc's
  built-in argp.

## Using the layer

Layer dependencies: `core` (openembedded-core) and `efibootguard`
(meta-efibootguard, its `master` branch for wrynose). The ethernet-switch-os
kas files set it all up; by hand:

```
MACHINE = "qemux86-64-switch"
bitbake qemu-switch-image ovmf qemu-helper-native
```

and boot `qemu-switch-image-qemux86-64-switch.rootfs.wic` with OVMF and an
i6300esb, as `scripts/x86-64-q35-qemu` in ethernet-switch-os does. EFI Boot Guard
refuses to boot without a watchdog it can arm.

## License

MIT, see [LICENSE](LICENSE).
