#include "common/mmio.h"
#include <stdio.h>
#include <string.h>


int main(void)
{

    

    //DMA adder control register address
	const unsigned long read_from_addr  = 0x5000 ;
    const unsigned long write_back_addr = 0x5008 ;
    const unsigned long added_count     = 0x5010 ;
    const unsigned long start_enable    = 0x5018 ;
    const unsigned long finish          = 0x5020 ;

    const unsigned long sram_base = 0x20000000 ;
    const unsigned long data_write_back_addr = sram_base + 0x050 ;
      
    //data memory copy
    long data[3] = {1, -50, 35};
    memcpy( sram_base, data, sizeof(data) );  

    //register setting
	reg_write64(read_from_addr, sram_base);
	printf("Setting: read_from_addr = 0x%x\n", reg_read64(read_from_addr));

	reg_write64(write_back_addr, data_write_back_addr );
	printf("Setting: write_back_addr = 0x%x\n", reg_read64(write_back_addr));

	reg_write64(added_count, 3);
	printf("Setting: added_count = %d\n", reg_read64(added_count));

	reg_write64(start_enable, 1);
	
	reg_write64(start_enable, 0);
	printf("Setting: start_enable = %d\n", reg_read64(start_enable));

    //pooling to check whether DMA adder complete process  
	while ( !reg_read64(finish) ){}
    
    //check result
    printf("accumulate result = %d\n", reg_read64(data_write_back_addr));
	return 0;
}
