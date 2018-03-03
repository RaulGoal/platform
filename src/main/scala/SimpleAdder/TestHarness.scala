package SimpleAdder

//below content do not need to modify when create another project directory and run the simulation(just copy to the directory)
import chisel3._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import testchipip.GeneratorApp
import example.{ExampleTop, ExampleTopModule}

case object BuildTop extends Field[(Clock, Bool, Parameters) => ExampleTopModule[ExampleTop]]

class TestHarness(implicit val p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val dut = p(BuildTop)(clock, reset.toBool, p)
  dut.debug := DontCare
  dut.connectSimAXIMem()
  dut.dontTouchPorts()
  dut.tieOffInterrupts()
  io.success := dut.connectSimSerial()
}

object Generator extends GeneratorApp {
  generateFirrtl
  generateAnno
}
