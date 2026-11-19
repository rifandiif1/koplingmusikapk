const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_alice_123";
const GUEST_UID = "guest_bob_456";
const OUTSIDER_UID = "outsider_eve_789";
const ROOM_CODE = "KPL74M";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validRoomPayload(hostUid, now = new Date(Date.now() - 2000)) {
  return {
    hostUid,
    hostName: "Alice",
    roomName: "Studio Malam",
    status: "active",
    guestsCanControl: false,
    memberUids: [hostUid],
    memberNames: [`${hostUid}|Alice`],
    currentTrackId: "dQw4w9WgXcQ",
    currentTrackTitle: "King For A Day",
    currentTrackArtist: "Pierce The Veil",
    currentTrackAlbum: "Collide with the Sky",
    currentTrackArtwork: "https://lh3.googleusercontent.com/art",
    currentTrackDurationMs: 236000,
    playing: true,
    positionMs: 12000,
    syncSequence: 1,
    queueTracks: [],
    lastActionBy: "Alice membuat ruang",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write listen_rooms or recent_rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("listen_rooms").doc(ROOM_CODE).get());
  await assertFails(unauthDb.collection("listen_rooms").get());
  await assertFails(
    unauthDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(HOST_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(HOST_UID).collection("recent_rooms").get()
  );
});

test("Host can create a valid room and query own joined rooms; unfiltered list fails", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(HOST_UID))
  );

  // Matches Kotlin query: whereArrayContains("memberUids", uid)
  await assertSucceeds(
    hostDb.collection("listen_rooms").where("memberUids", "array-contains", HOST_UID).get()
  );

  // Unfiltered list query MUST be rejected
  await assertFails(hostDb.collection("listen_rooms").get());
});

test("Room creation rejects invalid room codes, spoofed hostUid, extra fields, and future timestamps", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();

  // Invalid lowercase / short room code
  await assertFails(
    hostDb.collection("listen_rooms").doc("abc12").set(validRoomPayload(HOST_UID))
  );

  // Identity spoofing (setting someone else as hostUid)
  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(GUEST_UID))
  );

  // Shadow field injection (including forbidden @DocumentId 'id')
  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set({
      ...validRoomPayload(HOST_UID),
      id: ROOM_CODE,
    })
  );

  // Tampered future timestamp
  await assertFails(
    hostDb
      .collection("listen_rooms")
      .doc(ROOM_CODE)
      .set(validRoomPayload(HOST_UID, new Date(Date.now() + 60000)))
  );
});

test("Guest can look up room by 6-char code, join, leave, and respect guestsCanControl", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const guestDb = testEnv.authenticatedContext(GUEST_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(HOST_UID))
  );

  // Guest gets room by code (from typing code or scanning QR)
  await assertSucceeds(guestDb.collection("listen_rooms").doc(ROOM_CODE).get());

  // Guest joins room (adds only self)
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      memberUids: [HOST_UID, GUEST_UID],
      memberNames: [`${HOST_UID}|Alice`, `${GUEST_UID}|Bob`],
      lastActionBy: "Bob bergabung",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // While guestsCanControl == false, guest CANNOT change playback
  await assertFails(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: false,
      positionMs: 25000,
      syncSequence: 2,
      lastActionBy: "Bob menjeda",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Host enables guestsCanControl
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      guestsCanControl: true,
      lastActionBy: "Alice mengizinkan kontrol peserta",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Now joined guest CAN sync playback and add queue track
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: false,
      positionMs: 30000,
      syncSequence: 2,
      queueTracks: [
        {
          trackId: "abc12345678",
          title: "Caraphernelia",
          artist: "Pierce The Veil",
          album: "Selfish Machines",
          artworkUrl: "https://lh3.googleusercontent.com/art2",
          durationMs: 267000,
          addedByName: "Bob",
          addedByUid: GUEST_UID,
        },
      ],
      lastActionBy: "Bob menambahkan Caraphernelia",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Non-member outsider STILL cannot mutate playback even when guestsCanControl == true
  await assertFails(
    outsiderDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: true,
      positionMs: 0,
      syncSequence: 3,
      lastActionBy: "Eve hijack",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Guest cannot mutate host settings or end the room
  await assertFails(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "ended",
      lastActionBy: "Bob menutup",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Guest leaves room cleanly (removes only self)
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      memberUids: [HOST_UID],
      memberNames: [`${HOST_UID}|Alice`],
      lastActionBy: "Bob keluar",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Terminal state locking: ended room rejects all subsequent updates", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(HOST_UID))
  );

  // Host ends room
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "ended",
      lastActionBy: "Alice mengakhiri sesi",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Subsequent updates MUST fail (terminal state locked)
  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "active",
      lastActionBy: "Reopen attempt",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User-scoped recent_rooms enforces strict per-user isolation", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const guestDb = testEnv.authenticatedContext(GUEST_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    hostDb
      .collection("users")
      .doc(HOST_UID)
      .collection("recent_rooms")
      .doc(ROOM_CODE)
      .set({
        userId: HOST_UID,
        roomName: "Studio Malam",
        hostName: "Alice",
        role: "host",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Owner can list & update
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("recent_rooms").get()
  );
  await assertSucceeds(
    hostDb
      .collection("users")
      .doc(HOST_UID)
      .collection("recent_rooms")
      .doc(ROOM_CODE)
      .update({
        roomName: "Studio Malam Remix",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Cross-user read/list/write MUST fail
  await assertFails(
    guestDb.collection("users").doc(HOST_UID).collection("recent_rooms").get()
  );
  await assertFails(
    guestDb
      .collection("users")
      .doc(HOST_UID)
      .collection("recent_rooms")
      .doc(ROOM_CODE)
      .get()
  );
  await assertFails(
    guestDb
      .collection("users")
      .doc(HOST_UID)
      .collection("recent_rooms")
      .doc(ROOM_CODE)
      .delete()
  );
});
