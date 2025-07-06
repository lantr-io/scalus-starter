package starter

import scalus.*
import scalus.builtin.ByteString

class TxBuilderSpec extends munit.ScalaCheckSuite {

    private val appCtx = AppCtx.yaciDevKit("CO2 Tonne")

    private val pubKey: ByteString = ByteString.fromArray(appCtx.account.publicKeyBytes())

    test("create minting transaction") {
        val txBuilder = TxBuilder(appCtx)
        txBuilder.makeMintingTx(1000) match
            case Right(tx) =>
                assertEquals(
                  ByteString.fromArray(tx.getBody.getMint.get(0).getAssets.get(0).getNameAsBytes),
                  appCtx.tokenNameByteString
                )
                assertEquals(tx.getWitnessSet.getVkeyWitnesses.size(), 1)
                assertEquals(
                  ByteString.fromArray(tx.getWitnessSet.getVkeyWitnesses.get(0).getVkey),
                  pubKey
                )
            case Left(err) => fail(err)
    }

}
