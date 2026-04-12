# QA Error Flow Test Plan

Manual test cases for all user-facing error flows. Each case covers: how to trigger the error, what the user should see, and severity.

**UI pattern:** All errors are displayed via `BottomBarManager` — a bottom sheet with title, message, and action buttons. Unless otherwise noted, errors follow this pattern.

**Error types:**
- **Expected** — Result of user actions (bad input, insufficient funds, expired links). Normal app behavior. No notification needed.
- **Unexpected** — System/server failures, bugs, or infrastructure issues. These should trigger the notifiable interface for monitoring/alerting.

---

## 1. Login / Registration

**Error type:** `LoginError`, `RegisterError`
**Entry point:** App launch → Access Key screen (enter 12-word seed phrase, scan QR, or restore from credential manager)

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 1a | Not a Flipcash account | Expected | Enter a valid 12-word seed phrase that was never registered with Flipcash | Bottom bar: **"Not a Flipcash Account"** / "Only accounts created through Flipcash are currently supported" with buttons **"Create a New Flipcash Account"** (navigates to onboarding) and **"Try a Different Flipcash Account"** | High |
| 1b | Login invalid timestamp | Expected | Set device clock **more than 2 minutes** ahead or behind, enter valid seed phrase | Bottom bar: **"Login Failed"** / "Something went wrong". User stays on Access Key screen with Continue re-enabled | High |
| 1c | Network failure during login | Expected | Enable airplane mode, enter valid seed phrase and tap Continue | Bottom bar: **"No Internet Connection"** / "Please check your internet connection or try again later." No crash. | High |
| 1d | Timelock unlocked | Expected | Enter seed phrase for an account whose timelock has been unlocked on-chain | Bottom bar: **"Access Key No Longer Usable in Flipcash"** / "Your Access Key has initiated an unlock. As a result, you will no longer be able to use this Access Key in Flipcash". Navigates back (popAll). | Medium |
| 1e | Registration denied | Unexpected | **Not currently triggerable.** Proto defines `DENIED` but server `Register` handler does not return it. | Maps to `RegisterError.Other` → **"Login Failed"** / "Something went wrong" | Low |
| 1f | Registration invalid signature | Unexpected | (Hard to trigger manually — requires corrupted keypair). Server returns gRPC `Internal`, not typed `INVALID_SIGNATURE`. | Maps to `RegisterError.Other` → **"Login Failed"** / "Something went wrong" | Low |
| 1g | Credential restore failed | Unexpected | Use credential manager restore, but credential selection fails | Bottom bar: **"Something went wrong"** / "Failed to restore selected account. Please try again" | Medium |

---

## 2. Account Creation (Onboarding)

**Error type:** `CodeAccountCheckError`, `LinkAccountsError`
**Entry point:** Access Key screen → "Create a New Account" button → account creation flow

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 2a | Account not found | Expected | Import a seed phrase that has never been registered | Bottom bar: **"Not a Flipcash Account"** prompt with options to create new or try different account | High |
| 2b | Unlocked timelock | Expected | (Requires on-chain state) Account's timelock has been unlocked | Bottom bar: **"Access Key No Longer Usable in Flipcash"** / "Your Access Key has initiated an unlock..." | Medium |
| 2c | Create account failed | Unexpected | Account creation fails server-side | Bottom bar: **"Create Account Failed"** / "Something went wrong" | High |
| 2d | Link denied (unverified) | Expected | Try to link account before phone verification | `LinkAccountsError.Denied` — error shown | High |
| 2e | Link invalid account | Unexpected | (Requires invalid account state — backend test) | `LinkAccountsError.InvalidAccount` — error shown | Medium |

---

## 3. Give Cash (Bill Presentation)

**Error type:** `SubmitIntentError`
**Entry point:** Scanner screen → tap amount input → enter dollar amount → tap **"Give"** button. This presents a bill (QR code) that another user scans to grab.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 3a | Insufficient balance | Expected | Enter an amount greater than your balance, tap Give | Bottom bar: **"You Need More Cash"** / "Please add more cash, or try again with a lower amount" with **"Add More Cash"** button (routes to deposit/onramp) and **"Dismiss"** button | Critical |
| 3b | Per-transaction limit exceeded | Expected | Enter amount exceeding the send limit for your currency (server: **$250 USD**, EUR 250, GBP 250, JPY 25,000) | Amount input turns red, hint changes to **"You can only give up to [limit]"**. If submitted: bottom bar **"Transaction Limit Reached"** / "Flipcash is designed for small, every day transactions. Send limits reset daily" | High |
| 3b2 | Daily limit exceeded | Expected | Send multiple gives totaling over **$1,000 USD** in one day | Server returns `SubmitIntentError.Denied` with `"exceeds daily usd value"`. Bill is dismissed, cash returned to wallet. Bottom bar: **"Something Went Wrong"** / "The cash was returned to your wallet" | High |
| 3b3 | Too many payments | Expected | Send many give intents rapidly | Server returns `SubmitIntentError.Denied` with `"too many payments"`. Same error display as 3b2. | High |
| 3c | Stale state | Unexpected | Open give screen, wait extended time, then present bill and have it grabbed | `SubmitIntentError.StaleState` — bill dismissed, bottom bar: **"Something Went Wrong"** / "The cash was returned to your wallet" | High |
| 3d | Network loss mid-send | Expected | Present bill, kill network before grab completes | Bottom bar: **"No Internet Connection"** / "Please check your internet connection or try again later." Bill dismissed, no stuck state. | Critical |

---

## 4. Cancel Cash Link (After Send)

**Error type:** `VoidGiftCardError`
**Entry point:** Transaction history → find pending sent cash link → tap → **"Cancel Transfer"**. A confirmation prompt appears: **"Cancel [amount] Transfer?"** / "The money will be returned to your wallet" with **"Cancel Transfer"** and **"Nevermind"** buttons.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 4a | Already collected | Expected | Send cash link → other user claims → try to cancel from history | Bottom bar: **"Something went wrong"** / "We were unable to cancel your transfer. Please try again" (the `CLAIMED_BY_OTHER_USER` server code maps to a generic cancel failure in UI) | High |
| 4b | Not found | Unexpected | (Requires expired/deleted gift card vault) | Same as 4a — generic cancel failure error | Medium |
| 4c | Denied | Unexpected | Try to cancel a link you don't own (not reproducible normally) | Same as 4a — generic cancel failure error | Low |

---

## 5. Send Cash Link

**Error type:** `SubmitIntentError`
**Entry point:** Scanner screen → enter amount → tap **"Give"** → bill appears → tap **"Send as a Link"** → native share sheet opens → select app/copy link → confirm send.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 5a | Insufficient balance | Expected | Enter amount exceeding balance. Same client-side balance check as Give Cash (3a). AML limits also apply: **$250/tx**, **$1,000/day** | Bottom bar: **"You Need More Cash"** / "Please add more cash, or try again with a lower amount" with **"Add More Cash"** button | Critical |
| 5b | Gift card funding failed | Unexpected | Share link via sheet, but server rejects the gift card funding intent | Bill dismissed. Bottom bar: **"Something Went Wrong"** / "Please try again later" | Critical |
| 5c | Network failure | Expected | Present bill, tap "Send as a Link", share succeeds but network drops during funding | Bill dismissed. Bottom bar: **"Something Went Wrong"** / "Please try again later" | Critical |

---

## 6. Receive Cash Link (Deep Link)

**Error type:** `ReceiveGiftTransactorError`
**Entry point:** Tap a `flipcash.cash/...` deep link while logged in → app attempts to claim the gift card automatically.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 6a | Already collected | Expected | Tap a cash link that was already claimed by another user | Bottom bar: **"Cash Already Collected"** / "This cash has already been collected, or was cancelled by the sender" | High |
| 6b | Expired link | Expected | Tap a cash link older than 7 days (auto-returned to sender) | Bottom bar: **"Link Expired"** / "The cash was automatically returned to the sender because it wasn't collected within 7 days. Please ask them to send the cash again" | High |
| 6c | Own gift card | Expected | Tap a cash link you sent yourself | Bottom bar prompt: **"Collect Your Own Cash?"** / "You tapped to collect the cash you sent. Are you sure you want to collect it yourself?" with **"Don't Collect"** and **"Collect"** buttons | Medium |
| 6d | Malformed deep link | Expected | Open app with garbage deep link URL | Silent failure (logged to analytics), no crash, no error shown | Medium |
| 6e | Other claim failure | Unexpected | Any other server error during gift card claim | Bottom bar: **"Something Went Wrong"** / "Something went wrong. The cash could not be collected" | High |

---

## 7-8. Cancel Cash Link (Balance/History)

Same error paths as #4 (cancel uses `cancelRemoteSend` which calls `VoidGiftCard`). Test from both entry points:

**Entry point (History):** Scanner → Wallet → Token → **"View Transaction History"** → find pending sent link → tap to expand → **"Cancel Transfer"**

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 7a | Cancel from history — success | Expected | Navigate to transaction history → find pending link → cancel | Confirmation prompt: **"Cancel [amount] Transfer?"** with **"Cancel Transfer"** / **"Nevermind"**. On success: item updates in feed. | High |
| 8a | Cancel from history — failure | Unexpected | Same as 7a, but server returns error (already claimed, etc.) | Bottom bar: **"Something went wrong"** / "We were unable to cancel your transfer. Please try again" | High |

---

## 9. Buy Currency

**Error type:** `SwapError`
**Entry point:** Scanner → Wallet → tap a currency token → **"Buy"** button → enter amount → tap **"Buy"** → processing screen.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 9a | Insufficient USDF reserves | Expected | Enter buy amount exceeding your USDF balance | Bottom bar: **"Insufficient Funds"** / "You do not have enough funds to complete this transaction. You can deposit more funds in Settings" | Critical |
| 9b | Buy exceeds limit | Expected | Enter amount exceeding buy limit hint | Amount input turns red, hint changes to **"You can only buy up to [limit]"**. If somehow submitted: bottom bar: **"Insufficient Funds"** | High |
| 9c | Buy denied (server) | Unexpected | Server denies swap (flagged account, rate limited, creator restriction, etc.) | Bottom bar: **"Something Went Wrong"** / "Please try again" | Critical |
| 9d | Invalid swap — same mint | Unexpected | (Requires client bug — from and to mint identical) | `SwapError.InvalidSwap` → bottom bar: **"Something Went Wrong"** / "Please try again" | Medium |
| 9e | New currency creator restriction | Expected | Non-creator attempts initial token buy | `SwapError.Denied` with `"only the currency creator can buy initial tokens"` → bottom bar: **"Something Went Wrong"** / "Please try again" | High |
| 9f | Mint initializing | Unexpected | Buy during currency launch race condition | `SwapError.Denied` → bottom bar: **"Something Went Wrong"** / "Please try again" | Medium |
| 9g | Signature error | Unexpected | (Hard to trigger — requires key mismatch) | `SwapError.Signature` → bottom bar: **"Something Went Wrong"** / "Please try again" | Medium |
| 9h | Network failure during buy | Expected | Kill network during swap execution | Bottom bar: **"Something Went Wrong"** / "Please try again". `buyProgress` resets. | Critical |

---

## 10. Sell Currency

**Error type:** `SwapError`
**Entry point:** Scanner → Wallet → tap a currency token → **"Sell"** button → enter amount → review sell receipt (shows fee) → confirm → processing screen.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 10a | Insufficient token balance | Expected | Enter sell amount exceeding your token balance | Bottom bar: **"Insufficient Funds"** / "You do not have enough funds to complete this transaction. You can deposit more funds in Settings" | Critical |
| 10b | Sell exceeds limit | Expected | Enter amount exceeding sell limit | Amount input turns red, hint changes to **"You can only sell up to [limit]"** | High |
| 10c | Sell denied (server) | Unexpected | Server denies swap | Bottom bar: **"Something Went Wrong"** / "Please try again" | Critical |
| 10d | Network failure during sell | Expected | Kill network mid-swap | Bottom bar: **"Something Went Wrong"** / "Please try again". `sellProgress` resets. | Critical |

---

## 11. Swap Processing

**Error type:** `SwapError` (terminal failure from polling)
**Entry point:** After buy/sell is submitted → navigates to processing screen showing **"This Will Take a Minute"** / "This transaction typically takes about a minute. You may leave the app while it completes"

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 11a | Swap reaches terminal failure (FAILED state) | Unexpected | Start buy/sell, server-side failure during processing | `processingProgress` state updates to `error = true`. Processing screen shows failure state. Token balances are refreshed. | Critical |
| 11b | Swap timeout | Unexpected | Start buy/sell, server takes too long to reach FINALIZED | Processing screen remains in loading state. User can leave app and return. | High |

---

## 12. Withdraw to External Wallet

**Error type:** `SubmitIntentError`
**Entry point:** Scanner → tap menu button → **"Withdraw Funds"** → select token to withdraw → enter amount → **"Next"** → enter destination Solana address → **"Withdraw"** → confirmation prompt → **"Yes, Withdraw"**

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 12a | Insufficient balance | Expected | Enter amount exceeding USDF balance | Bottom bar: **"Insufficient Funds"** / "You do not have enough funds to complete this transaction. You can deposit more funds in Settings" | Critical |
| 12b | Invalid destination address | Expected | Enter a non-base58 string or wrong-length address | Address field does not validate. **"Invalid address"** text shown inline. Cannot proceed. | High |
| 12c | Destination not initialized | Expected | Enter a valid Solana address that has no USDC token account | Inline error: **"Destination Account Not Initialized"** / "Please make sure the address you're withdrawing to has been initialized by your wallet provider..." | High |
| 12d | Withdrawal too small | Expected | Enter amount where after fee deduction the remainder is ≤ 0 | Bottom bar: **"Withdrawal Amount Too Small"** / "Your withdrawal amount is too small to cover the one time fee. Please try a different amount" | High |
| 12e | Withdrawal failed (server) | Unexpected | Server rejects the withdrawal intent | Bottom bar: **"Transaction Failed"** / "Failed to withdraw your funds. Something went wrong, please attempt your withdrawal again." | Critical |
| 12f | Network failure | Expected | Kill network during withdrawal | Bottom bar: **"Transaction Failed"** / "Failed to withdraw your funds..." | Critical |

---

## 13-15. Phone Verification

**Error type:** `PhoneVerificationError`
**Entry point:** Settings → **"Verify Phone Number"** → phone number input screen → enter number → **"Send Code"** → OTP code input screen.

Client-side limit: **3 send attempts** before lockout (tracked in ViewModel state). Server-side limits are Twilio-enforced.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 13a | Invalid phone number | Expected | Enter non-phone string (letters, too short). Client validates with `PhoneUtils.isPhoneNumberValid` | **"Send Code" button stays disabled** — cannot proceed. No error bottom bar. | High |
| 13b | Unsupported phone type (landline) | Expected | Enter a **landline** number. **Note:** VOIP numbers ARE accepted — only landline is rejected by Twilio carrier type check. | Bottom bar: **"Device Not Supported"** / "We are unable to support your device at this time" | Medium |
| 13c | Rate limited (send) | Expected | Request code 3+ times in one session, or hit Twilio's server-side limit (error 60203, typically 5 per service) | **Client limit (3 attempts):** Bottom bar: **"Maximum Attempts Reached"** / "Please re-enter your phone number and try again." Navigates back. **Server limit:** Bottom bar: **"Something Went Wrong"** / "Please ensure that your phone number is entered correctly" | High |
| 13d | Twilio fraud detection | Unexpected | (Not directly controllable) Twilio ML-based fraud guard blocks the number (error 60410, phone only) | Bottom bar: **"Something Went Wrong"** / "Please ensure that your phone number is entered correctly" | Medium |
| 14a | Rate limited (resend) | Expected | On OTP screen, tap **"Resend"** repeatedly (60-second cooldown timer, 3 client-side attempts) | Resend button disabled during 60-second countdown timer. After 3 attempts: **"Maximum Attempts Reached"** | High |
| 15a | Invalid verification code | Expected | Enter wrong 6-digit code on OTP screen | Bottom bar: **"Something went wrong"** / "Please enter a valid code and try again." | High |
| 15b | No active verification (timed out) | Unexpected | Wait for verification to expire server-side, then enter code | Bottom bar: **"Something went wrong"** / "Please re-enter your phone number and try again." | Medium |
| 15c | Correct code | Expected | Enter valid 6-digit code | Success animation → bottom bar: **"Verification Successful"** / "Your phone number has been successfully linked to your profile" | Critical |

---

## 16-18. Email Verification

**Error type:** `EmailVerificationError`
**Entry point:** Settings → **"Verify Your Email"** → email input screen → enter email → **"Send Code"** → waiting screen: **"Check your inbox"** / "Tap the link we sent to [email]"

Email verification uses a **link** (not OTP code). A deep link returns to the app with the verification code automatically.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 16a | Invalid email | Expected | Enter text that doesn't match `Patterns.EMAIL_ADDRESS` | **"Send Code" button stays disabled** (client-side `canSendCode` validation) — cannot proceed | High |
| 16b | Rate limited (send) | Expected | Request code 3+ times in one session, or hit Twilio's server-side limit | **Client limit (3 attempts):** Bottom bar: **"Maximum Attempts Reached"** / "Please re-enter your phone number and try again." **Server limit:** Bottom bar: **"Something Went Wrong"** / "Please ensure that your email address is entered correctly" | High |
| 17a | Rate limited (resend) | Expected | Tap **"Resend"** / **"Resend Verification Email"** repeatedly (60-second timer, 3 client attempts) | Resend disabled during 60-second countdown. After 3 attempts: **"Maximum Attempts Reached"** | High |
| 18a | Invalid verification link | Expected | Tap an invalid/corrupted verification link | Bottom bar: **"Verification Link Invalid"** / "This verification link is invalid. Please try again" with **"Resend Verification Email"** button and Cancel | High |
| 18b | Expired verification link | Expected | Wait for link to expire, then tap it | Bottom bar: **"Verification Link Expired"** / "This verification link has expired. Please try again" with **"Resend Verification Email"** button and Cancel | Medium |
| 18c | Other verification failure | Unexpected | Any other server error during email check | Bottom bar: **"Something Went Wrong"** / "Please try again" with **"OK"** button. Dismissing (not via action) navigates back (Exit). | Medium |
| 18d | Correct link | Expected | Tap valid verification link while on email screen | Success animation → bottom bar: **"Verification Successful"** / "Your email address has been successfully linked to your profile" | Critical |

---

## 19-20. Coinbase Order

**Error type:** `CoinbaseOnRampWebError`
**Entry point:** Scanner → Wallet → **"Add Cash"** → **"Debit Card with Google Pay"** (staff-only, or via deposit flow) → Coinbase WebView checkout

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 19a | Card not debit | Expected | Use a credit card in Coinbase flow | `GUEST_CARD_NOT_DEBIT` error shown in WebView | High |
| 19b | Google Pay error | Expected | Use device without Google Pay configured | `GUEST_GOOGLE_PAY_ERROR` message in WebView | High |
| 19c | Transaction buy failed | Expected | Coinbase rejects the purchase (insufficient funds on card) | `GUEST_TRANSACTION_BUY_FAILED` error | Critical |
| 19d | AVS validation failed | Expected | Enter mismatched billing address | `AVS_VALIDATION_FAILED` error in WebView | High |
| 19e | Transaction send failed | Unexpected | Coinbase completes buy but send to wallet fails | `GUEST_TRANSACTION_SEND_FAILED` error | Critical |
| 20a | Internal Coinbase error | Unexpected | (Coinbase backend issue — hard to reproduce) | Generic error message shown | Medium |
| 20b | Session error | Unexpected | Session expires during checkout | Error shown, option to retry | Medium |
| 20c | Google Pay button not found | Unexpected | WebView rendering issue where button doesn't appear | Timeout error after retries | Medium |

---

## 21. Coinbase Apple Pay

**N/A on Android** — Google Pay equivalent is covered in #19b.

---

## 22. External Wallet Transaction

**Error type:** `DeeplinkOnRampError`
**Entry point:** Scanner → Wallet → **"Add Cash"** → **"Phantom Wallet"** / **"Solflare"** / **"Backpack"** → amount entry → confirm → opens external wallet app via deeplink

All errors show a bottom bar with title and description. The wallet name is interpolated via `%1$s` in strings.

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 22a | User rejected | Expected | Connect Phantom → reject the transaction prompt in Phantom | Bottom bar: **"Request Rejected in [Phantom]"** / "Make sure you have enough Solana USDC and enough SOL in your [Phantom] account to complete the transaction" | High |
| 22b | Wallet disconnected | Unexpected | Connect, then kill the wallet app mid-flow | Bottom bar: **"Something Went Wrong"** / "[Phantom] could not connect to the network. Please try again" | High |
| 22c | Transaction rejected | Unexpected | Phantom rejects the transaction (insufficient SOL for fees) | Bottom bar: **"Something Went Wrong"** / "The transaction sent to [Phantom] was invalid" | Critical |
| 22d | Failed to create tx | Unexpected | (Internal — bad transaction construction) | Bottom bar: **"Something Went Wrong"** / "Failed to generate transaction. Please try again" | Medium |
| 22e | Failed to send tx | Unexpected | RPC node rejects the transaction | Bottom bar: **"Transaction Failed"** / "Make sure you have enough Solana USDC and enough SOL in your [Phantom] account to complete the transaction" | Critical |
| 22f | Decryption error | Unexpected | Phantom returns corrupted encrypted payload | Bottom bar: **"Something Went Wrong"** / "Response from [Phantom] failed. Please try again" | Medium |
| 22g | Resource not available | Expected | Previous confirmation modal not closed in wallet before new request | Bottom bar: **"Something Went Wrong"** / "The requested resource is not available. This can occur if a previous confirmation modal was not closed in [Phantom] before requesting a new approval" | Medium |

---

## 23. External Wallet Swap Funding

**Error type:** `SwapError`
**Entry point:** Token info screen → **"Buy"** with external wallet funding method → funds sent from wallet → server initiates swap

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 23a | Funding notification failed | Unexpected | External wallet funds arrive but server notification fails | Error shown, funds safe on-chain | Critical |
| 23b | Swap denied after funding | Unexpected | Fund via wallet, server denies the swap | Bottom bar: **"Something Went Wrong"** / "Please try again" | Critical |

---

## 24. JWT / Coinbase Authorization

**Error type:** `GetJwtError`
**Entry point:** Attempt to use Coinbase onramp → app requests JWT → server checks: auth → registered → provider=COINBASE → API key valid → phone linked → email linked

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 24a | Unsupported provider | Unexpected | (Requires client bug — request with non-COINBASE provider) | `GetJwtError.UnsupportedProvider` | Low |
| 24b | Invalid API key | Unexpected | (Requires misconfigured server-side API key) | `GetJwtError.InvalidApiKey` | Medium |
| 24c | Phone verification required | Expected | Attempt Coinbase onramp without linked phone | `GetJwtError.PhoneVerificationRequired` — app should route to phone verification flow | High |
| 24d | Email verification required | Expected | Attempt Coinbase onramp without linked email | `GetJwtError.EmailVerificationRequired` — app should route to email verification flow. Entry point: **"Verify Your Phone Number And Email To Continue"** / "This will allow you to add funds from your debit card" | High |
| 24e | Denied | Unexpected | Flagged/unauthorized account attempts JWT | `GetJwtError.Denied` | High |

**Note:** Coinbase onramp is currently staff-only. Default users see Phantom, Solflare, Backpack, Manual Deposit providers.

---

## 25. Currency Launch Restrictions

**Error type:** `SwapError`, `SubmitIntentError`

| # | Scenario | Type | Steps to Trigger | Expected Behavior | Severity |
|---|----------|------|-----------------|-------------------|----------|
| 25a | Invalid initial purchase amount | Unexpected | Creator launches currency but buy amount ≠ 20 core mint quarks | `SwapError.Denied` → bottom bar: **"Something Went Wrong"** / "Please try again" | Medium |
| 25b | Non-creator initial buy | Expected | Non-creator tries to buy initial tokens of a new currency | `SwapError.Denied` → bottom bar: **"Something Went Wrong"** / "Please try again" | High |
| 25c | Core mint public send restricted | Unexpected | Attempt `SendPublicPayment` on core mint without withdrawal or swap funding flag | `SubmitIntentError.Denied` — bill dismissed, cash returned to wallet | Medium |

---

## Server-Side Constraints (Not User-Triggerable)

These are architectural constraints enforced server-side. They are not directly testable by QA but are documented for completeness.

| Constraint | Server Behavior |
|------------|----------------|
| Payments must be public | Antispam denies non-public payments: `"flipcash payments must be public"` |
| Pool account opening disabled | `OpenAccounts` with POOL account denied: `"pool account opening is disabled"` |
| On-ramp providers by role | Default users: PHANTOM, SOLFLARE, BACKPACK, MANUAL_DEPOSIT. Staff adds: COINBASE_VIRTUAL, BASE |

---

## General Cross-Cutting Tests

| # | Scenario | Type | Steps | Expected | Severity |
|---|----------|------|-------|----------|----------|
| G1 | Airplane mode → any action | Expected | Enable airplane mode, try any flow | Bottom bar: **"No Internet Connection"** / "Please check your internet connection or try again later." No crash, no data loss | Critical |
| G2 | Background → resume mid-flow | Expected | Start a give/send flow, background app 30s, resume | Bill dismissed (put in wallet), camera restarts. BottomBarManager cleared on background. | High |
| G3 | Low memory | Unexpected | Fill device memory, run app | No OOM crash on error paths | Medium |
| G4 | Rapid tapping | Expected | Double/triple tap Give/Buy/Sell buttons | Buttons are disabled during `loading` state (`LoadingSuccessState.loading = true`). No duplicate transactions. | Critical |
| G5 | Error → retry | Expected | Trigger any error, dismiss bottom bar, retry the same action | Retry works cleanly, no stale state | High |
