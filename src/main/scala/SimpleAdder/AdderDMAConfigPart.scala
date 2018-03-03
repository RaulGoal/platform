package SimpleAdder

import chisel3._
import chisel3.util._
import freechips.rocketchip.coreplex.HasPeripheryBus
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile.{XLen}

case class AdderDMACtrParams(address: BigInt, beatBytes: Int)

trait AdderDMACtrTLModule extends HasRegMap {

  implicit val p: Parameters
  val io: DeviceCtrlBundle

  def xLen: Int = p(XLen) //indicate availible bits for the system; ex: 64bits


  // start address to read data
  val read_from_addr = Reg(UInt(xLen.W))

  // address to write back data
  val write_back_addr = Reg(UInt(xLen.W))
  
  // how many number to be added
  val added_count = Reg(UInt(xLen.W))

  // start to run
  val start_enable = RegInit(false.B)

  // indicate whether complete all process
  val finish = RegInit(false.B)

  io.front.read_from_addr  := read_from_addr
  io.front.write_back_addr := write_back_addr
  io.front.added_count     := added_count 
  io.front.start_enable    := start_enable
  
  finish := io.back.finish



  regmap(

    AdderDMACtrlRegs.read_from_addr  -> Seq(RegField(xLen,read_from_addr)),
    AdderDMACtrlRegs.write_back_addr -> Seq(RegField(xLen, write_back_addr)),
    AdderDMACtrlRegs.added_count     -> Seq(RegField(xLen, added_count)),
    AdderDMACtrlRegs.start_enable    -> Seq(RegField(1, start_enable)),
    AdderDMACtrlRegs.finish          -> Seq(RegField(1, finish))
 )

}

class ResponseIO extends Bundle {

  val finish    = Input(Bool())

} 

class ControllerIOBundle (implicit val p: Parameters) extends Bundle {

  def xLen: Int = p(XLen)
  val read_from_addr  = Output((UInt(xLen.W)))
  val write_back_addr = Output((UInt(xLen.W)))
  val added_count     = Output((UInt(xLen.W)))
  val start_enable    = Output(Bool())
  
  override def cloneType =  new ControllerIOBundle().asInstanceOf[this.type] 
}

trait DeviceCtrlBundle extends Bundle {

  implicit val p: Parameters

  val front = new ControllerIOBundle
  val back  = new ResponseIO

}

class AdderDMACtrTL(c: AdderDMACtrParams)(implicit p: Parameters)
  extends TLRegisterRouter(
    c.address, "AdderDMACtr", Seq("Raul,AdderDMACtr"),
    beatBytes = c.beatBytes)(
      new TLRegBundle(c, _) with DeviceCtrlBundle)(
      new TLRegModule(c, _, _) with  AdderDMACtrTLModule)


trait HasAdderDMACtr extends HasPeripheryBus {
  implicit val p: Parameters

  private val address = 0x2000

  val adderDMA = LazyModule(new AdderDMACtrTL(AdderDMACtrParams(address, pbus.beatBytes))(p))

  adderDMA.node := pbus.toVariableWidthSlaves
}

