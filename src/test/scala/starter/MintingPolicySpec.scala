package starter

import com.bloxbean.cardano.client.account.Account
import scalus.*
import scalus.builtin.Data.toData
import scalus.builtin.{ByteString, Data, PlatformSpecific, given}
import scalus.ledger.api.v3.*
import scalus.prelude.*
import scalus.ledger.api.v1.Value.*
import scalus.testkit.ScalusTest
import scalus.uplc.*
import scalus.uplc.eval.*
import starter.MintingPolicy.MintingConfig

import scala.language.implicitConversions

enum Expected {
    case Success(budget: ExBudget)
    case Failure(reason: String)
}

class MintingPolicySpec extends munit.ScalaCheckSuite, ScalusTest {
    import Expected.*

    private val account = new Account()

    private val crypto = summon[PlatformSpecific] // platform specific crypto functions

    private val tokenName = ByteString.fromString("CO2 Tonne")

    private val pubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )

    private val mintingScript =
        MintingPolicyGenerator.makeMintingPolicyScript(pubKeyHash, tokenName)

    test(s"validator size is ${mintingScript.script.flatEncoded.length} bytes") {
        val size = mintingScript.script.flatEncoded.length
        assertEquals(size, 3719)
    }

    test(
      "minting should succeed when minted token name is correct and admin signature is correct"
    ) {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(pubKeyHash)
        )
        val config = MintingConfig(pubKeyHash, tokenName)
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicy.validate(config.toData)(ctx.toData)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.script $ ctx.toData,
          Success(ExBudget.fromCpuAndMemory(cpu = 51597034, memory = 199643))
        )
    }

    test("minting should fail when minted token name is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName ++ ByteString.fromString("extra"), 1000),
          signatories = List(pubKeyHash)
        )
        val config = MintingConfig(pubKeyHash, tokenName)

        interceptMessage[IllegalArgumentException]("Token name not found"):
            MintingPolicy.validate(config.toData)(ctx.toData)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    test("minting should fail when extra tokens are minted/burned") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000) + Value(
            mintingScript.scriptHash,
            ByteString.fromString("Extra"),
            1000
          ),
          signatories = List(pubKeyHash)
        )
        val config = MintingConfig(pubKeyHash, tokenName)

        interceptMessage[RuntimeException]("Multiple tokens found"):
            MintingPolicy.validate(config.toData)(ctx.toData)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    test("minting should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List.Nil
        )
        val config = MintingConfig(pubKeyHash, tokenName)

        interceptMessage[Exception]("Not signed by admin"):
            MintingPolicy.validate(config.toData)(ctx.toData)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    test("minting should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(crypto.blake2b_224(ByteString.fromString("wrong"))))
        )
        val config = MintingConfig(pubKeyHash, tokenName)

        interceptMessage[Exception]("Not signed by admin"):
            MintingPolicy.validate(config.toData)(ctx.toData)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    private def makeScriptContext(mint: Value, signatories: List[PubKeyHash]) =
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Nil,
            fee = 188021,
            mint = mint,
            signatories = signatories,
            id = random[TxId]
          ),
          redeemer = Data.unit,
          scriptInfo = ScriptInfo.MintingScript(mintingScript.scriptHash)
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
}
