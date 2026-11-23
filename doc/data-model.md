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
| `data/device/` | `DeviceIdentityManager`: the device ID is the user's ID. |
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
| imageId | string? | profile photo, `null` if none |
| notificationsEnabled | bool | default `true`; the Settings switch (US 01.10.01) |
| isAdmin | bool | set by hand in the Firebase console |
| createdAt | number | ms |

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

Owners save changes with `updateLottery(lottery, callerId)` (US 02.04.06). In one transaction, it
verifies ownership (`callerId` matches `stored.ownerId`), validates edit eligibility (not drawn,
removed, or closed) and model rules against the current `waitingUserIds` count, and writes only
`nightsOffered`, `nightsDescription`, `registrationStart`, `registrationEnd`, `bookingStart`,
`bookingEnd`, `winnersCount`, and `waitlistCapacity`. It never overwrites `ownerId`, `campsiteIds`,
`drawnAt`, `removed`, `waitingUserIds`, `entriesVersion`, `committedDrawId`, `manifestChunkCount`,
`participantCount`, or `winnerCount`, preventing concurrent entrants from being reset or lost.

### `lotteryEntries`: `LotteryEntry` (`LotteryRepository`)
Document ID = **`lotteryId_userId`**, so a user can only have one entry per lottery.

| Field | Type | Notes |
|---|---|---|
| lotteryId | string | |
| userId | string | name comes from `getDisplayName(userId)` |
| groupSize | number | |
| joinedAt | number | ms |
| status | string | `WAITING`, `WON`, `LOST`, `DECLINED`, `RESERVED`, `EXPIRED`, `REMOVED` |
| replacementUserId | string? | default `null`; promoted replacement user ID saved when a win is declined (T-12) |
| replacementPromotedAt | number? | ms, default `null`; promotion time for post-commit notice recovery (T-12) |
| replacementNotified | bool | default `false`; true once the replacement notice is delivered or skipped by opt-out |

Existing `lotteryEntries` documents missing the three replacement fields default to `replacementUserId = null`, `replacementPromotedAt = null`, and `replacementNotified = false`.

A winner declines via `LotteryDrawer.declineAndDrawReplacement` (US 01.04.03, T-12), which shuffles the lottery's `LOST` user IDs outside the transaction with its injected `Random` and calls `declineTransaction(lotteryId, userId, candidateUserIds, now)`. In one atomic transaction it re-reads the lottery and declining entry, checks `LotteryEntry.canDecline(lottery, now)` (entry still `WON`, lottery not removed, `now` within inclusive `bookingStart..bookingEnd`), reads candidate entries in the supplied order until finding one still `LOST` in the same lottery, sets the declining entry to `DECLINED` (recording `replacementUserId`, `replacementPromotedAt`, and `replacementNotified = false` when promoted), and sets the chosen replacement entry to `WON`. If the candidate pool is empty, the valid decline still succeeds with no promotion; if the decline is stale, neither entry changes. Notification delivery runs after the commit (`NotificationService.sendReplacementWin`) and acknowledges handling with `markReplacementNotified(lotteryId, decliningUserId, expectedReplacementUserId, expectedPromotedAt)` (updating only `replacementNotified = true` in a transaction after verifying `DECLINED`, `replacementUserId`, and `replacementPromotedAt` still match, idempotently on duplicates); if delivery fails, reloading the Reserve screen (`ReserveController.fetchData` -> `LotteryDrawer.retryPendingReplacementNotice`) recovers delivery without running another draw. Cancelling or rejecting a reservation keeps the entry `RESERVED`, so no replacement winner is drawn. Transactions need a connection, so declining fails offline.

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
promoted (T-12), `NotificationService.sendReplacementWin` writes a separate deterministic `WON` notice
with ID `replacement_<lotteryId>_<userId>` and `createdAt = promotionTime` via `addAll` (respecting
`optedIn`), leaving `draw_<lotteryId>_<userId>` (`NOT_SELECTED`) intact as the original draw record;
starting unread and unshown, preserving `read`, `shownOnDevice`, and `createdAt` on retry, and
preventing original-manifest replays from overwriting the promotion. `batchId` stays `null` for
draws; it only groups owner broadcasts.

Users who turned notifications off on Settings (`notificationsEnabled = false`) get none
(US 01.10.01). Before saving a notification for some users, call
`NotificationService.optedIn(userIds)`: it returns the ones who still want notifications. A user
with no profile, or whose profile can't be read, is kept. The draw already does this; owner
broadcasts (02.10.01), messages (02.12.01, 01.14.01), rejections (02.08.01) and phone notifications
(T-14) must too. A reservation's own "Reservation confirmed" notice is still written inside
`createReservation`, since it confirms what the user just did.

The inbox reads live with `listenToInbox(recipientId, listener)` (T-13): the `InboxListener` gets
that user's notifications, newest first, straight away and on every change, until the returned
`Subscription` is cancelled. If the inbox can't be loaded (e.g. permission denied) it gets
`onInboxError()` instead, and nothing more arrives. Firestore filters only by `recipientId` and sorts by `createdAt` on
the device, so no composite index is needed. `Subscription` keeps Firestore's
`ListenerRegistration` out of `domain/`.

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
| label | string | shown in the admin image browser, e.g. the campsite name |
| createdAt | number | ms |

- **Saving a photo:** pick it with the phone's photo picker, then `PhotoReader.readJpeg(uri, ImageAsset.MAX_JPEG_BYTES)`
  shrinks it (longest side 1024 px, JPEG quality lowered until it is at most 600 KB). Make the
  record with `ImageAsset.fromJpeg(...)` and save it with `addImage`, which returns its ID; put that
  ID in the campsite's or profile's `imageId`. `addImage` throws if the image is too big
  (`isSmallEnough()`). Like `addCampsite`, it doesn't wait for the server, so it works offline.
- **Showing a photo:** use `SavedPhoto(imageId, ...)` from `ui/common/Photo.kt`. It loads the image
  through the app's `ImageRepository`, caches it, and shows a placeholder while there's none.
- **Replacing or removing a photo:** save the new `imageId` (or `null`) first, then
  `deleteImage(oldId)`. An image never changes once saved; a new photo gets a new ID.
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
