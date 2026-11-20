const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validPetOwnerPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    fullName: "Elena Vance",
    email: "elena.vance@example.com",
    mobileNumber: "+1 555-019-2834",
    residentialAddress: "420 Maple Grove Avenue, Portland, OR",
    petName: "Barnaby",
    petType: "Dog",
    petBreed: "Golden Retriever",
    petAge: "3 years",
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user cannot list, get, create, update, or delete pet owners", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("pet_owners").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("pet_owners").doc("reg_1").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("pet_owners").doc("reg_1").set(validPetOwnerPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("pet_owners").doc("reg_1").delete()
  );
});

test("Authenticated owner can create, read, list, partially update, and delete pet owner records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("pet_owners");

  // Empty collection list succeeds
  await assertSucceeds(colRef.get());

  // Create matching Kotlin payload (no `id` field)
  await assertSucceeds(colRef.doc("reg_1").set(validPetOwnerPayload(ALICE_UID)));

  // Get and list succeed
  await assertSucceeds(colRef.doc("reg_1").get());
  await assertSucceeds(colRef.orderBy("createdAt", "desc").get());

  // Partial update preserving immutable userId & createdAt succeeds
  await assertSucceeds(
    colRef.doc("reg_1").update({
      fullName: "Elena M. Vance",
      petAge: "4 years",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Delete succeeds
  await assertSucceeds(colRef.doc("reg_1").delete());
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("pet_owners")
      .doc("reg_alice")
      .set(validPetOwnerPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceCol = bobDb.collection("users").doc(ALICE_UID).collection("pet_owners");

  await assertFails(aliceCol.get());
  await assertFails(aliceCol.doc("reg_alice").get());
  await assertFails(aliceCol.doc("reg_bob_inject").set(validPetOwnerPayload(BOB_UID)));
  await assertFails(
    aliceCol.doc("reg_alice").update({
      fullName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceCol.doc("reg_alice").delete());
});

test("Validation & security boundaries reject invalid payloads, ghost fields, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("pet_owners");

  // Reject payload containing @DocumentId `id` field
  await assertFails(
    colRef.doc("reg_with_id").set(validPetOwnerPayload(ALICE_UID, { id: "reg_with_id" }))
  );

  // Reject shadow/ghost field
  await assertFails(
    colRef.doc("reg_ghost").set(validPetOwnerPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject spoofed userId
  await assertFails(
    colRef.doc("reg_spoof").set(validPetOwnerPayload(BOB_UID))
  );

  // Reject future timestamp
  await assertFails(
    colRef.doc("reg_future").set(
      validPetOwnerPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Reject empty/too-short fullName
  await assertFails(
    colRef.doc("reg_short").set(validPetOwnerPayload(ALICE_UID, { fullName: "A" }))
  );

  // Create valid doc, then test immutable createdAt/userId update rejection and ghost update rejection
  await assertSucceeds(colRef.doc("reg_valid").set(validPetOwnerPayload(ALICE_UID)));

  await assertFails(
    colRef.doc("reg_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    colRef.doc("reg_valid").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    colRef.doc("reg_valid").update({
      ghostField: "hack",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
