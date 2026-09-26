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

## Documentation

**How to run it, the disk layout, the boot and the A/B update with rollback
are documented in the user guide, which is the only place for that
information:**
[QEMU x86-64 switch](https://albrechtl.github.io/ethernet-switch-os/installation/qemu-x86-64/).

## What the layer provides

- `qemu-switch-image` builds the GPT disk image from
  `files/wic/qemu-switch-ab.wks.in`: the ESP, the config partitions
  `BOOT0` and `BOOT1`, the two slots and `data`. BOOT0 starts with revision 2
  and BOOT1 with revision 1, so a new disk boots slot A.
- `qemu-switch-overlay-init` (`/sbin/overlay-init`) mounts `/dev/vda6` and
  stacks an overlay on the squashfs of the slot, as on the other BSPs.
- `rcS.d/S00watchdog.sh` (meta-efibootguard's busybox bbappend) starts
  busybox `watchdog`, which feeds `/dev/watchdog`.
- `qemu-switch-ports` (S03) renames the virtio-net devices to `lan1`..`lan8`
  in PCI order. `scripts/x86-64-q35-qemu` puts front port N into PCI slot
  `0x10+N-1`.
- `qemu-switch-ab-confirm` (S99) runs `bg_setenv -c` to confirm a freshly
  updated slot.

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
