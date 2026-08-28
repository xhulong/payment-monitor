package com.example.paymentmonitor.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.example.paymentmonitor.PaymentMonitorApplication

class PaymentMonitorTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(
            cl,
            TestPaymentMonitorApplication::class.java.name,
            context,
        )
    }
}

class TestPaymentMonitorApplication : PaymentMonitorApplication() {
    override fun shouldStartRuntimeServices(): Boolean = false
}
