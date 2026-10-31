# Data model

How the app stores its data in Firestore, and the Kotlin classes that match. Class names follow the
**Part 2 CRC cards** on the wiki. Please read this before building on it, and comment on the PR if
your story needs a field or method that isn't here.

## Rules for every collection

- **Money** is a whole number of **cents** (`5000` = $50.00).
- **Dates** (check-in, registration start…) are `"yyyy-MM-dd"` strings, e.g. `"2027-05-08"`.
- **Times** (createdAt, joinedAt…) are **milliseconds since 1970** (`System.currentTimeMillis()`).
- **Enums** are saved as their name, e.g. `"HINTON"`, `"CONFIRMED"`.
- **Removed** items stay in Firestore with `removed = true`, and lists skip them.
- **Computed, never stored:** lottery status (Upcoming / Open / Drawn / Closed), a reservation being
  Active or Past (and "Completed"), a campsite's average rating.

## Code layout (`project/app/src/main/java/com/example/vipers/`)

| Folder | What's there |
|---|---|
| `domain/model/` | The data classes and enums below. No Firebase or Android imports. |
| `domain/repository/` | Repository interfaces: what you can do with the data. |
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
| notificationsEnabled | bool | default `true` |
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
| imageId | string? | promo photo |
| lat, lng | number? | set by Find location |
| ratingSum, ratingCount | number | average = sum / count |
| removed | bool | |

### `availability`: `AvailabilityCalendar`
Document ID = **campsite ID**. Not used by a repository yet (added by the availability story).

| Field | Type | Notes |
|---|---|---|
| openNights | string[] | dates the owner opened |
| heldNights | string[] | dates held by CONFIRMED reservations |

### `lotteries`: `Lottery` (`LotteryRepository`)

| Field | Type | Notes |
|---|---|---|
| ownerId | string | |
| campsiteIds | string[] | one lottery can cover several campsites |
| nightsOffered | string[] | dates |
| registrationStart, registrationEnd | string | joining period |
| bookingStart, bookingEnd | string | reserving period |
| winnersCount | number | ≥ 1 |
| waitlistCapacity | number? | `null` = no limit |
| drawnAt | number? | ms, `null` until the draw runs |
| removed | bool | |

### `lotteryEntries`: `LotteryEntry` (`LotteryRepository`)
Document ID = **`lotteryId_userId`**, so a user can only have one entry per lottery.

| Field | Type | Notes |
|---|---|---|
| lotteryId | string | |
| userId | string | |
| userName | string | copied for the owner's waiting list |
| groupSize | number | |
| joinedAt | number | ms |
| status | string | `WAITING`, `WON`, `LOST`, `DECLINED`, `RESERVED`, `EXPIRED`, `REMOVED` |

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

### `notifications`: `Notification` (`NotificationRepository`)

| Field | Type | Notes |
|---|---|---|
| recipientId | string | |
| senderId | string | device ID, or `"system"` |
| campsiteId | string? | |
| type | string | `WON`, `NOT_SELECTED`, `RESERVATION_CONFIRMED`, `RESERVATION_CANCELLED`, `RESERVATION_REJECTED`, `OWNER_BROADCAST`, `NEW_MESSAGE`, `CAMPSITE_REMOVED` |
| title, message | string | |
| read | bool | |
| shownOnDevice | bool | stops a phone notification showing twice |
| batchId | string? | groups one owner broadcast |
| createdAt | number | ms |

### `messages`: `Message`
Not used by a repository yet (added by the messaging story).

| Field | Type | Notes |
|---|---|---|
| reservationId | string | one conversation per reservation |
| senderId | string | |
| text | string | must not be empty |
| createdAt | number | ms |

### `ratings`: `Rating`
Document ID = **`campsiteId_userId`**, so rating again replaces the old rating. Not used by a
repository yet (added by the rating story).

| Field | Type | Notes |
|---|---|---|
| campsiteId, userId | string | |
| stars | number | 1–5 |

### `images`: `ImageAsset`
Not used by a repository yet (added by the photo story).

| Field | Type | Notes |
|---|---|---|
| storagePath | string | where the file is stored |
| uploaderId | string | |
| ownerType | string | `campsite` or `profile` |
| ownerId | string | |
| label | string | shown in the admin image browser |

## Adding to a repository

Each repository only has the basic methods for now. When your story needs more (a transaction,
a search, a delete), add the method to **the interface, the Firestore version and the fake** in the
same PR, and add a unit test with the fake.

## Seed data

`SeedLoader.seed()` adds 4 campsites (one per region), 2 lotteries (one open, one drawn), a
waiting-list entry and 3 reservations for **your** device (2 active, 1 past). Running it again does
nothing if your device already has reservations. To reload it, delete the documents in the
Firebase console.
