package com.example.appusagetracker

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity

/**
 * Hosts Paystack's in-app checkout in a WebView using Paystack Inline JS and your PUBLIC
 * key. No backend is needed for this "quick" version: when Paystack's JavaScript reports a
 * successful charge, we finish with RESULT_OK and the caller unlocks premium on this phone.
 *
 * Limitation (by design of the quick version): without a server verifying the payment with
 * your SECRET key, a determined person could bypass this. Fine to start with; add a small
 * verification server later for real protection.
 */
class PaymentActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        val reference = "${PremiumManager.REF_PREFIX}-${System.currentTimeMillis()}"

        val webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        val html = buildHtml(
            publicKey = PremiumManager.PAYSTACK_PUBLIC_KEY,
            email = email,
            amountKobo = PremiumManager.PRICE_KOBO,
            currency = PremiumManager.CURRENCY,
            reference = reference,
            appName = PremiumManager.APP_NAME
        )
        webView.loadDataWithBaseURL("https://checkout.paystack.com", html, "text/html", "UTF-8", null)

        setContentView(webView)
    }

    inner class Bridge {
        @JavascriptInterface
        fun onSuccess(reference: String) {
            runOnUiThread {
                setResult(RESULT_OK, intent.putExtra(EXTRA_REFERENCE, reference))
                finish()
            }
        }

        @JavascriptInterface
        fun onCancel() {
            runOnUiThread {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_REFERENCE = "extra_reference"

        private fun buildHtml(
            publicKey: String,
            email: String,
            amountKobo: Long,
            currency: String,
            reference: String,
            appName: String
        ): String = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                html,body{height:100%;margin:0;background:#3C56D6;color:#fff;
                  font-family:-apple-system,Roboto,sans-serif;display:flex;
                  align-items:center;justify-content:center;text-align:center}
                .box{padding:24px}
                .spin{width:38px;height:38px;border:4px solid rgba(255,255,255,.3);
                  border-top-color:#fff;border-radius:50%;margin:0 auto 16px;
                  animation:s 1s linear infinite}
                @keyframes s{to{transform:rotate(360deg)}}
                button{margin-top:18px;background:#fff;color:#3C56D6;border:0;
                  border-radius:10px;padding:12px 20px;font-size:15px;font-weight:600}
              </style>
              <script src="https://js.paystack.co/v1/inline.js"></script>
            </head>
            <body>
              <div class="box">
                <div class="spin"></div>
                <div>Opening secure Paystack checkout…</div>
                <button onclick="startPay()">Tap if it doesn't open</button>
              </div>
              <script>
                function startPay(){
                  try{
                    var handler = PaystackPop.setup({
                      key: '$publicKey',
                      email: '$email',
                      amount: $amountKobo,
                      currency: '$currency',
                      ref: '$reference',
                      metadata: { custom_fields: [
                        { display_name: "App", variable_name: "app", value: "$appName" }
                      ]},
                      onClose: function(){ AndroidBridge.onCancel(); },
                      callback: function(response){ AndroidBridge.onSuccess(response.reference); }
                    });
                    handler.openIframe();
                  }catch(e){ AndroidBridge.onCancel(); }
                }
                window.onload = function(){ setTimeout(startPay, 400); };
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
