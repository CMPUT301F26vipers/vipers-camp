# Data model

How the app stores its data in Firestore, and the Kotlin classes that match. Class names follow the
**Part 2 CRC cards** on the wiki. Please read this before building on it, and comment on the PR if
your story needs a field or method that isn't here.

## Rules for every collection

- **Money** is a whole number of **cents** (`5000` = $50.00).
- **Nights** (check-in, check-out, nights offered, availability) are `"yyyy-MM-dd"` strings, e.g. `"2027-05-08"`.
- **Times** (lottery periods, createdAt, joinedAt…) are **milliseconds since 1970** (`System.currentTimeMillis()`).
- **Names are not copied.** Look a user's name up with `ProfileRepository.getDisplayName(userId)`,
  so a renamed user shows their new name everywhere (US 01.07.03).
- **Checks go in the model**, not the screen. Each story adds the checks it needs (required
  fields, date order, canLeave, canCancel…) to the model class, with unit tests.
- **Enums** are saved as their name, e.g. `"HINTON"`, `"CONFIRMED"`.
- **Removed** items stay in Firestore with `removed = true`, and lists skip them.
- **Computed, never stored:** lottery status (Upcoming / Open / Drawn / Closed), a reservation being
  Active or Past (and "Completed"), a campsite's average rating.

## Code layout (`project/app/src/main/java/com/example/vipers/`)

| Folder | What's there |
|---|---|
| `domain/model/` | The data classes and enums below. No Firebase or Android imports. |
| `domain/repository/` | Repository interfaces: what you can do with the data. |
| `domain/service/` | Logic shared by several screens, on top of the repositories (e.g. `NotificationService`). No Firebase or Android imports. |
| `data/firestore/` | `Firestore…Repository`: the real versions, plus `toMap()` / `to…()` mappers. |
| `data/fake/` | `Fake…Repository`: in-memory versions for unit and intent tests. |
| `data/device/` | `DeviceIdentityManager`: the device ID is the user's ID. `AndroidDeviceNotifier`: posts phone notifications (T-14); `DeviceNotificationLink`: the extras of the Intent a phone notification opens. |
| `data/seed/` | `SeedLoader`: demo data. |
| `CampApp.kt` / `AppContainer.kt` | Creates the repositories. Controllers get them with `(application as CampApp).container`. |

## Collections

### `users`: `UserProfile` (`ProfileRepository`)
Document ID = **device ID**.

| Field | Type | Notes |
|---|---|---|
| name | string | required |
| email | string | optional, `""` if not given |
| phone | string | optional, `""` if not given |
| imageId | string? | profile photo: the ID of its `images` document (US 01.08.02), `null` if none |
| notificationsEnabled | bool | default `true`; the Settings switch (US 01.10.01) |
| isAdmin | bool | set by hand in the Firebase console |
| createdAt | number | ms |

- `getAllProfiles()` lists every user profile for the admin profile browser (US 03.05.01).
- **Deleting a profile (US 01.08.01):** `DeletionService.deleteProfile` first marks the user's
  `WAITING`, `WON` and `LOST` entries `REMOVED` (`removeEntry`), cancels their confirmed
  reservations without a notice (nights go back), and removes their campsites
  (`removeCampsite`: confirmed reservations there are cancelled, with a notice to whoever
  `NotificationService.optedIn` keeps; the owner's lotteries for it have their entries
  `REMOVED` and are marked `removed` once none of their campsites are left; its photo is
  deleted; and the campsite is marked `removed` last). Last of all, `deleteProfile` deletes the
  `users` document and its photo in one batch, so a failed step keeps the profile and can be
  retried.
  Waiting lists and reservations then show "Deleted user" through `getDisplayName`.
- **Profile photo (US 01.08.02):** Change photo on Profile saves the photo straight away, not
  with the Save button. `ProfileRepository.setPhoto` saves it as an `images` document with
  `ownerType = profile`, `ownerId` = the device ID and `label` = the user's name (so admins can
  find it), changes only the profile's `imageId`, and deletes the old image, all in one
  Firestore batch. If any part is rejected nothing changes, so the profile never points to a
  missing or deleted image. Remove does the same with no new image and `imageId = null`. The
  label is the name when the photo was saved; the admin image browser shows the user's current
  name and only uses the label if the profile can't be found.
- `setNotificationsEnabled` (the Settings switch) and `setPhoto` each change only their own
  field, so flipping the switch while a photo saves can't undo either one.

### `campsites`: `Campsite` (`CampsiteRepository`)

| Field | Type | Notes |
|---|---|---|
| ownerId | string | owner's device ID |
| name | string | required |
| address | string | free text, e.g. "Hinton, AB"; required |
| region | string | `BANFF`, `JASPER`, `KANANASKIS`, `HINTON`; required |
| description | string | amenities |
| maxOccupancy | number | ≥ 1; required |
| tags | string[] | e.g. `["Cookhouse", "Tennis court"]` |
| pricePerNightCents | number | cents |
| rules | string | |
| cancellationPolicy | string | |
| imageId | string? | promo photo: the ID of its `images` document (US 02.01.02b), `null` if none |
| lat, lng | number? | set by Find location (US 02.01.02c); both or neither, `null` if not set |
| ratingSum, ratingCount | number | average = sum / count |
| removed | bool | |

Owners save changes with `updateCampsite(campsite)` (US 02.01.03). It writes only `name`, `address`,
`region`, `description`, `maxOccupancy`, `tags`, `pricePerNightCents`, `rules`,
`cancellationPolicy`, `lat` and `lng` (US 02.01.02c; `null` removes the map location) and `imageId`
(US 02.01.02b; `null` removes the photo), never `ownerId`, `ratingSum`, `ratingCount` or `removed`.
It throws `NoSuchElementException` if the campsite ID doesn't exist.

`newCampsiteId()` makes a campsite ID without saving anything (Firestore makes it on the phone), and
`addCampsite` saves a campsite under its own ID when it has one. The Add Campsite form uses this to
save the photo for the new campsite's ID before the campsite itself (US 02.01.02b).

**QR code link (US 02.02.01).** A campsite's QR code holds `vipers-camp://campsite/<campsiteId>`,
made by `Campsite.qrLink()`. Nothing about the QR code is stored: the app draws it from the ID
each time with ZXing, so it stays the same after edits and each campsite gets its own.
`Campsite.idFromQrLink(link)` reads the ID back when a code is scanned (US 01.12.01) and returns
`null` for anything that isn't exactly this format (another app's code, no ID, extra parts
after the ID, or characters other than letters, digits, `-` and `_`, like `?` or `#`, which would
break the Campsite Details route). Scanning uses Google's code scanner (`play-services-code-scanner`), which has its
own camera screen, so the app never asks for camera permission. A scanned ID is loaded with
`getCampsite` like any other; a removed campsite shows "Campsite not found." on Campsite Details.

### `availability`: `AvailabilityCalendar` (`CampsiteRepository`)
Document ID = **campsite ID**. Read and save with `getAvailability` / `saveAvailability`.

| Field | Type | Notes |
|---|---|---|
| openNights | string[] | dates the owner opened |
| heldNights | string[] | dates held by CONFIRMED reservations; a cancel or reject removes them |

### `lotteries`: `Lottery` (`LotteryRepository`)

| Field | Type | Notes |
|---|---|---|
| ownerId | string | |
| campsiteIds | string[] | one lottery can cover several campsites |
| nightsOffered | string[] | dates |
| nightsDescription | string? | optional description, e.g. "May 2027 weekends" |
| registrationStart, registrationEnd | number | ms; joining period (date **and** time) |
| bookingStart, bookingEnd | number | ms; reserving period |
| winnersCount | number | ≥ 1 |
| waitlistCapacity | number? | `null` = no limit |
| drawnAt | number? | ms, `null` until the draw runs |
| removed | bool | |
| waitingUserIds | string[] | entrant device IDs currently on the waiting list |
| entriesVersion | number | incremented on waiting-list join/leave |
| committedDrawId | string? | ID of committed draw manifest |
| manifestChunkCount | number | number of manifest chunks |
| participantCount | number | total participants in draw |
| winnerCount | number | total winners in draw |

Owners save changes with `updateLottery(lottery, callerId)` (US 02.04.02, 02.04.06). Using the stored lottery read inside the transaction as its baseline, it verifies ownership (`callerId` matches `stored.ownerId`) and edit eligibility (not drawn, removed, or closed), reads every submitted campsite document to confirm it exists, is active (`removed == false`), and belongs to `stored.ownerId`, and reads the availability calendars needed for any newly introduced `(campsiteId, night)` pairs before writing (reusing `validateLottery` / `conflictingOfferedNights` so unrelated edits preserve already-offered pairs while checking `waitingUserIds` capacity). It writes only `campsiteIds` (deduplicated), `nightsOffered`, `nightsDescription`, `registrationStart`, `registrationEnd`, `bookingStart`, `bookingEnd`, `winnersCount`, and `waitlistCapacity`, never overwriting `ownerId`, `drawnAt`, `removed`, `waitingUserIds`, `entriesVersion`, `committedDrawId`, `manifestChunkCount`, `participantCount`, or `winnerCount`.

**Starting the draw (US 02.05.01b).** Whenever the app opens, `DrawAndNotifyWorker` calls
`getLotteriesToDraw(now)` and draws each one. It returns the lotteries where `Lottery.isDueForDraw(now)`
is true: registration has ended, `drawnAt` is `null`, not removed, and the booking period hasn't ended.
Firestore is only asked for `drawnAt == null` and the dates are checked on the phone, so no composite
index is needed. The owner's Draw now button can also draw while registration is open: it calls
`closeRegistration(lotteryId, now)` first, which in one transaction sets `registrationEnd` to `now`
(only if `Lottery.canOwnerDraw(now)`), so nobody can join after the winners are picked. The draw
transaction still refuses to draw twice, so several phones running the check at once is safe.

### `lotteryEntries`: `LotteryEntry` (`LotteryRepository`)
Document ID = **`lotteryId_userId`**, so a user can only have one entry per lottery.

| Field | Type | Notes |
|---|---|---|
| lotteryId | string | |
| userId | string | name comes from `getDisplayName(userId)` |
| groupSize | number | |
| joinedAt | number | ms |
| status | string | `WAITING`, `WON`, `LOST`, `DECLINED`, `RESERVED`, `EXPIRED`, `REMOVED` |

A winner declines via `LotteryDrawer.declineAndDrawReplacement` (US 01.04.03, T-12), which shuffles the lottery's `LOST` user IDs once with its injected `Random`, prepares the replacement `WON` notice asynchronously for each candidate in turn outside the transaction (`NotificationService.prepareReplacementWinNotice`), and calls `declineTransaction(lotteryId, userId, candidateUserId, replacementNotice, now)`. In one atomic transaction it re-reads the lottery, declining entry, selected candidate entry, and deterministic replacement notice document before any write, checks `LotteryEntry.canDecline(lottery, now)` (entry still `WON`, lottery not removed, `now` within inclusive `bookingStart..bookingEnd`), and verifies the candidate is still `LOST` in the same lottery. If the candidate is stale, it writes nothing and returns `CandidateStale` so `LotteryDrawer` advances to the next shuffled candidate; if the decline is stale, it writes nothing and returns `NotDeclined`; if the pool is empty or exhausted, it commits the decline alone; otherwise it sets the declining entry to `DECLINED`, promotes the candidate entry to `WON`, and persists the prepared notice (preserving any existing notice's `read` and `shownOnDevice` flags). If the notice write fails, the transaction rolls back so the winner remains `WON` and the candidate remains `LOST` for retry. Cancelling or rejecting a reservation keeps the entry `RESERVED`, so no replacement winner is drawn. Transactions need a connection, so declining fails offline.

### `reservations`: `Reservation` (`ReservationRepository`)

| Field | Type | Notes |
|---|---|---|
| reservationNumber | string | shown to users, e.g. "R-20417" |
| userId | string | entrant |
| ownerId | string | campsite owner |
| campsiteId, campsiteName | string | name copied for lists |
| lotteryEntryId | string | the winning entry |
| checkIn, checkOut | string | dates |
| groupSize | number | |
| totalPriceCents | number | fixed when booked |
| rulesAcceptedAt | number | ms |
| status | string | `CONFIRMED`, `CANCELLED`, `REJECTED` |
| statusChangedAt | number | ms |
| rejectionReason | string? | only when `REJECTED` |

**Active** = `CONFIRMED` and check-out is today or later. Everything else is **Past**.
**Completed** = `CONFIRMED` and check-out is before today (`isCompleted`); the owner's Past tab
lists only completed stays, while cancelled and rejected reservations go in the owner's Cancelled tab.

My Reservations (US 01.06.01) and the owner's Reservations screen (US 02.07.01) both list the
Active ones sorted by check-in, soonest first: the entrant's come from
`getReservationsForUser(userId)` and the owner's from `getReservationsForOwner(ownerId)`. A
reservation whose dates can't be read is left out of both lists instead of breaking them.

**Cancelling and rejecting** (US 01.07.01, 02.08.01) both use
`changeStatus(id, status, reason, now)`, so there is one transaction for both. It only changes a
`CONFIRMED` reservation (otherwise it returns `false` and writes nothing), and in the same
transaction it:
- sets `status` to `CANCELLED` or `REJECTED` and `statusChangedAt` to `now`; `rejectionReason` is
  saved only for `REJECTED`,
- removes the reservation's nights (check-in up to the night before check-out) from the
  campsite's `heldNights`, so other winners can book them again,
- writes the entrant's notice (`Reservation.statusNotice`): ID `cancelled_<reservationId>` or
  `rejected_<reservationId>`, type `RESERVATION_CANCELLED` or `RESERVATION_REJECTED`, message
  `<campsite name>: <check-in> to <check-out>` (plus `. Reason: <reason>` for a rejection with a
  reason). A cancel is from `"system"`, a rejection from the owner.

The entrant can cancel only while `Reservation.canCancel(today)` is true: `CONFIRMED` and check-in
is today or later. The lottery entry stays `RESERVED` after a cancel or reject: the win was used,
so the entrant can't reserve from it again and no replacement winner is drawn. The cancel notice
is sent even if the entrant turned notifications off, the same as the confirmation, because it
confirms something they just did. A rejection should respect the opt-out: check the entrant with
`NotificationService.optedIn` first and pass `sendNotice = false` if they turned notifications
off. Everything else in the transaction still happens.

The owner rejects with `rejectTransaction(reservationId, reason, now, sendNotice)` (US 02.08.01).
It checks `Reservation.isValidRejectionReason(reason)` first and throws
`IllegalArgumentException`, writing nothing, if the reason is blank. Otherwise it runs the same
`changeStatus` transaction with `REJECTED` and the trimmed reason: if the reservation is missing or
no longer `CONFIRMED` (e.g. the entrant cancelled first, or a second tap) it writes nothing and
returns `false`; otherwise it sets `status`, `statusChangedAt` and `rejectionReason`, removes the
reservation's `nights()` from `heldNights` (nights already missing are ignored, and `openNights`
stays the same), writes the `rejected_<reservationId>` notice unless `sendNotice` is false, and
returns `true`. Transactions need a connection, so rejecting fails offline.

### `notifications`: `Notification` (`NotificationRepository`)

| Field | Type | Notes |
|---|---|---|
| recipientId | string | |
| senderId | string | device ID, or `"system"` |
| campsiteId | string? | |
| lotteryId | string? | a WON tap opens Reserve for this lottery |
| reservationId | string? | reservation notifications open this reservation |
| type | string | `WON`, `NOT_SELECTED`, `RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `RESERVATION_REJECTED`, `OWNER_BROADCAST`, `NEW_MESSAGE`, `CAMPSITE_REMOVED` |
| title, message | string | |
| read | bool | |
| shownOnDevice | bool | stops a phone notification showing twice |
| batchId | string? | groups one owner broadcast |
| createdAt | number | ms |

Computed, never stored (T-13): `target` is the screen a tap opens, a `NotificationTarget`
(`OpenReserve(lotteryId)` for `WON`, `OpenReservation(reservationId)` for `RESERVATION_*`,
`OpenThread(reservationId)` for `NEW_MESSAGE`, `OpenCampsite(campsiteId)` for `NOT_SELECTED`,
`OWNER_BROADCAST` and `CAMPSITE_REMOVED`), or `null` if the ID it needs is missing.
`displayCategory` is the label the inbox shows: "Won", "Not selected", "Reservation",
"Owner message", "Message" or "Campsite removed".

To send many at once (e.g. a draw's results), use `addAll(notifications)` (US 01.03.01). It saves each
notification under its own `id`, in Firestore transactions of up to 500 (the most one allows), and
writing the same IDs again overwrites them instead of adding copies. A notification that's already
saved keeps its `read` and `shownOnDevice`, so sending it again doesn't make a read item unread or show
it on the phone twice. If a later transaction fails the earlier ones stay saved, so call it again with
the same list to finish. Transactions need a connection, so `addAll` fails offline. Set the `id`
yourself; a blank `id` throws `IllegalArgumentException` and nothing is saved.

A draw's results (US 01.03.01, 01.03.02) use the ID `draw_<lotteryId>_<userId>`, so each entrant gets
one per draw however often it runs. Both kinds are from `"system"` with `lotteryId` set and `campsiteId`
set only when the lottery covers one campsite. A win is `WON` with the title "You won the lottery!" and
the message "You won a spot at <campsite names>. Reserve your nights before <booking end>." A loss is
`NOT_SELECTED` with the title "Not selected this time" and the message "You weren't selected for
<campsite names> this time." Someone in both lists only gets the win. `NotificationService.sendDrawResults`
builds them all and saves them in one `addAll`. When a winner declines and a `LOST` entrant is
promoted (T-12), `NotificationService.prepareReplacementWinNotice` prepares a separate deterministic
`WON` notice with ID `replacement_<lotteryId>_<userId>` and `createdAt = promotionTime` (returning
`null` if the recipient opted out via `optedIn`), and `LotteryRepository.declineTransaction` commits
it atomically with the decline and promotion—preserving `read` and `shownOnDevice` on any existing
deterministic notice, leaving `draw_<lotteryId>_<userId>` (`NOT_SELECTED`) intact as the original
draw record, and preventing original-manifest replays from overwriting the promotion. `batchId` stays
`null` for draws; it only groups owner broadcasts.

A rejected reservation (US 02.08.01) uses the ID `rejected_<reservationId>`, so the entrant gets one
per reservation however often it is sent. It is `RESERVATION_REJECTED` from the owner, with
`campsiteId` and `reservationId` set (a tap opens the reservation), the title "Reservation
rejected" and the message "<campsite name>: <check-in> to <check-out>. Reason: <reason>".
`NotificationService.sendReservationRejected(reservation, now)` builds it with
`Reservation.statusNotice` and saves it, but only for a `REJECTED` reservation with a reason and an
entrant who hasn't turned notifications off (`optedIn`); it returns whether it saved one.

An owner broadcast (US 02.10.01) goes to everyone on a lottery's waiting list as the owner sees it on
the Waiting List screen: `NotificationService.waitingListRecipients(lotteryId)` returns the user of
every `lotteryEntries` entry except `REMOVED` ones, before or after the draw, without duplicates.
(`Lottery.waitingUserIds` isn't used, because the draw empties it.) The owner sends it with
`NotificationService.sendOwnerBroadcast(senderId, lotteryId, message, batchId, now)`. It drops users
who turned notifications off and, unlike `optedIn`, users with no profile (a profile that can't be read
is still kept), then saves one `OWNER_BROADCAST` per recipient in one `addAll`. Each has the ID
`broadcast_<batchId>_<userId>`, the title "Message from owner", the trimmed text as `message`, the
owner's device ID as `senderId`, `lotteryId` set and `campsiteId` set only when the lottery covers one
campsite, and `read` and `shownOnDevice` false. Every notification from one send shares one `batchId`
and one `createdAt`, so the owner's Sent list (US 02.10.02) and the admin log (US 03.08.01) can group by
`batchId` to show one row per send, with the number of notifications in it as the recipient count.

`Notification.ownerMessageText(text)` is the empty-message check: it returns the text without the
whitespace around it, or `null` if nothing is left, and then nothing is sent. `sendOwnerBroadcast`
returns an `OwnerBroadcastResult`: `Sent(batchId, recipientCount)`, or `NobodyToNotify` (empty list or
everyone opted out), `EmptyMessage`, `LotteryNotFound` (missing or removed) or `NotOwner` (the sender
isn't the lottery's `ownerId`), which save nothing. A failed save (e.g. offline) throws; the Send
Notification screen keeps its `batchId` until a send works, so sending again writes the same IDs and
nobody gets a second copy.

Users who turned notifications off on Settings (`notificationsEnabled = false`) get none
(US 01.10.01). Before saving a notification for some users, call
`NotificationService.optedIn(userIds)`: it returns the ones who still want notifications. A user
with no profile, or whose profile can't be read, is kept. The draw already does this, and owner
broadcasts (02.10.01) skip opted-out users as described above; messages (02.12.01, 01.14.01),
rejections (02.08.01) and phone notifications (T-14) must too. A reservation's own "Reservation confirmed" notice is still written inside
`createReservation`, since it confirms what the user just did.

The inbox reads live with `listenToInbox(recipientId, listener)` (T-13): the `InboxListener` gets
that user's notifications, newest first, straight away and on every change, until the returned
`Subscription` is cancelled. If the inbox can't be loaded (e.g. permission denied) it gets
`onInboxError()` instead, and nothing more arrives. Firestore filters only by `recipientId` and sorts by `createdAt` on
the device, so no composite index is needed. `Subscription` keeps Firestore's
`ListenerRegistration` out of `domain/`.

Phone notifications (T-14) use two methods. `listUnshownOnDevice(recipientId)` returns that user's
notifications whose `shownOnDevice` is `false`, oldest first by `createdAt`. Firestore is only asked
for `recipientId ==` and `shownOnDevice == false` (equality filters only) and sorts on the device,
so no composite index is needed. `markShownOnDevice(notificationId)` sets `shownOnDevice` to `true`
in one transaction, only if it is still `false`, and writes no other field. It returns `true` if it
changed the notification, and `false` if there is no such notification or it was already shown, so
when the background check and the app-open check run at the same time only one of them posts it.
Transactions need a connection, so `markShownOnDevice` fails offline.

`NotificationService.showNewOnDevice(userId, notifier)` decides what to show on the phone and
returns how many it showed. `DeviceNotifier` (in `domain/service`) is how it posts them without
Android imports: `canPost()` says whether the app may show system notifications right now, and
`show(notification)` posts one. The Android version is `AndroidDeviceNotifier` in `data/device`:
it posts on the "Camp updates" channel (made in `CampApp.onCreate`) with the notification's `id` as
the tag, so posting it again replaces it, and tapping it opens `MainActivity` with the extras from
`DeviceNotificationLink`, whose `fromIntent` gets the screen from `Notification.target`, like the
inbox. `AppContainer.deviceNotifier` holds it; tests get a `FakeDeviceNotifier` that only records
what it was asked to show. Every type is shown.
- No profile (or it can't be read): nothing happens.
- `notificationsEnabled = false` (US 01.10.01): nothing is shown, and every unshown item is marked
  shown, so turning notifications back on doesn't bring back a pile of old items.
- `canPost()` is `false` (permission refused): nothing is shown or marked, so the inbox alone is
  used and the items can still be shown once permission is granted.
- Otherwise each item from `listUnshownOnDevice`, oldest first, is shown only if
  `markShownOnDevice` returns `true`, so each inbox item gives at most one phone notification.
  An item that fails is skipped and the rest are still handled; the method doesn't throw for it.

`DrawAndNotifyWorker` (WorkManager) runs this about every 15 minutes, even while the app is closed,
and once whenever the app opens; `MainActivity` schedules both with
`DrawAndNotifyWorker.schedule(context)` as unique work (`draw_and_notify_periodic`, kept if it's
already scheduled, and `draw_and_notify_app_open`, replaced), and both wait for a network
connection. Each run first starts the draws that are due, then calls `showNewOnDevice` for this
device's user, so a draw's result is shown in the same run. Each unshown inbox item is shown once
as a phone notification. Opted-out users get none (their items are marked shown), and when
permission is refused nothing is shown or marked.

On Android 13 and later, `AppRoot` asks for `POST_NOTIFICATIONS` once per app start, as soon as Home
is shown (never on Welcome), if it isn't granted yet. If the user allows it, `MainActivity` calls
`DrawAndNotifyWorker.checkNow(context)`, which runs one check straight away (the same unique
`draw_and_notify_app_open` work), so items already waiting in the inbox appear without waiting 15
minutes. If the user says no, nothing else happens and the in-app inbox is used alone. Intent tests
that show `AppRoot` grant the permission with `GrantPermissionRule`, so the system dialog doesn't
cover the screen.

Tapping a phone notification opens the same screen as tapping the item in the inbox. Both go
through `NavHostController.openNotificationTarget(target)` in `AppNavigation.kt`, the one place
that turns a `NotificationTarget` into a route. `MainActivity` reads the tap with
`DeviceNotificationLink.fromIntent` (in `onCreate` and `onNewIntent`), removes the extras with
`DeviceNotificationLink.clear` so it's handled once, and marks the item read with
`NotificationService.markRead`, like the inbox. `AppRoot` then opens the screen on top of the
Notifications tab once Home is shown, so Back returns to the inbox, as it does after tapping the
item there. On Welcome (no profile yet) the tap is dropped, and an intent with no tap, or whose
item has no target, just opens Home.

### `messages`: `Message` (`MessageRepository`)

| Field | Type | Notes |
|---|---|---|
| reservationId | string | one conversation per reservation |
| senderId | string | device ID of the owner or the entrant |
| text | string | must not be empty |
| createdAt | number | ms |

One conversation per reservation, between the reservation's entrant and the campsite's owner
(US 02.12.01, 01.14.01). Only those two can open it: `Reservation.canMessage(userId)`, and
`Reservation.otherSide(userId)` is the person to notify. Messaging works whatever the
reservation's status, so an owner can still explain a rejection. `Message.canSend(text)` stops
empty messages (only spaces counts as empty); the text is saved trimmed. Messages are never edited
or deleted, so the admin message log (03.12.01) can always show them.

- `addMessage(message)` saves a new message and returns its ID. Like `addCampsite`, it doesn't
  wait for the server, so sending works offline.
- `listenToThread(reservationId, listener)` sends the conversation, oldest first
  (`Message.inOrder`: by `createdAt`, then by ID), to a `ThreadListener` straight away and on every
  change, until the returned `Subscription` is cancelled; `onThreadError()` if it can't be read.
  Firestore filters only by `reservationId` and sorts on the device, so no composite index is
  needed. The fake re-sends the conversation after every `addMessage`, like Firestore.
- `getAllThreads()` is for the admin message log: every conversation, each oldest first, the one
  with the newest message first (`Message.threads`).

Each new message adds a `NEW_MESSAGE` notification to the other side's inbox
(`NotificationService.sendNewMessage`), unless they turned notifications off. Its ID is
`message_<messageId>`, it has the reservation's `campsiteId` and `reservationId` (so a tap opens
the conversation), the title "New message" and the message `<campsite name>: <text>`.

### `ratings`: `Rating`
Document ID = **`campsiteId_userId`**, so rating again replaces the old rating. Not used by a
repository yet (added by the rating story).

| Field | Type | Notes |
|---|---|---|
| campsiteId, userId | string | |
| stars | number | 1–5 |

### `images`: `ImageAsset` (`ImageRepository`)
One document per uploaded photo (US 02.01.02b). The photo itself is saved **in the document** as
Base64 text, so we need no file storage (Firebase Storage would need the paid Blaze plan) and no
new library.

| Field | Type | Notes |
|---|---|---|
| data | string | the JPEG as Base64 text; at most `ImageAsset.MAX_DATA_LENGTH` (900,000) characters, because a document can be at most 1 MB |
| uploaderId | string | device ID of who uploaded it |
| ownerType | string | `campsite` or `profile` (`ImageAsset.OWNER_CAMPSITE` / `OWNER_PROFILE`) |
| ownerId | string | the campsite's or profile's ID |
| label | string | shown in the admin image browser: the campsite name, or the user's name for a profile photo |
| createdAt | number | ms |

- **Saving a photo:** pick it with the phone's photo picker, then `PhotoReader.readJpeg(uri, ImageAsset.MAX_JPEG_BYTES)`
  shrinks it (longest side 1024 px, JPEG quality lowered until it is at most 600 KB). Make the
  record with `ImageAsset.fromJpeg(...)` and save it with `addImage`, which returns its ID; put that
  ID in the campsite's or profile's `imageId`. `addImage` throws if the image is too big
  (`isSmallEnough()`). Like `addCampsite`, it doesn't wait for the server, so it works offline.
- **Showing a photo:** use `SavedPhoto(imageId, ...)` from `ui/common/Photo.kt`. It loads the image
  through the app's `ImageRepository`, caches it, and shows a placeholder while there's none.
- **Replacing or removing a photo:** save the new `imageId` (or `null`) first, then
  `deleteImage(oldId)`. Profile photos use `ProfileRepository.setPhoto` instead, which does it
  all in one batch. An image never changes once saved; a new photo gets a new ID.
- `getAllImages()` lists every image, newest first, for the admin image browser (US 03.06.01).

## Adding to a repository

Each repository only has the basic methods for now. When your story needs more (a transaction,
a search, a delete), add the method to **the interface, the Firestore version and the fake** in the
same PR, and add a unit test with the fake.

## Seed data

`SeedLoader.seed()` adds, once for everyone:
- 2 owners (Priya Sharma, Daniel Okafor) and a demo entrant (Sam Patel)
- 4 campsites, one per region, each open from 45 days ago to 60 days ahead
- 5 lotteries: Hinton **open**; Banff, Jasper and Kananaskis **drawn** (booking now); an older Hinton one **closed**
- Sam is waiting on the open lottery and **lost** the Banff and Jasper draws

And for **your** device:
- waiting on the open lottery, **won** Jasper (can reserve), reserved Banff, Kananaskis and the old Hinton one
- 3 reservations (2 active, 1 past), with their nights in `heldNights`
- 3 notifications (a win, a confirmed reservation, an owner message)

Running it again does nothing if your device already has reservations. It isn't hooked up to a
button yet (that comes with T-02). To reload, delete the documents in the Firebase console.
