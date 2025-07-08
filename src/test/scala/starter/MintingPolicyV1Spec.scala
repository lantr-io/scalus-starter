package starter

import com.bloxbean.cardano.client.account.Account
import org.scalacheck.Arbitrary
import scalus.*
import scalus.builtin.Data.toData
import scalus.builtin.{ByteString, Data, PlatformSpecific, given}
import scalus.ledger.api.v1.*
import scalus.prelude.*
import scalus.ledger.api.v1.Value.*
import scalus.testkit.ScalusTest
import scalus.uplc.*
import scalus.uplc.eval.*

import scala.language.implicitConversions

class MintingPolicyV1Spec extends munit.ScalaCheckSuite, ScalusTest {
    import Expected.*

    private val account = new Account()

    private val crypto = summon[PlatformSpecific] // platform specific crypto functions

    private val tokenName = ByteString.fromString("CO2 Tonne")

    private val adminPubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    private val config = MintingConfig(adminPubKeyHash, tokenName)

    private val mintingScript =
        MintingPolicyV1Generator.makeMintingPolicyScript(adminPubKeyHash, tokenName)

    test("should fail when minted token name is not correct") {
        val wrongTokenName = tokenName ++ ByteString.fromString("extra")
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, wrongTokenName, 1000),
          signatories = List(adminPubKeyHash)
        )

        interceptMessage[Exception]("Token name not found") {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when extra tokens are minted/burned") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000)
              + Value(mintingScript.scriptHash, ByteString.fromString("Extra"), 1000),
          signatories = List(adminPubKeyHash)
        )

        interceptMessage[Exception]("Multiple tokens found") {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List.Nil
        )

        interceptMessage[Exception]("Not signed by admin") {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(crypto.blake2b_224(ByteString.fromString("wrong"))))
        )

        interceptMessage[Exception]("Not signed by admin") {
            MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        }

        assertEval(mintingScript.script $ Data.unit $ ctx.toData, Failure("Error evaluated"))
    }

    test("should succeed when minted token name is correct and admin signature is correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(adminPubKeyHash)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicyV1.validate(config.toData)(Data.unit, ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.script $ Data.unit $ ctx.toData,
          Success(ExBudget.fromCpuAndMemory(cpu = 42747371, memory = 181375))
        )
    }

    test(s"validator size is 2026 bytes") {
        val size = mintingScript.script.cborEncoded.length
        assertEquals(size, 2026)
    }

    private def makeScriptContext(mint: Value, signatories: List[PubKeyHash]) =
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Nil,
            outputs = List.Nil,
            fee = Value.lovelace(188021),
            mint = mint,
            dcert = List.Nil,
            withdrawals = List.Nil,
            validRange = Interval.always,
            signatories = signatories,
            data = List.Nil,
            id = random[TxId]
          ),
          purpose = ScriptPurpose.Minting(mintingScript.scriptHash)
        )

    private def assertEval(p: Program, expected: Expected): Unit = {
        val result = p.evaluateDebug
        (result, expected) match
            case (result: Result.Success, Expected.Success(expected)) =>
                assertEquals(result.budget, expected)
            case (result: Result.Failure, Expected.Failure(expected)) =>
                assertEquals(result.exception.getMessage, expected)
            case _ => fail(s"Unexpected result: $result, expected: $expected")
    }
    private given arbTxId: Arbitrary[TxId] = Arbitrary(genByteStringOfN(32).map(TxId.apply))
}
