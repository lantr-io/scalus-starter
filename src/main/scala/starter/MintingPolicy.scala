package starter

import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script
import scalus.*
import scalus.Compiler.compile
import scalus.builtin.ByteString.StringInterpolators
import scalus.builtin.Data.FromData
import scalus.builtin.Data.ToData
import scalus.builtin.Data.fromData
import scalus.builtin.Data.toData
import scalus.builtin.FromData
import scalus.builtin.ToData
import scalus.builtin.{ByteString, Data}
import scalus.ledger.api.PlutusLedgerLanguage
import scalus.ledger.api.v2.OutputDatum
import scalus.ledger.api.v3.*
import scalus.builtin.ToDataInstances.given
import scalus.builtin.FromDataInstances.given
import scalus.ledger.api.v1.FromDataInstances.given
import scalus.ledger.api.v1.ToDataInstances.given
import scalus.ledger.api.v3.FromDataInstances.given
import scalus.ledger.api.v3.ScriptPurpose.*
import scalus.prelude.given
import scalus.prelude.*
import scalus.sir.SIR
import scalus.uplc.Program
import scalus.utils.Utils
import starter.MintingPolicy.MintingConfig

import scala.language.implicitConversions

/* This annotation is used to generate the Scalus Intermediate Representation (SIR)
   for the code in the annotated object.
 */
@Compile
/** Minting policy script
  */
object MintingPolicy extends DataParameterizedValidator {

    case class MintingConfig(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName
    )

    given FromData[MintingConfig] = FromData.deriveCaseClass[MintingConfig]
    given ToData[MintingConfig] = ToData.deriveCaseClass[MintingConfig](0)

    /** Minting policy script
      *
      * @param adminPubKeyHash
      *   admin public key hash
      * @param tokenName
      *   token name to mint or burn
      * @param ctx
      *   [[ScriptContext]]
      */
    def mintingPolicy(
        adminPubKeyHash: PubKeyHash, // admin pub key hash
        tokenName: TokenName, // token name
        ownSymbol: CurrencySymbol,
        tx: TxInfo
    ): Unit = {
        // find the tokens minted by this policy id
        val mintedTokens = tx.mint.lookup(ownSymbol).getOrFail("Tokens not found")
        mintedTokens.toList match
            // there should be only one token with the given name
            case List.Cons((tokName, _), tail) =>
                tail match
                    case List.Nil => require(tokName == tokenName, "Token name not found")
                    case _        => fail("Multiple tokens found")
            case _ => fail("Tokens not found or multiple tokens found")

        // only admin can mint or burn tokens
        require(tx.signatories.contains(adminPubKeyHash), "Not signed by admin")
    }

    /** Minting policy validator
      *
      * The validator is parameterized by the [[MintingConfig]] which is passed as [[Data]] before
      * the validator is published on-chain.
      *
      * @param config
      *   minting policy configuration
      * @param ctxData
      *   context data
      */
    override def mint(
        param: Datum,
        redeemer: Datum,
        currencySymbol: CurrencySymbol,
        tx: TxInfo
    ): Unit = {
        val mintingConfig = param.to[MintingConfig]
        mintingPolicy(mintingConfig.adminPubKeyHash, mintingConfig.tokenName, currencySymbol, tx)
    }

}

object MintingPolicyGenerator {
    val mintingPolicySIR: SIR = compile(MintingPolicy.validate)
    private val script = mintingPolicySIR.toUplcOptimized(generateErrorTraces = true).plutusV3

    def makeMintingPolicyScript(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName
    ): MintingPolicyScript = {
        import scalus.uplc.TermDSL.{*, given}

        val config = MintingPolicy
            .MintingConfig(adminPubKeyHash = adminPubKeyHash, tokenName = tokenName)
        MintingPolicyScript(script = script $ config.toData)
    }
}

class MintingPolicyScript(val script: Program) {
    lazy val plutusScript: PlutusV3Script = PlutusV3Script
        .builder()
        .`type`("PlutusScriptV3")
        .cborHex(script.doubleCborHex)
        .build()
        .asInstanceOf[PlutusV3Script]

    lazy val scriptHash: ByteString = ByteString.fromArray(plutusScript.getScriptHash)
}
