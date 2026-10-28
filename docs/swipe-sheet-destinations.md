# Swipe sheets as navigation destinations

Status: plan. Nothing here is built yet; the groundwork is in place.

## Where things stand

Three flows end in a swipe-to-pay sheet: pay a request (`RequestDetailsScreen`), group send
(`GroupSendConfirmSheet` in `ChatFlowScreen`) and Currency Info send (`CurrencySendScreen`).
They used to be Material `ModalBottomSheet`s, which are windows of their own, so a bottom bar
raised while one was up drew underneath it.

They are now `SwipePaySheet` (`messenger/internal/swipepay`), a stateless composable over
`InWindowBottomSheet` (`ui/navigation`), drawn inside the route's content. The route's entry
decorator draws the bar after `entry.Content()` in the same window, so the bar lands above the
sheet. Each flow's view model exposes its sheet's actions as one `SwipePayActions`
(`swipePayActions`, `groupSendSwipeActions`), and the screen builds a `SwipePayUiState` from the
view model state. The view models still own the pay and send logic.

This is what option A builds on: the UI already takes only a `SwipePayUiState` and `SwipePayActions`.

## Target shape

- One navigation destination per flow: `PayRequestSheet`, `GroupSendConfirmSheet`,
  `CurrencySendConfirmSheet`. Each is a `Sheet` (wrap-content) route handled by
  `ModalBottomSheetSceneStrategy`, so it gets the scene's drag, scrim, back handling and insets
  without `InWindowBottomSheet`.
- `SwipePaySheet` content is shared unchanged. The destination supplies header and amount slots;
  `InWindowBottomSheet` and `rememberSwipePaySheetState` go away, because the scene owns the
  sheet state. The lock while a swipe is in flight becomes `navigator.sheetDismissDisabled`, which
  the scene already honours.
- One entry-scoped ViewModel per destination, holding the `SwipePayUiState` and implementing
  `SwipePayActions`. It owns the pay or send call and the post-swipe checks that today live in
  the parent view model.

## What each destination is given

Pass ids and small values in the key; re-derive the rest in the destination's view model.
Nothing live (a `ChatMember`, a `GroupSendPlan`, a `ConversationReference`) goes in a key.

| Flow | In the key | Re-derived in the destination view model |
|---|---|---|
| Pay request | `chatId`, `requestId` | `payments.prepare(requestId, viewer)` for the `Share` (it already does this on Pay tap and again after a failed swipe), the creator from the chat roster, the selected token |
| Group send | `chatId`, `total` (as currency code plus minor units), `selected` member ids | The roster for avatars and names, the plan from `GroupSendSplit.split(total, selected.size)` (pure), the selected token |
| Currency send | `mint`, `total`, `recipient.chatId` | The `ConversationReference` from the conversations source by chat id, the token from `tokenCoordinator.cachedToken(mint)` |

The token pill changes the selected token through the shared `SelectTokenViewModel`, not through
the flow. When the token changes inside the sheet, the destination view model re-derives from
the new token, not from anything it was handed:

- Pay request: the share's fiat amount is fixed; only the balance check changes. Re-run
  `payBlock` (balance and send limit) on the new token before the swipe is accepted.
- Group send: the plan depends on the total and the selection, not the token, so it stands. The
  balance check (`checkBalanceLimit`) re-runs, as `sendToMembers` does today.
- Currency send: the currency is fixed to `mint`; there is no picker.

## Result path

A destination reports back through the existing nav result store (`NavResultKey` with
`deliverOrPersist`), the same way other sheets do:

- `Paid` or `Sent`: the parent closes its step. Group send replaces `Event.SendComplete`; Currency
  send replaces the `done` flow that `finishSend` collects.
- `Canceled` (back, scrim, drag): delivered by the scene already. The parent returns to its
  previous step, as `onConfirmDismissed` and `OnGroupSendBack` do now.
- `AddMoney`: the destination pops itself and the parent opens the deposit screen, replacing the
  close-then-navigate in `presentDeposit`.

The sheet pops itself after the success checkmark has held (`holdPaymentSuccess`), then delivers
`Paid`. A failed swipe delivers nothing: the sheet stays and the modal shows over it.

## `hostsBars`

`hostsBars(isTopEntry, isSheet, coversScreen)` returns false for a partial sheet: its entry
leaves bars to the route beneath, which the sheet scene covers. That is fine while the sheet is
a layer inside a route (today), and wrong once the sheet is its own entry: a bar raised from it
would draw behind it.

The change: a sheet entry hosts bars when it is the top entry, and the bar is drawn by the
scene's own content rather than the entry decorator, so it sits inside the scene's
`SharedTransitionLayout` above the sheet surface and below nothing. Concretely, move
`BottomBarContainer` into `ModalBottomSheetScene.content` for sheets that opt in
(`NavMetadataKeys.HostsBars`), and make `hostsBars` false for those entries so the bar is not
drawn twice. `BarHostingTest` already pins the table; extend it with the opt-in case.

Two things to settle when doing it: a bar inside a partial sheet is clipped to the sheet's
bounds, so the scene should draw it outside the clipped `Sheet` but inside the scene's `Box`;
and the scrim must not dim the bar.

## Group send and the nested `FlowHost`

`GroupSendConfirmSheet` is shown from the chat route's `FlowHost`, which has its own `NavDisplay`
and no entry decorators. A sheet pushed on that stack would be a scene of the inner display, so
the outer app decorator and the app's `ModalBottomSheetSceneStrategy` never see it.

Options, in the order to prefer:

1. Give the chat `FlowHost` the same scene strategy and a `NavMessagingEntryDecorator`-style
   bar host for its sheet entries. The inner display then renders the sheet and draws bars
   above it. Cost: a second bar host, so the outer one must not also draw for the same message
   (host by the topmost display only).
2. Push the destination on the app back stack instead, with the chat route staying beneath it.
   The sheet and the bar are then handled by the app's display with no new plumbing. Cost: the
   group-send state (`GroupSendState` in `ChatViewModel`) is shared between steps, so the
   destination has to take its inputs by key (above) and report back by result, which is what
   the table already assumes.

Prefer 2 unless the inner flow needs to keep swipe state across process death on its own stack.

## Order

One flow per PR, each shippable on its own:

1. Currency send. No nested host, and its `finishSend` (hide, then pop) maps directly onto
   "pop after the checkmark". Introduces the destination pattern, the result key and the
   `HostsBars` metadata with its test.
2. Pay request. Partly done: `payTapped`, `confirmPay` and the `ready` share now live in
   `RequestPayFlow`, which `RequestDetailsViewModel` and the chat's `RequestPayViewModel` each own
   (the deck's Pay opens the sheet over the chat, not Request details). What is left is hosting
   that sheet in a destination instead of in each screen's content.
3. Group send. Last because of the nested `FlowHost`; do it once 1 and 2 have settled the
   result and bar handling.
4. Delete `InWindowBottomSheet` when nothing uses it.

## Other sheets with the same problem

These are still Material `ModalBottomSheet`s, so a bar raised over them draws underneath. Not
changed here:

- `StartChattingSheet` (`StartChattingSheet.kt`)
- `RequestMoneyScreen` (`RequestMoneyScreen.kt`)
- `TimeToPaySheet` (`TimeToPaySheet.kt`)
- the confirm and options `ActionSheet` in `RequestDetailsScreen`

They can move to `InWindowBottomSheet` independently if one of them starts raising a bar.
