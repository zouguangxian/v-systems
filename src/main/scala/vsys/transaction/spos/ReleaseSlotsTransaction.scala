package vsys.transaction.spos

import com.google.common.primitives.{Bytes, Ints, Longs, Shorts}
import com.wavesplatform.state2.ByteStr
import play.api.libs.json.{JsObject, Json}
import scorex.account._
import scorex.crypto.hash.FastCryptographicHash
import scorex.transaction.TransactionParser._
import scorex.transaction._
import vsys.transaction.ProvenTransaction
import vsys.transaction.proof._
import vsys.spos.SPoSCalc._

import scala.util.{Failure, Success, Try}


case class ReleaseSlotsTransaction private(slotId: Int,
                                           fee: Long,
                                           feeScale: Short,
                                           timestamp: Long,
                                           proofs: Proofs)
  extends ProvenTransaction {

  override val transactionType: TransactionType.Value = TransactionType.ReleaseSlotsTransaction

  lazy val toSign: Array[Byte] = Bytes.concat(
    Array(transactionType.id.toByte),
    Ints.toByteArray(slotId),
    Longs.toByteArray(fee),
    Shorts.toByteArray(feeScale),
    Longs.toByteArray(timestamp))

  override lazy val id: ByteStr = ByteStr(FastCryptographicHash(toSign))

  override lazy val json: JsObject = jsonBase() ++ Json.obj(
    "slotId" -> slotId,
    "fee" -> fee,
    "feeScale" -> feeScale,
    "timestamp" -> timestamp
  )

  override val assetFee: (Option[AssetId], Long, Short) = (None, fee, feeScale)
  override lazy val bytes: Array[Byte] = Bytes.concat(toSign, proofs.bytes)

}

object ReleaseSlotsTransaction {

  def parseTail(bytes: Array[Byte]): Try[ReleaseSlotsTransaction] = Try {
    val (slotBytes, slotIdEnd) = (bytes.slice(0, SlotIdLength), SlotIdLength)
    val slotId = Ints.fromByteArray(slotBytes)
    val fee = Longs.fromByteArray(bytes.slice(slotIdEnd, slotIdEnd + 8))
    val feeScale = Shorts.fromByteArray(bytes.slice(slotIdEnd + 8, slotIdEnd + 10))
    val timestamp = Longs.fromByteArray(bytes.slice(slotIdEnd + 10, slotIdEnd + 18))
    (for {
      proofs <- Proofs.fromBytes(bytes.slice(slotIdEnd + 18, bytes.length))
      tx <- ReleaseSlotsTransaction.create(slotId, fee, feeScale, timestamp, proofs)
    } yield tx).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  def create(slotId: Int,
             fee: Long,
             feeScale: Short,
             timestamp: Long,
             proofs: Proofs): Either[ValidationError, ReleaseSlotsTransaction] =
    if (fee <= 0) {
      Left(ValidationError.InsufficientFee)
    } else if (feeScale != 100){
      Left(ValidationError.WrongFeeScale(feeScale))
    } else if (slotId % SlotGap!= 0){
      Left(ValidationError.InvalidSlotId(slotId))
    } else {
      Right(ReleaseSlotsTransaction(slotId, fee, feeScale, timestamp, proofs))
    }

  def create(sender: PrivateKeyAccount,
             slotId: Int,
             fee: Long,
             feeScale: Short,
             timestamp: Long): Either[ValidationError, ReleaseSlotsTransaction] = for {
    unsigned <- create(slotId, fee, feeScale, timestamp, Proofs.empty)
    proofs <- Proofs.create(List(EllipticCurve25519Proof.createProof(unsigned.toSign, sender).bytes))
    tx <- create(slotId, fee, feeScale, timestamp, proofs)
  } yield tx

  def create(sender: PublicKeyAccount,
             slotId: Int,
             fee: Long,
             feeScale: Short,
             timestamp: Long,
             signature: ByteStr): Either[ValidationError, ReleaseSlotsTransaction] = for {
    proofs <- Proofs.create(List(EllipticCurve25519Proof.buildProof(sender, signature).bytes))
    tx <- create(slotId, fee, feeScale, timestamp, proofs)
  } yield tx

}
