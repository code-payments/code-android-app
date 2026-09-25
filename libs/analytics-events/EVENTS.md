# Analytics events

<!-- Generated from events.toml -- do not edit. After changing events.toml, run
     ./gradlew :libs:analytics-events:writeAnalyticsEventsPage -->

Every event both apps can send to Mixpanel, and the properties each one carries. To add
or change an event, edit [events.toml](events.toml) in a pull request.

Types: **text** is words, **yes/no** is true or false, **count** is a whole number,
**decimal** is a number with a fraction, **duration** is milliseconds, and a **list**
is one value from the [Lists](#lists) below. An optional property is left out when the
app has no value for it.

## Chat

### Sent Message

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |
| Error | text | yes | |

### Tip Received

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |
| amount | group | no | [amount](#amount) |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Message Received

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |

### Chat Muted

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |
| Duration | list | no | [MuteDuration](#muteduration) |
| State | list | no | [State](#state) |
| Error | text | yes | |

### Chat Unmuted

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |
| State | list | no | [State](#state) |
| Error | text | yes | |

## Group

Group chats. `Member Count` is the member count before the action, from what the screen already holds. `Gate Mint` is left out when the group has no token gate. `Error` is the RPC's result name as the proto spells it, such as RulesNotSatisfied, or Network for a transport failure.

### Group: New Opened

The New Group form opened.

No properties.

### Group: Created

StartChat returned, on success or failure.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |
| Gate Mint | text | yes | |
| Has Picture | yes/no | no | |

### Group: Edited

EditChat returned after a rename or a picture change.

| Property | Type | Optional | Values |
|---|---|---|---|
| Field | list | no | [GroupField](#groupfield) |
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Invite Sheet Opened

| Property | Type | Optional | Values |
|---|---|---|---|
| Source | list | no | [GroupInviteSheetSource](#groupinvitesheetsource) |
| Member Count | count | no | |

### Group: Invite Shared

Share was tapped in the invite sheet, or the invite link was copied.

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [GroupInviteMethod](#groupinvitemethod) |

### Group: Invite Followed

A group opened from an invite link, a scanned QR code, or an invite card in a chat.

| Property | Type | Optional | Values |
|---|---|---|---|
| Source | list | no | [GroupInviteSource](#groupinvitesource) |

### Group: Gate Shown

The gate first appeared for a chat. Sent once per visit to the chat.

| Property | Type | Optional | Values |
|---|---|---|---|
| Access | list | no | [GroupAccess](#groupaccess) |
| Gate Mint | text | yes | |
| Member Count | count | no | |

### Group: Gate Funding Tapped

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [GroupGateFunding](#groupgatefunding) |
| Gate Mint | text | yes | |

### Group: Joined

JoinChat returned.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |
| Member Count | count | no | |
| Gated | yes/no | no | |

### Group: Left

LeaveChat returned after the user confirmed.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |
| Member Count | count | no | |

### Group: Info Opened

The group info (profile) screen opened.

| Property | Type | Optional | Values |
|---|---|---|---|
| Member Count | count | no | |
| Is Member | yes/no | no | |

## Transfer

### Grab Bill Start

No properties.

### Give Bill Start

No properties.

### Grab Bill

| Property | Type | Optional | Values |
|---|---|---|---|
| Grab Time | duration | yes | |
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Grab Time in seconds, not milliseconds, and on failure sends only Fiat and Currency of the amount (see Amount).

### Give Bill

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Withdrawal

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Send Cash Link

| Property | Type | Optional | Values |
|---|---|---|---|
| Cash Link Choice | list | yes | [CashLinkChoice](#cashlinkchoice) |
| App | text | yes | |
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends no Cash Link Choice or App, and sends Exchange Rate and no USDC (see Amount). Android sends Failure only for a link posted to a group chat.

### Receive Cash Link

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Sent Cash

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Sent Tip

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

## Add Money

### Add Money: Opened

| Property | Type | Optional | Values |
|---|---|---|---|
| Source | list | no | [AddMoneySource](#addmoneysource) |

### Add Money: Method Selected

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [AddMoneyMethod](#addmoneymethod) |

> **Drift:** iOS has no Reserves method.

### Add Money: Amount Confirmed

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [AddMoneyMethod](#addmoneymethod) |
| amount | group | no | [amount](#amount) |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Add Money: Payment Invoked

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [AddMoneyMethod](#addmoneymethod) |
| amount | group | no | [amount](#amount) |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount). Android's USDC carries the native amount here.

### Add Money: Address Copied

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |

### Add Money

The outcome of an add-money flow, sent as the event named `Add Money`.

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [AddMoneyMethod](#addmoneymethod) |
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

## Onramp

### Onramp: {step}

Show Verification Info is Android only; part 2 decides whether iOS sends it.

`{step}` in the name is one of [OnrampStep](#onrampstep).

No properties.

## Wallet

The Phantom deeplink flow.

### Wallet: Connect

| Property | Type | Optional | Values |
|---|---|---|---|
| Provider | list | no | [WalletProvider](#walletprovider) |

> **Drift:** iOS sends no Provider, and fires on the request rather than after connecting.

### Wallet: Request Amount

| Property | Type | Optional | Values |
|---|---|---|---|
| Provider | list | no | [WalletProvider](#walletprovider) |
| amount | group | no | [amount](#amount) |

> **Drift:** iOS sends no Provider, and sends only Fiat (the native amount) and Currency where Android sends the token amount's block (see Amount).

### Wallet: Transactions Failed

| Property | Type | Optional | Values |
|---|---|---|---|
| Provider | list | no | [WalletProvider](#walletprovider) |

> **Drift:** iOS declares this with no Provider and never sends it.

### Wallet: Cancel

| Property | Type | Optional | Values |
|---|---|---|---|
| Provider | list | no | [WalletProvider](#walletprovider) |

> **Drift:** iOS sends no Provider, and fires on any error code where Android fires only on a user reject.

## Token Info

### Token Info: Opened From {source}

`{source}` in the name is one of [TokenInfoSource](#tokeninfosource).

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |

## Swap

Buying and selling a token.

### Token Purchase With {method}

Phantom and Coinbase are Android only; part 2 decides whether iOS sends them.

`{method}` in the name is one of [PurchaseMethod](#purchasemethod).

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |
| amount | group | no | [amount](#amount) |
| Error | text | yes | |

> **Drift:** (Reserves) iOS also sends State, Payment Mint and Payment Token Symbol, and sends only Fiat and Currency of the amount, with no USDC or Quarks.

### Token Sell

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |
| amount | group | no | [amount](#amount) |
| Fee | decimal | no | |
| Error | text | yes | |

> **Drift:** iOS also sends State, and sends only Fiat and Currency of the amount, with no USDC, Quarks or Fee.

## Scan

The gallery scan path, and the tip card.

### Gallery Scan: Image Picked

No properties.

### Gallery Scan: Succeeded

@param tier which rung of the crop ladder decoded, 1 to 3.

| Property | Type | Optional | Values |
|---|---|---|---|
| Tier | count | no | |
| Zoom | decimal | no | |
| Time | duration | no | |

> **Drift:** iOS sends this as Gallery Scan: Code Found, with Type {Kik, QR}, Tier as {wholeImage, quadrant, window}, Zoom, and Elapsed in seconds instead of Time in ms.

### Gallery Scan: Failed

@param exhausted true when the budget ran out rather than the ladder ending.

| Property | Type | Optional | Values |
|---|---|---|---|
| Time | duration | no | |
| Exhausted | yes/no | no | |

> **Drift:** iOS sends this as Gallery Scan: Nothing Found, with State {Exhausted, Cancelled, Route Refused} and Elapsed in seconds instead of Time in ms and Exhausted.

### Tip Card Scanned

No properties.

### Tip Card Presented

No properties.

## Deeplink

[url] arrives with its query and fragment already stripped. `type` is a string because the two apps' deeplink vocabularies differ.

### Deeplink: Open

| Property | Type | Optional | Values |
|---|---|---|---|
| URL | text | no | |

### Deeplink: Parse

Sent in 2 forms.

| Property | Type | Optional | Values |
|---|---|---|---|
| Type | text | no | |

> **Drift:** iOS's Type vocabulary is {Login, CashLink, EmailVerification, TokenInfo, Chat, ChatSendCash, Tip, Username, Wallet, DiscoverCurrencies, "Sheet:<x>"}, and iOS sends this for every link where Android sends it only on the gallery QR path.

| Property | Type | Optional | Values |
|---|---|---|---|
| Error | text | no | `Failed to parse deeplink => {url}` |

### Deeplink: Routed

| Property | Type | Optional | Values |
|---|---|---|---|
| Type | text | no | |
| Error | text | yes | |

> **Drift:** iOS never passes Error, and sends this for every kind where Android sends it only for a cash link.

## Display Name

### Display Name Set

The user had no display name before this submission.

| Property | Type | Optional | Values |
|---|---|---|---|
| Source | list | no | [DisplayNameSource](#displaynamesource) |

### Display Name Updated

The user replaced an existing display name.

| Property | Type | Optional | Values |
|---|---|---|---|
| Source | list | no | [DisplayNameSource](#displaynamesource) |

## Error Modal

### Error Modal Displayed

| Property | Type | Optional | Values |
|---|---|---|---|
| Title | text | no | |
| Message | text | no | |
| Screen | text | yes | |
| Call Site | text | yes | |

> **Drift:** iOS's Screen is `presentedSheet.description` or "scan" where Android's is the back stack top's `screenName()`, and iOS never sends Call Site.

## Account

### Create Account Payment

Android only; part 2 decides whether iOS sends it. iOS's Create Account fires at registration and is a different moment.

| Property | Type | Optional | Values |
|---|---|---|---|
| Fiat | decimal | no | |
| Currency | text | no | |
| Owner Public Key | text | no | |

### Entered Phone Number

No properties.

### Verified Phone Number

No properties.

### Linked Phone Number

No properties.

### Complete Onboarding

No properties.

## Button

### Button: {button}

`{button}` in the name is one of [Button](#button).

No properties.

## Groups

### amount

Sent by events that carry an amount of money. Defined in `Amount.kt`.

| Property | Type | Optional |
|---|---|---|
| Fiat | decimal | no |
| Currency | text | no |
| USDC | decimal | no |
| Quarks | count | no |
| Exchange Rate | decimal | yes |
| Mint | text | yes |

## Lists

### ChatType

The `Chat Type` property.

| Value sent | Name in code | Drift |
|---|---|---|
| Contact | `CONTACT` | |
| Tip | `TIP` | |
| Group | `GROUP` | |
| Unknown | `UNKNOWN` | |

### State

The `State` property on an event that reports an outcome.

| Value sent | Name in code | Drift |
|---|---|---|
| Success | `SUCCESS` | |
| Failure | `FAILURE` | |

### CashLinkChoice

The `Cash Link Choice` property on Send Cash Link: how the user passed the link on.

| Value sent | Name in code | Drift |
|---|---|---|
| Copied to clipboard | `COPIED` | |
| Shared to app | `SHARED` | |
| Posted to group chat | `GROUP_CHAT` | |

### AddMoneySource

The `Source` property on Add Money: Opened: where the user opened Add Money from.

| Value sent | Name in code | Drift |
|---|---|---|
| Menu | `MENU` | |
| Give Shortfall | `GIVE_SHORTFALL` | |
| Buy Shortfall | `BUY_SHORTFALL` | |
| Username Shortfall | `USERNAME_SHORTFALL` | |
| Chat | `CHAT` | |
| Scanner | `SCANNER` | |
| Balance | `BALANCE` | |

### AddMoneyMethod

The `Method` property on the Add Money events: how the user adds money.

| Value sent | Name in code | Drift |
|---|---|---|
| Coinbase | `COINBASE` | |
| Phantom | `PHANTOM` | |
| Other Wallet | `OTHER_WALLET` | |
| Reserves | `RESERVES` | iOS has no Reserves method. |

### OnrampStep

The verification screen shown, sent in the event name `Onramp: {step}`.

| Value sent | Name in code | Drift |
|---|---|---|
| Show Verification Info | `SHOW_INFO` | |
| Show Enter Phone | `ENTER_PHONE` | |
| Show Confirm Phone | `CONFIRM_PHONE` | |
| Show Enter Email | `ENTER_EMAIL` | |
| Show Confirm Email | `CONFIRM_EMAIL` | |

### WalletProvider

The `Provider` property on the Wallet events: the wallet app the flow goes through.

| Value sent | Name in code | Drift |
|---|---|---|
| Phantom | `PHANTOM` | |

### TokenInfoSource

Where Token Info was opened from. Discovery, Chat and Chat Gate are new in part 1; Android previously sent Wallet for all three. iOS's Give and Send sources stay native until part 2.

| Value sent | Name in code | Drift |
|---|---|---|
| Deeplink | `DEEPLINK` | |
| Wallet | `WALLET` | |
| Discovery | `DISCOVERY` | |
| Chat | `CHAT` | |
| Chat Gate | `CHAT_GATE` | iOS doesn't send this source: its gate buy button pushes the buy screen and never opens Token Info. Group: Gate Funding Tapped covers that tap on both apps. |

### MuteDuration

The `Duration` property on Chat Muted: the mute option picked. Both apps offer the same four.

| Value sent | Name in code | Drift |
|---|---|---|
| 1 Hour | `ONE_HOUR` | |
| 8 Hours | `EIGHT_HOURS` | |
| 1 Week | `ONE_WEEK` | |
| Always | `ALWAYS` | |

### GroupField

The `Field` property on Group: Edited: what the edit changed.

| Value sent | Name in code | Drift |
|---|---|---|
| Name | `NAME` | |
| Picture | `PICTURE` | |

### GroupInviteSheetSource

The `Source` property on Group: Invite Sheet Opened. Chat is Android's transcript CTA and iOS's head card.

| Value sent | Name in code | Drift |
|---|---|---|
| Chat | `CHAT` | |
| Profile | `PROFILE` | |

### GroupInviteMethod

The `Method` property on Group: Invite Shared: how the invite link left the app.

| Value sent | Name in code | Drift |
|---|---|---|
| Share | `SHARE` | |
| Copy | `COPY` | |

### GroupInviteSource

The `Source` property on Group: Invite Followed: what the group was opened from.

| Value sent | Name in code | Drift |
|---|---|---|
| Link | `LINK` | |
| QR | `QR` | |
| Chat Card | `CHAT_CARD` | |

### GroupAccess

The `Access` property on Group: Gate Shown: whether the viewer can join as they are.

| Value sent | Name in code | Drift |
|---|---|---|
| Eligible | `ELIGIBLE` | |
| Blocked | `BLOCKED` | |

### GroupGateFunding

The `Method` property on Group: Gate Funding Tapped: the funding button tapped on the gate.

| Value sent | Name in code | Drift |
|---|---|---|
| Buy Token | `BUY_TOKEN` | |
| Add Cash | `ADD_CASH` | |

### PurchaseMethod

How a token is paid for, sent in the event name `Token Purchase With {method}`.

| Value sent | Name in code | Drift |
|---|---|---|
| Reserves | `RESERVES` | |
| Phantom | `PHANTOM` | |
| Coinbase | `COINBASE` | |

### DisplayNameSource

The `Source` property on the Display Name events: the screen the name was set on.

| Value sent | Name in code | Drift |
|---|---|---|
| Onboarding | `ONBOARDING` | |
| My Account | `MY_ACCOUNT` | |
| Tip Card Setup | `TIP_CARD_SETUP` | |

### Button

The button tapped, sent in the event name `Button: {button}`.

| Value sent | Name in code | Drift |
|---|---|---|
| Create Account | `CREATE_ACCOUNT` | |
| Save Access Key | `SAVE_ACCESS_KEY` | |
| Wrote Access Key | `WROTE_ACCESS_KEY` | |
| Allow Push | `ALLOW_PUSH` | |
| Skip Push | `SKIP_PUSH` | |
| Allow Contacts | `ALLOW_CONTACTS` | |
| Skip Contacts | `SKIP_CONTACTS` | |
| Buy With Reserves | `BUY_WITH_RESERVES` | |
| Buy With Phantom | `BUY_WITH_PHANTOM` | |
| Buy With Coinbase | `BUY_WITH_COINBASE` | |
| Buy With Other Wallet | `BUY_WITH_OTHER_WALLET` | |
| Share Token Info | `SHARE_TOKEN_INFO` | |

## People counters

Cumulative per-user counters, stored as Mixpanel people properties.

| Counter | Name in code |
|---|---|
| Tips Received | `TIPS` |
| Tips Received Value | `TIPS_VALUE` |
| Messages Received | `MESSAGES` |
