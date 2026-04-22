package com.smartlife.sakemaru.bansuke.provisioning

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import com.smartlife.sakemaru.bansuke.config.DeviceConfigStore
import com.smartlife.sakemaru.bansuke.network.MdmApiClient
import com.smartlife.sakemaru.bansuke.ui.MainActivity
import com.smartlife.sakemaru.bansuke.worker.MdmWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProvisioningActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            DevicePolicyManager.ACTION_GET_PROVISIONING_MODE -> handleGetProvisioningMode()
            DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE -> handleAdminPolicyCompliance()
            else -> handleProvisioningExtrasAndFinish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleGetProvisioningMode() {
        val result = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result.putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun handleAdminPolicyCompliance() {
        handleProvisioningExtrasAndFinish()
    }

    private fun handleProvisioningExtrasAndFinish() {
        val extras = provisioningExtras(intent)
        scope.launch {
            val store = DeviceConfigStore(applicationContext)
            withContext(Dispatchers.IO) {
                store.saveProvisioningExtras(extras)
                val granter = ManagedDevicePermissionGranter(applicationContext)
                granter.grantAllManagedPermissions()
                granter.blockUninstall()
                granter.hideLauncherIcon()
            }
            
            // 疎通確認テストの実行
            val config = store.read()
            try {
                Log.d("Bansuke", "Testing health check to: ${config.mdmBaseUrl}")
                val client = MdmApiClient(config.mdmBaseUrl)
                val response = client.healthCheck()
                Log.d("Bansuke", "Health check success: $response")
            } catch (e: Exception) {
                Log.e("Bansuke", "Health check failed: ${e.message}", e)
            }
            
            MdmWorkScheduler.scheduleLoops(applicationContext)
            MdmWorkScheduler.enqueueRegistration(applicationContext)
            MdmWorkScheduler.enqueueImmediateSync(applicationContext)
            setResult(RESULT_OK)
            startActivity(Intent(this@ProvisioningActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun provisioningExtras(intent: Intent): PersistableBundle {
        val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                PersistableBundle::class.java,
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        }

        if (bundle != null) return bundle

        // テスト用: Intent のトップレベルにある Extra を PersistableBundle に変換
        val testBundle = PersistableBundle()
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            if (value is String) {
                testBundle.putString(key, value)
            }
        }
        return testBundle
    }
}
