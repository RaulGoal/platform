// See LICENSE for license details.
#ifndef _RISCV_DEBUG_MODULE_H
#define _RISCV_DEBUG_MODULE_H

#include <set>

#include "devices.h"

class sim_t;

typedef struct {
  bool haltreq;
  bool resumereq;
  unsigned hartsel;
  bool hartreset;
  bool dmactive;
  bool ndmreset;
} dmcontrol_t;

typedef struct {
  bool impebreak;
  bool allnonexistant;
  bool anynonexistant;
  bool allunavail;
  bool anyunavail;
  bool allrunning;
  bool anyrunning;
  bool allhalted;
  bool anyhalted;
  bool allresumeack;
  bool anyresumeack;
  bool authenticated;
  bool authbusy;
  bool cfgstrvalid;
  unsigned version;
} dmstatus_t;

typedef enum cmderr {
    CMDERR_NONE = 0,
    CMDERR_BUSY = 1,
    CMDERR_NOTSUP = 2,
    CMDERR_EXCEPTION = 3,
    CMDERR_HALTRESUME = 4,
    CMDERR_OTHER = 7  
} cmderr_t;

typedef struct {
  bool busy;
  unsigned datacount;
  unsigned progbufsize;
  cmderr_t cmderr;
} abstractcs_t;

typedef struct {
  unsigned autoexecprogbuf;
  unsigned autoexecdata;
} abstractauto_t;

class debug_module_t : public abstract_device_t
{
  public:
    debug_module_t(sim_t *sim, unsigned progbufsize);
    ~debug_module_t();

    void add_device(bus_t *bus);

    bool load(reg_t addr, size_t len, uint8_t* bytes);
    bool store(reg_t addr, size_t len, const uint8_t* bytes);

    // Debug Module Interface that the debugger (in our case through JTAG DTM)
    // uses to access the DM.
    // Return true for success, false for failure.
    bool dmi_read(unsigned address, uint32_t *value);
    bool dmi_write(unsigned address, uint32_t value);

  private:
    static const unsigned datasize = 2;
    // Size of program_buffer in 32-bit words, as exposed to the rest of the
    // world.
    unsigned progbufsize;
    // Actual size of the program buffer, which is 1 word bigger than we let on
    // to implement the implicit ebreak at the end.
    unsigned program_buffer_bytes;
    static const unsigned debug_data_start = 0x380;
    unsigned debug_progbuf_start;

    static const unsigned debug_abstract_size = 2;
    unsigned debug_abstract_start;

    sim_t *sim;

    uint8_t debug_rom_whereto[4];
    uint8_t debug_abstract[debug_abstract_size * 4];
    uint8_t *program_buffer;
    uint8_t dmdata[datasize * 4];
    
    bool halted[1024];
    bool resumeack[1024];
    uint8_t debug_rom_flags[1024];

    void write32(uint8_t *rom, unsigned int index, uint32_t value);
    uint32_t read32(uint8_t *rom, unsigned int index);

    dmcontrol_t dmcontrol;
    dmstatus_t dmstatus;
    abstractcs_t abstractcs;
    abstractauto_t abstractauto;
    uint32_t command;

    processor_t *current_proc() const;
    void reset();
    bool perform_abstract_command();
};

#endif
