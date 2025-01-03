package starter

import com.bloxbean.cardano.client.account.Account
import scalus.*
import scalus.builtin.given
import scalus.builtin.ByteString.given
import scalus.builtin.Data.toData
import scalus.builtin.{ByteString, Data, PlatformSpecific}
import scalus.ledger.api.v3.*
import scalus.ledger.api.v3.ToDataInstances.given
import scalus.prelude.*
import scalus.uplc.*
import scalus.uplc.TermDSL.{*, given}
import scalus.uplc.eval.*

import scala.language.implicitConversions

enum Expected {
    case Success(budget: ExBudget)
    case Failure(reason: String)
}

class MintingPolicySpec extends munit.ScalaCheckSuite {
    import Expected.*

    // Plutus V3 VM with default machine parameters
    private given PlutusVM = PlutusVM.makePlutusV3VM()

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
        assertEquals(size, 3516)
    }

    test(
      "minting should succeed when minted token name is correct and admin signature is correct"
    ) {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(pubKeyHash)
        )
        // run the minting policy script as a Scala function
        // here you can use debugger to debug the minting policy script
        MintingPolicy.mintingPolicy(pubKeyHash, tokenName, ctx)
        // run the minting policy script as a Plutus script
        assertEval(
          mintingScript.script $ ctx.toData,
          Success(ExBudget.fromCpuAndMemory(cpu = 49532838, memory = 188639))
        )
    }

    test("minting should fail when minted token name is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName ++ ByteString.fromString("extra"), 1000),
          signatories = List(pubKeyHash)
        )

        interceptMessage[IllegalArgumentException]("Token name not found"):
            MintingPolicy.mintingPolicy(pubKeyHash, tokenName, ctx)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    test("minting should fail when admin signature is not correct") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List(PubKeyHash(crypto.blake2b_224(ByteString.fromString("wrong"))))
        )

        interceptMessage[Exception]("Not found"):
            MintingPolicy.mintingPolicy(pubKeyHash, tokenName, ctx)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }

    test("minting should fail when admin signature is not provided") {
        val ctx = makeScriptContext(
          mint = Value(mintingScript.scriptHash, tokenName, 1000),
          signatories = List.Nil
        )

        interceptMessage[Exception]("Not found"):
            MintingPolicy.mintingPolicy(pubKeyHash, tokenName, ctx)

        assertEval(mintingScript.script $ ctx.toData, Failure("Error evaluated"))
    }
    
    private def makeScriptContext(mint: Value, signatories: List[PubKeyHash]) =
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Nil,
            referenceInputs = List.Nil,
            outputs = List.Nil,
            fee = BigInt("188021"),
            mint = mint,
            certificates = List.Nil,
            withdrawals = AssocMap.empty,
            validRange = Interval.always,
            signatories = signatories,
            redeemers = AssocMap.empty,
            data = AssocMap.empty,
            id = TxId(hex"1e0612fbd127baddfcd555706de96b46c4d4363ac78c73ab4dee6e6a7bf61fe9"),
            votes = AssocMap.empty,
            proposalProcedures = List.Nil,
            currentTreasuryAmount = Maybe.Nothing,
            treasuryDonation = Maybe.Nothing
          ),
          redeemer = Data.unit,
          scriptInfo = ScriptInfo.MintingScript(mintingScript.scriptHash)
        )

    def assertEval(p: Program, expected: Expected): Unit = {
        val result = p.evaluateDebug
        (result, expected) match
            case (result: Result.Success, Expected.Success(expected)) =>
                assertEquals(result.budget, expected)
            case (result: Result.Failure, Expected.Failure(expected)) =>
                assertEquals(result.exception.getMessage, expected)
            case _ => fail(s"Unexpected result: $result, expected: $expected")
    }
}
