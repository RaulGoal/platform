package SimpleAdder

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tile.{XLen}
import freechips.rocketchip.coreplex.{HasSystemBus}

class TLMasterAdder (address: BigInt, beatBytes: Int)(implicit p: Parameters) extends LazyModule {
 
  val mmio_control = TLIdentityNode()
  
  //TLMasterAdder would wrap a master port and set some parameter for the master port device
  val node = TLClientNode(Seq(TLClientPortParameters(Seq(TLClientParameters(
    sourceId = IdRange(0, 1), 
    name = s"Raul TileLink DMA Adder")))))

  val frontend = LazyModule(new AdderDMACtrTL(AdderDMACtrParams(address, beatBytes)))
  
  frontend.node := mmio_control
  
  lazy val module = new LazyModuleImp(this) {

    val io = IO(new Bundle {

      val front = Flipped(new ControllerIOBundle)
      val back  = Flipped(new ResponseIO)
    
    })

  	val (tl, edge) = node.out(0)    //declare to own a master port(out)

    
    def xLen: Int = p(XLen)

    val read_from_addr  = RegInit(0.U(xLen.W))
    val write_back_addr = RegInit(0.U(xLen.W))
    val added_count     = RegInit(0.U(xLen.W))
    val process_count   = RegInit(1.U(xLen.W))
    val opA             = RegInit(0.U(xLen.W))
    val opB             = RegInit(0.U(xLen.W))
    val opC             = RegInit(0.U(xLen.W))
    val accumulator     = RegInit(0.U(xLen.W))

    val start_enable    = RegInit(false.B)
    val finish          = RegInit(false.B)

    read_from_addr  := frontend.module.io.front.read_from_addr
    write_back_addr := frontend.module.io.front.write_back_addr
    added_count     := frontend.module.io.front.added_count
    start_enable    := frontend.module.io.front.start_enable

    frontend.module.io.back.finish := finish

    val idle :: mem_read_req :: mem_read :: addition :: mem_write :: mem_write_resp :: complete :: Nil = Enum(7)
    val state = RegInit(idle)

    val read_data = edge.Get(
      fromSource = 0.U,
      toAddress = read_from_addr,
      lgSize = 5.U)._2

    val write_data = edge.Put(
      fromSource = 0.U,
      toAddress = write_back_addr,
      lgSize = 3.U,
      data = accumulator)._2
  
    tl.a.valid := (state === mem_read_req) || (state === mem_write) 
    tl.d.ready := true.B//((state === mem_read_req) || (state === mem_read)) || ((state === mem_write) || (state === complete))
    tl.a.bits  := Mux(state === mem_read_req, read_data, write_data)

    //unusage signal
    tl.b.ready := false.B
    tl.c.valid := false.B
    tl.e.valid := false.B

    when (tl.d.fire() && state === mem_read){
      when ((edge.count(tl.d)._4) === 0.U){
  
        opA := tl.d.bits.data
  
      }.elsewhen ((edge.count(tl.d)._4) === 1.U){
        
        opB := tl.d.bits.data
  
      }.elsewhen ((edge.count(tl.d)._4) === 2.U){
        
        opC := tl.d.bits.data
      }
    } 
    
    switch (state) {

      is (idle) {

        when (start_enable && tl.a.ready) { state := mem_read_req }

      }

      is (mem_read_req) {

      	when (tl.a.fire()) { state := mem_read }

      }

      is (mem_read) {

      	when (edge.done(tl.d)) { state := addition }

      }
      is (addition) {

      	when (process_count === 1.U) { 

      	  accumulator   := opA + opB
      	  process_count := process_count + 1.U

        }.elsewhen (process_count === 2.U){

          accumulator := accumulator + opC
          state       := mem_write
     	
        }

      }
      is (mem_write) {

      	when (edge.done(tl.a)){ state := mem_write_resp }
   
      }
      is (mem_write_resp) {

      	when (tl.d.fire()){ state := complete }
   
      }
      is (complete) {

        state         := idle
        process_count := 1.U
        finish        := true.B

      }

    }

  }

}

trait HasDMAAdder extends HasSystemBus {
  implicit val p: Parameters

  val dma_adder = LazyModule(new TLMasterAdder(
    0x5000, sbus.beatBytes))

  val ram  = LazyModule(new TLRAM(AddressSet(0x20000000, 0xffff), beatBytes = 8, devName = Some("RaulSRAM") ))
 
  ram.node               := sbus.toVariableWidthSlaves
  dma_adder.mmio_control := sbus.toVariableWidthSlaves
  sbus.fromSyncPorts()   := dma_adder.node
  
}