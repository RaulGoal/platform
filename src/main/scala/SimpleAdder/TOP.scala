package SimpleAdder


import chisel3._
import freechips.rocketchip.coreplex._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._
import example.{ExampleTop, ExampleTopModule}

class ExampleTopWithHasDMAAdder(implicit p: Parameters) extends ExampleTop
    with HasDMAAdder {
  override lazy val module = new ExampleTopWithHasDMAAdderModule(this)
}

class ExampleTopWithHasDMAAdderModule(l: ExampleTopWithHasDMAAdder)
  extends ExampleTopModule(l) 