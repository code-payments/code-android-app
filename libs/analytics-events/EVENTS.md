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

The New Group form opened. Type says whether the user tapped Create a New Group or Create a New Community Chat.

| Property | Type | Optional | Values |
|---|---|---|---|
| Type | list | yes | [GroupType](#grouptype) |

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

Share was tapped in the invite sheet, the invite link was copied, or the group card's QR was exported.

| Property | Type | Optional | Values |
|---|---|---|---|
| Method | list | no | [GroupInviteMethod](#groupinvitemethod) |

### Group: Card Shown

The group scan card opened from the invite sheet.

| Property | Type | Optional | Values |
|---|---|---|---|
| Member Count | count | no | |

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

JoinChat returned. Neither app sends it for a join from an invite link.

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

### Group: Invite Link Created

CreateInvite returned for a private group's invite link, from New Link in Manage Invites or when Share Invite Link makes one because none exists.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Invite Link Revoked

RevokeInvite returned after the host confirmed Revoke Link.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Join Request Approved

A host tapped Approve on a join request and the admit call returned.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Join Request Denied

A host tapped Deny on a join request and the deny call returned.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Member Silenced

A host picked a silence option for a member and the call returned. The member can still read the group but can't send messages.

| Property | Type | Optional | Values |
|---|---|---|---|
| Duration | list | no | [SilenceDuration](#silenceduration) |
| State | list | no | [State](#state) |
| Error | text | yes | |

### Group: Member Removed

A host confirmed Remove on a member and the ban call returned. Both apps remove a member by banning them.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

## Search

The Chats search screen.

### Search: Opened

The Chats search screen opened.

No properties.

### Search: Result Tapped

A result row was tapped on the Chats search screen. Tapping a section's See all is not a result.

| Property | Type | Optional | Values |
|---|---|---|---|
| Result Type | list | no | [SearchResultType](#searchresulttype) |

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

Cash was sent to a person or to several people, or a share of a payment request was paid. `Context` says which: Direct for a send to one person, Group Send for a send to several members of a group, Payment Request for paying a share. `Recipient Count` is the number of members paid and is sent for a Group Send only.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |
| Context | list | yes | [SendContext](#sendcontext) |
| Recipient Count | count | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Sent Tip

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

> **Drift:** iOS sends Exchange Rate and no USDC (see Amount).

### Sent Boost

A paid reaction (boost) was confirmed, on success or failure. The amount is the whole boost, before it is split between the message's author and the group's creator. `Chat Type` is the chat the message is in.

| Property | Type | Optional | Values |
|---|---|---|---|
| Chat Type | list | no | [ChatType](#chattype) |
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

## Payment Request

Group payment requests: one amount asked of several members of a group chat. Paying a share is Sent Cash with `Context` set to Payment Request.

### Payment Request: Created

A group payment request was created, on success or failure. The amount is the request's total, split between everyone in it, the creator included when they are. `Payer Count` is the number of people asked, not counting the creator.

| Property | Type | Optional | Values |
|---|---|---|---|
| Payer Count | count | no | |
| State | list | no | [State](#state) |
| amount | group | yes | [amount](#amount) |
| Error | text | yes | |

### Payment Request: Closed

The creator closed a payment request before its deadline, on success or failure. A request that reaches its deadline on its own doesn't fire this.

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Error | text | yes | |

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

### Token Info: Tile Tapped

An action tile was tapped on Currency Info. It counts the tap, not whether the Send, Buy or Transactions screen completed.

| Property | Type | Optional | Values |
|---|---|---|---|
| Tile | list | no | [CurrencyInfoTile](#currencyinfotile) |
| Mint | text | no | |

### Token Info: More Option Picked

A row was picked in the More sheet on Currency Info.

| Property | Type | Optional | Values |
|---|---|---|---|
| Option | list | no | [CurrencyInfoMoreOption](#currencyinfomoreoption) |
| Mint | text | no | |

### Token Info: Community Chat Tapped

A community chat row was tapped on Currency Info, in the Top Community Chats preview or its View All list.

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |

## Swap

Buying and selling a token.

### Token Purchase With {method}

Phantom and Coinbase are Android only; part 2 decides whether iOS sends them. Currency is a buy paid with a held currency other than USDF reserves.

`{method}` in the name is one of [PurchaseMethod](#purchasemethod).

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |
| amount | group | no | [amount](#amount) |
| Error | text | yes | |

> **Drift:** (Reserves and Currency) iOS also sends State, Payment Mint and Payment Token Symbol, and sends only Fiat and Currency of the amount, with no USDC or Quarks.

### Token Sell

| Property | Type | Optional | Values |
|---|---|---|---|
| Mint | text | no | |
| amount | group | no | [amount](#amount) |
| Fee | decimal | no | |
| Error | text | yes | |

> **Drift:** iOS also sends State, and sends only Fiat and Currency of the amount, with no USDC, Quarks or Fee.

## Currency Launch

### Currency Launch With {method}

A currency launch finished, on success or failure. Mint is the launched currency, Payment Mint the token that paid the launch cost, and Fiat and Currency the launch cost in the user's currency. Reserves is a launch paid with USDF; Currency is one paid with another held currency.

`{method}` in the name is one of [LaunchMethod](#launchmethod).

| Property | Type | Optional | Values |
|---|---|---|---|
| State | list | no | [State](#state) |
| Mint | text | no | |
| Payment Mint | text | no | |
| Fiat | decimal | no | |
| Currency | text | no | |
| Error | text | yes | |

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

> **Drift:** iOS sends this as Gallery Scan: Code Found, with Type {Kik, QR}, Tier as {wholeImage, quadrant, window}, Zoom, and Elapsed in seconds instead of Time in ms. iOS will move to this event, and the Gallery Scan: Code Found series in Mixpanel ends on the iOS release that does.

### Gallery Scan: Failed

@param exhausted true when the budget ran out rather than the ladder ending.

| Property | Type | Optional | Values |
|---|---|---|---|
| Time | duration | no | |
| Exhausted | yes/no | no | |

> **Drift:** iOS sends this as Gallery Scan: Nothing Found, with State {Exhausted, Cancelled, Route Refused} and Elapsed in seconds instead of Time in ms and Exhausted. iOS will move to this event, and the Gallery Scan: Nothing Found series in Mixpanel ends on the iOS release that does.

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

> **Drift:** iOS's Type vocabulary is {Login, CashLink, EmailVerification, TokenInfo, Chat, ChatSendCash, ChatMessage, GroupInvite, Tip, Profile, Wallet, DiscoverCurrencies, "Sheet:<x>"}. iOS sends this for every link that resolves to an action and for no other, where Android sends it only on the gallery QR path.

| Property | Type | Optional | Values |
|---|---|---|---|
| Error | text | no | `Failed to parse deeplink => {url}` |

> **Drift:** iOS never sends this form: it reports a parse only for links that resolve to an action.

### Deeplink: Routed

| Property | Type | Optional | Values |
|---|---|---|---|
| Type | text | no | |
| Error | text | yes | |

> **Drift:** iOS never passes Error. It sends this for every kind except access-key and verify-email links, which never report a route, and only when a user is logged in; Android sends it only for a cash link.

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

### Create Account

The user chose to create an account and registration began, before the server answers.

No properties.

> **Drift:** iOS also sends Owner Public Key, a wallet identifier that isn't part of the shared contract. iOS stops sending it when it moves to this event.

### Auto-login complete

A saved account on the device logged in at launch with no action from the user.

No properties.

### Entered Phone Number

No properties.

### Verified Phone Number

No properties.

### Linked Phone Number

No properties.

### Complete Onboarding

No properties.

## Profile

### Profile: Settings Viewed

The Settings screen opened.

No properties.

### Profile: Edit Profile Viewed

The Edit Profile screen opened.

No properties.

### Profile: Field Saved

A profile field was saved.

| Property | Type | Optional | Values |
|---|---|---|---|
| Field | list | no | [ProfileField](#profilefield) |

### Profile: Bio Moderation Failed

A bio was rejected by moderation.

No properties.

### Profile: Menu Action

An action was picked from another user's profile menu.

| Property | Type | Optional | Values |
|---|---|---|---|
| Action | list | no | [ProfileMenuAction](#profilemenuaction) |

### Profile: Pinned Button Tapped

The pinned button on another user's profile was tapped.

| Property | Type | Optional | Values |
|---|---|---|---|
| Action | list | no | [ProfilePinnedAction](#profilepinnedaction) |

### Profile: Card Shown

A profile card was shown.

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

### SendContext

The `Context` property on Sent Cash: which send the payment came from.

| Value sent | Name in code | Drift |
|---|---|---|
| Direct | `DIRECT` | |
| Group Send | `GROUP_SEND` | |
| Payment Request | `PAYMENT_REQUEST` | |

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
| Profile | `PROFILE` | |

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

Where Token Info was opened from. Discovery, Chat and Chat Gate are new in part 1; Android previously sent Wallet for all three. Give and Send are iOS's, from the Add More Cash prompt on the Give and Send amount screens; the event names iOS sends today are unchanged. Search is a result tapped on the Chats search screen.

| Value sent | Name in code | Drift |
|---|---|---|
| Deeplink | `DEEPLINK` | |
| Wallet | `WALLET` | |
| Discovery | `DISCOVERY` | |
| Search | `SEARCH` | |
| Chat | `CHAT` | |
| Chat Gate | `CHAT_GATE` | iOS doesn't send this source: its gate buy button pushes the buy screen and never opens Token Info. Group: Gate Funding Tapped covers that tap on both apps. |
| Give | `GIVE` | Android has no such flow: its Give screen sends Add Money: Opened with source Give Shortfall instead of opening Token Info. |
| Send | `SEND` | Android has no such flow: its Send screen has no Add More Cash prompt that opens Token Info. |

### MuteDuration

The `Duration` property on Chat Muted: the mute option picked. Both apps offer the same four.

| Value sent | Name in code | Drift |
|---|---|---|
| 1 Hour | `ONE_HOUR` | |
| 8 Hours | `EIGHT_HOURS` | |
| 1 Week | `ONE_WEEK` | |
| Always | `ALWAYS` | |

### SilenceDuration

The `Duration` property on Group: Member Silenced: the silence option picked. Both apps offer these four. The last reads Forever here, where MuteDuration reads Always.

| Value sent | Name in code | Drift |
|---|---|---|
| 1 Hour | `ONE_HOUR` | |
| 8 Hours | `EIGHT_HOURS` | |
| 1 Week | `ONE_WEEK` | |
| Forever | `FOREVER` | |

### GroupType

The `Type` property on Group: New Opened: which New Chat row was tapped. Private is Create a New Group; Community is Create a New Community Chat.

| Value sent | Name in code | Drift |
|---|---|---|
| Private | `PRIVATE` | |
| Community | `COMMUNITY` | |

### GroupField

The `Field` property on Group: Edited: what the edit changed.

| Value sent | Name in code | Drift |
|---|---|---|
| Name | `NAME` | |
| Picture | `PICTURE` | |

### GroupInviteSheetSource

The `Source` property on Group: Invite Sheet Opened. Both apps send Profile, from the group profile and the join requests screen.

| Value sent | Name in code | Drift |
|---|---|---|
| Chat | `CHAT` | Neither app sends this today. It was Android's transcript CTA and iOS's card at the head of chat history; Android dropped its CTA in #1707 and iOS removed its card in cb036f487. |
| Profile | `PROFILE` | |

### GroupInviteMethod

The `Method` property on Group: Invite Shared: how the invite link left the app.

| Value sent | Name in code | Drift |
|---|---|---|
| Share | `SHARE` | |
| Copy | `COPY` | |
| Download | `DOWNLOAD` | |

### GroupInviteSource

The `Source` property on Group: Invite Followed: what the group was opened from.

| Value sent | Name in code | Drift |
|---|---|---|
| Link | `LINK` | |
| QR | `QR` | |
| Chat Card | `CHAT_CARD` | iOS sends this when a group card in a chat is tapped and its link names a chat other than the one on screen; a card whose link carries no chat id opens through the link path and sends Link. |

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

### SearchResultType

The `Result Type` property on Search: Result Tapped: the kind of result row. Chat covers direct messages and groups, and the recent chats shown before anything is typed.

| Value sent | Name in code | Drift |
|---|---|---|
| Chat | `CHAT` | |
| Currency | `CURRENCY` | |
| Message | `MESSAGE` | |
| Link | `LINK` | |

### CurrencyInfoTile

The `Tile` property on Token Info: Tile Tapped: the action tile tapped on Currency Info. Which tiles show depends on whether the currency is held.

| Value sent | Name in code | Drift |
|---|---|---|
| Send | `SEND` | |
| Buy | `BUY` | |
| Buy In | `BUY_IN` | |
| Transactions | `TRANSACTIONS` | |
| More | `MORE` | |

### CurrencyInfoMoreOption

The `Option` property on Token Info: More Option Picked: the row picked in the More sheet.

| Value sent | Name in code | Drift |
|---|---|---|
| Convert | `CONVERT` | |
| Withdraw | `WITHDRAW` | |
| Deposit | `DEPOSIT` | |

### PurchaseMethod

How a token is paid for, sent in the event name `Token Purchase With {method}`.

| Value sent | Name in code | Drift |
|---|---|---|
| Reserves | `RESERVES` | |
| Phantom | `PHANTOM` | |
| Coinbase | `COINBASE` | |
| Currency | `CURRENCY` | |

### LaunchMethod

How a currency launch is paid for, sent in the event name `Currency Launch With {method}`.

| Value sent | Name in code | Drift |
|---|---|---|
| Reserves | `RESERVES` | |
| Currency | `CURRENCY` | |

### DisplayNameSource

The `Source` property on the Display Name events: the screen the name was set on.

| Value sent | Name in code | Drift |
|---|---|---|
| Onboarding | `ONBOARDING` | |
| My Account | `MY_ACCOUNT` | |
| Tip Card Setup | `TIP_CARD_SETUP` | |

### ProfileField

The `Field` property on Profile: Field Saved: the profile field that was saved.

| Value sent | Name in code | Drift |
|---|---|---|
| Name | `NAME` | |
| Username | `USERNAME` | |
| Photo | `PHOTO` | |
| Cover | `COVER` | |
| Bio | `BIO` | |
| Minimum | `MINIMUM` | |

### ProfileMenuAction

The `Action` property on Profile: Menu Action: the action picked from another user's profile menu.

| Value sent | Name in code | Drift |
|---|---|---|
| Mute | `MUTE` | |
| Report | `REPORT` | |
| Block | `BLOCK` | |
| Unblock | `UNBLOCK` | |

### ProfilePinnedAction

The `Action` property on Profile: Pinned Button Tapped: what the profile's pinned button does.

| Value sent | Name in code | Drift |
|---|---|---|
| Start Chatting | `START_CHATTING` | |
| Open Chat | `OPEN_CHAT` | |
| Unblock | `UNBLOCK` | |

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
| Buy With Currency | `BUY_WITH_CURRENCY` | iOS sends this when a buy is paid with a held currency other than USDF reserves. Android sends no Button event for that buy. |
| Give | `GIVE` | iOS sends this on Currency Info's Give button, which opens its send-to-person amount screen. Android's matching tile is labelled Send and sends no Button event. |

## People counters

Cumulative per-user counters, stored as Mixpanel people properties.

| Counter | Name in code |
|---|---|
| Tips Received | `TIPS` |
| Tips Received Value | `TIPS_VALUE` |
| Messages Received | `MESSAGES` |
