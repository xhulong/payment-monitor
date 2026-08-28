# Preserve runtime annotations and generic signatures used by Retrofit, Gson and Room.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations

# Retrofit creates the device API implementation from method annotations.
-keep,allowoptimization,allowshrinking interface com.example.paymentmonitor.sync.PaymentDeviceApi
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# API envelopes and request/response DTOs use Gson field names as the wire contract.
-keep class com.example.paymentmonitor.sync.DeviceApiEnvelope { *; }
-keep class com.example.paymentmonitor.sync.DeviceApiErrorData { *; }
-keep class com.example.paymentmonitor.sync.PublicApiResponse { *; }
-keep class com.example.paymentmonitor.sync.AppReleaseData { *; }
-keep class com.example.paymentmonitor.sync.PairDeviceRequest { *; }
-keep class com.example.paymentmonitor.sync.PairDeviceData { *; }
-keep class com.example.paymentmonitor.sync.DeviceConfigData { *; }
-keep class com.example.paymentmonitor.sync.HeartbeatRequest { *; }
-keep class com.example.paymentmonitor.sync.PaymentEventBatchRequest { *; }
-keep class com.example.paymentmonitor.sync.PaymentEventItemData { *; }
-keep class com.example.paymentmonitor.sync.PaymentEventBatchData { *; }
-keep class com.example.paymentmonitor.sync.RejectedEventData { *; }

# Room and Compose also provide consumer rules; these explicit entries protect
# the application database and generated JSON snapshots during release shrinking.
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class **_Impl { *; }

-dontwarn javax.annotation.**
