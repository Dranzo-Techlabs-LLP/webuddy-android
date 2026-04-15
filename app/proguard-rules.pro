# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# JNA
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# TagSoup, coming from the RTE library
-keep class org.ccil.cowan.tagsoup.** { *; }

# kotlinx.serialization

# Kotlin serialization looks up the generated serializer classes through a function on companion
# objects. The companions are looked up reflectively so we need to explicitly keep these functions.
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# If a companion has the serializer function, keep the companion field on the original type so that
# the reflective lookup succeeds.
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class <1>.<2> {
  <1>.<2>$Companion Companion;
}

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
# Taken from https://raw.githubusercontent.com/square/okhttp/master/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Needed for Posthog
-keepclassmembers class android.view.JavaViewSpy {
    static int windowAttachCount(android.view.View);
}


# Keep LogSessionId class and related classes (https://github.com/androidx/media/issues/2535)
-keep class android.media.metrics.LogSessionId { *; }
-keep class android.media.metrics.** { *; }

# Keep Media3 classes that use reflection (https://github.com/androidx/media/issues/2535)
-keep class androidx.media3.** { *; }
-dontwarn android.media.metrics.**

# New rules after AGP 8.13.1 upgrade
-dontwarn androidx.window.extensions.WindowExtensions
-dontwarn androidx.window.extensions.WindowExtensionsProvider
-dontwarn androidx.window.extensions.area.ExtensionWindowAreaPresentation
-dontwarn androidx.window.extensions.layout.DisplayFeature
-dontwarn androidx.window.extensions.layout.FoldingFeature
-dontwarn androidx.window.extensions.layout.WindowLayoutComponent
-dontwarn androidx.window.extensions.layout.WindowLayoutInfo
-dontwarn androidx.window.sidecar.SidecarDeviceState
-dontwarn androidx.window.sidecar.SidecarDisplayFeature
-dontwarn androidx.window.sidecar.SidecarInterface$SidecarCallback
-dontwarn androidx.window.sidecar.SidecarInterface
-dontwarn androidx.window.sidecar.SidecarProvider
-dontwarn androidx.window.sidecar.SidecarWindowLayoutInfo

# Also needed after AGP 8.13.1 upgrade, it seems like proguard is now more aggressive on removing unused code
-keep,allowshrinking class org.matrix.rustcomponents.sdk.** { *;}
-keep,allowshrinking class uniffi.** { *;}
-keep,allowshrinking class io.element.android.x.di.** { *; }
-keepclasseswithmembernames,allowoptimization,allowshrinking class io.element.android.** { *; }

# Keep Metro classes
-keep,allowshrinking class dev.zacsweers.metro.** { *; }

# Razorpay
-keepattributes SourceFile,LineNumberTable
-keep class com.razorpay.** {*;}
-dontwarn com.razorpay.**
-keep class com.google.android.gms.wallet.** {*;}
-keep class com.google.android.gms.common.api.** {*;}
-keep class com.google.android.gms.common.internal.ReflectedParcelable {*;}
-keep class com.google.android.gms.common.internal.safeparcel.SafeParcelable {*;}
-keep class com.google.android.gms.common.util.PlatformVersion {*;}
-keep class com.google.android.gms.tasks.** {*;}
-keep class com.google.android.gms.auth.api.phone.SmsRetriever {*;}
-keep class com.google.android.gms.auth.api.phone.SmsRetrieverClient {*;}
-keep class com.google.android.gms.auth.api.credentials.Credential {*;}
-keep class com.google.android.gms.auth.api.credentials.HintRequest {*;}
-keep class com.google.android.gms.auth.api.credentials.CredentialPickerConfig {*;}
-keep class com.google.android.gms.auth.api.credentials.Credentials {*;}
-keep class com.google.android.gms.auth.api.credentials.CredentialsClient {*;}
-keep class com.google.android.gms.auth.api.credentials.CredentialRequest {*;}
-keep class com.google.android.gms.auth.api.credentials.CredentialRequestResponse {*;}
-keep class com.google.android.gms.auth.api.credentials.IdentityProviders {*;}
-keep class com.google.android.gms.auth.api.identity.GetSignInIntentRequest {*;}
-keep class com.google.android.gms.auth.api.identity.Identity {*;}
-keep class com.google.android.gms.auth.api.identity.SignInClient {*;}
-keep class com.google.android.gms.auth.api.identity.SignInCredential {*;}
-keep class com.google.android.gms.common.api.GoogleApiClient {*;}
-keep class com.google.android.gms.common.api.PendingResult {*;}
-keep class com.google.android.gms.common.api.Status {*;}
-keep class com.google.android.gms.common.ConnectionResult {*;}
-keep class com.google.android.gms.security.ProviderInstaller {*;}
-keep class com.google.android.gms.security.ProviderInstaller$ProviderInstallListener {*;}
-keep class org.apache.http.** {*;}
-dontwarn org.apache.http.**
