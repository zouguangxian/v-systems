package vee.transaction

import com.wavesplatform.TransactionGen
import org.scalatest._
import org.scalatest.prop.PropertyChecks
import scorex.transaction.TransactionParser.TransactionType
import scorex.transaction._
import vee.transaction.spos.ReleaseSlotsTransaction

class ReleaseSlotsTransactionSpecification extends PropSpec with PropertyChecks with Matchers with TransactionGen {

  property("ReleaseSlotstransaction serialization roundtrip") {
    forAll(releaseSlotsGen) { tx: ReleaseSlotsTransaction =>
      require(tx.bytes.head == TransactionType.ReleaseSlotsTransaction.id)
      val recovered = ReleaseSlotsTransaction.parseTail(tx.bytes.tail).get
      assertTxs(recovered, tx)
    }
  }

  property("ReleaseSlotstransaction serialization from TypedTransaction") {
    forAll(releaseSlotsGen) { tx: ReleaseSlotsTransaction =>
      val recovered = TransactionParser.parseBytes(tx.bytes).get
      assertTxs(recovered.asInstanceOf[ReleaseSlotsTransaction], tx)
    }
  }

  private def assertTxs(first: ReleaseSlotsTransaction, second: ReleaseSlotsTransaction): Unit = {
    first.sender.address shouldEqual second.sender.address
    first.timestamp shouldEqual second.timestamp
    first.fee shouldEqual second.fee
    first.slotid shouldEqual second.slotid
    first.bytes shouldEqual second.bytes
  }
}
