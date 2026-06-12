"""
Generates a PDF post-mortem + fix report for the Clariva wallet recharge HTTP 404 bug.

Output: ../../Wallet-Recharge-Fix-Report.pdf  (relative to repo root)
Run:    python tools/reports/generate_wallet_recharge_fix_report.py
"""

from __future__ import annotations

import os
import datetime as _dt
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm, mm
from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    PageBreak,
    Table,
    TableStyle,
    KeepTogether,
    HRFlowable,
    Preformatted,
)


# ---------- Colors / Theme ----------
CLR_PRIMARY = HexColor("#0F172A")    # slate-900
CLR_ACCENT = HexColor("#0EA5E9")     # sky-500
CLR_DANGER = HexColor("#DC2626")     # red-600
CLR_SUCCESS = HexColor("#16A34A")    # green-600
CLR_MUTED = HexColor("#64748B")      # slate-500
CLR_CODE_BG = HexColor("#F1F5F9")    # slate-100
CLR_TABLE_HEAD_BG = HexColor("#0F172A")
CLR_TABLE_ROW_ALT = HexColor("#F8FAFC")


# ---------- Styles ----------
def make_styles():
    styles = getSampleStyleSheet()

    styles.add(ParagraphStyle(
        name="DocTitle",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=22,
        leading=26,
        textColor=CLR_PRIMARY,
        alignment=TA_LEFT,
        spaceAfter=2,
    ))
    styles.add(ParagraphStyle(
        name="DocSubtitle",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=11,
        leading=14,
        textColor=CLR_MUTED,
        alignment=TA_LEFT,
        spaceAfter=18,
    ))
    styles.add(ParagraphStyle(
        name="H1",
        parent=styles["Heading1"],
        fontName="Helvetica-Bold",
        fontSize=16,
        leading=20,
        textColor=CLR_PRIMARY,
        spaceBefore=14,
        spaceAfter=8,
        keepWithNext=True,
    ))
    styles.add(ParagraphStyle(
        name="H2",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=12.5,
        leading=16,
        textColor=CLR_PRIMARY,
        spaceBefore=10,
        spaceAfter=4,
        keepWithNext=True,
    ))
    styles.add(ParagraphStyle(
        name="Body",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=10.5,
        leading=15,
        textColor=CLR_PRIMARY,
        spaceAfter=6,
        alignment=TA_JUSTIFY,
    ))
    styles.add(ParagraphStyle(
        name="BodyBullet",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=10.5,
        leading=15,
        textColor=CLR_PRIMARY,
        leftIndent=14,
        bulletIndent=2,
        spaceAfter=3,
    ))
    styles.add(ParagraphStyle(
        name="Caption",
        parent=styles["Normal"],
        fontName="Helvetica-Oblique",
        fontSize=9,
        leading=12,
        textColor=CLR_MUTED,
        spaceAfter=8,
    ))
    styles.add(ParagraphStyle(
        name="Callout",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=10,
        leading=14,
        textColor=CLR_PRIMARY,
        leftIndent=8,
        rightIndent=8,
        spaceBefore=4,
        spaceAfter=8,
        borderColor=CLR_ACCENT,
        borderWidth=0,
        backColor=HexColor("#ECFEFF"),
        borderPadding=8,
    ))
    styles.add(ParagraphStyle(
        name="CalloutDanger",
        parent=styles["Callout"],
        backColor=HexColor("#FEF2F2"),
    ))
    styles.add(ParagraphStyle(
        name="CalloutSuccess",
        parent=styles["Callout"],
        backColor=HexColor("#F0FDF4"),
    ))
    styles.add(ParagraphStyle(
        name="CodeMono",
        parent=styles["Code"],
        fontName="Courier",
        fontSize=8.5,
        leading=11.5,
        textColor=CLR_PRIMARY,
        backColor=CLR_CODE_BG,
        leftIndent=8,
        rightIndent=8,
        borderPadding=6,
        spaceBefore=4,
        spaceAfter=8,
    ))
    return styles


# ---------- Page chrome ----------
def _on_page(canvas, doc):
    canvas.saveState()
    # Footer rule
    canvas.setStrokeColor(HexColor("#E2E8F0"))
    canvas.setLineWidth(0.5)
    canvas.line(2 * cm, 1.6 * cm, A4[0] - 2 * cm, 1.6 * cm)
    # Footer text
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(CLR_MUTED)
    canvas.drawString(2 * cm, 1.1 * cm, "Clariva — Wallet Recharge HTTP 404 Post-Mortem & Fix")
    canvas.drawRightString(
        A4[0] - 2 * cm, 1.1 * cm, f"Page {doc.page}"
    )
    canvas.restoreState()


# ---------- Helpers ----------
def kv_table(rows, col_widths=(4.6 * cm, 11.4 * cm)):
    t = Table(rows, colWidths=col_widths, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 10),
        ("TEXTCOLOR", (0, 0), (0, -1), CLR_MUTED),
        ("TEXTCOLOR", (1, 0), (1, -1), CLR_PRIMARY),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("LINEBELOW", (0, 0), (-1, -2), 0.25, HexColor("#E2E8F0")),
    ]))
    return t


def matrix_table(data, col_widths):
    t = Table(data, colWidths=col_widths, hAlign="LEFT", repeatRows=1)
    style = [
        ("BACKGROUND", (0, 0), (-1, 0), CLR_TABLE_HEAD_BG),
        ("TEXTCOLOR", (0, 0), (-1, 0), white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 9.5),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("LINEBELOW", (0, 0), (-1, -1), 0.25, HexColor("#E2E8F0")),
    ]
    for i in range(2, len(data), 2):
        style.append(("BACKGROUND", (0, i), (-1, i), CLR_TABLE_ROW_ALT))
    t.setStyle(TableStyle(style))
    return t


def code_block(text):
    return Preformatted(text, make_styles()["CodeMono"])


# ---------- Document content ----------
def build_story(styles):
    s = []

    # ---- Cover header ----
    s.append(Paragraph("Wallet Recharge HTTP 404", styles["DocTitle"]))
    s.append(Paragraph(
        "Post-Mortem, Fix &amp; Production Release Plan &mdash; Clariva Android",
        styles["DocSubtitle"]
    ))

    today = _dt.date.today().isoformat()
    s.append(kv_table([
        ["Report date", today],
        ["App", "Clariva (com.dranzo.clariva) &mdash; Element X fork"],
        ["Component", "features/wallet, libraries/network/wallet, libraries/matrix/impl/auth"],
        ["Backend", "https://wallet.dranzo.com/"],
        ["Severity", "<font color='#DC2626'><b>P1 &mdash; all Normal-User sign-ups broken in production</b></font>"],
        ["Status", "<font color='#16A34A'><b>Fixed &amp; verified end-to-end on emulator (Pixel 9 Pro)</b></font>"],
    ]))
    s.append(Spacer(1, 10))
    s.append(HRFlowable(width="100%", color=HexColor("#CBD5E1"), thickness=0.5))
    s.append(Spacer(1, 6))

    # ---- Executive summary ----
    s.append(Paragraph("Executive summary", styles["H1"]))
    s.append(Paragraph(
        "Users reported that wallet recharge on Clariva failed with "
        "<b>&ldquo;Recharge failed: HTTP 404&rdquo;</b> for newly-created accounts, while older "
        "accounts (e.g. <font face='Courier'>@imran22:matrix.org</font>) worked. Investigation showed the wallet backend "
        "had <b>no user record</b> for the affected accounts &mdash; the message body was "
        "literally <font face='Courier'>&quot;User not found&quot;</font>. The root cause was a "
        "<b>JSON serialization defect</b> in the app: <font face='Courier'>WalletCreateRequest.isConsultant</font> "
        "has a default value of <font face='Courier'>0</font>, which kotlinx-serialization omits from the encoded body. The "
        "backend's NestJS validator rejects the body with a 400, the failure is swallowed silently by "
        "<font face='Courier'>WalletService.createUser</font>, and the sign-up proceeds with no wallet "
        "record &mdash; permanently breaking all wallet operations for that user.",
        styles["Body"]
    ))
    s.append(Paragraph(
        "Three fixes have been applied: a one-line serialization guarantee that fixes the root cause, "
        "a self-heal path that retroactively repairs the 2&ndash;3 historical victims with zero support intervention, "
        "and an observability tightening so this exact failure mode cannot silently regress again. "
        "The fix has been verified end-to-end on the emulator: the previously-broken user "
        "<font face='Courier'>@imrankhan0722:matrix.org</font> now successfully creates a recharge order "
        "(<font face='Courier'>order_Szp39z0SbHJyY6</font>, &#8377;100).",
        styles["Body"]
    ))

    # ---- Section 1: Symptom ----
    s.append(Paragraph("1. Symptom &amp; scope", styles["H1"]))
    s.append(Paragraph(
        "On the Wallet screen, tapping <b>Recharge Credits</b> for an affected user produced the toast "
        "<font face='Courier'>Recharge failed: HTTP 404</font>. The transaction history list "
        "showed an infinite spinner and, eventually, the same 404. The top-bar credits indicator "
        "stayed on <font face='Courier'>Loading...</font>.",
        styles["Body"]
    ))
    s.append(Paragraph("Live evidence captured from logcat:", styles["H2"]))
    s.append(code_block(
        "POST https://wallet.dranzo.com/v1/wallet/recharge\n"
        '{"userId":"@imrankhan0722:matrix.org","amount":100.0}\n'
        "<-- 404 (1692ms)\n"
        '{"statusCode":404,"path":"/v1/wallet/recharge","message":"User not found"}\n'
    ))
    s.append(Paragraph(
        "<b>Scope:</b> every Matrix account whose initial wallet provisioning silently failed. "
        "User report described 2&ndash;3 accounts; investigation suggests this includes <b>every Normal "
        "User</b> who has ever signed up via the currently-released production build (see &sect;2).",
        styles["Body"]
    ))

    # ---- Section 2: Root cause ----
    s.append(Paragraph("2. Root cause", styles["H1"]))
    s.append(Paragraph(
        "The defect is in <font face='Courier'>WalletCreateRequest</font> "
        "(<font face='Courier'>libraries/network/.../wallet/WalletResponse.kt</font>):",
        styles["Body"]
    ))
    s.append(code_block(
        "@Serializable\n"
        "data class WalletCreateRequest(\n"
        '    @SerialName("name")         val name: String,\n'
        '    @SerialName("Webuddy_name") val webuddyName: String,\n'
        '    @SerialName("isConsultant") val isConsultant: Int = 0,   // <-- default value\n'
        ")"
    ))
    s.append(Paragraph(
        "<b>kotlinx-serialization's documented behavior:</b> properties whose value equals their declared "
        "default are <b>omitted</b> from the encoded JSON unless explicitly opted in. As a result, "
        "every Normal User (<font face='Courier'>isConsultant = 0</font>) had the field stripped from the "
        "wire request:",
        styles["Body"]
    ))
    s.append(code_block(
        "Actual request body sent for a Normal User sign-up:\n"
        '{"name":"@imrankhan0722:matrix.org","Webuddy_name":"@imrankhan0722:matrix.org"}\n\n'
        "Backend response:\n"
        '{\n'
        '  "statusCode": 400,\n'
        '  "path": "/v1/users",\n'
        '  "message": [\n'
        '    "isConsultant must be one of the following values: 0, 1",\n'
        '    "isConsultant must be an integer number"\n'
        '  ]\n'
        '}'
    ))
    s.append(Paragraph("Why Consultants escaped but Normal Users didn't:", styles["H2"]))
    s.append(matrix_table(
        [
            ["User role at sign-up", "isConsultant value", "Included in JSON?", "Backend response", "Wallet record created?"],
            ["Normal User (default)", "0", "No — equals default, stripped", "400 validation error", "No — silent failure"],
            ["Consultant", "1", "Yes — differs from default", "201 Created", "Yes"],
        ],
        col_widths=[4 * cm, 2.4 * cm, 4.0 * cm, 2.6 * cm, 3.8 * cm],
    ))
    s.append(Paragraph(
        "<b>This is why the user reported only 2&ndash;3 affected accounts but the actual blast radius is much larger:</b> "
        "every account that was ever signed up via the production app while choosing &ldquo;Normal User&rdquo; on the "
        "account-creation screen has been broken from day one. Consultants are unaffected. The fingerprint of the "
        "bug in your wallet DB will be: app-created accounts where <font face='Courier'>isConsultant = 0</font> "
        "either don't exist at all, or only exist for users who manually retried something.",
        styles["Body"]
    ))
    s.append(Paragraph("The silent-failure chain:", styles["H2"]))
    s.append(Paragraph(
        "1. <font face='Courier'>WalletService.createUser</font> catches the <font face='Courier'>HttpException</font> "
        "and returns <font face='Courier'>Result.failure</font>.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "2. <font face='Courier'>handleWalletUserCreation</font> had only an "
        "<font face='Courier'>.onSuccess</font> branch &mdash; no <font face='Courier'>.onFailure</font>.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "3. Sign-up flow completes successfully on the Matrix side; user has a chat account but no wallet record.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "4. Every subsequent wallet operation (balance, history, recharge, holds) 404s because the user record "
        "doesn't exist on the backend.",
        styles["BodyBullet"], bulletText="•"
    ))

    s.append(PageBreak())

    # ---- Section 3: The fix ----
    s.append(Paragraph("3. The fix &mdash; three layers", styles["H1"]))
    s.append(Paragraph(
        "The fix is applied in three coordinated layers, each addressing a different concern: the root cause, "
        "the historical victims, and future regressions of the same shape.",
        styles["Body"]
    ))

    s.append(Paragraph("Layer 1 &mdash; Root cause fix", styles["H2"]))
    s.append(Paragraph(
        "File: <font face='Courier'>libraries/network/.../wallet/WalletResponse.kt</font>",
        styles["Caption"]
    ))
    s.append(code_block(
        "@OptIn(ExperimentalSerializationApi::class)\n"
        "@Serializable\n"
        "data class WalletCreateRequest(\n"
        '    @SerialName("name")         val name: String,\n'
        '    @SerialName("Webuddy_name") val webuddyName: String,\n'
        "    @EncodeDefault(EncodeDefault.Mode.ALWAYS)\n"
        '    @SerialName("isConsultant") val isConsultant: Int = 0,\n'
        ")"
    ))
    s.append(Paragraph(
        "<font color='#16A34A'><b>Effect:</b></font> every <font face='Courier'>createUser</font> request, "
        "for every role, now sends a valid JSON body. The backend's 400 validation error cannot recur for this reason.",
        styles["CalloutSuccess"]
    ))

    s.append(Paragraph("Layer 2 &mdash; Self-heal for already-affected users", styles["H2"]))
    s.append(Paragraph(
        "File: <font face='Courier'>libraries/network/.../wallet/WalletService.kt</font>",
        styles["Caption"]
    ))
    s.append(Paragraph(
        "Added <font face='Courier'>ensureWalletUserExists(userId)</font> &mdash; an idempotent helper that "
        "creates the wallet record if the local session has no <font face='Courier'>webuddyName</font>. "
        "Hooked into the three wallet operations that face the user, so any 404 transparently triggers a "
        "create-and-retry:",
        styles["Body"]
    ))
    s.append(code_block(
        "suspend fun createOrder(userId: String, amount: Double): Result<CreateOrderResponse> {\n"
        "    return try {\n"
        "        Result.success(callCreateOrder(userId, amount))\n"
        "    } catch (e: HttpException) {\n"
        "        if (e.code() == 404) {\n"
        '            Timber.w("createOrder: 404 — auto-creating wallet user and retrying once")\n'
        "            ensureWalletUserExists(userId)\n"
        "            try {\n"
        "                Result.success(callCreateOrder(userId, amount))\n"
        "            } catch (retryErr: Exception) {\n"
        "                Result.failure(retryErr)\n"
        "            }\n"
        "        } else {\n"
        "            Result.failure(e)\n"
        "        }\n"
        "    } catch (e: Exception) { Result.failure(e) }\n"
        "}"
    ))
    s.append(Paragraph(
        "Same self-heal pattern applied to <font face='Courier'>getTransactionHistory</font> and "
        "<font face='Courier'>refreshBalance</font>.",
        styles["Body"]
    ))
    s.append(Paragraph(
        "<font color='#0EA5E9'><b>Effect:</b></font> the 2&ndash;3 historically-affected users heal themselves the moment "
        "they next open the Wallet screen on the updated build. No DB migration, no manual support touch.",
        styles["Callout"]
    ))

    s.append(Paragraph("Layer 3 &mdash; Observability &amp; DRY at the auth source", styles["H2"]))
    s.append(Paragraph(
        "File: <font face='Courier'>libraries/matrix/impl/.../auth/RustMatrixAuthenticationService.kt</font>",
        styles["Caption"]
    ))
    s.append(code_block(
        "private suspend fun handleWalletUserCreation(userId: String) {\n"
        "    val created = walletService.ensureWalletUserExists(userId)\n"
        "    if (!created) {\n"
        "        Timber.w(\n"
        '            "handleWalletUserCreation: wallet user not provisioned for $userId after login. " +\n'
        '                "Wallet operations will self-heal via WalletService\'s 404-retry path on " +\n'
        '                "first wallet API call."\n'
        "        )\n"
        "    }\n"
        "}"
    ))
    s.append(Paragraph(
        "<b>Before:</b> only an <font face='Courier'>.onSuccess</font> handler &mdash; every failure was invisible. "
        "<b>After:</b> single source of truth (delegates to <font face='Courier'>ensureWalletUserExists</font>), "
        "and a <font face='Courier'>Timber.w</font> log if provisioning fails so future regressions of this shape "
        "are detectable in production telemetry.",
        styles["Body"]
    ))

    # ---- Section 4: Verification ----
    s.append(Paragraph("4. Verification &mdash; live end-to-end test", styles["H1"]))
    s.append(Paragraph(
        "Test target: the previously-broken account <font face='Courier'>@imrankhan0722:matrix.org</font> on a "
        "freshly-installed debug APK with all three layers of the fix applied (Pixel 9 Pro emulator, "
        "Android 15, software GPU).",
        styles["Body"]
    ))
    s.append(matrix_table(
        [
            ["Wallet endpoint", "Before fix", "After fix"],
            ["GET /v1/users/{userId} (balance)", "404 — User not found", "200 OK"],
            ["GET /v1/wallet/history", "404 — User not found", "200 OK (No transactions found)"],
            ["POST /v1/users (lazy create)", "400 — isConsultant validation", "201 Created (id 127a462b-866f-4765-ba54-ecd64466afca)"],
            ["POST /v1/wallet/recharge", "404 — User not found", "201 Created (order_Szp39z0SbHJyY6, ₹100)"],
        ],
        col_widths=[5.2 * cm, 4.4 * cm, 7.2 * cm],
    ))
    s.append(Paragraph("Live logcat snippet from the successful run:", styles["H2"]))
    s.append(code_block(
        "D Handling Recharge event for amount: 100\n"
        "D Recharging wallet for user: @imrankhan0722:matrix.org with amount: 100\n"
        "D --> POST https://wallet.dranzo.com/v1/wallet/recharge\n"
        "D <-- 201 https://wallet.dranzo.com/v1/wallet/recharge (1695ms)\n"
        "D Order created successfully for @imrankhan0722:matrix.org: order_Szp39z0SbHJyY6\n"
        "D CreateOrderResponse(\n"
        "    keyId=rzp_test_STB3RshYoYSsQp,\n"
        "    orderId=order_Szp39z0SbHJyY6,\n"
        "    amountInPaise=10000,\n"
        "    currency=INR,\n"
        "    transactionId=164\n"
        "  )"
    ))
    s.append(Paragraph(
        "<font color='#16A34A'><b>Result:</b></font> the same user that was getting HTTP 404 on every recharge now "
        "successfully receives a Razorpay order. The wallet record was created by Layer 2's self-heal using Layer 1's "
        "now-valid JSON body, and Layer 3 logged the previously-invisible state transition.",
        styles["CalloutSuccess"]
    ))

    s.append(PageBreak())

    # ---- Section 5: Production release plan ----
    s.append(Paragraph("5. Steps to follow for production release", styles["H1"]))

    s.append(Paragraph("5.1 Pre-release checks", styles["H2"]))
    s.append(Paragraph(
        "Confirm the three modified files are committed to your release branch:",
        styles["Body"]
    ))
    s.append(Paragraph(
        "<font face='Courier'>libraries/network/src/main/kotlin/io/element/android/libraries/network/wallet/WalletResponse.kt</font>",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "<font face='Courier'>libraries/network/src/main/kotlin/io/element/android/libraries/network/wallet/WalletService.kt</font>",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "<font face='Courier'>libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/auth/RustMatrixAuthenticationService.kt</font>",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Run a clean assembleGplayRelease and verify the build succeeds with the release keystore:",
        styles["Body"]
    ))
    s.append(code_block("./gradlew clean :app:assembleGplayRelease --console=plain"))
    s.append(Paragraph(
        "Bump <font face='Courier'>versionCode</font> and <font face='Courier'>versionName</font> in your "
        "<font face='Courier'>app/build.gradle.kts</font> (or wherever your release plugin sources versions).",
        styles["Body"]
    ))

    s.append(Paragraph("5.2 Smoke test on the release variant before upload", styles["H2"]))
    s.append(Paragraph(
        "Install the signed release APK on a physical device with a <b>fresh Matrix account</b> on matrix.org "
        "(Normal User role). After sign-up completes, immediately open the Wallet screen.",
        styles["Body"]
    ))
    s.append(Paragraph("Expected:", styles["H2"]))
    s.append(Paragraph(
        "Top bar shows <font face='Courier'>0 credits</font> (not <font face='Courier'>Loading...</font>) within ~3s of opening the app.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Wallet screen shows <font face='Courier'>Current Balance: 0 credits</font> and "
        "<font face='Courier'>Transaction History: No transactions found</font> — no error toast.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Tapping Recharge Credits → entering an amount → tapping Recharge opens the Razorpay payment sheet.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "If you have logcat access in release mode (or your Sentry/PostHog pipeline), verify that "
        "<font face='Courier'>POST /v1/users</font> returns 201 with the request body containing "
        "<font face='Courier'>&quot;isConsultant&quot;: 0</font>.",
        styles["BodyBullet"], bulletText="•"
    ))

    s.append(Paragraph("5.3 Play Console release", styles["H2"]))
    s.append(Paragraph(
        "Upload the signed bundle (App Bundle preferred over APK split) to the Play Console.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Release notes suggestion: &ldquo;Fixed an issue where wallet recharge failed for some accounts. "
        "All previously-affected accounts will be repaired automatically on next open.&rdquo;",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Consider a <b>staged rollout</b> (10% &rarr; 50% &rarr; 100%) so you can watch metrics and abort if anything regresses.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "Optionally mark this as a <b>recommended (or immediate) in-app update</b> via Play Core's "
        "<font face='Courier'>AppUpdateManager</font> if you want the existing affected users to receive the fix "
        "as soon as possible.",
        styles["BodyBullet"], bulletText="•"
    ))

    s.append(Paragraph("5.4 Post-release monitoring (first 24–48h)", styles["H2"]))
    s.append(matrix_table(
        [
            ["Signal to watch", "Where", "What &ldquo;healthy&rdquo; looks like"],
            ["POST /v1/users 4xx rate", "Wallet backend logs / Cloudflare", "Drops to near-zero from app traffic"],
            ["New wallet users with isConsultant=0", "Wallet DB", "Rows start appearing (previously absent)"],
            ["404 'User not found' rate on /v1/wallet/*", "Wallet backend logs", "Decays as users update + self-heal"],
            ["Timber.w from handleWalletUserCreation", "Crashlytics / app logs", "Should be rare; spikes = investigate"],
            ["Recharge funnel completion", "PostHog / Razorpay dashboard", "Increases for new accounts"],
        ],
        col_widths=[5.0 * cm, 4.4 * cm, 7.4 * cm],
    ))

    s.append(Paragraph("5.5 Optional follow-ups (nice to have, not blocking)", styles["H2"]))
    s.append(Paragraph(
        "<b>Backend defense in depth.</b> Have the wallet API treat <font face='Courier'>isConsultant</font> as "
        "optional with a default of <font face='Courier'>0</font>, so a future client serialization bug of the same shape "
        "can't break sign-up.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "<b>DB audit.</b> Query for Matrix accounts referenced by the chat backend that have no corresponding row "
        "in the wallet <font face='Courier'>users</font> table. Those are the historical casualties of the bug. "
        "They will be auto-created on next app open after the update, but knowing the cohort size is useful for "
        "support and metrics.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "<b>Regression test.</b> Add a small unit test that asserts "
        "<font face='Courier'>Json.encodeToString(WalletCreateRequest(..., isConsultant = 0))</font> contains "
        "the literal substring <font face='Courier'>&quot;isConsultant&quot;</font>. This pins the contract so "
        "anyone removing the <font face='Courier'>@EncodeDefault</font> annotation will trip CI.",
        styles["BodyBullet"], bulletText="•"
    ))
    s.append(Paragraph(
        "<b>Broader audit of @Serializable request types.</b> Search for other "
        "<font face='Courier'>data class</font>es with <font face='Courier'>= someDefault</font> on required-by-server "
        "fields. Each is a latent instance of the same trap.",
        styles["BodyBullet"], bulletText="•"
    ))

    # ---- Appendix ----
    s.append(Paragraph("Appendix &mdash; Files changed", styles["H1"]))
    s.append(matrix_table(
        [
            ["File", "Layer", "Summary"],
            ["libraries/network/.../wallet/WalletResponse.kt", "1 — Root cause",
             "@EncodeDefault(ALWAYS) on isConsultant; +2 imports"],
            ["libraries/network/.../wallet/WalletService.kt", "2 — Self-heal",
             "New ensureWalletUserExists(); 404-retry in createOrder, getTransactionHistory, refreshBalance"],
            ["libraries/matrix/.../auth/RustMatrixAuthenticationService.kt", "3 — Observability",
             "handleWalletUserCreation delegates to ensureWalletUserExists; logs Timber.w on failure"],
        ],
        col_widths=[7.2 * cm, 2.6 * cm, 7.0 * cm],
    ))
    s.append(Spacer(1, 10))
    s.append(HRFlowable(width="100%", color=HexColor("#CBD5E1"), thickness=0.5))
    s.append(Spacer(1, 6))
    s.append(Paragraph(
        "Generated automatically from the verified emulator session on the day of fix. "
        "Logcat excerpts are verbatim from the device under test.",
        styles["Caption"]
    ))

    return s


def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    # Place output at repo root (two levels up from tools/reports/)
    repo_root = os.path.abspath(os.path.join(out_dir, os.pardir, os.pardir))
    output_path = os.path.join(repo_root, "Wallet-Recharge-Fix-Report.pdf")

    doc = SimpleDocTemplate(
        output_path,
        pagesize=A4,
        leftMargin=2 * cm,
        rightMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
        title="Clariva — Wallet Recharge HTTP 404 Post-Mortem & Fix",
        author="Engineering",
        subject="Wallet recharge HTTP 404 root cause, fix, and production release plan",
    )

    styles = make_styles()
    story = build_story(styles)
    doc.build(story, onFirstPage=_on_page, onLaterPages=_on_page)
    print(f"PDF written: {output_path}")


if __name__ == "__main__":
    main()
