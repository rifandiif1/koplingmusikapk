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
    chatMessages: [],
    skipVoteUids: [],
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

  // Identity spoofing
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

test("Guest can join, collaborate on queue, live chat, vote skip, and respect guestsCanControl", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const guestDb = testEnv.authenticatedContext(GUEST_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).set(validRoomPayload(HOST_UID))
  );

  // Guest gets room by code & joins
  await assertSucceeds(guestDb.collection("listen_rooms").doc(ROOM_CODE).get());
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      memberUids: [HOST_UID, GUEST_UID],
      memberNames: [`${HOST_UID}|Alice`, `${GUEST_UID}|Bob`],
      lastActionBy: "Bob bergabung",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // While guestsCanControl == false, guest CANNOT change playback directly
  await assertFails(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: false,
      positionMs: 25000,
      syncSequence: 2,
      lastActionBy: "Bob menjeda",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Joined guest CAN add to collaborative shared queue even if guestsCanControl == false
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
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
          upvoterUids: [GUEST_UID],
        },
      ],
      syncSequence: 2,
      lastActionBy: "Bob menambahkan Caraphernelia ke antrian bersama",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Joined guest CAN send live chat message
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      chatMessages: [
        {
          id: "msg_1",
          uid: GUEST_UID,
          senderName: "Bob",
          text: "Lagunya keren banget!",
          sentAtMs: Date.now() - 750,
        },
      ],
      lastActionBy: "Bob: Lagunya keren banget!",
      updatedAt: new Date(Date.now() - 750),
    })
  );

  // Outsider CANNOT send chat, edit queue, or vote skip
  await assertFails(
    outsiderDb.collection("listen_rooms").doc(ROOM_CODE).update({
      chatMessages: [
        { id: "msg_evil", uid: OUTSIDER_UID, senderName: "Eve", text: "spam", sentAtMs: Date.now() },
      ],
      lastActionBy: "Eve: spam",
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertFails(
    outsiderDb.collection("listen_rooms").doc(ROOM_CODE).update({
      skipVoteUids: [OUTSIDER_UID],
      lastActionBy: "Eve vote skip",
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Vote Skip: Guest votes (1/2 -> not majority yet, so cannot skip track directly)
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      skipVoteUids: [GUEST_UID],
      lastActionBy: "Bob vote skip (1/2)",
      updatedAt: new Date(Date.now() - 600),
    })
  );

  // Guest cannot force majority skip when only 1/2 voted
  await assertFails(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      currentTrackId: "abc12345678",
      currentTrackTitle: "Caraphernelia",
      playing: true,
      positionMs: 0,
      syncSequence: 3,
      queueTracks: [],
      skipVoteUids: [],
      lastActionBy: "Force skip",
      updatedAt: new Date(Date.now() - 550),
    })
  );

  // Host casts 2nd vote -> majority (2/2) reached! Skip advances track & clears skipVoteUids
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      currentTrackId: "abc12345678",
      currentTrackTitle: "Caraphernelia",
      currentTrackArtist: "Pierce The Veil",
      currentTrackAlbum: "Selfish Machines",
      currentTrackArtwork: "https://lh3.googleusercontent.com/art2",
      currentTrackDurationMs: 267000,
      playing: true,
      positionMs: 0,
      syncSequence: 3,
      queueTracks: [],
      skipVoteUids: [],
      lastActionBy: "Vote Skip tercapai! Memutar Caraphernelia",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Host enables guestsCanControl
  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      guestsCanControl: true,
      lastActionBy: "Alice mengizinkan kontrol peserta",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Now joined guest CAN sync playback
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      playing: false,
      positionMs: 30000,
      syncSequence: 4,
      lastActionBy: "Bob menjeda pemutaran",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Guest cannot end the room
  await assertFails(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "ended",
      lastActionBy: "Bob menutup",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Guest leaves cleanly
  await assertSucceeds(
    guestDb.collection("listen_rooms").doc(ROOM_CODE).update({
      memberUids: [HOST_UID],
      memberNames: [`${HOST_UID}|Alice`],
      skipVoteUids: [],
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

  await assertSucceeds(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "ended",
      lastActionBy: "Alice mengakhiri sesi",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      status: "active",
      lastActionBy: "Reopen attempt",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    hostDb.collection("listen_rooms").doc(ROOM_CODE).update({
      chatMessages: [{ id: "m", uid: HOST_UID, senderName: "Alice", text: "hi", sentAtMs: Date.now() }],
      lastActionBy: "chat after end",
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

  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("recent_rooms").get()
  );
  await assertFails(
    guestDb.collection("users").doc(HOST_UID).collection("recent_rooms").get()
  );
  await assertFails(
    guestDb.collection("users").doc(HOST_UID).collection("recent_rooms").doc(ROOM_CODE).delete()
  );
});

test("User-scoped library_sync snapshot enforces ownership, schema bounds, and immutability", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const guestDb = testEnv.authenticatedContext(GUEST_UID).firestore();
  const created = new Date(Date.now() - 4000);

  const validSnapshot = {
    userId: HOST_UID,
    payloadJson: JSON.stringify({ likedTracks: [], playlists: [] }),
    likedCount: 12,
    playlistCount: 3,
    deviceLabel: "Pixel 9",
    createdAt: created,
    updatedAt: created,
  };

  // Unauthenticated blocked
  await assertFails(
    unauthDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").set(validSnapshot)
  );

  // Wrong docId blocked
  await assertFails(
    hostDb.collection("users").doc(HOST_UID).collection("library_sync").doc("other").set(validSnapshot)
  );

  // Owner create succeeds
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").set(validSnapshot)
  );

  // Owner read & partial update succeed
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").get()
  );
  await assertSucceeds(
    hostDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").update({
      payloadJson: JSON.stringify({ likedTracks: [{ id: "yt_1" }], playlists: [] }),
      likedCount: 13,
      playlistCount: 3,
      deviceLabel: "Pixel 9",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Tampering createdAt rejected
  await assertFails(
    hostDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read/write/delete rejected
  await assertFails(
    guestDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").get()
  );
  await assertFails(
    guestDb.collection("users").doc(HOST_UID).collection("library_sync").doc("snapshot").delete()
  );
});

