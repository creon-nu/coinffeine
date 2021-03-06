package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.util.control.NoStackTrace

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.payment.okpay.OkPayClient.FeePolicy
import coinffeine.peer.payment.okpay.ws.{OkPayWebService, OkPayWebServiceClient}

class OkPayClientFactory(lookupSettings: () => OkPaySettings)
  extends OkPayProcessorActor.ClientFactory {

  private val okPay = new OkPayWebService(Some(lookupSettings().serverEndpoint))

  object NotConfiguredClient extends OkPayClient {
    private val error = new IllegalStateException(
      "OKPay's user id and/or seed token are not configured") with NoStackTrace
    override lazy val accountId: AccountId = throw error
    override def findPayment(paymentId: PaymentId) = Future.failed(error)
    override def currentBalances() = Future.failed(error)
    override def sendPayment[C <: FiatCurrency](
        to: AccountId, amount: CurrencyAmount[C], comment: String, feePolicy: FeePolicy) =
      Future.failed(error)
    override protected def executionContext = throw error
  }

  override def build(): OkPayClient = {
    val settings = lookupSettings()
    (for {
      accountId <- settings.userAccount
      seedToken <- settings.seedToken
    } yield new OkPayWebServiceClient(okPay.service, accountId, seedToken))
      .getOrElse(NotConfiguredClient)
  }

  override def shutdown(): Unit = {
    okPay.httpClient.shutdown()
  }
}
