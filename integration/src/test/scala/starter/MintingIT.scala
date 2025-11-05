package starter

import com.bloxbean.cardano.client.transaction.spec.Transaction
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Try

/** This integration test mints and burns tokens.
  *
  * It requires a Blockfrost API available. Use Yaci Devkit to run this test.
  *
  * {{{
  *  devkit
  *  create-node
  *  start
  * }}}
  */
class MintingIT extends AnyFunSuite with BeforeAndAfterAll {

    private val appCtx = AppCtx.yaciDevKit("CO2 Tonne")
    private val txBuilder = TxBuilder(appCtx)
    private def submitTx(tx: Transaction): Either[String, String] =
        appCtx.backendService.getTransactionService
            .submitTransaction(tx.serialize())
            .toEither
    private def waitBlock(): Unit = Thread.sleep(3000)

    override def beforeAll(): Unit = {
        val params = Try(txBuilder.protocolParams)
        if (!params.isSuccess) {
            cancel("This test requires a Blockfrost API available. Start Yaci Devkit before running this test.")
        }
    }

    test("mint and burn tokens") {
        val result = for
            // mint 1000 tokens
            tx <- txBuilder.makeMintingTx(1000)
            _ = println(s"minting tx: $tx")
            _ <- submitTx(tx)
            _ = waitBlock()
            // burn 1000 tokens
            burnedTx <- txBuilder.makeBurningTx(-1000)
            _ = println(s"burning tx: $tx")
            _ <- submitTx(burnedTx)
            _ = waitBlock()
            // get utxos
            utxos <- appCtx.backendService.getUtxoService
                .getUtxos(
                  appCtx.account.getBaseAddress.getAddress,
                  appCtx.unitName,
                  100,
                  1
                )
                .toEither
        yield utxos
        result match
            case Right(utxos) => assert(utxos.isEmpty)
            case Left(err)    => fail(err)
    }
}
