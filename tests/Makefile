GCC=riscv64-unknown-elf-gcc
OBJDUMP=riscv64-unknown-elf-objdump
CFLAGS=-mcmodel=medany -std=gnu99 -O2 -fno-common -fno-builtin-printf -Wall
LDFLAGS=-static -nostdlib -nostartfiles -lgcc
COMMON=./common

PROGRAMS ?= Test

default: $(addsuffix .riscv,$(PROGRAMS))

dumps: $(addsuffix .dump,$(PROGRAMS))

$(COMMON)/%.o: $(COMMON)/%.S
	$(GCC) $(CFLAGS) -D__ASSEMBLY__=1 -c $< -o $@

$(COMMON)/%.o: $(COMMON)/%.c $(COMMON)/mmio.h
	$(GCC) $(CFLAGS) -c $< -o $@

$(PROGRAMS).o: $(PROGRAMS).c 
	$(GCC) $(CFLAGS) -c $< -o $@

%.riscv: $(PROGRAMS).o $(COMMON)/crt.o $(COMMON)/syscalls.o $(COMMON)/link.ld
	$(GCC) -T $(COMMON)/link.ld $(LDFLAGS) $< $(COMMON)/crt.o $(COMMON)/syscalls.o -o $@

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

clean:
	rm -f *.o *.dump

all_clean:
	rm -f *.o *.dump *.riscv
