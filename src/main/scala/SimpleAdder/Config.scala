package SimpleAdder

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.coreplex.{WithRoccExample, WithNMemoryChannels, WithNBigCores, WithRV32}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.XLen
import testchipip._
import ConfigValName._
import example.{BaseExampleConfig}

object ConfigValName {
  implicit val valName = ValName("TestHarness")
}


class WithHasDMAAdder extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) =>
    Module(LazyModule(new ExampleTopWithHasDMAAdder()(p)).module)
})

class HasDMAAdderConfig extends Config(new WithHasDMAAdder ++ new BaseExampleConfig)