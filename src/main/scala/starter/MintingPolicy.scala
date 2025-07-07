package starter

import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script
import scalus.*
import scalus.Compiler.compile
import scalus.builtin.Data.{FromData, ToData, toData}
import scalus.builtin.{ByteString, Data, FromData, ToData}
import scalus.ledger.api.v3.*
import scalus.ledger.api.v3.ScriptPurpose.*
import scalus.prelude.{*, given}
import scalus.sir.SIR
import scalus.uplc.Program

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

    given FromData[MintingConfig] = FromData.derived
    given ToData[MintingConfig] = ToData.derived

    /** Minting policy script
      *
      * @param adminPubKeyHash
      *   admin public key hash
      * @param tokenName
      *   token name to mint or burn
      * @param ownSymbol
      *   own currency symbol (minting policy id)
      * @param tx
      *   transaction information
      */
    private def mintingPolicy(
        adminPubKeyHash: PubKeyHash, // admin pub key hash
        tokenName: TokenName, // token name
        ownSymbol: CurrencySymbol,
        tx: TxInfo
    ): Unit = {
        // find the tokens minted by this policy id
        val mintedTokens = tx.mint.get(ownSymbol).getOrFail("Tokens not found")
        mintedTokens.toList match
            // there should be only one token with the given name
            case List.Cons((tokName, _), tail) =>
                tail match
                    case List.Nil => require(tokName == tokenName, "Token name not found")
                    case _        => fail("Multiple tokens found")
            case _ => fail("Impossible: no tokens found")

        // only admin can mint or burn tokens
        require(tx.signatories.contains(adminPubKeyHash), "Not signed by admin")
    }

    /** Minting policy validator */
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
