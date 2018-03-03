package testchipip

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config._
import freechips.rocketchip.util.HellaPeekingArbiter
import freechips.rocketchip.tilelink._

class SerialIO(w: Int) extends Bundle {
  val in = Flipped(Decoupled(UInt(w.W)))
  val out = Decoupled(UInt(w.W))

  def flipConnect(other: SerialIO) {
    in <> other.out
    other.in <> out
  }

  override def cloneType = new SerialIO(w).asInstanceOf[this.type]
}

class ValidSerialIO(w: Int) extends Bundle {
  val in = Flipped(Valid(UInt(w.W)))
  val out = Valid(UInt(w.W))

  def flipConnect(other: ValidSerialIO) {
    in <> other.out
    other.in <> out
  }

  override def cloneType = new ValidSerialIO(w).asInstanceOf[this.type]
}

class StreamChannel(val w: Int) extends Bundle {
  val data = UInt(w.W)
  val keep = UInt((w/8).W)
  val last = Bool()

  override def cloneType = new StreamChannel(w).asInstanceOf[this.type]
}

class StreamIO(w: Int) extends Bundle {
  val in = Flipped(Decoupled(new StreamChannel(w)))
  val out = Decoupled(new StreamChannel(w))

  def flipConnect(other: StreamIO) {
    in <> other.out
    other.in <> out
  }

  override def cloneType = new StreamIO(w).asInstanceOf[this.type]
}

class StreamNarrower(inW: Int, outW: Int) extends Module {
  require(inW > outW)
  require(inW % outW == 0)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(inW)))
    val out = Decoupled(new StreamChannel(outW))
  })

  val outBytes = outW / 8
  val outBeats = inW / outW

  val bits = Reg(new StreamChannel(inW))
  val count = Reg(UInt(log2Ceil(outBeats).W))

  val s_recv :: s_send :: Nil = Enum(2)
  val state = RegInit(s_recv)

  val nextData = bits.data >> outW.U
  val nextKeep = bits.keep >> outBytes.U

  io.in.ready := state === s_recv
  io.out.valid := state === s_send
  io.out.bits.data := bits.data(outW - 1, 0)
  io.out.bits.keep := bits.keep(outBytes - 1, 0)
  io.out.bits.last := bits.last && !nextKeep.orR

  when (io.in.fire()) {
    count := (outBeats - 1).U
    bits := io.in.bits
    state := s_send
  }

  when (io.out.fire()) {
    count := count - 1.U
    bits.data := nextData
    bits.keep := nextKeep
    when (io.out.bits.last || count === 0.U) {
      state := s_recv
    }
  }
}

class StreamWidener(inW: Int, outW: Int) extends Module {
  require(outW > inW)
  require(outW % inW == 0)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(inW)))
    val out = Decoupled(new StreamChannel(outW))
  })

  val inBytes = inW / 8
  val inBeats = outW / inW

  val data = Reg(Vec(inBeats, UInt(inW.W)))
  val keep = RegInit(Vec(Seq.fill(inBeats)(0.U(inBytes.W))))
  val last = Reg(Bool())

  val idx = RegInit(0.U(log2Ceil(inBeats).W))

  val s_recv :: s_send :: Nil = Enum(2)
  val state = RegInit(s_recv)

  io.in.ready := state === s_recv
  io.out.valid := state === s_send
  io.out.bits.data := data.asUInt
  io.out.bits.keep := keep.asUInt
  io.out.bits.last := last

  when (io.in.fire()) {
    idx := idx + 1.U
    data(idx) := io.in.bits.data
    keep(idx) := io.in.bits.keep
    when (io.in.bits.last || idx === (inBeats - 1).U) {
      last := io.in.bits.last
      state := s_send
    }
  }

  when (io.out.fire()) {
    idx := 0.U
    keep.foreach(_ := 0.U)
    state := s_recv
  }
}

object StreamWidthAdapter {
  def apply(out: DecoupledIO[StreamChannel], in: DecoupledIO[StreamChannel]) {
    if (out.bits.w > in.bits.w) {
      val widener = Module(new StreamWidener(in.bits.w, out.bits.w))
      widener.io.in <> in
      out <> widener.io.out
    } else if (out.bits.w < in.bits.w) {
      val narrower = Module(new StreamNarrower(in.bits.w, out.bits.w))
      narrower.io.in <> in
      out <> narrower.io.out
    } else {
      out <> in
    }
  }

  def apply(a: StreamIO, b: StreamIO) {
    apply(a.out, b.out)
    apply(b.in, a.in)
  }
}

class ValidStreamIO(w: Int) extends Bundle {
  val in = Flipped(Valid(new StreamChannel(w)))
  val out = Valid(new StreamChannel(w))

  def flipConnect(other: ValidStreamIO) {
    in <> other.out
    other.in <> out
  }

  override def cloneType =
    new ValidStreamIO(w).asInstanceOf[this.type]
}

class GenericSerializer[T <: Data](t: T, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(t))
    val out = Decoupled(UInt(w.W))
  })

  val dataBits = t.getWidth
  val dataBeats = (dataBits - 1) / w + 1
  val data = Reg(UInt(dataBits.W))

  val sending = RegInit(false.B)
  val (sendCount, sendDone) = Counter(io.out.fire(), dataBeats)

  io.in.ready := !sending
  io.out.valid := sending
  io.out.bits := data(w-1, 0)

  when (io.in.fire()) {
    data := io.in.bits.asUInt
    sending := true.B
  }

  when (io.out.fire()) { data := data >> w.U }

  when (sendDone) { sending := false.B }
}

class GenericDeserializer[T <: Data](t: T, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(w.W)))
    val out = Decoupled(t)
  })

  val dataBits = t.getWidth
  val dataBeats = (dataBits - 1) / w + 1
  val data = Reg(Vec(dataBeats, UInt(w.W)))

  val receiving = RegInit(true.B)
  val (recvCount, recvDone) = Counter(io.in.fire(), dataBeats)

  io.in.ready := receiving
  io.out.valid := !receiving
  io.out.bits := t.fromBits(data.asUInt)

  when (io.in.fire()) {
    data(recvCount) := io.in.bits
  }

  when (recvDone) { receiving := false.B }

  when (io.out.fire()) { receiving := true.B }
}
