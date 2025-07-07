package starter

import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.api.model.ProtocolParams
import com.bloxbean.cardano.client.api.model.Result
import com.bloxbean.cardano.client.backend.api.BackendService
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService
import com.bloxbean.cardano.client.common.model.Network
import com.bloxbean.cardano.client.common.model.Networks
import com.bloxbean.cardano.client.function.helper.SignerProviders
import com.bloxbean.cardano.client.plutus.spec.PlutusData
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder
import com.bloxbean.cardano.client.quicktx.ScriptTx
import com.bloxbean.cardano.client.transaction.spec.Asset
import com.bloxbean.cardano.client.transaction.spec.Transaction
import scalus.bloxbean.EvaluatorMode
import scalus.bloxbean.NoScriptSupplier
import scalus.bloxbean.ScalusTransactionEvaluator
import scalus.bloxbean.SlotConfig
import scalus.builtin.ByteString
import scalus.ledger.api.v1.PubKeyHash
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.math.BigInteger

case class AppCtx(
    network: Network,
    account: Account,
    backendService: BackendService,
    tokenName: String
) {
    lazy val pubKeyHash: PubKeyHash = PubKeyHash(
      ByteString.fromArray(account.hdKeyPair().getPublicKey.getKeyHash)
    )
    lazy val tokenNameByteString: ByteString = ByteString.fromString(tokenName)
    // combined minting script hash and token name
    lazy val unitName: String = (mintingScript.scriptHash ++ tokenNameByteString).toHex
    lazy val mintingScript: MintingPolicyScript =
        MintingPolicyGenerator.makeMintingPolicyScript(pubKeyHash, tokenNameByteString)
}

object AppCtx {
    def apply(
        network: Network,
        mnemonic: String,
        blockfrostApiKey: String,
        tokenName: String
    ): AppCtx = {
        val url =
            if network == Networks.mainnet() then Constants.BLOCKFROST_MAINNET_URL
            else if network == Networks.preview() then Constants.BLOCKFROST_PREVIEW_URL
            else if network == Networks.preprod() then Constants.BLOCKFROST_PREPROD_URL
            else sys.error(s"Unsupported network: $network")
        new AppCtx(
          network,
          new Account(network, mnemonic),
          new BFBackendService(url, blockfrostApiKey),
          tokenName
        )
    }

    def uzhCtx(mnemonic: String, tokenName: String): AppCtx = {
        val url = "http://130.60.24.200:3000"
        val network = new Network(0, 0)
        new AppCtx(
          network,
          new Account(network, mnemonic),
          new BFBackendService(url, ""),
          tokenName
        )
    }

    def yaciDevKit(tokenName: String): AppCtx = {
        val url = "http://localhost:8080/api/v1/"
        val network = new Network(0, 42)
        val mnemonic =
            "test test test test test test test test test test test test test test test test test test test test test test test sauce"
        new AppCtx(
          network,
          new Account(network, mnemonic),
          new BFBackendService(url, ""),
          tokenName
        )
    }
}

extension [A](result: Result[A])
    def toEither: Either[String, A] =
        if result.isSuccessful then Right(result.getValue)
        else Left(result.getResponse)

class TxBuilder(ctx: AppCtx) {
    private val backendService = ctx.backendService
    private val account = ctx.account
    private lazy val quickTxBuilder = QuickTxBuilder(backendService)

    lazy val protocolParams: ProtocolParams = {
        val result = backendService.getEpochService.getProtocolParameters
        if !result.isSuccessful then sys.error(result.getResponse)
        result.getValue
    }

    private lazy val utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService)

    private lazy val evaluator = ScalusTransactionEvaluator(
      slotConfig = SlotConfig.Preprod,
      protocolParams = protocolParams,
      utxoSupplier = utxoSupplier,
      scriptSupplier = NoScriptSupplier(),
      mode = EvaluatorMode.EVALUATE_AND_COMPUTE_COST
    )

    private val address: String = account.getBaseAddress.getAddress

    def makeMintingTx(amount: Long): Either[String, Transaction] = {
        for
            utxo <- backendService.getUtxoService
                .getUtxos(address, 100, 1)
                .toEither

            scriptTx = new ScriptTx()
                .mintAsset(
                  ctx.mintingScript.plutusScript,
                  Asset.builder().name(ctx.tokenName).value(BigInteger.valueOf(amount)).build(),
                  PlutusData.unit(),
                  address
                )
                .collectFrom(utxo)
                .withChangeAddress(address)

            signedTx = quickTxBuilder
                .compose(scriptTx)
                // evaluate script cost using scalus
                .withTxEvaluator(evaluator)
                .withSigner(SignerProviders.signerFrom(account))
                .withRequiredSigners(account.getBaseAddress)
                .feePayer(account.baseAddress())
                .buildAndSign()
        yield signedTx
    }

    def makeBurningTx(amount: Long): Either[String, Transaction] = {
        for
            utxo <- backendService.getUtxoService
                .getUtxos(address, ctx.unitName, 100, 1)
                .toEither

            scriptTx = new ScriptTx()
                .mintAsset(
                  ctx.mintingScript.plutusScript,
                  Asset.builder().name(ctx.tokenName).value(BigInteger.valueOf(amount)).build(),
                  PlutusData.unit(),
                  address
                )
                .collectFrom(utxo)
                .withChangeAddress(address)

            signedTx = quickTxBuilder
                .compose(scriptTx)
                // evaluate script cost using scalus
                .withTxEvaluator(evaluator)
                .withSigner(SignerProviders.signerFrom(account))
                .withRequiredSigners(account.getBaseAddress)
                .feePayer(account.baseAddress())
                .buildAndSign()
        yield signedTx
    }

    def submitMintingTx(amount: Long): Either[String, String] = {
        for
            signedTx <- makeMintingTx(amount)
            result = backendService.getTransactionService.submitTransaction(signedTx.serialize())
            r <- Either.cond(result.isSuccessful, result.getValue, result.getResponse)
        yield r
    }
}

class Server(ctx: AppCtx):
    private val mint = endpoint.put
        .in("mint")
        .in(query[Long]("amount"))
        .out(stringBody)
        .errorOut(stringBody)
        .handle(mintTokens)

    private val txBuilder = TxBuilder(ctx)

    private val apiEndpoints = List(mint)
    private val swaggerEndpoints = SwaggerInterpreter()
        .fromEndpoints[[X] =>> X](apiEndpoints.map(_.endpoint), "Token Minter", "0.1")

    private def mintTokens(amount: Long): Either[String, String] =
        txBuilder.submitMintingTx(amount)

    def start(): Unit =
        NettySyncServer()
            .port(8088)
            .addEndpoints(apiEndpoints ++ swaggerEndpoints)
            .startAndWait()
