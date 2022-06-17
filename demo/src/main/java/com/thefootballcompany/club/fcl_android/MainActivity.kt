package com.thefootballcompany.club.fcl_android

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nftco.flow.sdk.FlowTransactionResult
import com.thefootballcompany.club.fcl.android.AppFCL
import com.thefootballcompany.club.fcl.android.FCL
import com.thefootballcompany.club.fcl.android.model.Env
import com.thefootballcompany.club.fcl.android.model.FCLAuthResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val SCRIPT = """
                   transaction(test: String, testInt: Int) {
                       prepare(signer: AuthAccount) {
                            log(signer.address)
                            log(test)
                            log(testInt)
                       }
                   }
                """
    }

    private lateinit var fcl: FCL

    private val scope = MainScope()
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fcl = AppFCL.FCLBuilder(this)
            .appInfo {
                title("FCL-Demo-TFC")
                location("https://thefootballclub.com")
                //How to add vars
                addVar("key1", "value1")
                addVar("key2", "value2")
            }
            .env(Env.TEST_NET)
            .build()

        findViewById<TextView>(R.id.text_view_script).text = SCRIPT

        findViewById<Button>(R.id.butt_authenticate).setOnClickListener {
            if (checkIfJobIsActive()) {
                return@setOnClickListener
            }

            findViewById<TextView>(R.id.text_view_addr).text = null

            job = scope.launch {

                createAuthenticationFlow()
                    .onStart {
                        showProgress(true)
                    }
                    .onCompletion {
                        showProgress(false)
                    }
                    .catch {

                        toast("Error: Something went wrong")

                        Log.d("MainActivity", "something went wrong", it)
                    }
                    .collect {
                        findViewById<TextView>(R.id.text_view_addr).text = it.addr
                    }
            }
        }

        findViewById<Button>(R.id.butt_send_transaction).setOnClickListener {
            if (checkIfJobIsActive()) {
                return@setOnClickListener
            }

            if (!fcl.isAuthenticate()) {
                Toast.makeText(this, "Please authenticate first", Toast.LENGTH_LONG).show()
            } else {
                job = scope.launch {
                    createSndTransactionFlow()
                        .onStart {
                            showProgress(true)
                        }
                        .onCompletion {
                            showProgress(false)
                        }
                        .catch {

                            toast("Error: Something went wrong")

                            Log.d("MainActivity", "something went wrong", it)
                        }
                        .collect {
                            toast("Transaction sent with status: ${it.status}")
                        }
                }
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun createAuthenticationFlow(): Flow<FCLAuthResponse> = flow {
        val result = fcl.authenticate()
        emit(result)
    }

    private fun createSndTransactionFlow(): Flow<FlowTransactionResult> = flow {
        val result = fcl.sendAndWaitForSeal {
            script(SCRIPT)
            addArg {
                string("Test")
            }

            addArg {
                int("10")
            }
        }

        emit(result)
    }

    private fun toast(msg: String) {
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showProgress(show: Boolean) {
        findViewById<View>(R.id.progressBar).visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun checkIfJobIsActive(): Boolean {
        if (job != null && job?.isActive == true) {
            Toast.makeText(
                this,
                "Please wait for the current request request to finish first",
                Toast.LENGTH_LONG
            ).show()
            return true
        }

        return false
    }
}