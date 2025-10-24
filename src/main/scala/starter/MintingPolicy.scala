package starter

import com.bloxbean.cardano.client.plutus.spec.{PlutusScript, PlutusV3Script}
import scalus.*
import scalus.Compiler.{Options, compile}
import scalus.builtin.Data.{FromData, ToData, toData}
import scalus.builtin.{ByteString, Data, FromData, ToData}
import scalus.ledger.api.v3.*
import scalus.ledger.api.v3.ScriptPurpose.*
import scalus.prelude.{*, given}
import scalus.sir.SIR
import scalus.uplc.Program

import scala.language.implicitConversions

case class MintingConfig(
    adminPubKeyHash: PubKeyHash,
    tokenName: TokenName
)

@Compile
object MintingConfig {
    given FromData[MintingConfig] = FromData.derived
    given ToData[MintingConfig] = ToData.derived
}

/* This annotation is used to generate the Scalus Intermediate Representation (SIR)
   for the code in the annotated object.
 */
@Compile
/** Minting policy script */
object MintingPolicy extends DataParameterizedValidator {

    /** Minting policy script
      *
      * @param adminPubKeyHash
      *   admin public key hash
      * @param tokenName
      *   token name to mint or burn
      * @param ownPolicyId
      *   own currency symbol (minting policy id)
      * @param tx
      *   transaction information
      */
    private def mintingPolicy(
        adminPubKeyHash: PubKeyHash, // admin pub key hash
        tokenName: TokenName, // token name
        ownPolicyId: PolicyId,
        tx: TxInfo
    ): Unit = {
        // find the tokens minted by this policy id
        val mintedTokens = tx.mint.toSortedMap.get(ownPolicyId).getOrFail("Tokens not found")
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
        policyId: PolicyId,
        tx: TxInfo
    ): Unit = {
        val mintingConfig = param.to[MintingConfig]
        mintingPolicy(mintingConfig.adminPubKeyHash, mintingConfig.tokenName, policyId, tx)
    }

}

object MintingPolicyGenerator {
    // use Scalus new more efficient compiler backend to compile the minting policy
    inline given Options =
        Options(
          targetLoweringBackend = Compiler.TargetLoweringBackend.SirToUplcV3Lowering,
          generateErrorTraces = true,
          optimizeUplc = true,
          debug = false
        )
    val mintingPolicySIR: SIR = compile(MintingPolicy.validate)

    val script = mintingPolicySIR.toUplcOptimized(generateErrorTraces = true).plutusV3

    def makeMintingPolicyScript(
        adminPubKeyHash: PubKeyHash,
        tokenName: TokenName
    ): MintingPolicyScript = {
        val config = MintingConfig(adminPubKeyHash = adminPubKeyHash, tokenName = tokenName)
        MintingPolicyScript(script = script $ config.toData)
    }
}

trait MintingScript {
    def plutusScript: PlutusScript
    def scriptHash: ByteString = ByteString.fromArray(plutusScript.getScriptHash)
}

class MintingPolicyScript(val script: Program) extends MintingScript {
    lazy val plutusScript: PlutusV3Script = PlutusV3Script
        .builder()
        .`type`("PlutusScriptV3")
        .cborHex(script.doubleCborHex)
        .build()
        .asInstanceOf[PlutusV3Script]
}
